// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.model.message.attachment.Attachment;
import com.mirth.connect.model.ChannelStatistics;

/**
 * Message pruning and deletion over the client API: the three deletes the message browser offers,
 * and what each one takes with it. There is no fixture shape for this - a fixture submits a message
 * and asserts what was stored, where every assertion here is about what is no longer stored after a
 * later call.
 *
 * <p>The channel carries an attachment per message, a custom metadata column, and two destinations,
 * so every table a delete has to cascade into (content, attachments, custom metadata, connector
 * messages) holds a row before the delete runs. The cascades themselves are the point: they are
 * separate statements per dialect, and a database that keeps real foreign keys refuses the parent
 * delete if a child row is left behind, so a delete that returns at all has already proved most of
 * its own cascade.
 */
@DisplayName("240-message-deletion/01-cascades")
@TestInstance(PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MessageDeletionTest {

    private static final String CHANNEL = "channels/message-deletion.xml";

    /** Two regex attachments and a custom metadata value, so a delete has something to cascade into. */
    private static final String PAYLOAD = "PHOTO:snapshot bytes;REPORT:written up;body";

    /** What {@link #PAYLOAD} looks like once stored: the attachment handler has taken both values out. */
    private static final Pattern STORED_PAYLOAD = Pattern
            .compile("PHOTO:\\$\\{ATTACH:[0-9a-f-]+\\};REPORT:\\$\\{ATTACH:[0-9a-f-]+\\};body");

    /** The last destination in the chain; once it is SENT the message is done. */
    private static final int LAST_DESTINATION = 2;

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
    @Order(1)
    @DisplayName("deleting one message takes its content, metadata and attachments with it")
    void deleteOneMessage() throws Exception {
        OieServer server = SharedServer.get();
        long deleted = submit("deleted");
        long kept = submit("kept");

        assertEquals(2, attachments(deleted).size(), "the channel should have stored both attachments");

        server.removeMessage(channelId, deleted, null);

        assertNull(server.fetchMessage(channelId, deleted), "the message row survived the delete");
        assertEquals(List.of(), attachments(deleted), "the message's attachments outlived the message");

        // The delete was scoped to one message id, not to the channel.
        Message survivor = server.fetchMessage(channelId, kept);
        assertNotNull(survivor, "deleting one message deleted another one too");
        assertEquals("kept", survivor.getConnectorMessages().get(0).getMetaDataMap().get("MDSTRING"));
        assertEquals(2, attachments(kept).size(), "deleting one message took another message's attachments");
    }

    @Test
    @Order(2)
    @DisplayName("deleting one connector message leaves the rest of the message")
    void deleteConnectorMessage() throws Exception {
        OieServer server = SharedServer.get();
        long messageId = submit("partial");

        server.removeMessage(channelId, messageId, LAST_DESTINATION);

        Message message = server.fetchMessage(channelId, messageId);
        assertNotNull(message, "deleting a destination's connector message deleted the whole message");
        Map<Integer, ConnectorMessage> connectors = message.getConnectorMessages();
        assertFalse(connectors.containsKey(LAST_DESTINATION),
                () -> "destination " + LAST_DESTINATION + " is still there: " + connectors.keySet());
        assertTrue(connectors.containsKey(0) && connectors.containsKey(1),
                () -> "the source and the other destination should be untouched, but got " + connectors.keySet());

        // The remaining connectors kept their own rows in every table the cascade touched.
        ConnectorMessage source = connectors.get(0);
        assertStoredPayload(source.getRaw().getContent(), "the source's content went with the destination's");
        assertEquals("partial", source.getMetaDataMap().get("MDSTRING"),
                "the source's custom metadata went with the destination's");
        assertNotNull(connectors.get(1).getEncoded(), "destination 1's content went with destination 2's");
        assertEquals(2, attachments(messageId).size(),
                "attachments belong to the message, not to one connector, so a connector delete keeps them");
    }

    @Test
    @Order(3)
    @DisplayName("removing all messages empties the channel and leaves it usable")
    void removeAllMessages() throws Exception {
        OieServer server = SharedServer.get();
        long messageId = submit("bulk");
        assertTrue(server.messageCount(channelId) > 0, "nothing to remove");
        ChannelStatistics before = server.statistics(channelId);

        server.removeAllMessages(channelId, false);

        assertEquals(0, server.messageCount(channelId), "the channel still holds messages");
        assertEquals(List.of(), attachments(messageId), "the attachment table was not truncated with the messages");

        // Without clearStatistics the counters are a lifetime total and survive the delete.
        assertStatistics(before, server.statistics(channelId),
                "removing messages should not have touched the statistics");

        // Emptying the channel drops and re-adds the foreign keys between its message tables; a
        // message that processes and reads back afterwards is what proves they were all restored.
        long after = submit("after-bulk-delete");
        Message message = server.fetchMessage(channelId, after);
        assertNotNull(message, "the channel stored nothing after all its messages were removed");
        assertStoredPayload(message.getConnectorMessages().get(0).getRaw().getContent(),
                "the message stored after the bulk delete did not come back intact");
        assertEquals(2, attachments(after).size());
    }

    @Test
    @Order(4)
    @DisplayName("removing all messages can reset the channel's statistics at the same time")
    void removeAllMessagesClearingStatistics() throws Exception {
        OieServer server = SharedServer.get();
        submit("statistics");
        ChannelStatistics before = server.statistics(channelId);
        assertTrue(before.getReceived() > 0 && before.getSent() > 0,
                () -> "the channel should have counted the messages it processed, but got " + before);

        server.removeAllMessages(channelId, true);

        assertEquals(0, server.messageCount(channelId));
        assertStatistics(new ChannelStatistics(), server.statistics(channelId),
                "clearing the statistics should have zeroed every counter");
    }

    /** Submits one message whose {@code MDSTRING} column holds {@code label}, and waits for it to finish. */
    private long submit(String label) throws Exception {
        Map<String, Object> sourceMap = new LinkedHashMap<>();
        sourceMap.put("mdstring", label);
        long messageId = SharedServer.get().submitMessage(channelId, PAYLOAD, sourceMap);
        Harness.awaitConnectorStatus(channelId, messageId, LAST_DESTINATION, Status.SENT);
        // The statistics assertions read a channel-wide total, so wait for the commit that
        // finishes the message rather than only for its last destination.
        Harness.awaitProcessed(channelId, messageId);
        return messageId;
    }

    /** Asserts one stored raw payload is the whole message, with an attachment token per extracted value. */
    private static void assertStoredPayload(String raw, String message) {
        assertTrue(raw != null && STORED_PAYLOAD.matcher(raw).matches(), () -> message + "; got " + raw);
    }

    private List<Attachment> attachments(long messageId) throws Exception {
        return SharedServer.get().fetchAttachments(channelId, messageId);
    }

    /** Compares the four lifetime counters, which is what a delete can change. */
    private static void assertStatistics(ChannelStatistics expected, ChannelStatistics actual, String message) {
        assertEquals(
                List.of(expected.getReceived(), expected.getFiltered(), expected.getSent(), expected.getError()),
                List.of(actual.getReceived(), actual.getFiltered(), actual.getSent(), actual.getError()),
                () -> message + "; expected " + expected + " but got " + actual);
    }
}
