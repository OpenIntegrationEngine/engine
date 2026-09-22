// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.model.ChannelStatistics;

/**
 * Statistics assertions shared by the cases under {@code 250-statistics}.
 *
 * <p>A channel's counters reach a client two ways, computed separately by the server. The
 * dashboard reports a row per connector plus the channel's own aggregate row, which the engine
 * maintains as it goes; the statistics view reports one {@link ChannelStatistics} the server
 * re-adds up from the connector rows on each call. A test here states the per-connector counters
 * it expects and {@link #assertStatistics} derives both aggregates from them, so each case pins
 * down the connector counters it is about and every case cross-checks the two aggregates against
 * the same rule:
 *
 * <ul>
 * <li>RECEIVED counts the source connector only.</li>
 * <li>SENT counts the destination connectors only.</li>
 * <li>FILTERED and ERROR count every connector.</li>
 * </ul>
 *
 * <p>Only the four statuses in
 * {@code com.mirth.connect.donkey.server.channel.Statistics.TRACKED_STATUSES} are counted at all;
 * a queue is a depth the server measures when asked rather than a counter, which is why it is a
 * separate argument.
 */
final class StatisticsAssertions {

    /**
     * How long to keep re-reading before giving up on a counter. A connector's status and its
     * counters are committed together but read back separately, so a caller that waited for a
     * status can still be a moment early for the counter it implies.
     */
    private static final Duration GRACE = Duration.ofSeconds(5);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    private StatisticsAssertions() {
    }

    /** One connector's counters, in the order the engine tracks them. */
    static Map<Status, Long> counts(long received, long filtered, long sent, long error) {
        Map<Status, Long> counts = new LinkedHashMap<>();
        counts.put(Status.RECEIVED, received);
        counts.put(Status.FILTERED, filtered);
        counts.put(Status.SENT, sent);
        counts.put(Status.ERROR, error);
        return counts;
    }

    /**
     * Asserts every counter the server reports for one channel: the per-connector rows against
     * {@code expected}, keyed by metadata id, and both aggregates against the roll-up of
     * {@code expected} described above. Retries for {@link #GRACE} before failing.
     *
     * @param expectedQueued how many messages should be sitting on the channel's queues
     */
    static void assertStatistics(String channelId, Map<Integer, Map<Status, Long>> expected, long expectedQueued)
            throws Exception {
        long deadline = System.nanoTime() + GRACE.toNanos();
        while (true) {
            try {
                assertStatisticsNow(channelId, expected, expectedQueued);
                return;
            } catch (AssertionError e) {
                if (System.nanoTime() >= deadline) {
                    throw e;
                }
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
    }

    private static void assertStatisticsNow(String channelId, Map<Integer, Map<Status, Long>> expected,
            long expectedQueued) throws Exception {
        Map<Integer, Map<Status, Long>> actual = SharedServer.get().connectorStatistics(channelId);

        Map<Integer, Map<Status, Long>> actualConnectors = new LinkedHashMap<>(actual);
        Map<Status, Long> actualAggregate = actualConnectors.remove(null);
        assertEquals(expected, actualConnectors, "the per-connector statistics are wrong");

        Map<Status, Long> expectedAggregate = rollUp(expected);
        assertEquals(expectedAggregate, actualAggregate, "the channel's aggregate statistics row is wrong");

        // The statistics view adds the connector rows up again on its own, so it has to agree.
        ChannelStatistics statistics = SharedServer.get().statistics(channelId);
        assertEquals(
                List.of(expectedAggregate.get(Status.RECEIVED), expectedAggregate.get(Status.FILTERED),
                        expectedAggregate.get(Status.SENT), expectedAggregate.get(Status.ERROR), expectedQueued),
                List.of(statistics.getReceived(), statistics.getFiltered(), statistics.getSent(),
                        statistics.getError(), statistics.getQueued()),
                () -> "the statistics view disagrees with the dashboard; it reported " + statistics);
    }

    /** Adds the per-connector counters up the way the channel's own aggregate is defined. */
    private static Map<Status, Long> rollUp(Map<Integer, Map<Status, Long>> connectors) {
        long received = 0;
        long filtered = 0;
        long sent = 0;
        long error = 0;

        for (Map.Entry<Integer, Map<Status, Long>> connector : connectors.entrySet()) {
            boolean source = connector.getKey() == 0;
            Map<Status, Long> counts = connector.getValue();
            received += source ? counts.get(Status.RECEIVED) : 0;
            sent += source ? 0 : counts.get(Status.SENT);
            filtered += counts.get(Status.FILTERED);
            error += counts.get(Status.ERROR);
        }

        return counts(received, filtered, sent, error);
    }
}
