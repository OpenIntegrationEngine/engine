// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.openintegrationengine.smoketest.StatisticsAssertions.assertStatistics;
import static org.openintegrationengine.smoketest.StatisticsAssertions.counts;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * What a channel counts when its source filter rejects everything. A message the source filters
 * never reaches a destination, so the destination's counters have to stay at zero while the
 * channel still counts the message as both received and filtered - RECEIVED is what came in, not
 * what survived, and the two are separate counters rather than one moving between them.
 *
 * <ul>
 * <li>Source: a Channel Reader with a filter rule that returns false for every message.</li>
 * <li>Destination 1: a JavaScript Writer that would send, and never gets the chance.</li>
 * </ul>
 */
@DisplayName("250-statistics/02-filtered-at-the-source")
@TestInstance(PER_CLASS)
class SourceFilteredStatisticsTest {

    private static final String CHANNEL = "channels/statistics-source-filtered.xml";

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
    void aMessageFilteredAtTheSourceIsCountedThereAndNowhereElse() throws Exception {
        for (int i = 0; i < 3; i++) {
            long messageId = SharedServer.get().submitMessage(channelId, "rejected", new LinkedHashMap<>());
            Harness.awaitProcessed(channelId, messageId);
        }

        assertStatistics(channelId, Map.of(
                0, counts(3, 3, 0, 0),
                1, counts(0, 0, 0, 0)), 0);
    }
}
