// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.server.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.FileUtils;

public class Log4jMigrations {
    private static final String INVALID_LOG4J_PROPERTY = "dir.logs";

    private Log4jMigrations() {}

    public static void migrateConfiguration(File propertiesFile) {
        if (!propertiesFile.exists() || !propertiesFile.isFile()) {
            return;
        }

        try {
            List<String> lines = FileUtils.readLines(propertiesFile, StandardCharsets.UTF_8);
            if (stripDirLogs(lines)) {
                FileUtils.writeLines(propertiesFile, StandardCharsets.UTF_8.name(), lines, System.lineSeparator(), false);
            }
        } catch (IOException e) {
            System.err.println("Failed to migrate Log4j configuration: " + propertiesFile.getAbsolutePath());
            e.printStackTrace();
        }
    }

    private static boolean stripDirLogs(List<String> lines) {
        return lines.removeIf(line -> isPropertyLine(line, INVALID_LOG4J_PROPERTY));
    }

    private static boolean isPropertyLine(String line, String propertyName) {
        String trimmedLine = line.trim();
        int equalsIndex = trimmedLine.indexOf('=');
        return equalsIndex >= 0 && trimmedLine.substring(0, equalsIndex).trim().equals(propertyName);
    }
}