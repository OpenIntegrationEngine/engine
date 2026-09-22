// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Status;

/**
 * A destination queue outlives the channel that owns it: messages a failing destination could
 * not send are still queued after the channel is stopped and started, and the queue thread
 * drains them in message order once the destination accepts them.
 *
 * <p>The destination's in-memory buffer is set to 3 and the test queues eight messages, so the
 * queue the restarted channel drains cannot be the buffer it filled before the stop - it has to
 * be reloaded from the database and paged through.
 *
 * <p>The gate stays shut across the whole stop and start, so every step here is a positive
 * signal the server reports - queue depth, deployed state, connector status - and nothing is
 * inferred from time passing. That a stopped channel does not send at all is a channel
 * lifecycle claim rather than a queueing one, and is not asserted here.
 */
@DisplayName("220-queueing/02-destination-queue")
@TestInstance(PER_CLASS)
class DestinationQueueTest {

    private static final String CHANNEL = "channels/destination-queue.xml";

    /** The configuration property the destination fails on until it holds this run's token. */
    private static final String GATE = "oie.smoketest.releaseDestinationQueue";

    private static final int DESTINATION = 1;

    private static final int MESSAGE_COUNT = 8;

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
    void queuedMessagesSurviveAChannelRestartAndDrainInOrder() throws Exception {
        // The payload carries this run's token, so a gate left open by an earlier run cannot
        // release this one, and an index, so a failure names the message that came out of order.
        String token = UUID.randomUUID().toString();
        List<Long> messageIds = new ArrayList<>();

        try {
            for (int i = 1; i <= MESSAGE_COUNT; i++) {
                messageIds.add(SharedServer.get().submitMessage(channelId, token + "#" + i, new LinkedHashMap<>()));
            }
            // The destination leaves every message on the queue while the gate is shut, so the
            // queue is at its full depth - past the buffer it holds in memory - before the stop.
            Harness.awaitQueueSizeAtLeast(channelId, DESTINATION, MESSAGE_COUNT);

            // Take the channel down and back up with the queue full and the gate still shut, so
            // the queue the restarted channel drains is the one it reloaded from the database.
            Harness.stopChannel(channelId);
            Harness.startChannel(channelId);

            Harness.awaitQueueSizeAtLeast(channelId, DESTINATION, MESSAGE_COUNT);
        } finally {
            // Also on failure, so a channel left behind by this test is not a wedged one.
            Harness.setConfigurationProperty(GATE, token);
        }

        List<String> observedOrder = new ArrayList<>();
        for (long messageId : messageIds) {
            ConnectorMessage sent = Harness.awaitConnectorStatus(channelId, messageId, DESTINATION, Status.SENT);
            observedOrder.add(MessageAssertions.responsePayload(sent.getResponse().getContent()));
        }

        // The destination stamps each message with the position it was drained in, so the messages
        // queued first must carry the lowest positions.
        List<String> queueOrder = new ArrayList<>();
        for (int i = 1; i <= MESSAGE_COUNT; i++) {
            queueOrder.add("order=" + i);
        }
        assertEquals(queueOrder, observedOrder, "the destination queue should drain in message order");
    }
}
