// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.MessageContent;
import com.mirth.connect.donkey.model.message.Status;

/**
 * A channel with {@code encryptData} set stores its message content encrypted. No fixture can
 * assert this: encryption is per-message randomised, so the stored value is different every run,
 * and the only fixed part of it is the header the ciphertext carries.
 *
 * <p>Which of the two read APIs is used decides what comes back. {@code getMessages} with content
 * leaves the DAO's decryption off, so it hands back the column as stored; {@code getMessageContent}
 * decrypts. The two together are what makes "encrypted at rest" observable from a client.
 */
@DisplayName("210-encryption-at-rest")
@TestInstance(PER_CLASS)
class EncryptionAtRestTest {

    private static final String CHANNEL = "channels/encryption-at-rest.xml";

    /** Prefix {@code KeyEncryptor} writes in front of every ciphertext, carrying the IV. */
    private static final String ENCRYPTION_HEADER = "{alg=";

    private static final String RAW = "Hello world!";
    /** The source transformer prefixes the message, so transformed and encoded differ from raw. */
    private static final String TRANSFORMED = "transformed:" + RAW;

    private static final int SOURCE = 0;
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
    void storesEveryContentStageEncrypted() throws Exception {
        Message stored = process();
        ConnectorMessage source = connector(stored, SOURCE);
        ConnectorMessage destination = connector(stored, DESTINATION);

        assertStoredEncrypted("source raw", source.getRaw());
        assertStoredEncrypted("source transformed", source.getTransformed());
        assertStoredEncrypted("source encoded", source.getEncoded());
        // The destination's own content, written by a different DAO call than the source's.
        assertStoredEncrypted("destination sent", destination.getSent());
    }

    @Test
    void decryptsContentWhenReadBack() throws Exception {
        Message stored = process();
        ConnectorMessage source = connector(
                SharedServer.get().fetchDecryptedMessage(channelId, stored.getMessageId(),
                        List.of(SOURCE, DESTINATION)),
                SOURCE);

        assertDecrypted("source raw", source.getRaw(), RAW);
        assertDecrypted("source transformed", source.getTransformed(), TRANSFORMED);
        assertDecrypted("source encoded", source.getEncoded(), TRANSFORMED);
    }

    /** Submits one message and returns it as stored, once the destination has finished with it. */
    private Message process() throws Exception {
        OieServer server = SharedServer.get();
        long messageId = server.submitMessage(channelId, RAW, new LinkedHashMap<>());
        Harness.awaitConnectorStatus(channelId, messageId, DESTINATION, Status.SENT);
        return server.fetchMessage(channelId, messageId);
    }

    /**
     * Asserts that what the server stored is ciphertext: flagged encrypted, carrying the
     * encryptor's header, and not the plaintext it was made from.
     */
    private static void assertStoredEncrypted(String label, MessageContent content) {
        assertNotNull(content, () -> label + " was not stored at all");
        String stored = content.getContent();
        assertTrue(content.isEncrypted(), () -> label + " is not flagged as encrypted: " + stored);
        assertTrue(stored.startsWith(ENCRYPTION_HEADER),
                () -> label + " does not carry an encryption header: " + stored);
        assertFalse(stored.contains(RAW), () -> label + " leaks the plaintext payload: " + stored);
    }

    /** Asserts that the decrypting read path recovers the original content. */
    private static void assertDecrypted(String label, MessageContent content, String expected) {
        assertNotNull(content, () -> label + " was not returned by the decrypting read");
        assertEquals(expected, content.getContent(), () -> label + " did not decrypt to its original");
        assertFalse(content.isEncrypted(), () -> label + " is still flagged as encrypted after decryption");
    }

    private static ConnectorMessage connector(Message message, int metaDataId) {
        assertNotNull(message, "The server returned no message");
        ConnectorMessage connectorMessage = message.getConnectorMessages().get(metaDataId);
        assertNotNull(connectorMessage,
                () -> "Message " + message.getMessageId() + " has no connector " + metaDataId
                        + "; present ids: " + message.getConnectorMessages().keySet());
        return connectorMessage;
    }
}
