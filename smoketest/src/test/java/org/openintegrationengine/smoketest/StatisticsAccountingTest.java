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
 * What a channel counts when nothing goes wrong. The channel has two destinations that both
 * accept every message, so the source and each destination keep their own counters and the
 * channel's aggregate SENT is the two destinations added together rather than either one of them.
 *
 * <p>Statistics are a channel-wide running total rather than a property of a message, so there is
 * no fixture shape for them; every case under {@code 250-statistics} reads them back over the
 * client API instead.
 *
 * <ul>
 * <li>Source: a Channel Reader, so the harness submits the messages itself.</li>
 * <li>Destinations 1 and 2: JavaScript Writers that return a response, which is a send.</li>
 * </ul>
 */
@DisplayName("250-statistics/01-normal-flow")
@TestInstance(PER_CLASS)
class StatisticsAccountingTest {

    private static final String CHANNEL = "channels/statistics-normal-flow.xml";

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
    void everyConnectorCountsTheMessagesItHandled() throws Exception {
        submit();

        // Asserted after one message and again after three, so a counter that was assigned the
        // message count rather than incremented per message would still have to be caught.
        assertStatistics(channelId, Map.of(
                0, counts(1, 0, 0, 0),
                1, counts(1, 0, 1, 0),
                2, counts(1, 0, 1, 0)), 0);

        submit();
        submit();

        assertStatistics(channelId, Map.of(
                0, counts(3, 0, 0, 0),
                1, counts(3, 0, 3, 0),
                2, counts(3, 0, 3, 0)), 0);
    }

    /** Submits one message and waits for the server to finish with it, counters included. */
    private void submit() throws Exception {
        long messageId = SharedServer.get().submitMessage(channelId, "counted", new LinkedHashMap<>());
        Harness.awaitProcessed(channelId, messageId);
    }
}
