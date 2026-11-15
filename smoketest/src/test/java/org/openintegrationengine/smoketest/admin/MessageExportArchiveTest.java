// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package org.openintegrationengine.smoketest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openintegrationengine.smoketest.Harness;
import org.openintegrationengine.smoketest.SharedServer;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.PaginatedMessageList;
import com.mirth.connect.donkey.model.message.ContentType;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.filters.MessageFilter;
import com.mirth.connect.util.MessageExporter;
import com.mirth.connect.util.messagewriter.EncryptionType;
import com.mirth.connect.util.messagewriter.MessageWriter;
import com.mirth.connect.util.messagewriter.MessageWriterException;
import com.mirth.connect.util.messagewriter.MessageWriterFactory;
import com.mirth.connect.util.messagewriter.MessageWriterOptions;

/**
 * Exports messages into archives, as the administrator's export dialog does, with the engine's own
 * jars on the classpath. The client-side tests run the dialog's code (message writer, archive
 * creation and the zip library) in this JVM; the server-side test runs the same code in the server.
 * They check the archives with the JDK and commons-compress rather than the zip library under
 * test, so they hold across upgrades of that library.
 */
@DisplayName("Message export archives")
class MessageExportArchiveTest {

    private static final String TOKEN = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private static final String PASSWORD = "smoke-" + TOKEN;
    private static final int MESSAGES = 3;

    /**
     * Where server-side exports go: in the server's temp directory (dir.tempdata, appdata/temp by
     * default), relative to its working directory. The harness cannot delete files on the server,
     * so the export is left there, in a folder whose name says it can be deleted.
     */
    private static final String SERVER_EXPORT_PREFIX = "appdata/temp/safe-to-delete-smoketest-export";

    /**
     * What WinZip AES encryption adds to the extra field of each entry: header id 0x9901, data
     * size 7, version (1 for AE-1, 2 for AE-2) and vendor "AE". The key strength and the entry's
     * compression method follow. Which version is written depends on the zip library.
     */
    private static final byte[] AES_EXTRA_AE1 = {0x01, (byte) 0x99, 0x07, 0x00, 0x01, 0x00, 'A', 'E'};
    private static final byte[] AES_EXTRA_AE2 = {0x01, (byte) 0x99, 0x07, 0x00, 0x02, 0x00, 'A', 'E'};

    @TempDir
    static Path exportDirectory;

    private static String channelId;
    private static Map<String, String> contentByMessageId = new HashMap<>();

    @BeforeAll
    static void deployAndSend() throws Exception {
        Channel channel = Harness.newChannel("admin/channel.xml", "Smoke message export");
        channelId = SharedServer.get().deployChannel(channel, channel.getName(), "");

        for (int i = 1; i <= MESSAGES; i++) {
            String content = "export " + TOKEN + " message " + i;
            List<Long> ids = SharedServer.get().submitMessage(channelId, content, new HashMap<>());
            assertEquals(1, ids.size(), "messages produced by " + content);
            contentByMessageId.put(ids.get(0) + ".txt", content);
        }

        Harness.awaitAsserted(() -> {
            List<Message> messages = admin().getMessages(channelId, new MessageFilter(), false, 0, MESSAGES + 1);
            assertEquals(MESSAGES, messages.size(), "messages in the channel");
            assertTrue(messages.stream().allMatch(Message::isProcessed), "messages not all processed");
        });
    }

    @AfterAll
    static void remove() {
        Harness.undeploy(channelId);
    }

    @Test
    @DisplayName("writes an unencrypted ZIP the JDK can read")
    void zip() throws Exception {
        Path archive = exportLocally("zip", ArchiveStreamFactory.ZIP, null, null, null);

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertMessagesInZip(zip);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(EncryptionType.class)
    @DisplayName("writes a password protected ZIP the JDK refuses to open")
    void encryptedZip(EncryptionType type) throws Exception {
        Path archive = exportLocally("encrypted-" + type.name(), ArchiveStreamFactory.ZIP, null, PASSWORD, type);

        // The JDK cannot decrypt, and rejects an archive whose entries are marked as encrypted
        ZipException rejected = assertThrows(ZipException.class, () -> new ZipFile(archive.toFile()).close());
        assertTrue(rejected.getMessage().contains("encrypted"), rejected.getMessage());

        // The entry names are in the archive's bytes, as the headers hold them unencrypted. This
        // shows the archive was written out in full; the JDK's rejection above shows it is encrypted.
        byte[] bytes = Files.readAllBytes(archive);
        for (String name : contentByMessageId.keySet()) {
            assertTrue(indexOf(bytes, name.getBytes(StandardCharsets.UTF_8)) >= 0, "entry " + name + " in the archive");
        }

        // Entries encrypted with AES, and only those, carry the AES extra field
        boolean aes = indexOf(bytes, AES_EXTRA_AE1) >= 0 || indexOf(bytes, AES_EXTRA_AE2) >= 0;
        assertEquals(type != EncryptionType.STANDARD, aes, "AES extra record in the archive");
    }

    @Test
    @DisplayName("writes a gzipped TAR")
    void tarGz() throws Exception {
        Path archive = exportLocally("tar", ArchiveStreamFactory.TAR, CompressorStreamFactory.GZIP, null, null);

        Map<String, String> found = new HashMap<>();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(archive)))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    found.put(entry.getName(), new String(tar.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }

        assertContains(found);
    }

