/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.server.launcher;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mirth.connect.server.extprops.LoggerWrapper;

public class ExtensionLibrariesTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private Object originalLogger;
    private Field logger;

    @Before
    public void captureLogging() throws Exception {
        logger = MirthLauncher.class.getDeclaredField("logger");
        logger.setAccessible(true);
        originalLogger = logger.get(null);
        logger.set(null, mock(LoggerWrapper.class));
    }

    @After
    public void restoreLogging() throws Exception {
        logger.set(null, originalLogger);
    }

    @Test
    public void discoversEnabledServerAndSharedLibrariesBeforeCompatibilityValidation() throws Exception {
        File root = temporary.newFolder();
        File enabled = write(root, "enabled", "Enabled", "");
        write(root, "disabled", "Disabled", "");
        write(root, "install_temp", "Pending", "");
        write(root, ".install-work", "Incomplete", "");
        List<URL> urls = new ArrayList<>();
        MirthLauncher.addExtensionsToClasspath(urls, root, name -> !name.equals("Disabled"));
        assertEquals(List.of(new File(enabled, "server.jar").toURI().toURL(),
                new File(enabled, "shared.jar").toURI().toURL()), urls);
    }

    @Test
    public void canonicalTypeOverridesDoNotPreventLibraryDiscovery() throws Exception {
        File root = temporary.newFolder();
        File extension = write(root, "example", "Example", " resolves-to=\"connectorMetaData\"");
        List<URL> urls = new ArrayList<>();
        MirthLauncher.addExtensionsToClasspath(urls, root, name -> true);
        assertEquals(List.of(new File(extension, "server.jar").toURI().toURL(),
                new File(extension, "shared.jar").toURI().toURL()), urls);
    }

    @Test
    public void nestedDependencyNameDoesNotDetermineExtensionStatus() throws Exception {
        File root = temporary.newFolder();
        File extension = write(root, "consumer", "Consumer", "");
        Path descriptor = new File(extension, "plugin.xml").toPath();
        Files.writeString(descriptor, Files.readString(descriptor).replace("<dependencies><dependency type=\"engine-api\" minVersion=\"2.0.0\"/></dependencies>", "").replace("<name>Consumer</name>",
                "<dependencies><dependency type=\"plugin\" minVersion=\"1.0.0\">"
                + "<name>Provider</name></dependency></dependencies><name>Consumer</name>"));
        List<URL> urls = new ArrayList<>();
        MirthLauncher.addExtensionsToClasspath(urls, root, name -> name.equals("Consumer"));
        assertEquals(2, urls.size());
    }

    @Test
    public void malformedDescriptorAndStatusFailureDoNotHideUnrelatedLibraries() throws Exception {
        File root = temporary.newFolder();
        write(root, "broken", "Broken", "");
        File malformed = write(root, "malformed", "Malformed", "");
        Files.writeString(new File(malformed, "plugin.xml").toPath(), "<pluginMetaData>");
        File good = write(root, "good", "Good", "");
        List<URL> urls = new ArrayList<>();
        MirthLauncher.addExtensionsToClasspath(urls, root, name -> {
            if (name.equals("Broken")) {
                throw new IllegalStateException("Unavailable status");
            }
            return true;
        });
        assertEquals(List.of(new File(good, "server.jar").toURI().toURL(),
                new File(good, "shared.jar").toURI().toURL()), urls);
    }

    private File write(File root, String path, String name, String attributes) throws Exception {
        File directory = new File(root, path);
        Files.createDirectory(directory.toPath());
        Files.writeString(new File(directory, "plugin.xml").toPath(),
                "<pluginMetaData" + attributes + "><name>" + name + "</name>"
                + "<dependencies><dependency type=\"engine-api\" minVersion=\"2.0.0\"/></dependencies>"
                + "<library type=\"SERVER\" path=\"server.jar\"/><library type=\"SHARED\" path=\"shared.jar\"/>"
                + "<library type=\"CLIENT\" path=\"client.jar\"/><library type=\"SERVER\" path=\"missing.jar\"/>"
                + "</pluginMetaData>");
        for (String library : List.of("server.jar", "shared.jar", "client.jar")) {
            Files.createFile(new File(directory, library).toPath());
        }
        return directory;
    }
}
