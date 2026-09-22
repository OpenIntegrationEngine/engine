// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.ArrayList;
import java.util.Collections;
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
 * A rotating destination queue steps over the message at its head rather than retrying it
 * forever, so one message that never sends does not hold up the ones behind it.
 *
 * <p>The first message submitted is the one the destination never accepts, and it therefore
 * owns the head of the queue for the whole test. Without rotation the queue thread would keep
 * re-acquiring it and nothing else would ever be sent, so every later message reaching SENT is
 * the rotation, not just a slow queue.
 *
 * <p>The order they are sent in is deliberately not asserted. Rotation moves the queue's starting
 * point past whatever it just returned, so a message can come round on a later cycle than the one
 * that was queued after it - a run of this has produced 2, 3, 4, 1. Reordering is what rotation
 * is; the claim is that each message is sent exactly once.
 *
 * <p>That the stuck message is still queued is read once, after the others are all sent: by then
 * the queue thread has demonstrably been past it, so nothing has to be inferred from a wait.
 */
@DisplayName("220-queueing/03-destination-queue-rotation")
@TestInstance(PER_CLASS)
class DestinationQueueRotationTest {

    private static final String CHANNEL = "channels/destination-queue-rotate.xml";

    /** The configuration property the destination leaves messages queued on until it holds this run's token. */
    private static final String GATE = "oie.smoketest.releaseDestinationQueueRotate";

    private static final int DESTINATION = 1;

    /** What the destination prefixes its handover position with. */
    private static final String POSITION = "order=";

    /** Messages behind the stuck one, which all send once the gate is open. */
    private static final int SENDABLE_COUNT = 4;

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
    void aMessageThatNeverSendsDoesNotBlockTheOnesQueuedBehindIt() throws Exception {
        // The payload carries this run's token, so a gate left open by an earlier run cannot
        // release this one. The first message is the stuck one, and being first it is also the
        // one the queue would otherwise acquire over and over.
        String token = UUID.randomUUID().toString();
        long stuckMessageId;
        List<Long> sendableMessageIds = new ArrayList<>();

        try {
            stuckMessageId = SharedServer.get().submitMessage(channelId, token + "#stuck", new LinkedHashMap<>());
            for (int i = 1; i <= SENDABLE_COUNT; i++) {
                sendableMessageIds.add(
                        SharedServer.get().submitMessage(channelId, token + "#" + i, new LinkedHashMap<>()));
            }

            Harness.awaitQueueSizeAtLeast(channelId, DESTINATION, SENDABLE_COUNT + 1);
        } finally {
            // Also on failure, so the gate is never left holding an earlier run's token.
            Harness.setConfigurationProperty(GATE, token);
        }

        List<Integer> positions = new ArrayList<>();
        for (long messageId : sendableMessageIds) {
            ConnectorMessage sent = Harness.awaitConnectorStatus(channelId, messageId, DESTINATION, Status.SENT);
            String stamp = MessageAssertions.responsePayload(sent.getResponse().getContent());
            assertTrue(stamp != null && stamp.startsWith(POSITION),
                    "message " + messageId + " has an unexpected destination response: " + stamp);
            positions.add(Integer.valueOf(stamp.substring(POSITION.length())));
        }

        // Sorted, because which cycle a message comes round on is rotation's business. That the
        // positions are distinct and leave no gap is the claim: four messages, four handovers.
        Collections.sort(positions);
        List<Integer> distinctPositions = new ArrayList<>();
        for (int i = 1; i <= SENDABLE_COUNT; i++) {
            distinctPositions.add(i);
        }
        assertEquals(distinctPositions, positions,
                "rotation should step over the stuck message and send each of the others exactly once");

        // Every message behind it has been sent, so the queue thread has acquired and returned the
        // stuck one at least that many times. It is still queued: rotation moved past it rather
        // than failing it or dropping it.
        assertEquals(Status.QUEUED, Harness.connectorMessage(channelId, stuckMessageId, DESTINATION).getStatus(),
                "rotation should leave the stuck message on the queue, not end it");
    }
}
