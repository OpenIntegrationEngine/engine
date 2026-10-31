// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestReporter;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.model.ChannelStatistics;

/**
 * The simpler half: one channel, three independent queued destinations, four queue readers
 * each. Nothing chains onwards, so what this exercises is the queue itself — filling it faster
 * than it drains, then draining it with several readers per destination.
 *
 * <p>Ingest and drain are timed separately because the source answers as soon as a message is
 * accepted; the work carries on behind it.
 */
@TestInstance(PER_CLASS)
@DisplayName("load: queued destinations with multiple readers")
class QueuedDestinationsLoadTest {

    private static final int MESSAGES = 1000;
    private static final int DESTINATIONS = 3;
    private static final String RUN = "queue";

    private String controlId;
    private String queueId;
    private LoadSupport.Expectations expected;

    @BeforeAll
    void deploy() throws Exception {
        controlId = Harness.deploy(LoadSupport.CONTROL_CHANNEL);
        expected = LoadSupport.prepare(controlId, RUN, MESSAGES);
        queueId = Harness.deploy("loadtest/load-queue.xml");
    }

    @AfterAll
    void undeploy() throws Exception {
        if (controlId != null) {
            LoadSupport.clean(controlId);
        }
        Harness.undeploy(queueId);
        Harness.undeploy(controlId);
    }

    @Test
    @DisplayName(MESSAGES + " messages through " + DESTINATIONS + " queues, four readers each")
    void drainQueues(TestReporter reporter) throws Exception {
        ChannelStatistics startStats = SharedServer.get().statistics(queueId);
        long baseline = startStats.getReceived();
        long sentBaseline = startStats.getSent();
        long errorBaseline = startStats.getError();

        SharedServer.get().stopChannel(queueId);
        LoadSupport.arm(controlId, RUN);

        LoadSupport.Sample before = LoadSupport.stats(controlId);
        long startNanos = System.nanoTime();
        SharedServer.get().startChannel(queueId);

        long ingestNanos = LoadSupport.awaitReceived(queueId, baseline, MESSAGES, startNanos);
        long[] drain = LoadSupport.awaitQueuesDrained(queueId, System.nanoTime());

        // An empty queue means nothing is waiting, not that the last dispatches off it have
        // finished recording their responses.
        LoadSupport.awaitDispatched(queueId, sentBaseline, errorBaseline, (long) MESSAGES * DESTINATIONS);
        LoadSupport.Sample after = LoadSupport.stats(controlId);

        long drainNanos = drain[0];
        long maxQueueDepth = drain[1];

        List<Message> messages = LoadSupport.allMessages(queueId, MESSAGES);
        assertEquals(MESSAGES, messages.size(), "messages stored on the queue channel");

        List<Long> latencies = new ArrayList<>(MESSAGES);
        boolean[] seen = new boolean[MESSAGES];

        for (Message message : messages) {
            int seq = LoadSupport.sequenceOf(message);
            assertTrue(seq >= 0 && seq < MESSAGES, "sequence " + seq + " is outside the run");
            assertTrue(!seen[seq], "sequence " + seq + " arrived more than once");
            seen[seq] = true;

            String digest = expected.digests().get(seq);
            for (int metaDataId = 1; metaDataId <= DESTINATIONS; metaDataId++) {
                ConnectorMessage destination = message.getConnectorMessages().get(metaDataId);
                assertNotNull(destination, "sequence " + seq + " never reached destination " + metaDataId);
                assertEquals(Status.SENT, destination.getStatus(),
                        "status of destination " + metaDataId + " for sequence " + seq);
                assertEquals("D" + metaDataId + ":" + digest,
                        LoadSupport.responseText(destination.getResponse()),
                        "digest from destination " + metaDataId + " for sequence " + seq);
            }
            latencies.add(LoadSupport.latencyMillis(message));
        }

        assertEquals(0, LoadSupport.queueDepth(queueId), "queue depth once the run has settled");
        assertEquals(0, SharedServer.get().statistics(queueId).getError() - errorBaseline,
                "errors on the queue channel");
        assertEquals((long) MESSAGES * DESTINATIONS, SharedServer.get().statistics(queueId).getSent() - sentBaseline,
                "messages sent across all destinations");

        // A queue that never backs up is not being exercised; say so rather than passing quietly.
        assertTrue(maxQueueDepth > 0, "destination queues never backed up, so queueing went untested");

        new LoadSupport.Metrics("load: queued destinations")
                .put("messages", MESSAGES)
                .put("bytes", expected.bytes())
                .put("destinations", DESTINATIONS)
                .put("queue.maxDepth", maxQueueDepth)
                .millis("ingest.millis", ingestNanos)
                .rate("ingest.msgPerSec", MESSAGES, ingestNanos)
                .rate("ingest.bytesPerSec", expected.bytes(), ingestNanos)
                .millis("drain.millis", drainNanos)
                .rate("drain.dispatchPerSec", (long) MESSAGES * DESTINATIONS, drainNanos)
                .latencies("latency.millis", latencies)
                .resources(before, after, MESSAGES)
                .publish(reporter);
    }
}
