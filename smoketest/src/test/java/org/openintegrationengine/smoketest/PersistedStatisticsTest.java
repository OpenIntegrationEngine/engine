// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.openintegrationengine.smoketest.StatisticsAssertions.assertStatistics;
import static org.openintegrationengine.smoketest.StatisticsAssertions.counts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.mirth.connect.model.ChannelStatistics;

/**
 * The counters a running channel reports are held in memory and written to the database behind
 * it, and every other case here reads the memory. This one reads the database.
 *
 * <p>That matters because the statement that writes them is one of the few the engine keeps a
 * per-dialect version of - derby cannot use the {@code GREATEST} the others do and falls back to
 * a {@code CASE} rewrite - and a write nothing reads back is a write nothing checks. A client
 * only ever sees the stored rows for a channel that is not deployed, because a deployed one is
 * always answered from the engine's own tallies, so the channel is undeployed without being
 * removed and asked again.
 *
 * <p>The message mixture is the one from
 * {@link MixedDestinationStatisticsTest}, so all four stored counters are non-zero and a write
 * that lost or crossed a column has somewhere to show up.
 *
 * <ul>
 * <li>Source: a Channel Reader.</li>
 * <li>Destination 1 "Filtered": a filter rule that returns false.</li>
 * <li>Destination 2 "Sent": a JavaScript Writer that returns a response.</li>
 * <li>Destination 3 "Error": a JavaScript Writer that throws, with no queue and no retries.</li>
 * </ul>
 */
@DisplayName("250-statistics/05-written-to-the-database")
@TestInstance(PER_CLASS)
class PersistedStatisticsTest {

    private static final String CHANNEL = "channels/statistics-persisted.xml";

    private static final int MESSAGE_COUNT = 3;

    private String channelId;

    @BeforeAll
    void deploy() throws Exception {
        channelId = Harness.deploy(CHANNEL);
    }

    @AfterAll
    void undeploy() {
        Harness.undeploy(channelId);
    }

    @Test
    void theStoredCountersAreTheOnesTheRunningChannelReported() throws Exception {
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            long messageId = SharedServer.get().submitMessage(channelId, "persisted", new LinkedHashMap<>());
            Harness.awaitProcessed(channelId, messageId);
        }

        assertStatistics(channelId, Map.of(
                0, counts(MESSAGE_COUNT, 0, 0, 0),
                1, counts(MESSAGE_COUNT, MESSAGE_COUNT, 0, 0),
                2, counts(MESSAGE_COUNT, 0, MESSAGE_COUNT, 0),
                3, counts(MESSAGE_COUNT, 0, 0, MESSAGE_COUNT)), 0);

        Harness.undeployChannel(channelId);

        // The engine writes the counters out on a timer, so the stored rows can be a moment
        // behind the channel that has just stopped reporting them.
        awaitStoredStatistics(MESSAGE_COUNT, MESSAGE_COUNT, MESSAGE_COUNT, MESSAGE_COUNT);
    }

    /** Polls the stored counters until they hold what the running channel reported. */
    private void awaitStoredStatistics(long received, long filtered, long sent, long error) throws Exception {
        List<Long> expected = List.of(received, filtered, sent, error);
        long deadline = System.nanoTime() + HarnessConfig.TIMEOUT.toNanos();
        List<Long> actual = null;
        do {
            ChannelStatistics stored = SharedServer.get().statistics(channelId);
            actual = List.of(stored.getReceived(), stored.getFiltered(), stored.getSent(), stored.getError());
            if (expected.equals(actual)) {
                return;
            }
            Thread.sleep(250);
        } while (System.nanoTime() < deadline);

        assertEquals(expected, actual, "the counters the engine stored are not the ones it reported");
    }
}
