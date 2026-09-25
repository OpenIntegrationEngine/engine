// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.MessageContent;
import com.mirth.connect.donkey.model.message.Status;

/**
 * The entry points the generated smoke tests call (see :smoketest:generateSmokeTests
 * and the generated smoketest/build/generated/smoketest files).
 */
public final class Harness {

    /** Statuses that mean the server has not finished with the message yet. */
    private static final List<Status> PENDING_STATUSES = List.of(Status.PENDING, Status.QUEUED);

    private Harness() {
    }

    private static OieServer server() {
        return SharedServer.get();
    }

    /**
     * Skips the enclosing channel unless the running configuration is one it targets. An
     * empty configuration (a developer pointing the harness at a server by hand) runs
     * everything.
     */
    public static void assumeConfiguration(String... configurations) {
        String configuration = HarnessConfig.CONFIGURATION;
        Assumptions.assumeTrue(
                configuration == null || configuration.isBlank()
                        || Arrays.asList(configurations).contains(configuration),
                () -> "fixture is not enabled for configuration '" + configuration + "'");
    }

    /** Deploys the channel exported at {@code channelResource} and returns its id. */
    public static String deploy(String channelResource) throws Exception {
        return server().deployChannel(resource(channelResource), channelResource);
    }

    /** Undeploys and removes a channel, tolerating a null id left by a failed deploy. */
    public static void undeploy(String channelId) {
        if (channelId != null) {
            server().removeChannel(channelId);
        }
    }

    /**
     * Submits {@code <base>/source} (with {@code <base>/source_sourcemap.yml} when
     * {@code hasSourceMap}) into the channel, then retries the named assertion files until
     * they all hold or every message reaches a terminal state. Because messages are written
     * asynchronously, an early poll can legitimately fail; only a failure that persists once
     * the messages are terminal is a real failure.
     *
     * @param assertionFiles a bare file name for a payload that produces one message, or one
     *                       prefixed with the message's 1-based number when it produces several,
     *                       e.g. {@code "02/source_status"}.
     */
    public static void runMessage(String channelId, String base, boolean hasSourceMap, int expectedMessageCount,
            String... assertionFiles) throws Exception {
        String source = resource(base + "/source");
        Map<String, Object> sourceMap = sourceMap(base, hasSourceMap);

        // Load the fixtures once; the poll loop below may check them many times.
        List<Map<String, String>> assertions = loadAssertions(base, expectedMessageCount, assertionFiles);

        List<Long> messageIds;
        try {
            messageIds = server().submitMessage(channelId, source, sourceMap);
        } catch (ClientException e) {
            throw new AssertionError(base + " failed: the server refused the payload. Add source_rejected"
                    + " to the fixture if that is expected. " + e.getMessage(), e);
        }

        if (messageIds.size() != expectedMessageCount) {
            throw new AssertionError(base + " failed: expected the payload to produce " + expectedMessageCount
                    + " message(s), found " + messageIds.size() + " " + messageIds);
        }

        long deadline = System.nanoTime() + HarnessConfig.TIMEOUT.toNanos();
        AssertionError lastFailure = null;
        List<Message> lastMessages = List.of();
        while (System.nanoTime() < deadline) {
            List<Message> messages = fetchMessages(channelId, messageIds);
            lastMessages = messages;
            if (!messages.contains(null)) {
                try {
                    assertAll(messages, assertions);
                    return;
                } catch (AssertionError e) {
                    lastFailure = e;
                    if (messages.stream().allMatch(Harness::isTerminal)) {
                        break;
                    }
                }
            }
            Thread.sleep(500);
        }

        if (lastFailure != null) {
            throw new AssertionError(base + " failed: " + lastFailure.getMessage()
                    + "\n\n" + describe(lastMessages), lastFailure);
        }
        throw new AssertionError("Timed out after " + HarnessConfig.TIMEOUT.toSeconds() + "s waiting for message(s) "
                + messageIds + " for fixture " + base + "\n\n" + describe(lastMessages));
    }

    /**
     * Submits {@code <base>/source} and requires the server to refuse it. A refusal reaches the
     * client only as a {@link ClientException} carrying the status line as text, so the refusal
     * itself is the assertion.
     */
    public static void runRejectedMessage(String channelId, String base, boolean hasSourceMap) throws Exception {
        String source = resource(base + "/source");
        Map<String, Object> sourceMap = sourceMap(base, hasSourceMap);

        List<Long> messageIds;
        try {
            messageIds = server().submitMessage(channelId, source, sourceMap);
        } catch (ClientException refused) {
            return;
        }

        throw new AssertionError(base + " failed: expected the server to reject the payload, but it was accepted"
                + " and produced message(s) " + messageIds + "\n\n" + describe(fetchMessages(channelId, messageIds)));
    }

