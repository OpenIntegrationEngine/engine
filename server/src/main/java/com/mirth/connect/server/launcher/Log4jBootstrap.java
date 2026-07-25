// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.server.launcher;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.filefilter.WildcardFileFilter;

/** Utility class for bootstrapping Log4j. */
final class Log4jBootstrap {
    private static final File LOG4J_LIB_DIR = new File("./server-lib/log4j");
    private static final String[] JAR_PATTERNS = { "log4j-core-*.jar", "log4j-api-*.jar", "log4j-1.2-api-*.jar" };

    private Log4jBootstrap() {}

    /** Returns the classpath URLs for the Log4j libraries. */
    static List<URL> getClasspathUrls() throws IOException {
        return getClasspathUrls(LOG4J_LIB_DIR);
    }

    /** Unit testable version of getClasspathUrls. */
    static List<URL> getClasspathUrls(File log4jLibDir) throws IOException {
        List<URL> log4jClasspathUrls = new ArrayList<>();

        for (String jarPattern : JAR_PATTERNS) {
            File[] matchingFiles = log4jLibDir.listFiles((FileFilter) new WildcardFileFilter(jarPattern));
            if (matchingFiles == null || matchingFiles.length != 1) {
                throw new IOException("Expected exactly one " + jarPattern + " in " + log4jLibDir.getAbsolutePath());
            }
            log4jClasspathUrls.add(matchingFiles[0].toURI().toURL());
        }

        return log4jClasspathUrls;
    }
}