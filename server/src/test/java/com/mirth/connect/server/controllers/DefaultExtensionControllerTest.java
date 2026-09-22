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
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DefaultExtensionControllerTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test(expected = ZipException.class)
    public void testExtractZipEntryZipSlipWithRelativePath() throws Exception {
        File destination = temporaryFolder.newFolder("extraction");
        try (ZipFile zip = createTempZipFile("ZipSlip.txt")) {
            ExtensionInstaller.extractZipEntry(new ZipEntry("../ZipSlip.txt"), destination, zip);
        }
    }

    @Test
    public void testExtractZipEntryValidPath() throws Exception {
        File destination = temporaryFolder.newFolder("extraction");
        try (ZipFile zip = createTempZipFile("good.txt")) {
            ExtensionInstaller.extractZipEntry(new ZipEntry("good.txt"), destination, zip);
        }
        assertTrue(new File(destination, "good.txt").exists());
    }

    private ZipFile createTempZipFile(String fileName) throws Exception {
        File archive = temporaryFolder.newFile("archive.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive.toPath()))) {
            zip.putNextEntry(new ZipEntry(fileName));
            zip.write("file contents".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new ZipFile(archive);
    }
}
