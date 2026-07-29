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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.plugins.ServerPlugin;
import com.mirth.connect.util.ZipTestUtils;

public class DefaultExtensionControllerTest {

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
     * A plugin class may implement several of the plugin type interfaces (ServicePlugin,
     * ChannelPlugin, and so on). initPlugins registers the instance once per interface it
     * implements, so registration must ignore an instance that is already registered. Otherwise
     * start() and stop() get invoked once per implemented interface instead of once per plugin.
     */
    @Test
    public void testAddServerPluginIgnoresDuplicateRegistrationOfSameInstance() {
        DefaultExtensionController extensionController = new DefaultExtensionController();
        CountingServerPlugin plugin = new CountingServerPlugin("multi-type plugin");

        extensionController.addServerPlugin(plugin);
        extensionController.addServerPlugin(plugin);
        extensionController.addServerPlugin(plugin);

        List<ServerPlugin> registeredPlugins = extensionController.getServerPlugins();
        assertEquals(1, registeredPlugins.size());
        assertSame(plugin, registeredPlugins.get(0));
    }

    @Test
    public void testStopPluginsStopsPluginRegisteredForMultipleTypesOnce() {
        DefaultExtensionController extensionController = new DefaultExtensionController();
        CountingServerPlugin plugin = new CountingServerPlugin("multi-type plugin");

        extensionController.addServerPlugin(plugin);
        extensionController.addServerPlugin(plugin);

        extensionController.stopPlugins();

        assertEquals(1, plugin.stopCount);
    }

    /*
     * Registration deliberately compares instances by identity, so two separate plugin instances
     * are both registered and both stopped even when the plugin class reports them as equal.
     */
    @Test
    public void testAddServerPluginRegistersDistinctButEqualInstancesSeparately() {
        DefaultExtensionController extensionController = new DefaultExtensionController();
        AlwaysEqualServerPlugin firstPlugin = new AlwaysEqualServerPlugin();
        AlwaysEqualServerPlugin secondPlugin = new AlwaysEqualServerPlugin();

        extensionController.addServerPlugin(firstPlugin);
        extensionController.addServerPlugin(secondPlugin);

        assertEquals(2, extensionController.getServerPlugins().size());

        extensionController.stopPlugins();

        assertEquals(1, firstPlugin.stopCount);
        assertEquals(1, secondPlugin.stopCount);
    }

    private static class CountingServerPlugin implements ServerPlugin {
        private final String pluginPointName;
        int stopCount;

        private CountingServerPlugin(String pluginPointName) {
            this.pluginPointName = pluginPointName;
        }

        @Override
        public String getPluginPointName() {
            return pluginPointName;
        }

        @Override
        public void start() {
            // Not exercised here: startPlugins() also reaches into ControllerFactory.
        }

        @Override
        public void stop() {
            stopCount++;
        }
    }

    private static class AlwaysEqualServerPlugin extends CountingServerPlugin {
        private AlwaysEqualServerPlugin() {
            super("always equal plugin");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof AlwaysEqualServerPlugin;
        }

        @Override
        public int hashCode() {
            return AlwaysEqualServerPlugin.class.hashCode();
        }
    }
}