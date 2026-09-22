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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Status;

/**
 * The source queue: that it keeps its order once it is deeper than the buffer it holds in
 * memory, that it survives the channel it belongs to being halted, and that concurrent
 * submitters cannot lose a message between committing it and queueing it.
 *
 * <p>The channel sets {@code queueBufferSize} to 3 and every test here queues more than that,
 * so refilling the buffer from the database is the path most messages take rather than an edge
 * case. Its destination parks until the gate property holds the run's token, which is what lets
 * a queue build up before anything drains; the token is fresh per run, so a gate left open by an
 * earlier run cannot release a later one.
 *
 * <p>The destination stamps each message with the position it was handed over in, taken from a
 * counter in the global channel map. Assertions are on the <em>relative</em> positions - a
 * consecutive ascending run - because the counter carries across tests and resets when the
 * channel restarts, and because relative positions are exactly what the ordering claim is.
 */
@DisplayName("220-queueing/01-source-queue")
@TestInstance(PER_CLASS)
class SourceQueueTest {

    private static final String CHANNEL = "channels/source-queue-order.xml";

    /** The configuration property the destination parks on. */
    private static final String GATE = "oie.smoketest.releaseSourceQueue";

    /** What the destination prefixes its handover position with. */
    private static final String POSITION = "order=";

    private static final int SOURCE = 0;
    private static final int DESTINATION = 1;

    /** {@code queueBufferSize} in the channel: how much of the source queue is held in memory. */
    private static final int BUFFER_CAPACITY = 3;

    private static final int MESSAGE_COUNT = 10;

    private String channelId;

    @BeforeAll
    void deploy() throws Exception {
        channelId = Harness.deploy(CHANNEL);
    }

    @AfterAll
    void undeploy() {
        Harness.undeploy(channelId);
    }

    /**
     * A queue deeper than its buffer still hands every message to the destination exactly once
     * and in the order they were received, which means the refill from the database picks up
     * where the buffer left off rather than starting over or skipping.
     */
    @Test
    void drainsInReceiptOrderPastItsBufferCapacity() throws Exception {
        String token = UUID.randomUUID().toString();
        List<Long> messageIds = new ArrayList<>();

        try {
            for (int i = 1; i <= MESSAGE_COUNT; i++) {
                messageIds.add(SharedServer.get().submitMessage(channelId, token + "#" + i, new LinkedHashMap<>()));
            }

            // The first message is parked in the destination and the rest are behind it. Waiting
            // for the queue to pass its buffer is what makes the refill certain rather than hoped
            // for; if it never happens the wait fails the test.
            Harness.awaitQueueSizeAtLeast(channelId, SOURCE, BUFFER_CAPACITY + 1);
        } finally {
            // Also on failure, so a parked destination never outlives the test.
            Harness.setConfigurationProperty(GATE, token);
        }

        assertConsecutive(handoverPositions(messageIds),
                "the source queue should drain in the order the messages were received");
    }

    /**
     * Halting a channel with a full source queue leaves the queue alone, and starting it again
     * drains it in order. A halt rather than a stop because a stop waits for the message the
     * destination is parked on, which is the one thing this test needs to still be parked.
     *
     * <p>That the queue thread is not running in between is not asserted - it cannot be, over an
     * API that only reports state. What is asserted is the consequence that matters: the queue
     * was still there afterwards, and the restarted channel reloaded and drained it.
     */
    @Test
    void survivesAHaltedChannelAndDrainsWhenItStartsAgain() throws Exception {
        String token = UUID.randomUUID().toString();
        List<Long> messageIds = new ArrayList<>();

        try {
            for (int i = 1; i <= MESSAGE_COUNT; i++) {
                messageIds.add(SharedServer.get().submitMessage(channelId, token + "#" + i, new LinkedHashMap<>()));
            }
            // One message is in the destination, so the rest are the queue.
            int queued = MESSAGE_COUNT - 1;
            Harness.awaitQueueSizeAtLeast(channelId, SOURCE, queued);

            Harness.haltChannel(channelId);

            assertEquals(Long.valueOf(queued), SharedServer.get().queueSize(channelId, SOURCE),
                    "halting the channel should leave its source queue intact, not drain or drop it");
        } finally {
            // Also on failure, so neither a parked destination nor a halted channel outlives the
            // test. Starting an already-started channel is a no-op, so this is safe either way.
            Harness.setConfigurationProperty(GATE, token);
            Harness.startChannel(channelId);
        }

        assertConsecutive(handoverPositions(messageIds),
                "the restarted channel should drain the queue it reloaded, in order");
    }

