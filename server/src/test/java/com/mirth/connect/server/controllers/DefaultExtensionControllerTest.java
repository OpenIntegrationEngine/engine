/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.model.PluginClass;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.plugins.ResourcePlugin;
import com.mirth.connect.plugins.ServerPlugin;
import com.mirth.connect.util.ZipTestUtils;

public class DefaultExtensionControllerTest {

    private static final String TEST_PLUGIN_NAME = "Test Plugin";

    @Test(expected = ZipException.class)
    public void testExtractZipEntryZipSlipWithRelativePath() throws Exception {
        DefaultExtensionController extensionController = new DefaultExtensionController();

        File installTempDir = new File("tests/zipextraction");
        ZipEntry entry = new ZipEntry("../ZipSlip.txt");
        ZipFile zipFile = createTempZipFile("ZipSlip.txt");

        extensionController.extractZipEntry(entry, installTempDir, zipFile);
    }

    @Test
    public void testExtractZipEntryValidPath() throws Exception {
        File installTempDir = new File("tests/zipextraction/");

        DefaultExtensionController extensionController = new DefaultExtensionController();

        ZipEntry entry = new ZipEntry("good.txt");
        ZipFile zipFile = createTempZipFile("good.txt");
        extensionController.extractZipEntry(entry, installTempDir, zipFile);

        File extractedFile = new File("tests/zipextraction/", "good.txt");
        assertTrue(extractedFile.exists());
    }

    @Test
    public void testInitPluginsWarnsAboutServerClassWithoutPluginType() throws Exception {
        Logger logger = mock(Logger.class);
        DefaultExtensionController extensionController = createControllerWithPlugin(NoTypeServerPlugin.class, logger);

        extensionController.initPlugins();

        // The plugin is still not registered, but it is no longer ignored silently
        assertTrue(extensionController.getServerPlugins().isEmpty());
        verify(logger).warn(contains(NoTypeServerPlugin.class.getName()));
    }

    @Test
    public void testInitPluginsDoesNotWarnAboutServerClassWithPluginType() throws Exception {
        Logger logger = mock(Logger.class);
        DefaultExtensionController extensionController = createControllerWithPlugin(ResourceServerPlugin.class, logger);

        extensionController.initPlugins();

        assertEquals(1, extensionController.getServerPlugins().size());
        verify(logger, never()).warn(anyString());
    }

    @Before
    public void createTestFolder() {
        File installTempDir = new File("tests/zipextraction/");
        if (!installTempDir.exists()) {
            installTempDir.mkdir();
        } else {
            cleanupTestFolder();
        }
    }

    @After
    public void cleanupTestFolder() {
        File tempDir = new File("tests/zipextraction/");
        if (tempDir.exists()) {
            for (File file : tempDir.listFiles()) {
                file.delete();
            }
        }
    }

    private ZipFile createTempZipFile(String fileName) throws Exception {
        return new ZipFile(ZipTestUtils.createTempZipFile(fileName));
    }

    /*
     * Creates a controller that knows a single enabled plugin with the given server class, and
     * that writes its log messages to the given logger.
     */
    private DefaultExtensionController createControllerWithPlugin(Class<? extends ServerPlugin> serverPluginClass, Logger logger) throws Exception {
        PluginClass serverClass = new PluginClass();
        serverClass.setName(serverPluginClass.getName());

        final PluginMetaData pluginMetaData = new PluginMetaData();
        pluginMetaData.setName(TEST_PLUGIN_NAME);
        pluginMetaData.setServerClasses(Collections.singletonList(serverClass));

        DefaultExtensionController extensionController = new DefaultExtensionController() {
            @Override
            public Map<String, PluginMetaData> getPluginMetaData() {
                return Collections.singletonMap(TEST_PLUGIN_NAME, pluginMetaData);
            }

            @Override
            public boolean isExtensionEnabled(String extensionName) {
                return true;
            }
        };

        Field loggerField = DefaultExtensionController.class.getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(extensionController, logger);

        return extensionController;
    }

    public static class NoTypeServerPlugin implements ServerPlugin {
        @Override
        public String getPluginPointName() {
            return TEST_PLUGIN_NAME;
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}
    }

    public static class ResourceServerPlugin extends NoTypeServerPlugin implements ResourcePlugin {}
}