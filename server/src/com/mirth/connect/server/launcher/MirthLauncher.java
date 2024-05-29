/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.launcher;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.mirth.connect.server.extprops.ExtensionStatuses;
import com.mirth.connect.server.extprops.LoggerWrapper;

public class MirthLauncher {
    private static final String EXTENSIONS_DIR = "./extensions";
    private static final String SERVER_LAUNCHER_LIB_DIR = "./server-launcher-lib";
    private static final String MIRTH_PROPERTIES_FILE = "./conf/mirth.properties";
    private static final String PROPERTY_APP_DATA_DIR = "dir.appdata";
    private static final String PROPERTY_INCLUDE_CUSTOM_LIB = "server.includecustomlib";
    private static final String[] LOG4J_JAR_FILES = { "./core-lib/shared/log4j/log4j-core-2.17.2.jar",
            "./core-lib/shared/log4j/log4j-api-2.17.2.jar",
            "./core-lib/shared/log4j/log4j-1.2-api-2.17.2.jar" };

    private static String appDataDir = null;

    private static LoggerWrapper logger;

    public static void main(String[] args) {
        JarFile mirthServerJarFile = null;
        try {
            List<URL> classpathUrls = new ArrayList<>();
            // Always add log4j
            for (String log4jJar : LOG4J_JAR_FILES) {
                classpathUrls.add(new File(log4jJar).toURI().toURL());
            }
            classpathUrls.addAll(addServerLauncherLibJarsToClasspath());
            URLClassLoader mirthLauncherClassLoader = new URLClassLoader(classpathUrls.toArray(new URL[classpathUrls.size()]), Thread.currentThread().getContextClassLoader());
            Thread.currentThread().setContextClassLoader(mirthLauncherClassLoader);

            // Disable Threadlocals for log4j 2.x, since it messes with the server log
            System.setProperty("log4j2.enableThreadlocals", "false");

            logger = new LoggerWrapper(mirthLauncherClassLoader.loadClass("org.apache.logging.log4j.LogManager").getMethod("getLogger", Class.class).invoke(null, MirthLauncher.class));

            try {
                uninstallPendingExtensions();
                installPendingExtensions();
            } catch (Exception e) {
                logger.error("Error uninstalling or installing pending extensions.", e);
            }

            Properties mirthProperties = new Properties();
            String includeCustomLib = null;

            try (FileInputStream inputStream = new FileInputStream(new File(MIRTH_PROPERTIES_FILE))) {
                mirthProperties.load(inputStream);
                includeCustomLib = mirthProperties.getProperty(PROPERTY_INCLUDE_CUSTOM_LIB);
                createAppdataDir(mirthProperties);
            } catch (Exception e) {
                logger.error("Error creating the appdata directory.", e);
            }

            ManifestFile mirthServerJar = new ManifestFile("server-lib/mirth-server.jar");
            ManifestDirectory coreLibServerDirMirthLibs = new ManifestDirectory("core-lib/server");
            coreLibServerDirMirthLibs.setIncludePrefix("mirth-core-");
            ManifestDirectory coreLibSharedDirMirthLibs = new ManifestDirectory("core-lib/shared");
            coreLibSharedDirMirthLibs.setIncludePrefix("mirth-core-");
            ManifestDirectory coreLibServerDir = new ManifestDirectory("core-lib/server");
            coreLibServerDir.setExcludePrefix("mirth-core-");
            ManifestDirectory coreLibSharedDir = new ManifestDirectory("core-lib/shared");
            coreLibSharedDir.setExcludePrefix("mirth-core-");
            ManifestDirectory serverLibDir = new ManifestDirectory("server-lib");

            List<ManifestEntry> manifestList = new ArrayList<ManifestEntry>();
            manifestList.add(mirthServerJar);
            manifestList.add(coreLibServerDirMirthLibs);
            manifestList.add(coreLibSharedDirMirthLibs);
            manifestList.add(coreLibServerDir);
            manifestList.add(coreLibSharedDir);
            manifestList.add(serverLibDir);

            // We want to include custom-lib if the property isn't found, or if it equals "true"
            if (includeCustomLib == null || Boolean.valueOf(includeCustomLib)) {
                manifestList.add(new ManifestDirectory("custom-lib"));
            }

            ManifestEntry[] manifest = manifestList.toArray(new ManifestEntry[manifestList.size()]);

            // Get the current server version
            mirthServerJarFile = new JarFile(mirthServerJar.getName());
            Properties versionProperties = new Properties();
            versionProperties.load(mirthServerJarFile.getInputStream(mirthServerJarFile.getJarEntry("version.properties")));
            String currentVersion = versionProperties.getProperty("mirth.version");

            addManifestToClasspath(manifest, classpathUrls);
            addExtensionsToClasspath(classpathUrls, currentVersion);
            URLClassLoader classLoader = new URLClassLoader(classpathUrls.toArray(new URL[classpathUrls.size()]), Thread.currentThread().getContextClassLoader());
            Class<?> mirthClass = classLoader.loadClass("com.mirth.connect.server.Mirth");
            Thread mirthThread = (Thread) mirthClass.newInstance();
            mirthThread.setContextClassLoader(classLoader);
            mirthThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (mirthServerJarFile != null) {
                    mirthServerJarFile.close();
                }
            } catch (IOException e) {
                logger.error("Error closing mirthServerJarFile.", e);
            }
        }
    }

    // if we have an uninstall file, uninstall the listed extensions
    private static void uninstallPendingExtensions() throws Exception {
        File extensionsDir = new File(EXTENSIONS_DIR);
        File uninstallFile = new File(extensionsDir, "uninstall");

        if (uninstallFile.exists()) {
            List<String> extensionPaths = FileUtils.readLines(uninstallFile);

            for (String extensionPath : extensionPaths) {
                File extensionFile = new File(extensionsDir, extensionPath);

                if (extensionFile.exists() && extensionFile.isDirectory()) {
                    logger.trace("uninstalling extension: " + extensionFile.getName());
                    FileUtils.deleteDirectory(extensionFile);
                }
            }

            // delete the uninstall file when we're done
            FileUtils.deleteQuietly(uninstallFile);
        }
    }

    /*
     * This picks up any folders in the installation temp dir and moves them over to the extensions
     * dir, in effect "installing" them.
     */
    private static void installPendingExtensions() throws Exception {
        File extensionsDir = new File(EXTENSIONS_DIR);
        File extensionsTempDir = new File(extensionsDir, "install_temp");

        if (extensionsTempDir.exists()) {
            File[] extensions = extensionsTempDir.listFiles();

            for (int i = 0; i < extensions.length; i++) {
                if (extensions[i].isDirectory()) {
                    logger.trace("installing extension: " + extensions[i].getName());
                    File target = new File(extensionsDir, extensions[i].getName());

                    // delete it if it's already there
                    if (target.exists()) {
                        FileUtils.deleteQuietly(target);
                    }

                    extensions[i].renameTo(target);
                }
            }

            FileUtils.deleteDirectory(extensionsTempDir);
        }
    }

    private static List<URL> addServerLauncherLibJarsToClasspath() {
        File serverLauncherLibDir = new File(SERVER_LAUNCHER_LIB_DIR);
        List<URL> classpathUrls = new ArrayList<>();

        if (serverLauncherLibDir.exists() && serverLauncherLibDir.isDirectory()) {
            FileFilter jarFileFilter = new WildcardFileFilter("*.jar");
            File[] jarFiles = serverLauncherLibDir.listFiles(jarFileFilter);

            for (File jarFile : jarFiles) {
                try {
                    URL jarFileURL = jarFile.toURI().toURL();
                    classpathUrls.add(jarFileURL);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return classpathUrls;
    }

    private static void addManifestToClasspath(ManifestEntry[] manifestEntries, List<URL> urls) throws Exception {
        for (ManifestEntry manifestEntry : manifestEntries) {
            File manifestEntryFile = new File(manifestEntry.getName());

            if (manifestEntryFile.exists()) {
                if (manifestEntryFile.isDirectory()) {
                    ManifestDirectory manifestDir = (ManifestDirectory) manifestEntry;
                    IOFileFilter fileFilter = FileFilterUtils.fileFileFilter();

                    if (manifestDir.getIncludePrefix() != null) {
                        fileFilter = FileFilterUtils.and(fileFilter, new PrefixFileFilter(manifestDir.getIncludePrefix()));
                    }
                    if (manifestDir.getExcludePrefix() != null) {
                        fileFilter = FileFilterUtils.and(fileFilter, FileFilterUtils.notFileFilter(new PrefixFileFilter(manifestDir.getExcludePrefix())));
                    }
                    if (manifestDir.getExcludes().length > 0) {
                        fileFilter = FileFilterUtils.and(fileFilter, FileFilterUtils.notFileFilter(new NameFileFilter(manifestDir.getExcludes())));
                    }

                    Collection<File> pathFiles = FileUtils.listFiles(manifestEntryFile, fileFilter, FileFilterUtils.trueFileFilter());

                    for (File pathFile : pathFiles) {
                        logger.trace("adding library to classpath: " + pathFile.getAbsolutePath());
                        urls.add(pathFile.toURI().toURL());
                    }
                } else {
                    logger.trace("adding library to classpath: " + manifestEntryFile.getAbsolutePath());
                    urls.add(manifestEntryFile.toURI().toURL());
                }
            } else {
                logger.warn("manifest path not found: " + manifestEntryFile.getAbsolutePath());
            }
        }
    }

    private static void addExtensionsToClasspath(List<URL> urls, String currentVersion) throws Exception {
        FileFilter extensionFileFilter = new NameFileFilter(new String[] { "plugin.xml",
                "source.xml", "destination.xml" }, IOCase.INSENSITIVE);
        FileFilter directoryFilter = FileFilterUtils.directoryFileFilter();
        File extensionPath = new File(EXTENSIONS_DIR);

        ExtensionStatuses extensionStatuses = ExtensionStatuses.getInstance();

        if (extensionPath.exists() && extensionPath.isDirectory()) {
            File[] directories = extensionPath.listFiles(directoryFilter);

            for (File directory : directories) {
                File[] extensionFiles = directory.listFiles(extensionFileFilter);

                for (File extensionFile : extensionFiles) {
                    try {
                		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                		dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                		Document document = dbf.newDocumentBuilder().parse(extensionFile);
                		Element rootElement = document.getDocumentElement();

                        boolean enabled = extensionStatuses.isEnabled(rootElement.getElementsByTagName("name").item(0).getTextContent());
                        boolean compatible = isExtensionCompatible(rootElement.getElementsByTagName("mirthVersion").item(0).getTextContent(), currentVersion);

                        // Only add libraries from extensions that are not disabled and are compatible with the current version
                        if (enabled && compatible) {
                            NodeList libraries = rootElement.getElementsByTagName("library");

                            for (int i = 0; i < libraries.getLength(); i++) {
                                Element libraryElement = (Element) libraries.item(i);
                                String type = libraryElement.getAttribute("type");

                                if (type.equalsIgnoreCase("server") || type.equalsIgnoreCase("shared")) {
                                    File pathFile = new File(directory, libraryElement.getAttribute("path"));

                                    if (pathFile.exists()) {
                                        logger.trace("adding library to classpath: " + pathFile.getAbsolutePath());
                                        urls.add(pathFile.toURI().toURL());
                                    } else {
                                        logger.error("could not locate library: " + pathFile.getAbsolutePath());
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("failed to parse extension metadata: " + extensionFile.getAbsolutePath(), e);
                    }
                }
            }
        } else {
            logger.warn("no extensions found");
        }
    }

    private static boolean isExtensionCompatible(String extensionVersion, String currentVersion) {
        if (extensionVersion != null) {
            String[] extensionMirthVersions = extensionVersion.split(",");

            // If there is no build version, just use the patch version
            if (currentVersion.split("\\.").length == 4) {
                currentVersion = currentVersion.substring(0, currentVersion.lastIndexOf('.'));
            }

            for (int i = 0; i < extensionMirthVersions.length; i++) {
                if (extensionMirthVersions[i].trim().equals(currentVersion)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void createAppdataDir(Properties mirthProperties) {
        File appDataDirFile = null;

        if (mirthProperties.getProperty(PROPERTY_APP_DATA_DIR) != null) {
            appDataDirFile = new File(mirthProperties.getProperty(PROPERTY_APP_DATA_DIR));

            if (!appDataDirFile.exists()) {
                if (appDataDirFile.mkdir()) {
                    logger.debug("created app data dir: " + appDataDirFile.getAbsolutePath());
                } else {
                    logger.error("error creating app data dir: " + appDataDirFile.getAbsolutePath());
                }
            }
        } else {
            appDataDirFile = new File(".");
        }

        appDataDir = appDataDirFile.getAbsolutePath();
        logger.debug("set app data dir: " + appDataDir);
    }
}