    /**
     * Several connections dispatching at once still get every message processed exactly once.
     * The engine commits a source message and adds it to the queue under the queue's own lock;
     * without that, a message committed but not yet queued is picked up by another thread's
     * buffer refill and the queue's count and contents disagree, stranding one of them.
     * {@code ConnectorMessageQueueTest} pins that mechanism down directly - this is the same
     * claim where a client can see it, so it catches the lock going missing rather than
     * describing what happens when it does.
     *
     * <p>A connection serialises its own requests, so each submitter needs its own; sharing one
     * would serialise the very dispatch this is about.
     */
    @Test
    void concurrentSubmittersEachGetTheirMessageProcessedExactlyOnce() throws Exception {
        String token = UUID.randomUUID().toString();
        int submitters = 3;
        int perSubmitter = 8;

        List<Long> messageIds = new ArrayList<>();
        List<OieServer> connections = new ArrayList<>();
        ExecutorService submitterPool = Executors.newFixedThreadPool(submitters);

        try {
            List<Future<List<Long>>> submitted = new ArrayList<>();
            for (int submitter = 0; submitter < submitters; submitter++) {
                OieServer connection = OieServer.connect();
                connections.add(connection);

                String prefix = token + "#" + submitter + "-";
                submitted.add(submitterPool.submit(() -> {
                    List<Long> mine = new ArrayList<>();
                    for (int i = 1; i <= perSubmitter; i++) {
                        mine.add(connection.submitMessage(channelId, prefix + i, new LinkedHashMap<>()));
                    }
                    return mine;
                }));
            }
            for (Future<List<Long>> batch : submitted) {
                messageIds.addAll(batch.get());
            }

            Harness.awaitQueueSizeAtLeast(channelId, SOURCE, BUFFER_CAPACITY + 1);
        } finally {
            Harness.setConfigurationProperty(GATE, token);
            submitterPool.shutdown();
            connections.forEach(OieServer::close);
        }

        // Which submitter won a given position is a race and not asserted; that every message got
        // a position, and no two got the same one, is the claim.
        List<Integer> positions = handoverPositions(messageIds);
        Collections.sort(positions);
        assertEquals(submitters * perSubmitter, positions.size(), "a message went missing");
        assertConsecutive(positions, "every concurrently submitted message should be handed over"
                + " exactly once, so their positions are distinct and leave no gap");
    }

    /**
     * Waits for every message to be sent and returns the position its destination stamped it
     * with, in the order the server committed them - which is message id order, since the id is
     * assigned by the insert.
     */
    private List<Integer> handoverPositions(List<Long> messageIds) throws Exception {
        List<Long> inCommitOrder = new ArrayList<>(messageIds);
        Collections.sort(inCommitOrder);

        List<Integer> positions = new ArrayList<>();
        for (long messageId : inCommitOrder) {
            ConnectorMessage sent = Harness.awaitConnectorStatus(channelId, messageId, DESTINATION, Status.SENT);
            String stamp = MessageAssertions.responsePayload(sent.getResponse().getContent());
            assertTrue(stamp != null && stamp.startsWith(POSITION),
                    "message " + messageId + " has an unexpected destination response: " + stamp);
            positions.add(Integer.valueOf(stamp.substring(POSITION.length())));
        }
        return positions;
    }

    /**
     * Asserts the positions are a consecutive ascending run. Where they start is the channel
     * counter's business; that they rise by one each time is the queue's.
     */
    private static void assertConsecutive(List<Integer> positions, String message) {
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            expected.add(positions.get(0) + i);
        }
        assertEquals(expected, positions, message);
    }
}
