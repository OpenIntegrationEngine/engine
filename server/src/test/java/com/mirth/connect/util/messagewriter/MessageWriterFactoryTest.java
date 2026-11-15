// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package com.mirth.connect.util.messagewriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mirth.connect.util.ArchiveUtils;

public class MessageWriterFactoryTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void passwordIsAcceptedForPlainZip() throws Exception {
        MessageWriter writer = MessageWriterFactory.getInstance().getMessageWriter(options(ArchiveStreamFactory.ZIP, null, "secret"), null);

        assertTrue(writer instanceof MessageWriterArchive);
    }

    @Test
    public void passwordIsRejectedForNonZipArchive() throws Exception {
        assertPasswordRejected(ArchiveStreamFactory.TAR, null);
    }

    @Test
    public void passwordIsRejectedForZipWithCompressor() throws Exception {
        assertPasswordRejected(ArchiveStreamFactory.ZIP, CompressorStreamFactory.GZIP);
    }

    @Test
    public void blankPasswordIsNotRejected() throws Exception {
        MessageWriter writer = MessageWriterFactory.getInstance().getMessageWriter(options(ArchiveStreamFactory.TAR, CompressorStreamFactory.GZIP, ""), null);

        assertTrue(writer instanceof MessageWriterArchive);
    }

    @Test
    public void passwordIsIgnoredWhenNotEnabled() throws Exception {
        MessageWriterOptions options = options(ArchiveStreamFactory.TAR, null, "secret");
        options.setPasswordEnabled(false);

        assertTrue(MessageWriterFactory.getInstance().getMessageWriter(options, null) instanceof MessageWriterArchive);
    }

    private void assertPasswordRejected(String archiver, String compressor) throws Exception {
        try {
            MessageWriterFactory.getInstance().getMessageWriter(options(archiver, compressor, "secret"), null);
            fail("Expected the password to be rejected");
        } catch (MessageWriterException e) {
            assertEquals(ArchiveUtils.PASSWORD_UNSUPPORTED_MESSAGE, e.getMessage());
        }

        // Nothing should have been created for a run that cannot succeed
        try (Stream<Path> created = Files.list(tempFolder.getRoot().toPath())) {
            assertEquals(0, created.count());
        }
    }

    private MessageWriterOptions options(String archiver, String compressor, String password) {
        MessageWriterOptions options = new MessageWriterOptions();
        options.setBaseFolder(tempFolder.getRoot().getPath());
        options.setRootFolder(new File(tempFolder.getRoot(), "export").getPath());
        options.setFilePattern("message");
        options.setArchiveFormat(archiver);
        options.setCompressFormat(compressor);
        options.setPasswordEnabled(true);
        options.setPassword(password);
        options.setEncryptionType(EncryptionType.AES256);
        return options;
    }
}