    /**
     * The server writes the archive to its own file system, which this harness cannot read, so
     * this checks that the export completes and counts the messages. A missing library or a
     * failure to finish the archive fails the request.
     */
    @Test
    @DisplayName("exports a password protected ZIP on the server")
    void serverSideEncryptedZip() throws Exception {
        MessageWriterOptions options = options("server-side", ArchiveStreamFactory.ZIP, null, PASSWORD, EncryptionType.AES256);
        options.setBaseFolder(null);
        options.setRootFolder(serverFolder(""));

        assertEquals(MESSAGES, admin().exportMessagesServer(channelId, new MessageFilter(), 2, options));
    }

    /** A password cannot be applied to a TAR, so the export is refused up front, not written unprotected. */
    @Test
    @DisplayName("refuses a password for a TAR")
    void passwordIsRefusedForTar() throws Exception {
        MessageWriterOptions options = options("refused", ArchiveStreamFactory.TAR, null, PASSWORD, EncryptionType.AES256);
        options.setBaseFolder(exportDirectory.toString());
        options.setRootFolder(exportDirectory.resolve("refused").toString());

        assertThrows(MessageWriterException.class, () -> MessageWriterFactory.getInstance().getMessageWriter(options, null));
        assertFalse(Files.exists(exportDirectory.resolve("refused")), "export directory of a refused export");

        // The server refuses it too, before it writes anything
        MessageWriterOptions serverOptions = options("refused-on-server", ArchiveStreamFactory.TAR, null, PASSWORD, EncryptionType.AES256);
        serverOptions.setBaseFolder(null);
        serverOptions.setRootFolder(serverFolder("-refused"));
        assertThrows(ClientException.class, () -> admin().exportMessagesServer(channelId, new MessageFilter(), 2, serverOptions));
    }

    private static String serverFolder(String suffix) {
        return SERVER_EXPORT_PREFIX + suffix + "-" + TOKEN;
    }

    /** Exports the channel's messages to a file in a new directory, as the export dialog does locally. */
    private static Path exportLocally(String name, String archiver, String compressor, String password, EncryptionType type) throws Exception {
        Path root = Files.createDirectories(exportDirectory.resolve(name));
        MessageWriterOptions options = options(name, archiver, compressor, password, type);
        options.setBaseFolder(root.toString());
        options.setRootFolder(root.toString());

        PaginatedMessageList messages = new PaginatedMessageList();
        messages.setClient(admin());
        messages.setChannelId(channelId);
        messages.setMessageFilter(new MessageFilter());
        messages.setPageSize(2);
        messages.setIncludeContent(true);
        messages.setItemCount(admin().getMessageCount(channelId, new MessageFilter()));

        MessageWriter writer = MessageWriterFactory.getInstance().getMessageWriter(options, null);
        int exported;
        try {
            exported = new MessageExporter().exportMessages(messages, writer, null, options);
            writer.finishWrite();
        } finally {
            writer.close();
        }
        assertEquals(MESSAGES, exported, "messages exported");

        String extension = compressor == null ? archiver : archiver + "." + compressor;
        Path archive = root.resolve(name + "." + extension);

        List<String> files;
        try (var list = Files.list(root)) {
            files = list.map(file -> file.getFileName().toString()).sorted().toList();
        }
        assertTrue(Files.isRegularFile(archive), "archive " + archive.getFileName() + " in " + files);
        // Next to the archive: the export's readme, and none of the working folder it was built in
        assertEquals(List.of("EXPORTREADME.txt", archive.getFileName().toString()), files, "files in the export directory");
        return archive;
    }

    private static MessageWriterOptions options(String archiveName, String archiver, String compressor, String password, EncryptionType type) {
        MessageWriterOptions options = new MessageWriterOptions();
        options.setContentType(ContentType.RAW);
        options.setFilePattern("${message.messageId}.txt");
        options.setArchiveFileName(archiveName);
        options.setArchiveFormat(archiver);
        options.setCompressFormat(compressor);
        options.setPasswordEnabled(password != null);
        options.setPassword(password);
        options.setEncryptionType(type);
        return options;
    }

    private static void assertMessagesInZip(ZipFile zip) throws IOException {
        Map<String, String> found = new HashMap<>();
        for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
            if (!entry.isDirectory()) {
                try (InputStream in = zip.getInputStream(entry)) {
                    found.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }

        assertContains(found);
    }

    /** The archive holds each message's raw content under its file name (and may hold other files). */
    private static void assertContains(Map<String, String> found) {
        for (Map.Entry<String, String> expected : contentByMessageId.entrySet()) {
            // The writer ends each message with blank lines
            assertEquals(expected.getValue(), found.get(expected.getKey()) == null ? null : found.get(expected.getKey()).strip(), "content of " + expected.getKey() + " in the archive of " + new ArrayList<>(found.keySet()));
        }
    }

    private static int indexOf(byte[] bytes, byte[] target) {
        outer: for (int i = 0; i + target.length <= bytes.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (bytes[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static Client admin() {
        return SharedServer.get().client();
    }
}
