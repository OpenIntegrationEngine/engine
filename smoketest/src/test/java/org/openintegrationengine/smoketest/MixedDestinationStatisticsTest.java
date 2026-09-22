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
 * Destinations that end the same message differently are counted separately. Every message goes
 * to all three destinations, so each destination receives the same three messages and the only
 * thing that differs is the status it ends them in - which is the counter it credits. The channel
 * aggregate then has to carry all three outcomes at once rather than only the last or the worst.
 *
 * <ul>
 * <li>Source: a Channel Reader, which sends every message down the whole chain.</li>
 * <li>Destination 1 "Filtered": a filter rule that returns false, so its messages are filtered.</li>
 * <li>Destination 2 "Sent": a JavaScript Writer that returns a response.</li>
 * <li>Destination 3 "Error": a JavaScript Writer that throws, with its queue off and no
 * retries, so the failure is final.</li>
 * </ul>
 */
@DisplayName("250-statistics/03-mixed-destination-outcomes")
@TestInstance(PER_CLASS)
class MixedDestinationStatisticsTest {

    private static final String CHANNEL = "channels/statistics-mixed-destinations.xml";

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
    void eachDestinationCreditsItsOwnOutcome() throws Exception {
        for (int i = 0; i < 3; i++) {
            long messageId = SharedServer.get().submitMessage(channelId, "mixed", new LinkedHashMap<>());
            Harness.awaitProcessed(channelId, messageId);
        }

        assertStatistics(channelId, Map.of(
                0, counts(3, 0, 0, 0),
                1, counts(3, 3, 0, 0),
                2, counts(3, 0, 3, 0),
                3, counts(3, 0, 0, 3)), 0);
    }
}