    /** Reads a staged fixture from the classpath. */
    static String resource(String path) {
        try (InputStream in = Harness.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture resource on the classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read fixture resource " + path, e);
        }
    }

    private static Map<String, Object> sourceMap(String base, boolean hasSourceMap) {
        return hasSourceMap
                ? MessageAssertions.parseSourceMap(resource(base + "/source_sourcemap.yml"))
                : new LinkedHashMap<>();
    }

    /** Groups the assertion files by the message they describe, keyed by their bare file name. */
    private static List<Map<String, String>> loadAssertions(String base, int expectedMessageCount,
            String[] assertionFiles) {
        List<Map<String, String>> assertions = new ArrayList<>();
        for (int index = 0; index < expectedMessageCount; index++) {
            assertions.add(new LinkedHashMap<>());
        }

        for (String path : assertionFiles) {
            int separator = path.lastIndexOf('/');
            int index = separator < 0 ? 0 : Integer.parseInt(path.substring(0, separator)) - 1;
            assertions.get(index).put(path.substring(separator + 1), resource(base + "/" + path));
        }
        return assertions;
    }

    private static void assertAll(List<Message> messages, List<Map<String, String>> assertions) {
        for (int index = 0; index < messages.size(); index++) {
            for (Map.Entry<String, String> assertion : assertions.get(index).entrySet()) {
                MessageAssertions.assertFixtureFile(messages.get(index), assertion.getKey(), assertion.getValue());
            }
        }
    }

    /** Reads each message back, leaving a null in place of one the server has not stored yet. */
    private static List<Message> fetchMessages(String channelId, List<Long> messageIds) throws ClientException {
        List<Message> messages = new ArrayList<>(messageIds.size());
        for (Long messageId : messageIds) {
            messages.add(server().fetchMessage(channelId, messageId));
        }
        return messages;
    }

    /** True once the server has finished processing and no connector is still pending. */
    private static boolean isTerminal(Message message) {
        if (!message.isProcessed()) {
            return false;
        }
        Map<Integer, ConnectorMessage> connectorMessages = message.getConnectorMessages();
        if (connectorMessages == null) {
            return false;
        }
        return connectorMessages.values().stream()
                .noneMatch(connectorMessage -> PENDING_STATUSES.contains(connectorMessage.getStatus()));
    }

    /** Renders the messages the way a fixture author needs to see them to fix a mismatch. */
    private static String describe(List<Message> messages) {
        if (messages.isEmpty()) {
            return "No messages were retrieved from the server.";
        }

        StringBuilder detail = new StringBuilder();
        for (int index = 0; index < messages.size(); index++) {
            if (index > 0) {
                detail.append("\n\n");
            }
            if (messages.size() > 1) {
                detail.append("--- message ").append(index + 1).append(" of ").append(messages.size()).append(" ---\n");
            }
            detail.append(describeMessage(messages.get(index)));
        }
        return detail.toString();
    }

    private static String describeMessage(Message message) {
        if (message == null) {
            return "No message was retrieved from the server.";
        }

        StringBuilder detail = new StringBuilder("Actual message ").append(message.getMessageId())
                .append(" (processed=").append(message.isProcessed()).append("):");
        Map<Integer, ConnectorMessage> connectorMessages = message.getConnectorMessages();
        if (connectorMessages == null) {
            return detail.append("\n  <no connector messages>").toString();
        }

        connectorMessages.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ConnectorMessage connectorMessage = entry.getValue();
            detail.append("\n  [").append(entry.getKey()).append("] ").append(connectorMessage.getConnectorName())
                    .append(" status=").append(connectorMessage.getStatus());
            appendContent(detail, "raw", connectorMessage.getRaw());
            appendContent(detail, "transformed", connectorMessage.getTransformed());
            appendContent(detail, "encoded", connectorMessage.getEncoded());
            appendContent(detail, "sent", connectorMessage.getSent());
            appendContent(detail, "response", connectorMessage.getResponse());
            detail.append("\n        sourceMap=").append(connectorMessage.getSourceMap())
                    .append("\n        connectorMap=").append(connectorMessage.getConnectorMap())
                    .append("\n        metaDataMap=").append(connectorMessage.getMetaDataMap());
            if (connectorMessage.getProcessingError() != null) {
                detail.append("\n        processingError=").append(connectorMessage.getProcessingError());
            }
        });
        return detail.toString();
    }

    private static void appendContent(StringBuilder detail, String label, MessageContent content) {
        if (content != null && content.getContent() != null) {
            detail.append("\n        ").append(label).append('=').append(quote(content.getContent()));
        }
    }

    private static String quote(String content) {
        String escaped = content.replace("\r\n", "\\n").replace("\r", "\\n").replace("\n", "\\n");
        return "\"" + (escaped.length() > 2000 ? escaped.substring(0, 2000) + "\"... (truncated)" : escaped + "\"");
    }
}
