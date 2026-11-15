// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: 2026 Tony Germano

package com.mirth.connect.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mirth.connect.util.messagewriter.EncryptionType;

public class ArchiveUtilsTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File installTempDir;

    @Before
    public void createTestFolder() throws Exception {
        // Nested so that a "../" entry stays inside the temporary folder
        installTempDir = tempFolder.newFolder("zipextraction");
    }

    @Test(expected = ZipException.class)
    public void testExtractZipEntryZipSlipWithRelativePath() throws Exception {
        File file = ZipTestUtils.createTempZipFile(tempFolder.getRoot(), "../ZipSlip.txt");
        try (ZipInputStream zis = openZip(file)) {
            ArchiveUtils.extractArchive(installTempDir, zis);
        }
    }

    @Test
    public void testExtractZipNoZipSlip() throws Exception {
        File file = ZipTestUtils.createTempZipFile(tempFolder.getRoot(), "ZipSlip.txt");
        List<URL> fileUrls;
        try (ZipInputStream zis = openZip(file)) {
            fileUrls = ArchiveUtils.extractArchive(installTempDir, zis);
        }
        assertFalse(fileUrls.isEmpty());
        assertTrue(Files.exists(installTempDir.toPath().resolve("ZipSlip.txt")));
    }

    @Test
    public void testExtractEmptyZipFile() throws Exception {
        File file = ZipTestUtils.createTempZipFile(tempFolder.getRoot(), null);
        List<URL> fileUrls;
        try (ZipInputStream zis = openZip(file)) {
            fileUrls = ArchiveUtils.extractArchive(installTempDir, zis);
        }
        assertTrue(fileUrls.isEmpty());
    }

    private static ZipInputStream openZip(File file) throws IOException {
        return new ZipInputStream(new BufferedInputStream(Files.newInputStream(file.toPath())));
    }

    @Test
    public void testCreateZipUnencrypted() throws Exception {
        File source = createSourceFolder();
        File archive = new File(tempFolder.getRoot(), "plain.zip");

        ArchiveUtils.createArchive(source, archive, ArchiveStreamFactory.ZIP, null, null, null);

        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(archive)) {
            assertEquals("alpha", readEntry(zipFile, "a.txt"));
            assertEquals("bravo", readEntry(zipFile, "sub/b.txt"));
        }
    }

    @Test
    public void testCreateZipStandardEncryption() throws Exception {
        assertEncryptedRoundTrip(EncryptionType.STANDARD, EncryptionMethod.ZIP_STANDARD, null);
    }

    @Test
    public void testCreateZipAes128() throws Exception {
        assertEncryptedRoundTrip(EncryptionType.AES128, EncryptionMethod.AES, AesKeyStrength.KEY_STRENGTH_128);
    }

    @Test
    public void testCreateZipAes256() throws Exception {
        assertEncryptedRoundTrip(EncryptionType.AES256, EncryptionMethod.AES, AesKeyStrength.KEY_STRENGTH_256);
    }

    @Test
    public void testCreateTar() throws Exception {
        File archive = new File(tempFolder.getRoot(), "plain.tar");

        ArchiveUtils.createArchive(createSourceFolder(), archive, ArchiveStreamFactory.TAR, null, null, null);

        try (InputStream in = Files.newInputStream(archive.toPath())) {
            assertSourceFolderContents(readTar(in));
        }
    }

    @Test
    public void testCreateTarGz() throws Exception {
        File archive = new File(tempFolder.getRoot(), "plain.tar.gz");

        ArchiveUtils.createArchive(createSourceFolder(), archive, ArchiveStreamFactory.TAR, CompressorStreamFactory.GZIP, null, null);

        try (InputStream in = new GZIPInputStream(Files.newInputStream(archive.toPath()))) {
            assertSourceFolderContents(readTar(in));
        }
    }

    @Test
    public void testCreateZipGz() throws Exception {
        File archive = new File(tempFolder.getRoot(), "plain.zip.gz");

        ArchiveUtils.createArchive(createSourceFolder(), archive, ArchiveStreamFactory.ZIP, CompressorStreamFactory.GZIP, null, null);

        Map<String, String> found = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new GZIPInputStream(Files.newInputStream(archive.toPath())))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    found.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        assertSourceFolderContents(found);
    }

    @Test
    public void testCreateZipFromEmptyFolder() throws Exception {
        File archive = new File(tempFolder.getRoot(), "empty.zip");

        ArchiveUtils.createArchive(tempFolder.newFolder(), archive, ArchiveStreamFactory.ZIP, null, null, null);

        // A valid archive with no entries, which needs its central directory to have been written
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(archive)) {
            assertEquals(0, zipFile.size());
        }
    }

    private static Map<String, String> readTar(InputStream in) throws IOException {
        Map<String, String> found = new HashMap<>();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(in)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (!entry.isDirectory()) {
                    found.put(entry.getName(), new String(tar.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return found;
    }

    private static void assertSourceFolderContents(Map<String, String> found) {
        Map<String, String> expected = new HashMap<>();
        expected.put("a.txt", "alpha");
        expected.put("sub/b.txt", "bravo");
        assertEquals(expected, found);
    }

    @Test
    public void testZipWriteFailureIsReportedAndFileIsClosed() throws Exception {
        assertWriteFailureIsReportedAndFileIsClosed(ArchiveStreamFactory.ZIP, null);
    }

    @Test
    public void testTarWriteFailureIsReportedAndFileIsClosed() throws Exception {
        assertWriteFailureIsReportedAndFileIsClosed(ArchiveStreamFactory.TAR, null);
    }

    @Test
    public void testTarGzWriteFailureIsReportedAndFileIsClosed() throws Exception {
        assertWriteFailureIsReportedAndFileIsClosed(ArchiveStreamFactory.TAR, CompressorStreamFactory.GZIP);
    }

    @Test
    public void testZipGzWriteFailureIsReportedAndFileIsClosed() throws Exception {
        assertWriteFailureIsReportedAndFileIsClosed(ArchiveStreamFactory.ZIP, CompressorStreamFactory.GZIP);
    }

    /**
     * /dev/full accepts the open but fails every write with ENOSPC. The failure must be reported,
     * including when it only surfaces as the archive is finished, and not hidden by an error from
     * closing an entry; and the file must still be closed.
     */
    private void assertWriteFailureIsReportedAndFileIsClosed(String archiver, String compressor) throws Exception {
        Path devFull = Path.of("/dev/full");
        Path openFiles = Path.of("/proc/self/fd");
        // Opening it for writing is what the test needs; it can exist but be unusable in a container
        Assume.assumeTrue("Requires Linux (/dev/full and /proc)", Files.isWritable(devFull) && Files.isDirectory(openFiles));

        File source = createSourceFolder();
        int before = countOpenFilesMatching(openFiles, devFull);

        try {
            ArchiveUtils.createArchive(source, devFull.toFile(), archiver, compressor, null, null);
            fail("Expected the write to fail");
        } catch (ArchiveUtils.CompressException e) {
            Throwable cause = e;
            boolean foundIOException = false;
            while (cause != null) {
                assertFalse("The write failure was hidden by " + cause, cause instanceof NullPointerException);
                assertFalse("The archive was not opened: " + cause, cause instanceof FileNotFoundException);
                foundIOException |= cause instanceof IOException;
                cause = cause.getCause();
            }
            assertTrue("Expected the IOException from the failed write in the cause chain", foundIOException);
        }

        assertEquals("The archive file was left open", before, countOpenFilesMatching(openFiles, devFull));
    }

    private static int countOpenFilesMatching(Path fdDirectory, Path target) throws IOException {
        int count = 0;
        try (Stream<Path> fds = Files.list(fdDirectory)) {
            for (Path fd : (Iterable<Path>) fds::iterator) {
                try {
                    if (Files.readSymbolicLink(fd).equals(target)) {
                        count++;
                    }
                } catch (IOException e) {
                    // The descriptor was closed while listing (including the one used to list)
                }
            }
        }
        return count;
    }

    @Test
    public void testPasswordRejectedForNonZipArchive() throws Exception {
        assertPasswordRejectedAndNothingWritten(ArchiveStreamFactory.TAR, null);
    }

    @Test
    public void testPasswordRejectedForZipWithCompressor() throws Exception {
        assertPasswordRejectedAndNothingWritten(ArchiveStreamFactory.ZIP, CompressorStreamFactory.GZIP);
    }

    private void assertPasswordRejectedAndNothingWritten(String archiver, String compressor) throws Exception {
        File archive = new File(tempFolder.getRoot(), "rejected.out");

        File source = createSourceFolder();

        assertThrows(ArchiveUtils.CompressException.class, () -> ArchiveUtils.createArchive(source, archive, archiver, compressor, "secret", EncryptionType.AES256));
        assertFalse(archive.exists());
    }

    private void assertEncryptedRoundTrip(EncryptionType type, EncryptionMethod method, AesKeyStrength strength) throws Exception {
        File source = createSourceFolder();
        File archive = new File(tempFolder.getRoot(), type.name() + ".zip");

        ArchiveUtils.createArchive(source, archive, ArchiveStreamFactory.ZIP, null, "secret", type);

        // Independent evidence that it is encrypted: the JDK refuses archives whose entries are
        java.util.zip.ZipException rejected = assertThrows(java.util.zip.ZipException.class, () -> new java.util.zip.ZipFile(archive).close());
        assertTrue(rejected.getMessage(), rejected.getMessage().contains("encrypted"));

        File extracted = tempFolder.newFolder(type.name() + "-extracted");

        try (ZipFile zipFile = new ZipFile(archive, "secret".toCharArray())) {
            assertTrue(zipFile.isEncrypted());

            List<FileHeader> headers = zipFile.getFileHeaders();
            assertEquals(2, headers.size());
            for (FileHeader header : headers) {
                assertTrue(header.isEncrypted());
                assertEquals(method, header.getEncryptionMethod());
                if (strength != null) {
                    assertEquals(strength, header.getAesExtraDataRecord().getAesKeyStrength());
                }
            }

            zipFile.extractAll(extracted.getPath());
        }

        assertEquals("alpha", Files.readString(extracted.toPath().resolve("a.txt"), StandardCharsets.UTF_8));
        assertEquals("bravo", Files.readString(extracted.toPath().resolve("sub/b.txt"), StandardCharsets.UTF_8));

        File wrongPasswordTarget = tempFolder.newFolder(type.name() + "-wrong");
        assertThrows(net.lingala.zip4j.exception.ZipException.class, () -> {
            try (ZipFile zipFile = new ZipFile(archive, "wrong".toCharArray())) {
                zipFile.extractAll(wrongPasswordTarget.getPath());
            }
        });
    }

    private File createSourceFolder() throws Exception {
        Path source = tempFolder.newFolder().toPath();
        Files.writeString(source.resolve("a.txt"), "alpha", StandardCharsets.UTF_8);
        Files.writeString(Files.createDirectory(source.resolve("sub")).resolve("b.txt"), "bravo", StandardCharsets.UTF_8);
        return source.toFile();
    }

    private static String readEntry(java.util.zip.ZipFile zipFile, String name) throws Exception {
        try (InputStream in = zipFile.getInputStream(zipFile.getEntry(name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
