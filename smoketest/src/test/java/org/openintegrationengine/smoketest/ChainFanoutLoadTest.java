// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.mirth.connect.model.ChannelStatistics;

/**
 * Pushes a thousand messages through a chain of three channels and checks every one of them.
 *
 * <p>The entry channel pulls each message off disk, pulls its blob out into an attachment, and
 * routes it on content to exactly one of two loopback hops: MLLP to one channel, HTTP to
 * another. Each hop answers with the SHA-256 of what actually arrived, and the control channel
 * says up front what that digest and route should be. A match means the payload survived the
 * attachment table and the hop byte for byte.
 */
@TestInstance(PER_CLASS)
@DisplayName("load: chained fan-out over MLLP and HTTP")
class ChainFanoutLoadTest {

    private static final int MESSAGES = 1000;
    private static final String RUN = "entry";

    /** Destination metadata ids, in the order the entry channel declares them. */
    private static final int MLLP = 1;
    private static final int HTTP = 2;

    private String controlId;
    private String entryId;
    private String mllpEchoId;
    private String httpEchoId;
    private LoadSupport.Expectations expected;

    @BeforeAll
    void deploy() throws Exception {
        controlId = Harness.deploy(LoadSupport.CONTROL_CHANNEL);

        // Stage the whole run before anything is listening; arming it is the start of the run.
        expected = LoadSupport.prepare(controlId, RUN, MESSAGES);

        mllpEchoId = Harness.deploy("loadtest/load-mllp-echo.xml");
        httpEchoId = Harness.deploy("loadtest/load-http-echo.xml");
        entryId = Harness.deploy("loadtest/load-entry.xml");
    }

    @AfterAll
    void undeploy() throws Exception {
        if (controlId != null) {
            LoadSupport.clean(controlId);
        }
        Harness.undeploy(entryId);
        Harness.undeploy(httpEchoId);
        Harness.undeploy(mllpEchoId);
        Harness.undeploy(controlId);
    }

    @Test
    @DisplayName(MESSAGES + " messages fan out to MLLP and HTTP with matching digests")
    void fanOut(TestReporter reporter) throws Exception {
        ChannelStatistics entryBefore = SharedServer.get().statistics(entryId);
        long entryBaseline = entryBefore.getReceived();
        long entrySentBaseline = entryBefore.getSent();
        long entryErrorBaseline = entryBefore.getError();
        long mllpBaseline = SharedServer.get().statistics(mllpEchoId).getReceived();
        long httpBaseline = SharedServer.get().statistics(httpEchoId).getReceived();
        long mllpErrorBaseline = SharedServer.get().statistics(mllpEchoId).getError();
        long httpErrorBaseline = SharedServer.get().statistics(httpEchoId).getError();

        // Nothing has been consumed yet, so starting the channel below is the one and only
        // moment the engine can begin work.
        SharedServer.get().stopChannel(entryId);
        LoadSupport.arm(controlId, RUN);

        LoadSupport.Sample before = LoadSupport.stats(controlId);
        long startNanos = System.nanoTime();
        SharedServer.get().startChannel(entryId);
        long ingestNanos = LoadSupport.awaitReceived(entryId, entryBaseline, MESSAGES, startNanos);

        // Ingest only means the messages exist; the hops behind them still have to finish
        // before every response is on record and worth asserting on.
        LoadSupport.awaitDispatched(entryId, entrySentBaseline, entryErrorBaseline, MESSAGES);
        LoadSupport.Sample after = LoadSupport.stats(controlId);

        List<Message> messages = LoadSupport.allMessages(entryId, MESSAGES);
        assertEquals(MESSAGES, messages.size(), "messages stored on the entry channel");

        List<Long> latencies = new ArrayList<>(MESSAGES);
        boolean[] seen = new boolean[MESSAGES];
        int mllpCount = 0;
        int httpCount = 0;

        for (Message message : messages) {
            int seq = LoadSupport.sequenceOf(message);
            assertTrue(seq >= 0 && seq < MESSAGES, "sequence " + seq + " is outside the run");
            assertTrue(!seen[seq], "sequence " + seq + " arrived more than once");
            seen[seq] = true;

            String route = expected.routes().get(seq);
            boolean mllp = "MLLP".equals(route);
            int taken = mllp ? MLLP : HTTP;

            ConnectorMessage destination = message.getConnectorMessages().get(taken);
            assertNotNull(destination, "sequence " + seq + " should have been routed over " + route);
            assertEquals(route + ":" + expected.digests().get(seq),
                    LoadSupport.responseText(destination.getResponse()),
                    "digest echoed back for sequence " + seq + " over " + route);

            // The routing step removes the other destination, so it must not have run at all.
            assertNull(message.getConnectorMessages().get(mllp ? HTTP : MLLP),
                    "sequence " + seq + " also reached the destination it should have skipped");

            if (mllp) {
                mllpCount++;
            } else {
                httpCount++;
            }
            latencies.add(LoadSupport.latencyMillis(message));
        }

        assertEquals(MESSAGES, mllpCount + httpCount, "messages accounted for across both destinations");
        assertTrue(mllpCount > 0 && httpCount > 0,
                "routing never varied: " + mllpCount + " over MLLP, " + httpCount + " over HTTP");

        assertEquals(0, SharedServer.get().statistics(entryId).getError() - entryErrorBaseline,
                "errors on the entry channel");
        assertEquals(mllpCount, SharedServer.get().statistics(mllpEchoId).getReceived() - mllpBaseline,
                "messages received by the MLLP echo channel");
        assertEquals(httpCount, SharedServer.get().statistics(httpEchoId).getReceived() - httpBaseline,
                "messages received by the HTTP echo channel");
        assertEquals(0, SharedServer.get().statistics(mllpEchoId).getError() - mllpErrorBaseline,
                "errors on the MLLP echo channel");
        assertEquals(0, SharedServer.get().statistics(httpEchoId).getError() - httpErrorBaseline,
                "errors on the HTTP echo channel");

        new LoadSupport.Metrics("load: chained fan-out")
                .put("messages", MESSAGES)
                .put("bytes", expected.bytes())
                .put("routed.mllp", mllpCount)
                .put("routed.http", httpCount)
                .millis("ingest.millis", ingestNanos)
                .rate("ingest.msgPerSec", MESSAGES, ingestNanos)
                .rate("ingest.bytesPerSec", expected.bytes(), ingestNanos)
                .latencies("latency.millis", latencies)
                .resources(before, after, MESSAGES)
                .publish(reporter);
    }
}
