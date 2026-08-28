// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;

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
     * they all hold or the message reaches a terminal state. Because the message is written
     * asynchronously, an early poll can legitimately fail; only a failure that persists once
     * the message is terminal is a real failure.
     */
    public static void runMessage(String channelId, String base, boolean hasSourceMap, String... assertionFiles)
            throws Exception {
        String source = resource(base + "/source");
        Map<String, Object> sourceMap = hasSourceMap
                ? MessageAssertions.parseSourceMap(resource(base + "/source_sourcemap.yml"))
                : new LinkedHashMap<>();

        // Load the fixtures once; the poll loop below may check them many times.
        Map<String, String> assertions = new LinkedHashMap<>();
        for (String fileName : assertionFiles) {
            assertions.put(fileName, resource(base + "/" + fileName));
        }

        long messageId = server().submitMessage(channelId, source, sourceMap);

        long deadline = System.nanoTime() + HarnessConfig.TIMEOUT.toNanos();
        AssertionError lastFailure = null;
        Message lastMessage = null;
        while (System.nanoTime() < deadline) {
            Message message = server().fetchMessage(channelId, messageId);
            if (message != null) {
                lastMessage = message;
                try {
                    for (Map.Entry<String, String> assertion : assertions.entrySet()) {
                        MessageAssertions.assertFixtureFile(message, assertion.getKey(), assertion.getValue());
                    }
                    return;
                } catch (AssertionError e) {
                    lastFailure = e;
                    if (isTerminal(message)) {
                        break;
                    }
                }
            }
            Thread.sleep(500);
        }

        if (lastFailure != null) {
            throw new AssertionError(base + " failed: " + lastFailure.getMessage()
                    + "\n\n" + describe(lastMessage), lastFailure);
        }
        throw new AssertionError("Timed out after " + HarnessConfig.TIMEOUT.toSeconds() + "s waiting for message "
                + messageId + " for fixture " + base + "\n\n" + describe(lastMessage));
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

    /** Renders the message the way a fixture author needs to see it to fix a mismatch. */
    private static String describe(Message message) {
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
            detail.append("\n        connectorMap=").append(connectorMessage.getConnectorMap())
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
