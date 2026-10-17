/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.extprops;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

/**
 * Applies drop-in override files from a mirth.properties.d directory on top of an already-loaded
 * configuration. Files directly under the directory whose names end in ".properties" are applied
 * in lexical filename order: later files override earlier ones, and all of them override the base
 * configuration. Merge semantics come from java.util.Properties.load(), where the last occurrence
 * of a key wins.
 *
 * This class lives in the extprops package because it is shared by the launcher jar and the server
 * jar, and must not reference classes outside this package and the launcher's classpath.
 */
public class DropInProperties {

    /**
     * Loads drop-in files into the given properties, overriding any existing values. Files that
     * cannot be read are logged and skipped.
     */
    public static void load(Properties properties, File dropInDir, LoggerWrapper logger) {
        File[] files = listDropInFiles(dropInDir);

        if (files == null) {
            if (isUnlistableDirectory(dropInDir)) {
                logger.error("Unable to list drop-in properties directory: " + dropInDir.getPath());
            }
            return;
        }

        for (File file : files) {
            try (InputStream is = new FileInputStream(file)) {
                properties.load(is);
            } catch (IOException | IllegalArgumentException e) {
                // IllegalArgumentException covers a malformed \\uXXXX escape in Properties.load()
                logger.error("Unable to read drop-in properties file: " + file.getPath(), e);
            }
        }
    }

    /**
     * Returns a configuration with any drop-in files applied on top of the given base
     * configuration. The base configuration is never modified; it is returned as-is when there are
     * no drop-in files. A drop-in file that cannot be read or parsed throws a ConfigurationException
     * so that the caller can fail closed rather than start with a partially applied configuration.
     */
    public static PropertiesConfiguration overlay(PropertiesConfiguration base, File dropInDir) throws ConfigurationException {
        File[] files = listDropInFiles(dropInDir);

        if (files == null) {
            if (isUnlistableDirectory(dropInDir)) {
                throw new ConfigurationException("Unable to list drop-in properties directory: " + dropInDir.getPath());
            }
            return base;
        }

        if (files.length == 0) {
            return base;
        }

        Properties dropIns = new Properties();

        for (File file : files) {
            try (InputStream is = new FileInputStream(file)) {
                dropIns.load(is);
            } catch (IOException | IllegalArgumentException e) {
                // IllegalArgumentException covers a malformed \\uXXXX escape in Properties.load().
                // A file that cannot be read or parsed is fatal here so a broken drop-in fails startup
                // rather than silently reverting to the base configuration.
                throw new ConfigurationException("Unable to read drop-in properties file: " + file.getPath(), e);
            }
        }

        PropertiesConfiguration merged = new PropertiesConfiguration();
        ConfigurationUtils.copy(base, merged);

        for (String key : dropIns.stringPropertyNames()) {
            merged.setProperty(key, dropIns.getProperty(key));
        }

        return merged;
    }

    private static File[] listDropInFiles(File dropInDir) {
        File[] files = dropInDir == null ? null : dropInDir.listFiles(file -> file.isFile() && file.getName().endsWith(".properties"));

        if (files != null) {
            Arrays.sort(files);
        }

        return files;
    }

    /** listFiles() returns null both for a missing directory and an unlistable one; only the latter deserves noise. */
    private static boolean isUnlistableDirectory(File dropInDir) {
        return dropInDir != null && dropInDir.isDirectory();
    }
}
