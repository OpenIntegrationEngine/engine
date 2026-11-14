// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.TestReporter;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.MessageContent;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.model.ChannelStatistics;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

/**
 * What the load tests need from the server, and nothing about the load itself.
 *
 * <p>{@code loadtest/load-control.xml} owns a run end to end: it writes the payload files
 * inside the server container, knows where they live, and reports the digest and route each
 * message should produce. The harness only issues commands and checks the answers against
 * that report, so the payload rules live in one place and the harness never needs a view of
 * the server's filesystem.
 */
final class LoadSupport {

    static final String CONTROL_CHANNEL = "loadtest/load-control.xml";

    /**
     * Ceiling on any one phase of a load run. The fixture tests' 90s is sized for a single
     * message and is far too tight for a thousand of them on a slow database.
     */
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private static final int PAGE_SIZE = 100;

    /** Stored response content is a serialised {@link Response}, same as the fixture tests see. */
    private static final Pattern RESPONSE_ENVELOPE = Pattern.compile("^\\s*<response[\\s>].*", Pattern.DOTALL);

    private LoadSupport() {
    }

    // ------------------------------------------------------------- control channel

    /**
     * Runs one control-channel command and returns its reply. The control channel answers
     * before processing so a long command cannot outlive the client's request timeout, which
     * is why this polls for the reply rather than reading it from the submit.
     */
    static String control(String controlChannelId, String command, Map<String, Object> arguments)
            throws Exception {
        Map<String, Object> sourceMap = new LinkedHashMap<>(arguments);
        sourceMap.put("cmd", command);

        List<Long> messageIds = SharedServer.get().submitMessage(controlChannelId, command, sourceMap);
        if (messageIds.size() != 1) {
            throw new AssertionError("Control command '" + command + "' produced " + messageIds.size()
                    + " messages, expected exactly one: " + messageIds);
        }
        long messageId = messageIds.get(0);

        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Message message = SharedServer.get().fetchMessage(controlChannelId, messageId);
            if (message != null && message.isProcessed()) {
                ConnectorMessage connectorMessage = message.getConnectorMessages().get(1);
                String reply = responseText(connectorMessage == null ? null : connectorMessage.getResponse());
                if (reply == null) {
                    throw new AssertionError("Control command '" + command + "' produced no response: "
                            + (connectorMessage == null ? "no connector message"
                                    : connectorMessage.getStatus() + " " + connectorMessage.getProcessingError()));
                }
                return reply;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Control command '" + command + "' did not finish within "
                + TIMEOUT.toSeconds() + "s");
    }

    /**
     * What a prepared run should produce, as the control channel reports it: the digest each
     * message's payload hashes to, and which loopback it should be routed over.
     */
    record Expectations(List<String> digests, List<String> routes, long bytes) {

        int count() {
            return digests.size();
        }
    }

    /**
     * Stages a run of {@code count} messages for {@code run} and returns what to expect. The
     * files land in a staging directory, so nothing is visible to a File Reader until
     * {@link #arm}.
     */
    static Expectations prepare(String controlChannelId, String run, int count) throws Exception {
        String reply = control(controlChannelId, "prepare", Map.of("run", run, "count", String.valueOf(count)));

        String[] lines = reply.split("\n");
        long bytes = Long.parseLong(lines[0].replaceFirst(".*;bytes=", "").trim());

        List<String> digests = new ArrayList<>(count);
        List<String> routes = new ArrayList<>(count);
        for (int i = 1; i < lines.length; i++) {
            String[] fields = lines[i].trim().split(",");
            if (fields.length != 3) {
                continue;
            }
            int seq = Integer.parseInt(fields[0]);
            while (digests.size() <= seq) {
                digests.add(null);
                routes.add(null);
            }
            digests.set(seq, fields[1]);
            routes.set(seq, fields[2]);
        }

        if (digests.size() != count) {
            throw new AssertionError("Control channel staged " + digests.size() + " of " + count + " messages");
        }
        return new Expectations(digests, routes, bytes);
    }

    /** Makes a prepared run visible to its File Reader, all at once. */
    static void arm(String controlChannelId, String run) throws Exception {
        control(controlChannelId, "arm", Map.of("run", run));
    }

    static void clean(String controlChannelId) throws Exception {
        control(controlChannelId, "clean", Map.of());
    }

    /** A snapshot of the server JVM, taken either side of a run. */
    record Sample(long cpuNanos, long heapUsed, long gcCount, long gcMillis, long threads) {

