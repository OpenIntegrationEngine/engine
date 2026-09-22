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

/**
 * Deleting messages a destination queue is still holding. A queue is a database table and an
 * in-memory buffer at once, so a delete that only reaches the table leaves the queue thread
 * retrying rows that are no longer there; the server is supposed to invalidate the queue as part
 * of the delete, which is what makes the dashboard's depth fall to zero.
 *
 * <p>The destination fails every send while the gate is shut, so the messages are on the queue and
 * staying there when the delete lands, rather than racing the queue thread to be drained first.
 */
@DisplayName("240-message-deletion/02-queued-destination")
@TestInstance(PER_CLASS)
class QueuedMessageDeletionTest {

    private static final String CHANNEL = "channels/message-deletion-queued.xml";

    /** The configuration property the destination fails on until it holds this run's token. */
    private static final String GATE = "oie.smoketest.releaseDeletionQueue";

    private static final int DESTINATION = 1;

    private static final int MESSAGE_COUNT = 4;

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
    void deletingQueuedMessagesEmptiesTheQueue() throws Exception {
        OieServer server = SharedServer.get();
        // The payload carries this run's token, so a gate left open by an earlier run cannot
        // release this one.
        String token = UUID.randomUUID().toString();
        List<Long> messageIds = new ArrayList<>();

        try {
            for (int i = 1; i <= MESSAGE_COUNT; i++) {
                messageIds.add(server.submitMessage(channelId, token, new LinkedHashMap<>()));
            }
            Harness.awaitQueueSizeAtLeast(channelId, DESTINATION, MESSAGE_COUNT);

            for (long messageId : messageIds) {
                server.removeMessage(channelId, messageId, null);
            }

            assertEquals(0, server.messageCount(channelId), "the queued messages were not deleted");
            Harness.awaitQueueSizeAtMost(channelId, DESTINATION, 0);
        } finally {
            // Also on failure, so a channel left behind by this test is not a wedged one.
            Harness.setConfigurationProperty(GATE, token);
        }
    }
}
