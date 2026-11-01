/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.server.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.ExtensionLoader;
import com.mirth.connect.server.controllers.ExtensionController.InstallationResult;
import com.mirth.connect.server.extprops.ExtensionStatuses;

/** Exercises installation and administrative actions without an engine or database. */
public class ExtensionDependencyInstallationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final Map<String, Boolean> enabled = new HashMap<>();
    private Path extensions;
    private DefaultExtensionController controller;
    private ExtensionStatuses statuses;
    private ExtensionLoader loader;
    private ObjectXMLSerializer serializer;

    @Before
    public void setUp() throws Exception {
        extensions = temporaryFolder.newFolder("extensions").toPath();
        serializer = new ObjectXMLSerializer(getClass().getClassLoader());
        statuses = mock(ExtensionStatuses.class);
        when(statuses.isEnabled(anyString())).thenAnswer(call -> enabled.getOrDefault(call.getArgument(0), true));
        doAnswer(call -> {
            enabled.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(statuses).setEnabled(anyString(), anyBoolean());
        loader = spy(new ExtensionLoader(serializer) {
            @Override
            protected String getServerVersion() {
                return "4.5.2.123";
            }
        });
        doReturn(Collections.emptyMap()).when(loader).getPluginMetaData();
        createController();
    }

    private void createController() {
        controller = new DefaultExtensionController(serializer, mock(ConfigurationController.class), loader, statuses, extensions.toFile());
    }

    @Test
    public void bundledProvidersResolveRegardlessOfZipEntryOrder() throws Exception {
        for (boolean providerFirst : new boolean[] { false, true }) {
            extensions = temporaryFolder.newFolder().toPath();
            createController();
            Map<String, String> archive = new LinkedHashMap<>();
            if (providerFirst) {
                archive.putAll(plugin("provider", "Provider", "2.1.0"));
            }
            archive.putAll(plugin("consumer", "Consumer", "1.0.0", "Provider"));
            if (!providerFirst) {
                archive.putAll(plugin("provider", "Provider", "2.1.0"));
            }
            assertAccepted(install(archive));
            assertEquals("Consumer", Files.readString(staged("consumer/payload.txt")));
            assertEquals("Provider", Files.readString(staged("provider/payload.txt")));
        }
    }

    @Test
    public void installedAndPreviouslyStagedProvidersAreAvailable() throws Exception {
        writeInstalled(plugin("installed", "Installed", "2.1.0"));
        assertAccepted(install(plugin("staged", "Staged", "2.1.0")));
        assertAccepted(install(plugin("consumer", "Consumer", "1.0.0", "Installed", "Staged")));
        assertTrue(Files.exists(staged("consumer/plugin.xml")));
    }

    @Test
    public void connectorRequirementsUseTheSameArchiveInventory() throws Exception {
        for (String file : new String[] { "source.xml", "destination.xml" }) {
            extensions = temporaryFolder.newFolder().toPath();
            createController();
            Map<String, String> archive = plugin("connector", "Connector", "1.0.0", "Provider");
            String metadata = archive.remove("connector/plugin.xml").replace("pluginMetaData", "connectorMetaData");
            archive.put("connector/" + file, metadata);
            assertRejected(install(archive), "Provider");
            assertTrue(stagedFiles().isEmpty());
            archive.putAll(plugin("provider", "Provider", "2.1.0"));
            assertAccepted(install(archive));
            assertTrue(Files.exists(staged("connector/" + file)));
        }
    }

    @Test
    public void archiveTypeOverridesUseTheDeserializedProviderType() throws Exception {
        for (String attribute : new String[] { "class", "resolves-to" }) {
            extensions = temporaryFolder.newFolder().toPath();
            createController();
            Map<String, String> archive = plugin("provider", "Provider", "2.1.0");
            archive.put("provider/plugin.xml", archive.get("provider/plugin.xml")
                    .replace("<pluginMetaData path=", "<pluginMetaData " + attribute + "=\"connectorMetaData\" path="));
            assertAccepted(install(archive));
            assertRejected(install(plugin("consumer", "Consumer", "1.0.0", "Provider")), "Provider");
        }
    }

    @Test
    public void pendingRemovalIsAppliedBeforePendingInstallation() throws Exception {
        writeInstalled(plugin("provider", "Provider", "2.1.0"));
        Files.writeString(extensions.resolve("uninstall"), "provider\n");
        assertRejected(install(plugin("consumer", "Consumer", "1.0.0", "Provider")), "Provider");
        assertFalse(Files.exists(staged("consumer/payload.txt")));
        assertAccepted(install(plugin("provider", "Provider", "2.2.0")));
        assertAccepted(install(plugin("consumer", "Consumer", "1.0.0", "Provider")));
    }

    @Test
    public void aliasedPendingRemovalStillExcludesProviderFromNewInstallation() throws Exception {
        writeInstalled(plugin("provider", "Provider", "2.1.0"));
        Files.writeString(extensions.resolve("uninstall"), "./provider/\n");
        assertRejected(install(plugin("consumer", "Consumer", "1.0.0", "Provider")), "Provider");
        assertFalse(Files.exists(staged("consumer/plugin.xml")));
    }

    @Test
    public void disabledProviderRejectsConsumerUntilCorrected() throws Exception {
        writeInstalled(plugin("provider", "Provider", "2.1.0"));
        enabled.put("Provider", false);
        assertRejected(install(plugin("consumer", "Consumer", "1.0.0", "Provider")), "Provider");
        assertFalse(Files.exists(staged("consumer/payload.txt")));
        controller.setExtensionEnabled("Provider", true);
        assertAccepted(install(plugin("consumer", "Consumer", "1.0.0", "Provider")));
    }

    @Test
    public void disabledConsumerDefersDependenciesUntilEnable() throws Exception {
        enabled.put("Consumer", false);
        assertAccepted(install(plugin("consumer", "Consumer", "1.0.0", "Provider")));
        assertActionRejected(() -> controller.setExtensionEnabled("Consumer", true), "Provider");
        assertFalse(controller.isExtensionEnabled("Consumer"));
        assertAccepted(install(plugin("provider", "Provider", "2.1.0")));
        controller.setExtensionEnabled("Consumer", true);
        assertTrue(controller.isExtensionEnabled("Consumer"));
    }

    @Test
    public void providerUpdateCannotBreakExistingConsumerOrReplacePriorStaging() throws Exception {
        writeInstalled(plugin("consumer", "Consumer", "1.0.0", "Provider"));
        assertAccepted(install(plugin("provider", "Provider", "2.1.0")));
        Map<String, String> before = stagedFiles();
        assertRejected(install(plugin("provider", "Provider", "3.0.0")), "Consumer");
        assertEquals(before, stagedFiles());
        assertAccepted(install(plugin("provider", "Provider", "2.2.0")));
    }

    @Test
    public void disableChecksCurrentAndStagedConsumersBeforeChangingStatus() throws Exception {
        writeInstalled(plugin("provider", "Provider", "2.1.0"));
        assertAccepted(install(plugin("consumer", "Consumer", "1.0.0", "Provider")));
        assertActionRejected(() -> controller.setExtensionEnabled("Provider", false), "Consumer");
        assertTrue(controller.isExtensionEnabled("Provider"));
        controller.setExtensionEnabled("Consumer", false);
        controller.setExtensionEnabled("Provider", false);
        assertFalse(controller.isExtensionEnabled("Provider"));
        assertActionRejected(() -> controller.setExtensionEnabled("Consumer", true), "Provider");
        assertFalse(controller.isExtensionEnabled("Consumer"));
    }

    @Test
    public void uninstallRequiresConsumersFirstAndDoesNotWriteOnRejection() throws Exception {
        writeInstalled(plugin("provider", "Provider", "2.1.0"));
        writeInstalled(plugin("consumer", "Consumer", "1.0.0", "Provider"));
        assertActionRejected(() -> controller.prepareExtensionForUninstallation("provider"), "Consumer");
        assertFalse(Files.exists(extensions.resolve("uninstall")));
        assertFalse(Files.exists(extensions.resolve(ExtensionController.EXTENSIONS_UNINSTALL_PROPERTIES_FILE)));
        controller.prepareExtensionForUninstallation("consumer");
        controller.prepareExtensionForUninstallation("provider");
        assertEquals(List.of("consumer", "provider"), Files.readAllLines(extensions.resolve("uninstall")));
    }

    @Test
    public void uninstallQueuesSchemaCleanupForTheLoadedPlugin() throws Exception {
        Map<String, String> archive = plugin("provider", "Provider", "2.1.0");
        writeInstalled(archive);
        PluginMetaData metadata = serializer.deserialize(archive.get("provider/plugin.xml"), PluginMetaData.class);
        doReturn(Map.of("Provider", metadata)).when(loader).getPluginMetaData();
        controller.prepareExtensionForUninstallation("provider");
        assertEquals(List.of("provider"), Files.readAllLines(extensions.resolve("uninstall")));
        assertEquals(List.of("Provider"), Files.readAllLines(extensions.resolve(ExtensionController.EXTENSIONS_UNINSTALL_PROPERTIES_FILE)));
    }

    @Test
    public void statusPersistenceFailureRestoresMemoryAndAllowsRetry() throws Exception {
        writeInstalled(plugin("provider", "Provider", "2.1.0"));
        doThrow(new IllegalStateException("Status storage unavailable")).when(statuses).save();
        assertActionRejected(() -> controller.setExtensionEnabled("Provider", false), "Could not save");
        assertTrue(controller.isExtensionEnabled("Provider"));
        doNothing().when(statuses).save();
        controller.setExtensionEnabled("Provider", false);
        assertFalse(controller.isExtensionEnabled("Provider"));
    }

    @Test
    public void unavailableVersionResourceRejectsEveryMutationUntilRetry() throws Exception {
        writeInstalled(plugin("provider", "Provider", "2.1.0"));
        doThrow(new ControllerException("Could not determine extension compatibility."))
                .when(loader).getCompatibilityErrors(any(), any());
        assertRejected(install(plugin("independent", "Independent", "1.0.0")), "Could not determine extension compatibility");
        assertActionRejected(() -> controller.setExtensionEnabled("Provider", false), "Could not determine extension compatibility");
        assertActionRejected(() -> controller.prepareExtensionForUninstallation("provider"), "Could not determine extension compatibility");
        assertTrue(stagedFiles().isEmpty());
        assertTrue(controller.isExtensionEnabled("Provider"));
        assertFalse(Files.exists(extensions.resolve("uninstall")));
        doCallRealMethod().when(loader).getCompatibilityErrors(any(), any());
        assertAccepted(install(plugin("independent", "Independent", "1.0.0")));
        controller.setExtensionEnabled("Provider", false);
        controller.prepareExtensionForUninstallation("provider");
        assertFalse(controller.isExtensionEnabled("Provider"));
        assertEquals(List.of("provider"), Files.readAllLines(extensions.resolve("uninstall")));
    }

    @Test
    public void unavailableStatusRejectsEveryMutationUntilRetry() throws Exception {
        writeInstalled(plugin("provider", "Provider", "2.1.0"));
        when(statuses.isEnabled("Provider")).thenThrow(new IllegalStateException("Status unavailable"));
        assertRejected(install(plugin("independent", "Independent", "1.0.0")), "Could not read extension status");
        assertActionRejected(() -> controller.setExtensionEnabled("Provider", false), "Could not read extension status");
        assertActionRejected(() -> controller.prepareExtensionForUninstallation("provider"), "Could not read extension status");
        assertTrue(stagedFiles().isEmpty());
        assertFalse(enabled.containsKey("Provider"));
        assertFalse(Files.exists(extensions.resolve("uninstall")));
        doAnswer(call -> enabled.getOrDefault("Provider", true)).when(statuses).isEnabled("Provider");
        assertAccepted(install(plugin("independent", "Independent", "1.0.0")));
        controller.setExtensionEnabled("Provider", false);
        controller.prepareExtensionForUninstallation("provider");
        assertFalse(controller.isExtensionEnabled("Provider"));
        assertEquals(List.of("provider"), Files.readAllLines(extensions.resolve("uninstall")));
    }

    @Test
    public void uninstallAliasesCannotBypassConsumerProtection() throws Exception {
        writeInstalled(plugin("provider", "Provider", "2.1.0"));
        writeInstalled(plugin("consumer", "Consumer", "1.0.0", "Provider"));
        for (String alias : new String[] { "provider/", "./provider", "./provider/" }) {
            assertActionRejected(() -> controller.prepareExtensionForUninstallation(alias), "Consumer");
            assertFalse(Files.exists(extensions.resolve("uninstall")));
        }
        controller.setExtensionEnabled("Consumer", false);
        controller.prepareExtensionForUninstallation("./provider/");
        assertEquals(List.of("provider"), Files.readAllLines(extensions.resolve("uninstall")));
    }

    @Test
    public void uninstallSymlinkQueuesTheLinkWithoutResolvingItsTarget() throws Exception {
        Map<String, String> provider = plugin("provider", "Provider", "2.1.0");
        writeInstalled(provider);
        try {
            Files.createSymbolicLink(extensions.resolve("link"), extensions.resolve("provider"));
        } catch (IOException | UnsupportedOperationException e) {
            assumeNoException(e);
        }
        controller.prepareExtensionForUninstallation("link");
        assertEquals(List.of("link"), Files.readAllLines(extensions.resolve("uninstall")));
        assertEquals(provider.get("provider/plugin.xml"), Files.readString(extensions.resolve("provider/plugin.xml")));
        assertTrue(Files.isSymbolicLink(extensions.resolve("link")));
    }

    @Test
    public void uninstallRejectsCaseAliasesAndParentSegmentsBeforeMutation() throws Exception {
        writeInstalled(plugin("provider", "Provider", "2.1.0"));
        assertActionRejected(() -> controller.prepareExtensionForUninstallation("PROVIDER"), "case");
        assertActionRejected(() -> controller.prepareExtensionForUninstallation("provider/../provider"), "Invalid extension package path");
        assertFalse(Files.exists(extensions.resolve("uninstall")));
        Files.writeString(extensions.resolve("uninstall"), "PROVIDER\n");
        assertRejected(install(plugin("consumer", "Consumer", "1.0.0", "Provider")), "case");
        assertTrue(stagedFiles().isEmpty());
    }

    @Test
    public void caseOnlyPackageUpdatesCannotOverwriteInstalledOrStagedPackages() throws Exception {
        for (boolean staged : new boolean[] { false, true }) {
            extensions = temporaryFolder.newFolder().toPath();
            createController();
            Map<String, String> original = plugin("provider", "Provider", "2.1.0");
            if (staged) {
                assertAccepted(install(original));
            } else {
                writeInstalled(original);
            }
            Map<String, String> before = stagedFiles();
            // A different metadata identity avoids relying on duplicate provider-name rejection.
            assertNotNull(install(plugin("PROVIDER", "Replacement", "2.2.0")).getCause());
            assertEquals(before, stagedFiles());
            Path descriptor = staged ? staged("provider/plugin.xml") : extensions.resolve("provider/plugin.xml");
            assertEquals(original.get("provider/plugin.xml"), Files.readString(descriptor));
        }
    }

    @Test
    public void duplicateProvidersAndCyclesAreRejectedBeforePayloadExtraction() throws Exception {
        Map<String, String> duplicate = plugin("one", "Provider", "2.1.0");
        duplicate.putAll(plugin("two", "Provider", "2.1.0"));
        assertRejected(install(duplicate), "Provider");
        assertTrue(stagedFiles().isEmpty());
        Map<String, String> cyclic = plugin("alpha", "Alpha", "2.1.0", "Beta");
        cyclic.putAll(plugin("beta", "Beta", "2.1.0", "Alpha"));
        assertNotNull(install(cyclic).getCause());
        assertTrue(stagedFiles().isEmpty());
    }

    @Test
    public void transitiveFailureRejectsNewConsumerButNotUnrelatedInstallation() throws Exception {
        writeInstalled(plugin("provider", "Provider", "2.1.0", "Missing"));
        assertRejected(install(plugin("consumer", "Consumer", "1.0.0", "Provider")), "Provider");
        assertFalse(Files.exists(staged("consumer/payload.txt")));
        assertAccepted(install(plugin("independent", "Independent", "1.0.0")));
    }

    private static Map<String, String> plugin(String path, String name, String version, String... providers) {
        StringBuilder requirements = new StringBuilder("<dependency type=\"engine-api\" minVersion=\"1.0.0\"/>");
        for (String provider : providers) {
            requirements.append("<dependency type=\"plugin\" name=\"").append(provider).append("\" minVersion=\"2.1.0\"/>");
        }
        Map<String, String> entries = new LinkedHashMap<>();
        // Place payload before the descriptor to prove rejection precedes extraction.
        entries.put(path + "/payload.txt", name);
        entries.put(path + "/plugin.xml", "<pluginMetaData path=\"" + path + "\"><name>" + name
                + "</name><pluginVersion>" + version + "</pluginVersion><dependencies>" + requirements
                + "</dependencies></pluginMetaData>");
        return entries;
    }

    private void writeInstalled(Map<String, String> entries) throws Exception {
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            Path file = extensions.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue());
        }
    }

    private InstallationResult install(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            Set<String> directories = new HashSet<>();
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                String parent = entry.getKey().substring(0, entry.getKey().lastIndexOf('/') + 1);
                if (directories.add(parent)) {
                    zip.putNextEntry(new ZipEntry(parent));
                    zip.closeEntry();
                }
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return controller.extractExtension(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private Path staged(String relative) {
        return extensions.resolve("install_temp").resolve(relative);
    }

    private Map<String, String> stagedFiles() throws Exception {
        Map<String, String> contents = new TreeMap<>();
        Path root = staged("");
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                    contents.put(root.relativize(path).toString(), Files.readString(path));
                }
            }
        }
        return contents;
    }

    private static void assertAccepted(InstallationResult result) {
        assertNull("Installation failed: " + result.getCause(), result.getCause());
    }

    private static void assertRejected(InstallationResult result, String requirement) {
        assertNotNull("Installation unexpectedly succeeded", result.getCause());
        assertTrue(result.getCause().toString(), result.getCause().toString().contains(requirement));
    }

    private static void assertActionRejected(ControllerAction action, String requirement) throws Exception {
        try {
            action.run();
            fail("Administrative action unexpectedly succeeded");
        } catch (ControllerException e) {
            assertTrue(e.toString(), e.toString().contains(requirement));
        }
    }

    private interface ControllerAction {
        void run() throws ControllerException;
    }
}