        static Sample parse(String reply) {
            Map<String, Long> values = new LinkedHashMap<>();
            for (String pair : reply.trim().split(";")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    values.put(parts[0].trim(), Long.parseLong(parts[1].trim()));
                }
            }
            return new Sample(values.getOrDefault("cpuNanos", -1L), values.getOrDefault("heapUsed", -1L),
                    values.getOrDefault("gcCount", -1L), values.getOrDefault("gcMillis", -1L),
                    values.getOrDefault("threads", -1L));
        }
    }

    static Sample stats(String controlChannelId) throws Exception {
        return Sample.parse(control(controlChannelId, "stats", Map.of()));
    }

    // ------------------------------------------------------------------- polling

    /**
     * Waits until the channel's lifetime received count has risen by {@code expected} and
     * returns how long that took. Lifetime counters survive a redeploy, hence the baseline.
     */
    static long awaitReceived(String channelId, long baseline, int expected, long startNanos) throws Exception {
        long deadline = startNanos + TIMEOUT.toNanos();
        long received = 0;
        while (System.nanoTime() < deadline) {
            received = SharedServer.get().statistics(channelId).getReceived() - baseline;
            if (received >= expected) {
                return System.nanoTime() - startNanos;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Only " + received + " of " + expected + " messages reached channel " + channelId
                + " within " + TIMEOUT.toSeconds() + "s");
    }

    /**
     * Waits until every dispatch the run should produce has finished. The received counter
     * rises when a message is created, not when its destinations are done, so on its own it
     * lets a run be read back while the last messages are still in flight.
     *
     * <p>Errors count as finished: a run that errors should fail on the assertion that says
     * so, not by sitting here until the timeout.
     */
    static void awaitDispatched(String channelId, long sentBaseline, long errorBaseline, long expected)
            throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        long finished = 0;
        while (System.nanoTime() < deadline) {
            ChannelStatistics statistics = SharedServer.get().statistics(channelId);
            finished = statistics.getSent() - sentBaseline + statistics.getError() - errorBaseline;
            if (finished >= expected) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Only " + finished + " of " + expected + " dispatches finished on channel "
                + channelId + " within " + TIMEOUT.toSeconds() + "s");
    }

    /**
     * Waits for every destination queue to empty, returning the duration and the deepest
     * queue seen. A max depth of zero means the queue drained as fast as it filled and the
     * test is not exercising what it claims.
     */
    static long[] awaitQueuesDrained(String channelId, long startNanos) throws Exception {
        long deadline = startNanos + TIMEOUT.toNanos();
        long maxDepth = 0;
        long depth = 0;
        while (System.nanoTime() < deadline) {
            depth = queueDepth(channelId);
            maxDepth = Math.max(maxDepth, depth);
            if (depth == 0) {
                return new long[] { System.nanoTime() - startNanos, maxDepth };
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Queues on channel " + channelId + " still held " + depth + " messages after "
                + TIMEOUT.toSeconds() + "s");
    }

    static long queueDepth(String channelId) throws Exception {
        long depth = 0;
        for (var child : SharedServer.get().status(channelId).getChildStatuses()) {
            Long queued = child.getQueued();
            if (queued != null) {
                depth += queued;
            }
        }
        return depth;
    }

    // ------------------------------------------------------------------ messages

    /** Reads every message on a channel, with content, so assertions can cover all of them. */
    static List<Message> allMessages(String channelId, int expected) throws Exception {
        List<Message> messages = new ArrayList<>(expected);
        for (int offset = 0; offset < expected; offset += PAGE_SIZE) {
            List<Message> page = SharedServer.get().fetchMessages(channelId, true, offset, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            messages.addAll(page);
        }
        return messages;
    }

    /** The {@code seq=N} the control channel stamped on the payload. */
    static int sequenceOf(Message message) {
        ConnectorMessage source = message.getConnectorMessages().get(0);
        String raw = source == null ? null : text(source.getRaw());
        if (raw == null) {
            throw new AssertionError("Message " + message.getMessageId() + " has no source content");
        }

        int start = raw.indexOf("seq=") + "seq=".length();
        return Integer.parseInt(raw.substring(start, raw.indexOf(';', start)));
    }

    /** Null-safe content accessor; a connector may legitimately have no content of a kind. */
    static String text(MessageContent content) {
        return content == null ? null : content.getContent();
    }

    /** The payload a connector answered with, unwrapped from its {@link Response} envelope. */
    static String responseText(MessageContent content) {
        String stored = text(content);
        if (stored == null || !RESPONSE_ENVELOPE.matcher(stored).matches()) {
            return stored;
        }
        Response response = ObjectXMLSerializer.getInstance().deserialize(stored.trim(), Response.class);
        return response == null ? null : response.getMessage();
    }

    /**
     * End-to-end engine latency: from the message arriving to the last connector finishing.
     * This excludes the harness and the network, so it stays comparable across machines.
     */
    static long latencyMillis(Message message) {
        Calendar received = message.getReceivedDate();
        if (received == null) {
            return -1;
        }

        long last = received.getTimeInMillis();
        for (ConnectorMessage connectorMessage : message.getConnectorMessages().values()) {
            Calendar done = connectorMessage.getResponseDate() != null ? connectorMessage.getResponseDate()
                    : connectorMessage.getReceivedDate();
            if (done != null) {
                last = Math.max(last, done.getTimeInMillis());
            }
        }
        return last - received.getTimeInMillis();
    }

    // ------------------------------------------------------------------- metrics

    /**
     * An ordered set of measurements. Publishing sends each one to the JUnit report (report
     * entries land in {@code <system-out>} of the XML) and prints a table, because the XML is
     * an artifact while the console output is right there in the CI job log.
     */
    static final class Metrics {

        private final String title;
        private final Map<String, String> values = new LinkedHashMap<>();

        Metrics(String title) {
            this.title = title;
            put("configuration", HarnessConfig.CONFIGURATION.isBlank() ? "(none)" : HarnessConfig.CONFIGURATION);
        }

        Metrics put(String key, String value) {
            values.put(key, value);
            return this;
        }

        Metrics put(String key, long value) {
            return put(key, Long.toString(value));
        }

        Metrics rate(String key, long count, long nanos) {
            return put(key, nanos <= 0 ? "n/a" : String.format("%.1f", count * 1_000_000_000.0 / nanos));
        }

        Metrics millis(String key, long nanos) {
            return put(key, String.format("%.1f", nanos / 1_000_000.0));
        }

        /** Adds the usual spread for a set of per-message latencies, in milliseconds. */
        Metrics latencies(String prefix, List<Long> samples) {
            if (samples.isEmpty()) {
                return put(prefix, "n/a");
            }
            List<Long> sorted = new ArrayList<>(samples);
            sorted.sort(null);

            long sum = 0;
            for (long sample : sorted) {
                sum += sample;
            }
            put(prefix + ".mean", String.format("%.1f", (double) sum / sorted.size()));
            put(prefix + ".p50", percentile(sorted, 0.50));
            put(prefix + ".p90", percentile(sorted, 0.90));
            put(prefix + ".p95", percentile(sorted, 0.95));
            put(prefix + ".p99", percentile(sorted, 0.99));
            put(prefix + ".max", sorted.get(sorted.size() - 1));
            return this;
        }

        /** The JVM counters either side of a run, as deltas. */
        Metrics resources(Sample before, Sample after, int messages) {
            if (before.cpuNanos() < 0 || after.cpuNanos() < 0) {
                put("cpu.seconds", "n/a").put("cpu.millisPerMessage", "n/a");
            } else {
                long cpuNanos = after.cpuNanos() - before.cpuNanos();
                put("cpu.seconds", String.format("%.3f", cpuNanos / 1_000_000_000.0));
                put("cpu.millisPerMessage", String.format("%.3f", cpuNanos / 1_000_000.0 / messages));
            }
            return put("gc.count", after.gcCount() - before.gcCount())
                    .put("gc.millis", after.gcMillis() - before.gcMillis())
                    .put("heap.usedDelta", after.heapUsed() - before.heapUsed())
                    .put("threads", after.threads());
        }

        private static long percentile(List<Long> sorted, double fraction) {
            int index = (int) Math.ceil(fraction * sorted.size()) - 1;
            return sorted.get(Math.min(Math.max(index, 0), sorted.size() - 1));
        }

        void publish(TestReporter reporter) {
            reporter.publishEntry(values);

            StringBuilder table = new StringBuilder("\n").append(title).append('\n');
            int width = values.keySet().stream().mapToInt(String::length).max().orElse(1);
            values.forEach((key, value) -> table.append(String.format("  %-" + width + "s  %s%n", key, value)));
            System.out.print(table);
        }
    }
}
