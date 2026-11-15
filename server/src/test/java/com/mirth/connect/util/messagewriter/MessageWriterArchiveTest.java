// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package com.mirth.connect.util.messagewriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mirth.connect.donkey.model.message.Message;

import net.lingala.zip4j.ZipFile;

public class MessageWriterArchiveTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path exportFolder;
    private Path rootFolder;
    private File archiveFile;

    @Before
    public void createFolders() throws Exception {
        exportFolder = tempFolder.newFolder("export").toPath();
        // Where the messages are written before they are archived, as MessageWriterFactory sets it up
        rootFolder = exportFolder.resolve(".archive");
        archiveFile = exportFolder.resolve("archive.zip").toFile();
    }

    @Test
    public void testFinishWriteMovesPasswordProtectedArchiveIntoPlace() throws Exception {
        MessageWriterArchive writer = new MessageWriterArchive(new FolderWriter(true), rootFolder.toFile(), archiveFile, ArchiveStreamFactory.ZIP, null, "secret", EncryptionType.AES256);

        assertTrue(writer.write(new Message()));
        writer.finishWrite();
        writer.close();

        // Only the archive is left: neither the folder the messages were written to nor the file it was built in
        assertEquals(List.of("archive.zip"), listing());

        try (ZipFile zipFile = new ZipFile(archiveFile, "secret".toCharArray())) {
            assertTrue(zipFile.isEncrypted());

            File extracted = tempFolder.newFolder("extracted");
            zipFile.extractAll(extracted.getPath());
            assertEquals("message", Files.readString(extracted.toPath().resolve("message.txt"), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testFinishWriteCreatesNoArchiveWhenNothingWasWritten() throws Exception {
        MessageWriterArchive writer = new MessageWriterArchive(new FolderWriter(false), rootFolder.toFile(), archiveFile, ArchiveStreamFactory.ZIP, null, null, null);

        assertFalse(writer.write(new Message()));
        writer.finishWrite();
        writer.close();

        assertFalse(archiveFile.exists());
    }

    @Test
    public void testFinishWriteLeavesNoPartialArchiveWhenArchivingFails() throws Exception {
        MessageWriterArchive writer = new MessageWriterArchive(new FolderWriter(true), rootFolder.toFile(), archiveFile, "not-an-archiver", null, null, null);

        assertTrue(writer.write(new Message()));
        try {
            writer.finishWrite();
            fail("Expected the archive to fail");
        } catch (MessageWriterException e) {
            // expected
        }

        // Neither an archive, nor the file it was being built in, nor the exported messages
        assertEquals(List.of(), listing());
    }

    @Test
    public void testFinishWriteKeepsFinishedArchiveWhenMovingItFails() throws Exception {
        // An archive at the destination that cannot be replaced: a folder that cannot be emptied
        Path blocking = Files.createDirectories(archiveFile.toPath());
        Files.writeString(blocking.resolve("keep.txt"), "keep", StandardCharsets.UTF_8);
        Assume.assumeTrue("Requires POSIX permissions", Files.getFileStore(blocking).supportsFileAttributeView("posix"));
        Files.setPosixFilePermissions(blocking, PosixFilePermissions.fromString("r-xr-xr-x"));

        try {
            Assume.assumeFalse("Running as a user that ignores permissions", Files.isWritable(blocking));

            MessageWriterArchive writer = new MessageWriterArchive(new FolderWriter(true), rootFolder.toFile(), archiveFile, ArchiveStreamFactory.ZIP, null, null, null);
            assertTrue(writer.write(new Message()));
            try {
                writer.finishWrite();
                fail("Expected moving the archive to fail");
            } catch (MessageWriterException e) {
                // expected
            }

            // The finished archive is kept where it was built, and the exported messages are removed
            assertEquals(List.of(".archive.zip", "archive.zip"), listing());
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(exportFolder.resolve(".archive.zip").toFile())) {
                assertTrue(zip.getEntry("message.txt") != null);
            }
        } finally {
            Files.setPosixFilePermissions(blocking, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    private List<String> listing() throws Exception {
        try (Stream<Path> files = Files.list(exportFolder)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    /** Stands in for the writer that puts each message into a file in the folder to archive. */
    private class FolderWriter implements MessageWriter {

        private final boolean written;

        FolderWriter(boolean written) {
            this.written = written;
        }

        @Override
        public boolean write(Message message) throws MessageWriterException {
            if (written) {
                try {
                    Files.createDirectories(rootFolder);
                    Files.writeString(rootFolder.resolve("message.txt"), "message", StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new MessageWriterException(e);
                }
            }
            return written;
        }

        @Override
        public void finishWrite() {
        }

        @Override
        public void close() {
        }
    }
}
