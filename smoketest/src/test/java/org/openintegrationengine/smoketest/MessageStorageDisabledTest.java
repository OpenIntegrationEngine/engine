// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.mirth.connect.donkey.model.message.Message;

/**
 * {@code MessageStorageMode.DISABLED}, the one storage level that ci/tests/160-message-storage-levels
 * cannot express. The channel is given a pass-through DAO, so no message row is ever written and
 * there is no message for the fixture harness to retrieve and assert against.
 */
@DisplayName("160-message-storage-levels/05-disabled")
@TestInstance(PER_CLASS)
class MessageStorageDisabledTest {

    private static final String CHANNEL = "channels/message-storage-disabled.xml";

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
    void storesNoMessage() throws Exception {
        OieServer server = SharedServer.get();
        long messageId = server.submitMessage(channelId, "Hello world!", new LinkedHashMap<>());
        Message stored = server.fetchMessage(channelId, messageId);
        assertNull(stored, () -> "DISABLED storage wrote a row for message " + messageId);
    }
}
