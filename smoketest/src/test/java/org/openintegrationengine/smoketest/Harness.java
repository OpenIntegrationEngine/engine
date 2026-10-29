// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import com.mirth.connect.donkey.model.message.attachment.Attachment;

/**
 * The entry points the generated smoke tests call (see :smoketest:generateSmokeTests
 * and the generated smoketest/build/generated/smoketest files).
 */
public final class Harness {

    /** Statuses that mean the server has not finished with the message yet. */
    private static final List<Status> PENDING_STATUSES = List.of(Status.PENDING, Status.QUEUED);

    /**
     * How long to keep retrying after the message looks terminal. Some work outruns the
     * statuses: a channel with a queued destination and removeContentOnCompletion deletes
     * its content in a transaction committed after the destination is already SENT.
     */
    private static final Duration TERMINAL_GRACE = Duration.ofSeconds(5);

    /** How often to re-read a message while waiting for it to reach a state. */
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

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
     * they all hold or every message has been terminal for {@link #TERMINAL_GRACE}. Because
     * messages are written asynchronously, an early poll can legitimately fail; only a failure
     * that outlives the messages' terminal state is a real failure.
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
        // Attachments are a second read against the server, so only pay for it when asked.
        boolean withAttachments = Arrays.stream(assertionFiles)
                .anyMatch(path -> MessageAssertions.isAttachmentFixture(bareName(path)));

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
        List<List<Attachment>> lastAttachments = List.of();
        long graceDeadline = 0;
        while (System.nanoTime() < deadline) {
            List<Message> messages = fetchMessages(channelId, messageIds);
            lastMessages = messages;
            if (!messages.contains(null)) {
                List<List<Attachment>> attachments = fetchAttachments(channelId, messageIds, withAttachments);
                lastAttachments = attachments;
                try {
                    assertAll(messages, attachments, assertions);
                    return;
                } catch (AssertionError e) {
                    lastFailure = e;
                    if (messages.stream().allMatch(Harness::isTerminal)) {
                        if (graceDeadline == 0) {
                            graceDeadline = System.nanoTime() + TERMINAL_GRACE.toNanos();
                        } else if (System.nanoTime() >= graceDeadline) {
                            break;
                        }
                    }
                }
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }

        if (lastFailure != null) {
            throw new AssertionError(base + " failed: " + lastFailure.getMessage()
                    + "\n\n" + describe(lastMessages, lastAttachments), lastFailure);
        }
        throw new AssertionError("Timed out after " + HarnessConfig.TIMEOUT.toSeconds() + "s waiting for message(s) "
                + messageIds + " for fixture " + base + "\n\n" + describe(lastMessages, lastAttachments));
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
                + " and produced message(s) " + messageIds + "\n\n"
                + describe(fetchMessages(channelId, messageIds), List.of()));
    }

