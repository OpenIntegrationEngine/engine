// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: 2026 Tony Germano

package com.mirth.connect.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.base.Strings;

public class ZipTestUtils {

    /** Creates a zip with one entry (or none, if fileName is empty) in the given directory. */
    public static File createTempZipFile(File directory, String fileName) throws Exception {
        Path tempFile = Files.createTempFile(directory.toPath(), "temp_zip", ".zip");

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
