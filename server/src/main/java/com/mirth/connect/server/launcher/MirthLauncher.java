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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.Properties;
import java.util.jar.JarFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.mirth.connect.client.core.ExtensionDependencies;
import com.mirth.connect.client.core.ExtensionDependencies.Extension;
import com.mirth.connect.client.core.ExtensionDependency;
import com.mirth.connect.server.extprops.ExtensionStatuses;
import com.mirth.connect.server.extprops.LoggerWrapper;

public class MirthLauncher {
    private static final String EXTENSIONS_DIR = "./extensions";
    private static final String SERVER_LAUNCHER_LIB_DIR = "./server-launcher-lib";
    private static final String MIRTH_PROPERTIES_FILE = "./conf/mirth.properties";
    private static final String LOG4J_PROPERTIES_FILE = "./conf/log4j2.properties";
    private static final String PROPERTY_APP_DATA_DIR = "dir.appdata";
    private static final String PROPERTY_INCLUDE_CUSTOM_LIB = "server.includecustomlib";

    private static String appDataDir = null;

    private static LoggerWrapper logger;

    public static void main(String[] args) {
        JarFile mirthClientCoreJarFile = null;
        try {
            Log4jMigrations.migrateConfiguration(new File(LOG4J_PROPERTIES_FILE));

            List<URL> classpathUrls = new ArrayList<>();
            // Always add log4j
            classpathUrls.addAll(Log4jBootstrap.getClasspathUrls());
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
            ManifestFile mirthClientCoreJar = new ManifestFile("server-lib/mirth-client-core.jar");
            ManifestDirectory serverLibDir = new ManifestDirectory("server-lib");
            serverLibDir.setExcludes(new String[] { "mirth-client-core.jar" });

            List<ManifestEntry> manifestList = new ArrayList<ManifestEntry>();
            manifestList.add(mirthServerJar);
            manifestList.add(mirthClientCoreJar);
            manifestList.add(serverLibDir);

            // We want to include custom-lib if the property isn't found, or if it equals "true"
            if (includeCustomLib == null || Boolean.valueOf(includeCustomLib)) {
                manifestList.add(new ManifestDirectory("custom-lib"));
            }

            ManifestEntry[] manifest = manifestList.toArray(new ManifestEntry[manifestList.size()]);

            // Get the current server version
            mirthClientCoreJarFile = new JarFile(mirthClientCoreJar.getName());
            Properties versionProperties = new Properties();
            versionProperties.load(mirthClientCoreJarFile.getInputStream(mirthClientCoreJarFile.getJarEntry("version.properties")));
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
                if (mirthClientCoreJarFile != null) {
                    mirthClientCoreJarFile.close();
                }
            } catch (IOException e) {
                logger.error("Error closing mirthClientCoreJarFile.", e);
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
                    IOFileFilter fileFilter = null;

                    if (manifestDir.getExcludes().length > 0) {
                        fileFilter = FileFilterUtils.and(FileFilterUtils.fileFileFilter(), FileFilterUtils.notFileFilter(new NameFileFilter(manifestDir.getExcludes())));
                    } else {
                        fileFilter = FileFilterUtils.fileFileFilter();
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
        addExtensionsToClasspath(urls, currentVersion, new File(EXTENSIONS_DIR), ExtensionStatuses.getInstance()::isEnabled);
    }

    static void addExtensionsToClasspath(List<URL> urls, String currentVersion, File extensionPath,
            Predicate<String> enabled) throws Exception {
        FileFilter extensionFileFilter = new NameFileFilter(new String[] { "plugin.xml",
                "source.xml", "destination.xml" }, IOCase.INSENSITIVE);
        File[] directories = extensionPath.listFiles((FileFilter) FileFilterUtils.directoryFileFilter());
        if (directories == null) {
            logger.warn("no extensions found");
            return;
        }

        // Resolve the complete inventory before adding libraries, regardless of filesystem order.
        Map<Extension, Element> metadata = new LinkedHashMap<>();
        Map<Extension, File> paths = new LinkedHashMap<>();
        for (File directory : directories) {
            if ("install_temp".equals(directory.getName()) || directory.getName().startsWith(".install-")) {
                continue;
            }
            File[] extensionFiles = directory.listFiles(extensionFileFilter);
            if (extensionFiles == null) {
                continue;
            }
            for (File extensionFile : extensionFiles) {
                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    Document document = dbf.newDocumentBuilder().parse(extensionFile);
                    Element root = document.getDocumentElement();
                    String name = getMetadataValue(root, "name");
                    if (name == null || name.trim().isEmpty()) {
                        throw new IllegalArgumentException("Extension metadata must declare a name");
                    }
                    Extension extension = readExtension(root, enabled.test(name));
                    metadata.put(extension, root);
                    paths.put(extension, directory);
                } catch (Exception e) {
                    logger.error("failed to parse extension metadata: " + extensionFile.getAbsolutePath(), e);
                }
            }
        }

        Map<Extension, String> errors = ExtensionDependencies.validate(metadata.keySet(), currentVersion);
        for (Map.Entry<Extension, Element> entry : metadata.entrySet()) {
            Extension extension = entry.getKey();
            if (errors.containsKey(extension)) {
                logger.error("could not load extension " + extension.getName() + ": " + errors.get(extension));
                continue;
            }
            if (!extension.isEnabled()) {
                continue;
            }
            for (Node child = entry.getValue().getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof Element) || !"library".equals(child.getNodeName())) {
                    continue;
                }
                Element library = (Element) child;
                String type = library.getAttribute("type");
                if (type.equalsIgnoreCase("server") || type.equalsIgnoreCase("shared")) {
                    File pathFile = new File(paths.get(extension), library.getAttribute("path"));
                    if (pathFile.exists()) {
                        logger.trace("adding library to classpath: " + pathFile.getAbsolutePath());
                        urls.add(pathFile.toURI().toURL());
                    } else {
                        logger.error("could not locate library: " + pathFile.getAbsolutePath());
                    }
                }
            }
        }
    }

    static boolean isExtensionCompatible(Element metadata, String currentVersion) {
        try {
            return ExtensionDependencies.getEngineError(readExtension(metadata, true), currentVersion) == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static Extension readExtension(Element metadata, boolean enabled) {
        String root = metadata.getNodeName();
        if (!"pluginMetaData".equals(root) && !"connectorMetaData".equals(root)) {
            throw new IllegalArgumentException("Unknown extension metadata type: " + root);
        }
        String metadataType = metadata.hasAttribute("resolves-to") ? metadata.getAttribute("resolves-to") : metadata.getAttribute("class");
        String className = "com.mirth.connect.model." + ("pluginMetaData".equals(root) ? "PluginMetaData" : "ConnectorMetaData");
        if (!metadataType.isEmpty() && !root.equals(metadataType) && !className.equals(metadataType)) {
            throw new IllegalArgumentException("Extension metadata type must match its root element");
        }
        List<ExtensionDependency> dependencies = new ArrayList<>();
        boolean foundDependencies = false;
        for (Node child = metadata.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element) || !"dependencies".equals(child.getNodeName())) {
                continue;
            }
            if (foundDependencies) {
                throw new IllegalArgumentException("Duplicate dependencies list");
            }
            foundDependencies = true;
            Element list = (Element) child;
            if (isNull(list)) {
                continue;
            }
            for (Node item = list.getFirstChild(); item != null; item = item.getNextSibling()) {
                if (!(item instanceof Element)) {
                    if ((item.getNodeType() == Node.TEXT_NODE || item.getNodeType() == Node.CDATA_SECTION_NODE)
                            && !item.getNodeValue().trim().isEmpty()) {
                        throw new IllegalArgumentException("Dependencies must contain dependency elements");
                    }
                    continue;
                }
                Element dependency = (Element) item;
                if (!"dependency".equals(dependency.getNodeName())) {
                    throw new IllegalArgumentException("Unknown dependency element: " + dependency.getNodeName());
                }
                for (int i = 0; i < dependency.getAttributes().getLength(); i++) {
                    String name = dependency.getAttributes().item(i).getNodeName();
                    if (!"type".equals(name) && !"name".equals(name) && !"minVersion".equals(name)) {
                        throw new IllegalArgumentException("Unknown dependency attribute: " + name);
                    }
                }
                for (Node value = dependency.getFirstChild(); value != null; value = value.getNextSibling()) {
                    if (value instanceof Element || ((value.getNodeType() == Node.TEXT_NODE
                            || value.getNodeType() == Node.CDATA_SECTION_NODE) && !value.getNodeValue().trim().isEmpty())) {
                        throw new IllegalArgumentException("Dependency values must be attributes");
                    }
                }
                dependencies.add(new ExtensionDependency(attribute(dependency, "type"),
                        attribute(dependency, "name"), attribute(dependency, "minVersion")));
            }
        }
        return new Extension(getMetadataValue(metadata, "name"), "pluginMetaData".equals(root),
                getMetadataValue(metadata, "pluginVersion"), getMetadataValue(metadata, "mirthVersion"),
                getMetadataValue(metadata, "minExtensionApiVersion"), dependencies, enabled);
    }

    private static String attribute(Element element, String name) {
        return element.hasAttribute(name) ? element.getAttribute(name) : null;
    }

    private static boolean isNull(Element element) {
        String type = element.hasAttribute("resolves-to") ? element.getAttribute("resolves-to") : element.getAttribute("class");
        return "null".equals(type) || "com.thoughtworks.xstream.mapper.Mapper$Null".equals(type);
    }

    private static String getMetadataValue(Element metadata, String name) {
        String result = null;
        boolean found = false;
        for (Node child = metadata.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && name.equals(child.getNodeName())) {
                if (found) {
                    throw new IllegalArgumentException("Duplicate metadata field: " + name);
                }
                found = true;
                // Match XStream's null and scalar text handling without loading XStream here.
                Element element = (Element) child;
                if (isNull(element)) {
                    continue;
                }
                StringBuilder text = new StringBuilder();
                for (Node value = child.getFirstChild(); value != null; value = value.getNextSibling()) {
                    if (value.getNodeType() == Node.TEXT_NODE || value.getNodeType() == Node.CDATA_SECTION_NODE) {
                        text.append(value.getNodeValue());
                    }
                }
                result = text.toString();
            }
        }
        return result;
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
