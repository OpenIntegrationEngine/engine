// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.server.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Unit tests for {@link Log4jBootstrap}. */
public class Log4jBootstrapTest {

    /** Tests that {@link Log4jBootstrap#getClasspathUrls(File)} correctly resolves Log4j JARs regardless of version. */
    @Test
    public void testGetClasspathUrlsDoesNotDependOnVersions() throws Exception {
        File log4jLibDir = Files.createTempDirectory("log4j").toFile();
        log4jLibDir.deleteOnExit();

        for (String name : Arrays.asList("log4j-core-99.0.0.jar", "log4j-api-99.0.0.jar", "log4j-1.2-api-99.0.0.jar")) {
            File jarFile = new File(log4jLibDir, name);
            assertTrue(jarFile.createNewFile());
            jarFile.deleteOnExit();
        }

        List<URL> classpathUrls = Log4jBootstrap.getClasspathUrls(log4jLibDir);

        assertEquals(Arrays.asList(
                new File(log4jLibDir, "log4j-core-99.0.0.jar").toURI().toURL(),
                new File(log4jLibDir, "log4j-api-99.0.0.jar").toURI().toURL(),
                new File(log4jLibDir, "log4j-1.2-api-99.0.0.jar").toURI().toURL()), classpathUrls);
    }
}