    /**
     * Polls until one connector of one message reaches {@code status}, and returns it. Java
     * tests need this where {@link #runMessage} cannot help: it stops at the first terminal
     * state, so it can never observe a message mid-flight.
     */
    public static ConnectorMessage awaitConnectorStatus(String channelId, long messageId, int metaDataId,
            Status status) throws Exception {
        long deadline = System.nanoTime() + HarnessConfig.TIMEOUT.toNanos();
        Status lastStatus = null;
        do {
            Message message = server().fetchMessage(channelId, messageId);
            ConnectorMessage connectorMessage = message == null ? null
                    : message.getConnectorMessages().get(metaDataId);
            if (connectorMessage != null) {
                lastStatus = connectorMessage.getStatus();
                if (lastStatus == status) {
                    return connectorMessage;
                }
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        } while (System.nanoTime() < deadline);

        throw new AssertionError("Timed out after " + HarnessConfig.TIMEOUT.toSeconds() + "s waiting for connector "
                + metaDataId + " of message " + messageId + " to reach " + status + "; last status was " + lastStatus);
    }

    /**
     * Polls until the server has marked one message processed, and returns it. A connector reaching
     * its final status is not the end of the message: the engine still has the postprocessor to run
     * and the message row to mark, and that last commit is where the remaining statistics land. A
     * test that reads anything channel-wide has to wait for it.
     */
    public static Message awaitProcessed(String channelId, long messageId) throws Exception {
        long deadline = System.nanoTime() + HarnessConfig.TIMEOUT.toNanos();
        Message message = null;
        do {
            message = server().fetchMessage(channelId, messageId);
            if (message != null && message.isProcessed()) {
                return message;
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        } while (System.nanoTime() < deadline);

        throw new AssertionError("Timed out after " + HarnessConfig.TIMEOUT.toSeconds() + "s waiting for message "
                + messageId + " of channel " + channelId + " to be processed\n\n"
                + describe(message == null ? List.of() : List.of(message), List.of()));
    }

    /**
     * Polls until at least {@code minimum} messages are queued for one connector. Queue tests use
     * this to know a queue has really built up - past its in-memory buffer, say - before releasing
     * whatever is holding it, so that the depth is a precondition the test enforces rather than one
     * it hopes for.
     */
    public static void awaitQueueSizeAtLeast(String channelId, int metaDataId, long minimum) throws Exception {
        long deadline = System.nanoTime() + HarnessConfig.TIMEOUT.toNanos();
        Long lastSize = null;
        do {
            lastSize = server().queueSize(channelId, metaDataId);
            if (lastSize != null && lastSize >= minimum) {
                return;
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        } while (System.nanoTime() < deadline);

        throw new AssertionError("Timed out after " + HarnessConfig.TIMEOUT.toSeconds() + "s waiting for connector "
                + metaDataId + " of channel " + channelId + " to have at least " + minimum
                + " messages queued; last size was " + lastSize);
    }

    /**
     * Polls until a connector's queue has fallen to {@code maximum} messages or fewer. The mirror of
     * {@link #awaitQueueSizeAtLeast}, for a test that emptied a queue rather than filled one.
     */
    public static void awaitQueueSizeAtMost(String channelId, int metaDataId, long maximum) throws Exception {
        long deadline = System.nanoTime() + HarnessConfig.TIMEOUT.toNanos();
        Long lastSize = null;
        do {
            lastSize = server().queueSize(channelId, metaDataId);
            if (lastSize != null && lastSize <= maximum) {
                return;
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        } while (System.nanoTime() < deadline);

        throw new AssertionError("Timed out after " + HarnessConfig.TIMEOUT.toSeconds() + "s waiting for connector "
                + metaDataId + " of channel " + channelId + " to have at most " + maximum
                + " messages queued; last size was " + lastSize);
    }

    /**
     * Reads one connector of one message as it stands right now, without waiting for anything.
     * This is for asserting where a message has <em>not</em> got to, which is only sound once
     * something else has proved the engine went past it - never on its own, as a message that has
     * simply not been picked up yet looks identical.
     */
    public static ConnectorMessage connectorMessage(String channelId, long messageId, int metaDataId)
            throws Exception {
        Message message = server().fetchMessage(channelId, messageId);
        ConnectorMessage connectorMessage = message == null ? null
                : message.getConnectorMessages().get(metaDataId);
        if (connectorMessage == null) {
            throw new AssertionError("Message " + messageId + " of channel " + channelId
                    + " has no connector " + metaDataId);
        }
        return connectorMessage;
    }

    /** Stops a deployed channel, so its queue threads are no longer running. */
    public static void stopChannel(String channelId) throws Exception {
        server().stopChannel(channelId);
    }

    /** Halts a deployed channel, interrupting whatever it is processing rather than waiting. */
    public static void haltChannel(String channelId) throws Exception {
        server().haltChannel(channelId);
    }

    /** Starts a stopped channel back up. */
    public static void startChannel(String channelId) throws Exception {
        server().startChannel(channelId);
    }

    /** Undeploys a channel without removing it, leaving everything it stored in place. */
    public static void undeployChannel(String channelId) throws Exception {
        server().undeployChannel(channelId);
    }

    /** Sets one configuration map entry, which channel scripts read back as {@code configurationMap}. */
    public static void setConfigurationProperty(String key, String value) throws Exception {
        server().setConfigurationProperty(key, value);
    }

    /** Reads a staged fixture from the classpath as UTF-8 text. */
    static String resource(String path) {
        return resource(path, StandardCharsets.UTF_8);
    }

    /**
     * Reads a staged fixture from the classpath. Most fixtures are text and are read as UTF-8;
     * an attachment's content can be any bytes at all, so it is read as ISO-8859-1, which maps
     * every byte to one char and back again and so compares byte for byte.
     */
    static String resource(String path, Charset charset) {
        try (InputStream in = Harness.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture resource on the classpath: " + path);
            }
            return new String(in.readAllBytes(), charset);
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
            String fileName = bareName(path);
            assertions.get(index).put(fileName,
                    resource(base + "/" + path, MessageAssertions.charsetFor(fileName)));
        }
        return assertions;
    }

    /** The assertion file's own name, with the {@code NN/} message prefix a multi-message fixture adds. */
    private static String bareName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static void assertAll(List<Message> messages, List<List<Attachment>> attachments,
            List<Map<String, String>> assertions) {
        for (int index = 0; index < messages.size(); index++) {
            for (Map.Entry<String, String> assertion : assertions.get(index).entrySet()) {
                MessageAssertions.assertFixtureFile(messages.get(index), attachments.get(index),
                        assertion.getKey(), assertion.getValue());
            }
        }
    }

    /** Reads each message's attachments, or hands back empty lists when no fixture asks for them. */
    private static List<List<Attachment>> fetchAttachments(String channelId, List<Long> messageIds,
            boolean withAttachments) throws ClientException {
        List<List<Attachment>> attachments = new ArrayList<>(messageIds.size());
        for (Long messageId : messageIds) {
            attachments.add(withAttachments ? server().fetchAttachments(channelId, messageId) : List.<Attachment>of());
        }
        return attachments;
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
    private static String describe(List<Message> messages, List<List<Attachment>> attachments) {
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
            detail.append(describeMessage(messages.get(index),
                    index < attachments.size() ? attachments.get(index) : List.of()));
        }
        return detail.toString();
    }

    private static String describeMessage(Message message, List<Attachment> attachments) {
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
            appendContent(detail, "responseTransformed", connectorMessage.getResponseTransformed());
            appendContent(detail, "processedResponse", connectorMessage.getProcessedResponse());
            detail.append("\n        sourceMap=").append(connectorMessage.getSourceMap())
                    .append("\n        connectorMap=").append(connectorMessage.getConnectorMap())
                    .append("\n        metaDataMap=").append(connectorMessage.getMetaDataMap());
            if (connectorMessage.getProcessingError() != null) {
                detail.append("\n        processingError=").append(connectorMessage.getProcessingError());
            }
        });
        MessageAssertions.order(message, attachments).forEach(attachment -> detail
                .append("\n  attachment ").append(attachment.getId())
                .append(" type=").append(attachment.getType())
                .append(" content=")
                .append(quote(new String(attachment.getContent(), StandardCharsets.ISO_8859_1))));
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
