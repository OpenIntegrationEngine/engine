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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.controllers.ExtensionInstaller.Result;

/** Archive validation and rollback without controller or engine globals. */
public class ExtensionInstallerTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path extensions;
    private ObjectXMLSerializer serializer;

    @Before
    public void setUp() throws Exception {
        extensions = temporaryFolder.newFolder("extensions").toPath();
        serializer = new ObjectXMLSerializer(getClass().getClassLoader());
    }

    @Test
    public void caseAliasesAndReservedDirectoriesAreRejectedBeforeExtraction() throws Exception {
        Map<String, String> duplicateFile = plugin("provider", "Provider", "2.1.0");
        duplicateFile.put("provider/PLUGIN.XML", plugin("provider", "Other", "2.1.0").get("provider/plugin.xml"));
        Map<String, String> duplicatePackage = plugin("provider", "Provider", "2.1.0");
        duplicatePackage.putAll(plugin("PROVIDER", "Other", "2.1.0"));
        for (Map<String, String> archive : List.of(duplicateFile, duplicatePackage,
                plugin("Install_Temp", "Provider", "2.1.0"))) {
            assertNotNull("Ambiguous or reserved ZIP path was accepted", install(archive).cause());
            assertTrue(stagedFiles().isEmpty());
        }
    }

    @Test
    public void malformedDescriptorRejectsTheWholeArchiveAndPreservesStaging() throws Exception {
        assertAccepted(install(plugin("provider", "Provider", "2.1.0")));
        Map<String, String> before = stagedFiles();
        Map<String, String> broken = plugin("provider", "Provider", "2.2.0");
        broken.put("provider/source.xml", "<connectorMetaData><name>Incomplete");
        assertNotNull(install(broken).cause());
        assertEquals(before, stagedFiles());
    }

    @Test
    public void incomingDescriptorsMustHaveAnExtensionTypeAndNonblankName() throws Exception {
        String valid = plugin("provider", "Provider", "2.1.0").get("provider/plugin.xml");
        for (String invalid : List.of(valid.replace("<name>Provider</name>", ""),
                valid.replace("<name>Provider</name>", "<name> </name>"), "<null/>", "<string>Unexpected type</string>")) {
            Map<String, String> archive = plugin("provider", "Provider", "2.1.0");
            archive.put("provider/plugin.xml", invalid);
            assertNotNull("Invalid metadata identity was accepted", install(archive).cause());
            assertTrue(stagedFiles().isEmpty());
        }
    }

    @Test
    public void extractionFailurePreservesPriorStagingAndCorrectedRetryReplacesWholePackage() throws Exception {
        Map<String, String> original = plugin("provider", "Provider", "2.1.0");
        original.put("provider/obsolete.jar", "old library");
        assertAccepted(install(original));
        Map<String, String> before = stagedFiles();
        Map<String, String> broken = plugin("provider", "Provider", "2.2.0");
        broken.put("provider/collision", "file blocks directory creation");
        broken.put("provider/collision/child", "cannot extract");
        assertNotNull(install(broken).cause());
        assertEquals(before, stagedFiles());
        for (int retry = 0; retry < 2; retry++) {
            assertAccepted(install(plugin("provider", "Provider", "2.2.0")));
            assertFalse(Files.exists(staged("provider/obsolete.jar")));
            assertEquals(2, stagedFiles().size());
        }
        try (var paths = Files.list(extensions)) {
            assertFalse("Failed extraction left private staging behind",
                    paths.anyMatch(path -> path.getFileName().toString().startsWith(".install-")));
        }
    }

    @Test
    public void failedPackagePromotionRestoresAllPriorStagedPackages() throws Exception {
        Map<String, String> original = plugin("one", "One", "2.1.0");
        original.putAll(plugin("two", "Two", "2.1.0"));
        assertAccepted(install(original));
        Map<String, String> before = stagedFiles();
        Map<String, String> updated = plugin("one", "One", "2.2.0");
        updated.putAll(plugin("two", "Two", "2.2.0"));
        AtomicInteger promotions = new AtomicInteger();
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.move(any(Path.class), any(Path.class))).thenAnswer(call -> {
                Path source = call.getArgument(0);
                if (source.getParent().getFileName().toString().equals("payload")
                        && promotions.incrementAndGet() == 2) {
                    throw new IOException("Simulated failure after one package was promoted");
                }
                return call.callRealMethod();
            });
            assertRejected(install(updated), "Simulated failure");
        }
        assertEquals(2, promotions.get());
        assertEquals(before, stagedFiles());
        assertAccepted(install(updated));
    }

    @Test
    public void failedRollbackRetainsOriginalPackageAndReportsRecoveryLocation() throws Exception {
        Map<String, String> original = plugin("provider", "Provider", "2.1.0");
        assertAccepted(install(original));
        Result result;
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.move(any(Path.class), any(Path.class))).thenAnswer(call -> {
                Path source = call.getArgument(0);
                String directory = source.getParent().getFileName().toString();
                if (directory.equals("payload") || directory.equals("backup")) {
                    throw new IOException("Simulated promotion and rollback failure");
                }
                return call.callRealMethod();
            });
            result = install(plugin("provider", "Provider", "2.2.0"));
        }
        assertRejected(result, "recovery files retained at");
        try (var paths = Files.list(extensions)) {
            Path work = paths.filter(path -> path.getFileName().toString().startsWith(".install-")).findFirst().orElseThrow();
            Path backup = work.resolve("backup");
            assertTrue(result.cause().toString().contains(backup.toString()));
            assertEquals(original.get("provider/plugin.xml"), Files.readString(backup.resolve("provider/plugin.xml")));
            assertEquals("Provider", Files.readString(backup.resolve("provider/payload.txt")));
        }
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

    private Result install(Map<String, String> entries) throws Exception {
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
        return new ExtensionInstaller(extensions.toFile(), serializer).install(new ByteArrayInputStream(bytes.toByteArray()), incoming -> {});
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

    private static void assertAccepted(Result result) {
        assertNull("Installation failed: " + result.cause(), result.cause());
    }

    private static void assertRejected(Result result, String requirement) {
        assertNotNull("Installation unexpectedly succeeded", result.cause());
        assertTrue(result.cause().toString(), result.cause().toString().contains(requirement));
    }

}
