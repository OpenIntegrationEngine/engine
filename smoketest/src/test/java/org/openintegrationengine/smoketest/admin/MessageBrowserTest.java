// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package org.openintegrationengine.smoketest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.openintegrationengine.smoketest.Harness;
import org.openintegrationengine.smoketest.SharedServer;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.attachment.Attachment;
import com.mirth.connect.donkey.model.message.attachment.AttachmentHandlerProperties;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.attachments.AttachmentHandlerType;
import com.mirth.connect.model.filters.MessageFilter;
import com.mirth.connect.model.filters.elements.MetaDataSearchElement;
import com.mirth.connect.model.filters.elements.MetaDataSearchOperator;

/**
 * Sends a few messages through a channel with a Regex attachment handler, then searches,
 * pages, reprocesses and removes them, as the administrator's message browser does.
 */
@DisplayName("Message browser")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MessageBrowserTest {

    private static final String TOKEN = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private static final String SOURCE = "src-" + TOKEN;
    private static final String BLOB = "BLOB0A1B2C";

    private static String channelId;
    private static long withAttachment;
    private static long plain;
    private static long other;
    private static long reprocessed;

    @BeforeAll
    static void deployAndSend() throws Exception {
        Channel channel = Harness.newChannel("admin/channel.xml", "Smoke message browser");
        AttachmentHandlerProperties attachments = AttachmentHandlerType.REGEX.getDefaultProperties();
        attachments.getProperties().put("regex.pattern0", "BLOB[0-9A-F]+");
        attachments.getProperties().put("regex.mimetype0", "text/plain");
        channel.getProperties().setAttachmentProperties(attachments);
        channel.getProperties().setStoreAttachments(true);
        channelId = SharedServer.get().deployChannel(channel, channel.getName(), "");

        // SOURCE is the channel's default metadata column, filled from mirth_source in any map.
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("mirth_source", SOURCE);
        withAttachment = send("alpha " + TOKEN + " " + BLOB, sourceMap);
        plain = send("bravo " + TOKEN, new HashMap<>());
        other = send("charlie", new HashMap<>());
        awaitProcessed(3);
    }

    @AfterAll
    static void remove() {
        Harness.undeploy(channelId);
    }

    // Text search ignores case on some databases but not on Oracle, so the searches below use
    // the exact case.

    @Test
    @Order(1)
    @DisplayName("finds messages by text in their content")
    void textSearchInContent() throws Exception {
        MessageFilter filter = new MessageFilter();
        filter.setTextSearch(TOKEN);

        assertEquals(List.of(plain, withAttachment), ids(admin().getMessages(channelId, filter, false, 0, 10)));
        assertEquals(2L, admin().getMessageCount(channelId, filter));
    }

    @Test
    @Order(2)
    @DisplayName("finds messages by text in a metadata column")
    void textSearchInMetaDataColumn() throws Exception {
        MessageFilter filter = new MessageFilter();
        filter.setTextSearch(SOURCE);
        filter.setTextSearchMetaDataColumns(new ArrayList<>(List.of("SOURCE")));

        assertEquals(List.of(withAttachment), ids(admin().getMessages(channelId, filter, false, 0, 10)));
    }

    @Test
    @Order(3)
    @DisplayName("finds messages by a metadata column's value")
    void metaDataSearch() throws Exception {
        MessageFilter filter = new MessageFilter();
        filter.setMetaDataSearch(new ArrayList<>(
                List.of(new MetaDataSearchElement("SOURCE", MetaDataSearchOperator.EQUAL.name(), SOURCE, false))));

        assertEquals(List.of(withAttachment), ids(admin().getMessages(channelId, filter, false, 0, 10)));
    }

    @Test
    @Order(4)
    @DisplayName("finds messages with attachments and returns their content")
    void attachments() throws Exception {
        MessageFilter filter = new MessageFilter();
        filter.setAttachment(true);
        assertEquals(List.of(withAttachment), ids(admin().getMessages(channelId, filter, false, 0, 10)));

        List<Attachment> attachments = admin().getAttachmentsByMessageId(channelId, withAttachment, true);
        assertEquals(1, attachments.size(), "attachments of message " + withAttachment);
        assertEquals("text/plain", attachments.get(0).getType());

        Attachment attachment = admin().getAttachment(channelId, withAttachment, attachments.get(0).getId());
        assertEquals(BLOB, new String(attachment.getContent(), StandardCharsets.UTF_8));
    }

    @Test
    @Order(5)
    @DisplayName("pages through messages newest first")
    void pages() throws Exception {
        MessageFilter filter = new MessageFilter();
        List<Long> all = new ArrayList<>();
        all.addAll(ids(admin().getMessages(channelId, filter, false, 0, 2)));
        all.addAll(ids(admin().getMessages(channelId, filter, false, 2, 2)));

        assertEquals(List.of(other, plain, withAttachment), all);
    }

    @Test
    @Order(6)
    @DisplayName("reprocesses a message")
    void reprocesses() throws Exception {
        admin().reprocessMessage(channelId, plain, false, null);
        List<Message> messages = awaitProcessed(4);

        Message copy = messages.get(0);
        assertEquals(Long.valueOf(plain), copy.getOriginalId(), "original id of message " + copy.getMessageId());
        reprocessed = copy.getMessageId();
    }

    @Test
    @Order(7)
    @DisplayName("removes messages matching a filter")
    void removes() throws Exception {
        MessageFilter filter = new MessageFilter();
        filter.setTextSearch("charlie");
        admin().removeMessages(channelId, filter);

        Harness.awaitAsserted(() -> assertEquals(0L, admin().getMessageCount(channelId, filter).longValue(), "messages left matching the filter"));
        assertEquals(List.of(reprocessed, plain, withAttachment), ids(admin().getMessages(channelId, new MessageFilter(), false, 0, 10)), "messages left in the channel");
    }

    private static long send(String content, Map<String, Object> sourceMap) throws Exception {
        List<Long> ids = SharedServer.get().submitMessage(channelId, content, sourceMap);
        assertEquals(1, ids.size(), "messages produced by " + content);
        return ids.get(0);
    }

    /** Waits until the channel holds {@code count} messages and all of them are processed. */
    private static List<Message> awaitProcessed(int count) throws Exception {
        return Harness.awaitAssertedValue(() -> {
            List<Message> messages = admin().getMessages(channelId, new MessageFilter(), false, 0, count + 1);
            assertEquals(count, messages.size(), "messages in the channel: " + ids(messages));
            assertTrue(messages.stream().allMatch(Message::isProcessed), "messages not all processed: " + ids(messages));
            return messages;
        });
    }

    private static List<Long> ids(List<Message> messages) {
        List<Long> ids = new ArrayList<>();
        for (Message message : messages) {
            ids.add(message.getMessageId());
        }
        return ids;
    }

    private static Client admin() {
        return SharedServer.get().client();
    }
}
