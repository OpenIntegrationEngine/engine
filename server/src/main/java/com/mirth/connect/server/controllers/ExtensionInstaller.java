/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.server.controllers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.VersionMismatchException;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.MetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.ExtensionLoader;

/** Reads and stages complete packages only after their proposed inventory has been validated. */
final class ExtensionInstaller {
    private final File root;
    private final ObjectXMLSerializer serializer;

    ExtensionInstaller(File root, ObjectXMLSerializer serializer) {
        this.root = root;
        this.serializer = serializer;
    }

    @FunctionalInterface
    interface Validator {
        void validate(Map<String, List<MetaData>> incoming) throws ControllerException, VersionMismatchException;
    }

    record Result(Throwable cause, Set<MetaData> metadata) {}

    Result install(InputStream input, Validator validator) {
        Set<MetaData> metadata = new LinkedHashSet<>();
        File work = null;
        Throwable cause = null;
        try {
            File staging = new File(root, "install_temp");
            FileUtils.forceMkdir(staging);
            // Unvalidated payloads must not appear in the pending installation inventory.
            work = Files.createTempDirectory(root.toPath(), ".install-").toFile();
            File archive = new File(work, "extension.zip");
            Files.copy(input, archive.toPath());
            File payload = new File(work, "payload");
            FileUtils.forceMkdir(payload);
            try (ZipFile zip = new ZipFile(archive)) {
                Map<String, List<MetaData>> incoming = readArchive(zip, metadata);
                if (metadata.isEmpty()) {
                    throw new ZipException("Extension archive contains no extension metadata.");
                }
                validator.validate(incoming);
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    extractZipEntry(entries.nextElement(), payload, zip);
                }
            }
            stagePackages(payload, staging, work);
        } catch (Exception e) {
            cause = e instanceof ControllerException || e instanceof VersionMismatchException ? e
                    : new ControllerException("Error extracting extension. " + e, e);
        } finally {
            // Preserve the last copy if rollback failed; the exception reports its location.
            if (work != null && !new File(work, "backup").exists()) {
                FileUtils.deleteQuietly(work);
            }
        }
        return new Result(cause, metadata);
    }

    private Map<String, List<MetaData>> readArchive(ZipFile zip, Set<MetaData> parsed) throws IOException {
        Map<String, List<MetaData>> incoming = new LinkedHashMap<>();
        Set<String> entryNames = new HashSet<>();
        Map<String, String> packageNames = new HashMap<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            String[] parts = name.split("/");
            if (!entryNames.add(name.toLowerCase(Locale.ROOT)) || parts.length == 0 || parts[0].isEmpty()
                    || name.contains("\\") || name.startsWith("/") || isReservedPath(parts[0])
                    || (!entry.isDirectory() && parts.length < 2)) {
                throw new ZipException("Invalid extension archive entry: " + name);
            }
            for (String part : parts) {
                if (part.equals(".") || part.equals("..") || part.isEmpty()) {
                    throw new ZipException("Invalid extension archive entry: " + name);
                }
            }
            String previous = packageNames.putIfAbsent(parts[0].toLowerCase(Locale.ROOT), parts[0]);
            if (previous != null && !previous.equals(parts[0])) {
                throw new ZipException("Package paths must not differ only by case: " + parts[0]);
            }
            List<MetaData> metadata = incoming.computeIfAbsent(parts[0], key -> new ArrayList<>());
            if (!entry.isDirectory() && ExtensionLoader.isMetaDataFile(parts[parts.length - 1])) {
                if (parts.length != 2) {
                    throw new ZipException("Extension metadata must be directly inside its package: " + name);
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    MetaData extension = serializer.deserialize(IOUtils.toString(input), MetaData.class);
                    if (!(extension instanceof PluginMetaData || extension instanceof ConnectorMetaData)
                            || StringUtils.isBlank(extension.getName())) {
                        throw new ZipException("Expected named plugin or connector metadata: " + name);
                    }
                    if (!parts[0].equals(extension.getPath())) {
                        throw new ZipException("Metadata path must match its package directory: " + name);
                    }
                    metadata.add(extension);
                    parsed.add(extension);
                }
            }
        }
        return incoming;
    }

    static boolean isReservedPath(String path) {
        path = path.toLowerCase(Locale.ROOT);
        return path.equals("install_temp") || path.startsWith(".install-")
                || path.equals(ExtensionController.EXTENSIONS_UNINSTALL_FILE)
                || path.equals(ExtensionController.EXTENSIONS_UNINSTALL_PROPERTIES_FILE.toLowerCase(Locale.ROOT))
                || path.equals(ExtensionController.EXTENSIONS_UNINSTALL_SCRIPTS_FILE.toLowerCase(Locale.ROOT));
    }

    static void extractZipEntry(ZipEntry entry, File destination, ZipFile zip) throws IOException {
        File file = new File(destination, entry.getName());
        if (!file.getCanonicalPath().startsWith(destination.getCanonicalPath() + File.separator)) {
            throw new ZipException("Zip file is attempting to traverse out of base directory");
        }
        if (entry.isDirectory()) {
            FileUtils.forceMkdir(file);
        } else {
            FileUtils.forceMkdirParent(file);
            try (InputStream input = zip.getInputStream(entry)) {
                Files.copy(input, file.toPath());
            }
        }
    }

    /** Replace whole packages so retries cannot retain descriptors from an older archive. */
    private void stagePackages(File payload, File staging, File work) throws IOException {
        File backup = new File(work, "backup");
        FileUtils.forceMkdir(backup);
        List<File> staged = new ArrayList<>();
        try {
            for (File source : payload.listFiles()) {
                File target = new File(staging, source.getName());
                if (target.exists()) {
                    Files.move(target.toPath(), new File(backup, source.getName()).toPath());
                }
                Files.move(source.toPath(), target.toPath());
                staged.add(target);
            }
        } catch (IOException e) {
            try {
                for (File target : staged) {
                    FileUtils.deleteDirectory(target);
                }
                for (File original : backup.listFiles()) {
                    Files.move(original.toPath(), new File(staging, original.getName()).toPath());
                }
                FileUtils.deleteDirectory(backup);
            } catch (IOException rollback) {
                e.addSuppressed(rollback);
                throw new IOException("Could not restore pending extensions; recovery files retained at " + backup, e);
            }
            throw e;
        }
        if (!FileUtils.deleteQuietly(backup)) {
            LogManager.getLogger(ExtensionInstaller.class).warn("Extensions staged successfully, but old staging files could not be removed: {}", backup);
        }
    }
}
