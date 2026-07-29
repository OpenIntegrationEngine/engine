// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Giovanni Giannola <giogiannola@globalesm.com>

package com.mirth.connect.donkey.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Calendar;

import org.junit.Test;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.Status;

public class MessageTest {

    private static final String CHANNEL_ID = "test-channel-id";
    private static final String CHANNEL_NAME = "Test Channel";
    private static final String SERVER_ID = "test-server-id";
    private static final long MESSAGE_ID = 1L;

    /**
     * Regression test for issue #309: the channelName variable was not available in the
     * Postprocessor script because the merged connector message (used to build the postprocessor
     * scope) never had its channelName populated.
     */
    @Test
    public void mergedConnectorMessageInheritsChannelNameFromSourceConnectorMessage() {
        Calendar receivedDate = Calendar.getInstance();

        Message message = new Message();
        message.setMessageId(MESSAGE_ID);
        message.setChannelId(CHANNEL_ID);
        message.setServerId(SERVER_ID);
        message.setReceivedDate(receivedDate);

        ConnectorMessage sourceConnectorMessage = new ConnectorMessage(CHANNEL_ID, CHANNEL_NAME, MESSAGE_ID, 0, SERVER_ID, receivedDate, Status.RECEIVED);
        message.getConnectorMessages().put(0, sourceConnectorMessage);

        assertEquals(CHANNEL_NAME, message.getMergedConnectorMessage().getChannelName());
    }

    /**
     * When the Message itself carries a channelName, it should be propagated to the merged
     * connector message even if (theoretically) the source connector message did not have one.
     */
    @Test
    public void mergedConnectorMessageUsesMessageChannelNameWhenSet() {
        Calendar receivedDate = Calendar.getInstance();

        Message message = new Message();
        message.setMessageId(MESSAGE_ID);
        message.setChannelId(CHANNEL_ID);
        message.setChannelName(CHANNEL_NAME);
        message.setServerId(SERVER_ID);
        message.setReceivedDate(receivedDate);

        ConnectorMessage sourceConnectorMessage = new ConnectorMessage(CHANNEL_ID, null, MESSAGE_ID, 0, SERVER_ID, receivedDate, Status.RECEIVED);
        message.getConnectorMessages().put(0, sourceConnectorMessage);

        assertEquals(CHANNEL_NAME, message.getMergedConnectorMessage().getChannelName());
    }

    /**
     * With no channel name available anywhere, the merged connector message channelName stays null
     * (i.e. the fix does not fabricate a value).
     */
    @Test
    public void mergedConnectorMessageChannelNameNullWhenUnavailable() {
        Calendar receivedDate = Calendar.getInstance();

        Message message = new Message();
        message.setMessageId(MESSAGE_ID);
        message.setChannelId(CHANNEL_ID);
        message.setServerId(SERVER_ID);
        message.setReceivedDate(receivedDate);

        ConnectorMessage sourceConnectorMessage = new ConnectorMessage(CHANNEL_ID, null, MESSAGE_ID, 0, SERVER_ID, receivedDate, Status.RECEIVED);
        message.getConnectorMessages().put(0, sourceConnectorMessage);

        assertNull(message.getMergedConnectorMessage().getChannelName());
    }
}
