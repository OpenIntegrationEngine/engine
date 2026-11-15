/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.controllers;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mirth.connect.util.ZipTestUtils;

public class DefaultExtensionControllerTest {

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
        DefaultExtensionController extensionController = new DefaultExtensionController();

        ZipEntry entry = new ZipEntry("../ZipSlip.txt");
        try (ZipFile zipFile = createTempZipFile("ZipSlip.txt")) {
            extensionController.extractZipEntry(entry, installTempDir, zipFile);
        }
    }

    @Test
    public void testExtractZipEntryValidPath() throws Exception {
        DefaultExtensionController extensionController = new DefaultExtensionController();

        ZipEntry entry = new ZipEntry("good.txt");
        try (ZipFile zipFile = createTempZipFile("good.txt")) {
            extensionController.extractZipEntry(entry, installTempDir, zipFile);
        }

        assertTrue(Files.exists(installTempDir.toPath().resolve("good.txt")));
    }

    private ZipFile createTempZipFile(String fileName) throws Exception {
        return new ZipFile(ZipTestUtils.createTempZipFile(tempFolder.getRoot(), fileName));
    }
}
