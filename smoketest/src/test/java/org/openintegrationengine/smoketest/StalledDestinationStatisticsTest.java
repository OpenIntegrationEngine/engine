// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.openintegrationengine.smoketest.StatisticsAssertions.assertStatistics;
import static org.openintegrationengine.smoketest.StatisticsAssertions.counts;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * What a channel reports while a destination is stalled. A queued message has been received by
 * the destination but not sent, and the queue it is sitting on is a depth the server measures
 * rather than a counter it keeps: the destination's RECEIVED rises as soon as the message reaches
 * it, SENT stays where it was until the destination actually accepts the message, and the queue
 * depth is what carries the difference in the meantime.
 *
 * <ul>
 * <li>Source: a Channel Reader.</li>
 * <li>Destination 1: a JavaScript Writer with its queue on, which returns QUEUED - a send failure
 * the queue retries, not an error - until the configuration map holds this run's token.</li>
 * </ul>
 */
@DisplayName("250-statistics/04-stalled-destination")
@TestInstance(PER_CLASS)
class StalledDestinationStatisticsTest {

    private static final String CHANNEL = "channels/statistics-queued-destination.xml";

    /** The configuration property the destination stalls on until it holds this run's token. */
    private static final String GATE = "oie.smoketest.releaseStatisticsQueue";

    private static final int DESTINATION = 1;

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
    void aStalledDestinationReportsItsQueueDepthUntilItSends() throws Exception {
        // The payload is this run's token, so a gate left open by an earlier run cannot release
        // this one, and every message carries the same token so one write releases them all.
        String token = UUID.randomUUID().toString();

        try {
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                SharedServer.get().submitMessage(channelId, token, new LinkedHashMap<>());
            }
            // The depth is a precondition the test enforces rather than one it hopes for: nothing
            // below is read until the server itself says all three messages are on the queue.
            Harness.awaitQueueSizeAtLeast(channelId, DESTINATION, MESSAGE_COUNT);

            assertStatistics(channelId, Map.of(
                    0, counts(MESSAGE_COUNT, 0, 0, 0),
                    DESTINATION, counts(MESSAGE_COUNT, 0, 0, 0)), MESSAGE_COUNT);
        } finally {
            // Also on failure, so a channel left behind by this test is not a wedged one.
            Harness.setConfigurationProperty(GATE, token);
        }

        Harness.awaitQueueSizeAtMost(channelId, DESTINATION, 0);

        assertStatistics(channelId, Map.of(
                0, counts(MESSAGE_COUNT, 0, 0, 0),
                DESTINATION, counts(MESSAGE_COUNT, 0, MESSAGE_COUNT, 0)), 0);
    }
}
