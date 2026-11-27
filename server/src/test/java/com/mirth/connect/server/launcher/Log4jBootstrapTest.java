// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.server.launcher;

import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Unit tests for {@link Log4jBootstrap}. */
public class Log4jBootstrapTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** Tests that {@link Log4jBootstrap#getClasspathUrls(java.io.File)} correctly resolves Log4j JARs regardless of version. */
    @Test
    public void testGetClasspathUrlsDoesNotDependOnVersions() throws Exception {
        Path log4jLibDir = tempFolder.newFolder("log4j").toPath();

        List<URL> expected = new ArrayList<>();
        for (String name : Arrays.asList("log4j-core-99.0.0.jar", "log4j-api-99.0.0.jar")) {
            expected.add(Files.createFile(log4jLibDir.resolve(name)).toUri().toURL());
        }

        List<URL> classpathUrls = Log4jBootstrap.getClasspathUrls(log4jLibDir.toFile());

        assertEquals(expected, classpathUrls);
    }
}