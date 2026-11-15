/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.base.Strings;

public class ZipTestUtils {

    public static File createTempZipFile(String fileName) throws Exception {
        return createTempZipFile(null, fileName); // system defined temp
    }

    public static File createTempZipFile(File directory, String fileName) throws Exception {
        Path tempFile = directory == null ? Files.createTempFile("temp_zip", ".zip") : Files.createTempFile(directory.toPath(), "temp_zip", ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
            if (!Strings.isNullOrEmpty(fileName)) {
                zos.putNextEntry(new ZipEntry(fileName));
                zos.write("file contents".getBytes());
                zos.closeEntry();
            }
        }

        return tempFile.toFile();
    }
}
