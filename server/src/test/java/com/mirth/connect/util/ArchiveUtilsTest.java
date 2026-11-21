/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
}
