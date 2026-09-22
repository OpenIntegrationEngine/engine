// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.LinkedHashMap;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Status;

/**
 * A destination whose response transformer has not returned yet, the one part of
 * ci/tests/190-response-handling that is not a fixture: the harness retries every fixture
 * assertion until the message is terminal, so it can never see the PENDING the engine parks
 * the destination at while the transformer runs.
 *
 * <p>Nothing here waits on a clock. The channel queues at its source, so the submit returns
 * the message id straight away, and its response transformer parks until the gate property
 * holds that message's token. The token is fresh per run, so a gate left set by an earlier run
 * cannot release this one.
 */
@DisplayName("190-response-handling/06-blocking-response-transformer")
@TestInstance(PER_CLASS)
class BlockingResponseTransformerTest {

    private static final String CHANNEL = "channels/blocking-response-transformer.xml";

    /** The configuration property the response transformer parks on. */
    private static final String GATE = "oie.smoketest.releaseResponse";

    private static final int DESTINATION = 1;

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
    void destinationStaysPendingUntilTheResponseTransformerReturns() throws Exception {
        // The payload is this run's token: the destination echoes it back, so it is also the
        // response the transformer parks on and the content it rewrites.
        String token = UUID.randomUUID().toString();
        long messageId = SharedServer.get().submitMessage(channelId, token, new LinkedHashMap<>());

        try {
            ConnectorMessage blocked = Harness.awaitConnectorStatus(channelId, messageId, DESTINATION, Status.PENDING);
            assertNotNull(blocked.getSent(),
                    "the destination sent its message before the response transformer ran, so the"
                            + " sent content should already be stored alongside the PENDING status");
            assertNull(blocked.getProcessedResponse(),
                    "the response transformer has not returned, so there is no processed response yet");
        } finally {
            // Also on failure, so a wedged transformer never outlives the test.
            Harness.setConfigurationProperty(GATE, token);
        }

        ConnectorMessage finished = Harness.awaitConnectorStatus(channelId, messageId, DESTINATION, Status.SENT);
        assertNotNull(finished.getProcessedResponse(), "no processed response was stored");
        assertEquals("released<" + token + ">",
                MessageAssertions.responsePayload(finished.getProcessedResponse().getContent()),
                "the response transformer's output should be the destination's processed response");
    }
}
