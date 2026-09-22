/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.controllers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.VersionMismatchException;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.model.MetaData;
import com.mirth.connect.model.PluginClass;
import com.mirth.connect.model.PluginClassCondition;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.plugins.AuthorizationPlugin;
import com.mirth.connect.plugins.ChannelPlugin;
import com.mirth.connect.plugins.CodeTemplateServerPlugin;
import com.mirth.connect.plugins.DataTypeServerPlugin;
import com.mirth.connect.plugins.MultiFactorAuthenticationPlugin;
import com.mirth.connect.plugins.ResourcePlugin;
import com.mirth.connect.plugins.ServerPlugin;
import com.mirth.connect.plugins.ServicePlugin;
import com.mirth.connect.plugins.TransmissionModeProvider;
import com.mirth.connect.server.ExtensionLoader;
import com.mirth.connect.server.extprops.ExtensionStatuses;
import com.mirth.connect.server.migration.Migrator;
import com.mirth.connect.server.util.DatabaseUtil;
import com.mirth.connect.server.util.ResourceUtil;
import com.mirth.connect.server.util.ServerUUIDGenerator;

public class DefaultExtensionController extends ExtensionController {
    private Logger logger = LogManager.getLogger(this.getClass());
    private final ObjectXMLSerializer serializer;
    private final ConfigurationController configurationController;
    private final File extensionsRoot;

    // these are plugins for specific extension points, keyed by plugin name
    // (not path)
    /*
     * A plugin class may implement several of the plugin type interfaces, in which case it is
     * registered once for each interface it implements. A Set holds a single entry per plugin,
     * so start() and stop() are invoked once per plugin rather than once per interface.
     * LinkedHashSet because initPlugins loads plugins in a deliberate order (by plugin weight)
     * and that order is preserved when they are started and stopped.
     */
    private Set<ServerPlugin> serverPlugins = new LinkedHashSet<ServerPlugin>();
    private Map<String, ServicePlugin> servicePlugins = new LinkedHashMap<String, ServicePlugin>();
    private Map<String, ChannelPlugin> channelPlugins = new LinkedHashMap<String, ChannelPlugin>();
    private Map<String, CodeTemplateServerPlugin> codeTemplateServerPlugins = new LinkedHashMap<String, CodeTemplateServerPlugin>();
    private Map<String, DataTypeServerPlugin> dataTypePlugins = new LinkedHashMap<String, DataTypeServerPlugin>();
    private Map<String, ResourcePlugin> resourcePlugins = new LinkedHashMap<String, ResourcePlugin>();
    private Map<String, TransmissionModeProvider> transmissionModeProviders = new LinkedHashMap<String, TransmissionModeProvider>();
    private MultiFactorAuthenticationPlugin multiFactorAuthenticationPlugin = null;
    private AuthorizationPlugin authorizationPlugin = null;
    private final ExtensionLoader extensionLoader;
    private final ExtensionStatuses extensionStatuses;

    // singleton pattern
    private static ExtensionController instance = null;

    public static ExtensionController create() {
        synchronized (DefaultExtensionController.class) {
            if (instance == null) {
                instance = ExtensionLoader.getInstance().getControllerInstance(ExtensionController.class);

                if (instance == null) {
                    instance = new DefaultExtensionController();
                }
            }

            return instance;
        }
    }

    DefaultExtensionController() {
        this(ObjectXMLSerializer.getInstance(), ControllerFactory.getFactory().createConfigurationController(),
                ExtensionLoader.getInstance(), ExtensionStatuses.getInstance(), new File(getExtensionsPath()));
    }

    DefaultExtensionController(ObjectXMLSerializer serializer, ConfigurationController configurationController,
            ExtensionLoader extensionLoader, ExtensionStatuses extensionStatuses, File extensionsRoot) {
        this.serializer = serializer;
        this.configurationController = configurationController;
        this.extensionLoader = extensionLoader;
        this.extensionStatuses = extensionStatuses;
        this.extensionsRoot = extensionsRoot;
    }

    @Override
    public void removePropertiesForUninstalledExtensions() {
        try {
            File uninstallFile = new File(extensionsRoot, EXTENSIONS_UNINSTALL_PROPERTIES_FILE);

            if (uninstallFile.exists()) {
                List<String> extensionPaths = FileUtils.readLines(uninstallFile);

                for (String extensionPath : extensionPaths) {
                    configurationController.removePropertiesForGroup(extensionPath);
                }

                // delete the uninstall file when we're done
                FileUtils.deleteQuietly(uninstallFile);
            }
        } catch (Exception e) {
            logger.error("Error removing properties for uninstalled extensions.", e);
        }
    }

    @Override
    public void setDefaultExtensionStatus() {
        for (MetaData metaData : getPluginMetaData().values()) {
            if (!extensionStatuses.containsKey(metaData.getName())) {
                extensionStatuses.setEnabled(metaData.getName(), true);
            }
        }

        for (MetaData metaData : getConnectorMetaData().values()) {
            if (!extensionStatuses.containsKey(metaData.getName())) {
                extensionStatuses.setEnabled(metaData.getName(), true);
            }
        }

        /*
         * Remove extensions from the extensionProperties if they are not in the pluginMetaDataMap
         * or connectorMetaDataMap
         */
        for (String key : extensionStatuses.keySet()) {
            if (!getPluginMetaData().containsKey(key) && !getConnectorMetaData().containsKey(key)) {
                extensionStatuses.remove(key);
            }
        }

        extensionStatuses.save();
    }

    @Override
    public void initPlugins() {
        // Order all the plugins by their weight before loading any of them.
        Map<String, String> pluginNameMap = new HashMap<String, String>();
        NavigableMap<Integer, List<String>> weightedPlugins = new TreeMap<Integer, List<String>>();
        for (PluginMetaData pmd : getPluginMetaData().values()) {
            if (isExtensionEnabled(pmd.getName())) {
                if (pmd.getServerClasses() != null) {
                    for (PluginClass pluginClass : pmd.getServerClasses()) {
                        String clazzName = pluginClass.getName();
                        int weight = pluginClass.getWeight();
                        String conditionClass = pluginClass.getConditionClass();

                        boolean accept = true;
                        if (StringUtils.isNotBlank(conditionClass)) {
                            try {
                                accept = ((PluginClassCondition) Class.forName(conditionClass).newInstance()).accept(pluginClass);
                            } catch (Exception e) {
                                logger.warn("Error instantiating plugin condition class \"" + conditionClass + "\".");
                            }
                        }

                        if (accept) {
                            pluginNameMap.put(clazzName, pmd.getName());

                            List<String> classList = weightedPlugins.get(weight);
                            if (classList == null) {
                                classList = new ArrayList<String>();
                                weightedPlugins.put(weight, classList);
                            }

                            classList.add(clazzName);
                        }
                    }
                }
            } else {
                logger.warn("Plugin \"" + pmd.getName() + "\" is not enabled.");
            }
        }

        // Load the plugins in order of their weight
        for (List<String> classList : weightedPlugins.descendingMap().values()) {
            for (String clazzName : classList) {
                String pluginName = pluginNameMap.get(clazzName);

                try {
                    ServerPlugin serverPlugin = (ServerPlugin) Class.forName(clazzName).newInstance();

                    if (serverPlugin instanceof ServicePlugin) {
                        ServicePlugin servicePlugin = (ServicePlugin) serverPlugin;
                        /*
                         * load any properties that may currently be in the database
                         */
                        Properties currentProperties = getPluginProperties(pluginName);
                        /* get the default properties for the plugin */
                        Properties defaultProperties = servicePlugin.getDefaultProperties();

                        /*
                         * if there are any properties that not currently set, set them to the the
                         * default
                         */
                        for (Object key : defaultProperties.keySet()) {
                            if (!currentProperties.containsKey(key)) {
                                currentProperties.put(key, defaultProperties.get(key));
                            }
                        }

                        /* save the properties to the database */
                        setPluginProperties(pluginName, currentProperties);

                        /*
                         * initialize the plugin with those properties and add it to the list of
                         * loaded plugins
                         */
                        servicePlugin.init(currentProperties);
                        servicePlugins.put(servicePlugin.getPluginPointName(), servicePlugin);
                        serverPlugins.add(servicePlugin);
                        logger.debug("sucessfully loaded server plugin: " + serverPlugin.getPluginPointName());
                    }

                    if (serverPlugin instanceof ChannelPlugin) {
                        ChannelPlugin channelPlugin = (ChannelPlugin) serverPlugin;
                        channelPlugins.put(channelPlugin.getPluginPointName(), channelPlugin);
                        serverPlugins.add(channelPlugin);
                        logger.debug("sucessfully loaded server channel plugin: " + serverPlugin.getPluginPointName());
                    }

                    if (serverPlugin instanceof CodeTemplateServerPlugin) {
                        CodeTemplateServerPlugin codeTemplateServerPlugin = (CodeTemplateServerPlugin) serverPlugin;
                        codeTemplateServerPlugins.put(codeTemplateServerPlugin.getPluginPointName(), codeTemplateServerPlugin);
                        serverPlugins.add(codeTemplateServerPlugin);
                        logger.debug("sucessfully loaded server code template plugin: " + serverPlugin.getPluginPointName());
                    }

                    if (serverPlugin instanceof DataTypeServerPlugin) {
                        DataTypeServerPlugin dataTypePlugin = (DataTypeServerPlugin) serverPlugin;
                        dataTypePlugins.put(dataTypePlugin.getPluginPointName(), dataTypePlugin);
                        serverPlugins.add(dataTypePlugin);
                        logger.debug("sucessfully loaded server data type plugin: " + serverPlugin.getPluginPointName());
                    }

                    if (serverPlugin instanceof ResourcePlugin) {
                        ResourcePlugin resourcePlugin = (ResourcePlugin) serverPlugin;
                        resourcePlugins.put(resourcePlugin.getPluginPointName(), resourcePlugin);
                        serverPlugins.add(resourcePlugin);
                        logger.debug("Successfully loaded resource plugin: " + resourcePlugin.getPluginPointName());
                    }

                    if (serverPlugin instanceof TransmissionModeProvider) {
                        TransmissionModeProvider transmissionModeProvider = (TransmissionModeProvider) serverPlugin;
                        transmissionModeProviders.put(transmissionModeProvider.getPluginPointName(), transmissionModeProvider);
                        serverPlugins.add(transmissionModeProvider);
                        logger.debug("Successfully loaded transmission mode provider plugin: " + transmissionModeProvider.getPluginPointName());
                    }

                    if (serverPlugin instanceof AuthorizationPlugin) {
                        AuthorizationPlugin authorizationPlugin = (AuthorizationPlugin) serverPlugin;

                        if (this.authorizationPlugin != null) {
                            throw new Exception("Multiple Authorization Plugins are not permitted.");
                        }

                        this.authorizationPlugin = authorizationPlugin;
                        serverPlugins.add(authorizationPlugin);
                        logger.debug("sucessfully loaded server authorization plugin: " + serverPlugin.getPluginPointName());
                    }

                    if (serverPlugin instanceof MultiFactorAuthenticationPlugin) {
                        MultiFactorAuthenticationPlugin multiFactorAuthenticationPlugin = (MultiFactorAuthenticationPlugin) serverPlugin;

                        if (this.multiFactorAuthenticationPlugin != null) {
                            throw new Exception("Multiple Multi-Factor Authentication Plugins are not permitted.");
                        }

                        this.multiFactorAuthenticationPlugin = multiFactorAuthenticationPlugin;
                        serverPlugins.add(multiFactorAuthenticationPlugin);
                        logger.debug("sucessfully loaded server multi-factor authentication plugin: " + serverPlugin.getPluginPointName());
                    }
                } catch (Exception e) {
                    logger.error("Error instantiating plugin: " + pluginName, e);
                }
            }
        }
    }

    /* These are the maps for the different types of plugins */
    /* ********************************************************************** */

    @Override
    public Map<String, ServicePlugin> getServicePlugins() {
        return servicePlugins;
    }

    @Override
    public Map<String, ChannelPlugin> getChannelPlugins() {
        return channelPlugins;
    }

    @Override
    public Map<String, CodeTemplateServerPlugin> getCodeTemplateServerPlugins() {
        return codeTemplateServerPlugins;
    }

    @Override
    public Map<String, DataTypeServerPlugin> getDataTypePlugins() {
        return dataTypePlugins;
    }

    @Override
    public Map<String, ResourcePlugin> getResourcePlugins() {
        return resourcePlugins;
    }

    @Override
    public Map<String, TransmissionModeProvider> getTransmissionModeProviders() {
        return transmissionModeProviders;
    }

    @Override
    public AuthorizationPlugin getAuthorizationPlugin() {
        return authorizationPlugin;
    }

    @Override
    public MultiFactorAuthenticationPlugin getMultiFactorAuthenticationPlugin() {
        return multiFactorAuthenticationPlugin;
    }

    /* ********************************************************************** */

    @Override
    public synchronized void setExtensionEnabled(String extensionName, boolean enabled) throws ControllerException {
        Map<String, List<MetaData>> inventory = getPlannedExtensions();
        List<MetaData> required = new ArrayList<>();
        if (enabled) {
            for (MetaData metadata : flatten(inventory)) {
                if (extensionName.equals(metadata.getName())) {
                    required.add(metadata);
                }
            }
        }
        String error = getExtensionChangeError(inventory, inventory, required, extensionName, enabled);
        if (error != null) {
            throw new ControllerException(error);
        }
        boolean previous = extensionStatuses.isEnabled(extensionName);
        extensionStatuses.setEnabled(extensionName, enabled);
        try {
            extensionStatuses.save();
        } catch (RuntimeException e) {
            extensionStatuses.setEnabled(extensionName, previous);
            throw new ControllerException("Could not save extension status.", e);
        }
    }

    @Override
    public boolean isExtensionEnabled(String extensionName) {
        return extensionStatuses.isEnabled(extensionName);
    }

    @Override
    public Set<String> getDisabledExtensions() {
        Set<String> disabledExtensions = new LinkedHashSet<String>();

        for (MetaData metaData : getPluginMetaData().values()) {
            if (!isExtensionEnabled(metaData.getName())) {
                disabledExtensions.add(metaData.getName());
            }
        }

        for (MetaData metaData : getConnectorMetaData().values()) {
            if (!isExtensionEnabled(metaData.getName())) {
                disabledExtensions.add(metaData.getName());
            }
        }

        return disabledExtensions;
    }

    @Override
    public void startPlugins() {
        for (ServerPlugin serverPlugin : serverPlugins) {
            serverPlugin.start();
        }

        // Get all of the server plugin extension permissions and add those to
        // the authorization controller.
        AuthorizationController authorizationController = ControllerFactory.getFactory().createAuthorizationController();

        for (ServicePlugin plugin : servicePlugins.values()) {
            if (plugin.getExtensionPermissions() != null) {
                for (ExtensionPermission extensionPermission : plugin.getExtensionPermissions()) {
                    authorizationController.addExtensionPermission(extensionPermission);
                }
            }
        }
    }

    @Override
    public void stopPlugins() {
        for (ServerPlugin serverPlugin : serverPlugins) {
            serverPlugin.stop();
        }
    }

    @Override
    public void updatePluginProperties(String name, Properties properties) {
        ServicePlugin servicePlugin = servicePlugins.get(name);

        if (servicePlugin != null) {
            servicePlugin.update(properties);
        } else {
            logger.error("Error setting properties for service plugin that has not been loaded: name=" + name);
        }
    }

    @Override
    public synchronized InstallationResult extractExtension(InputStream inputStream) {
        ExtensionInstaller.Result result = new ExtensionInstaller(extensionsRoot, serializer).install(inputStream, incoming -> {
            Map<String, List<MetaData>> before = getPlannedExtensions();
            for (String path : incoming.keySet()) {
                for (String installedPath : before.keySet()) {
                    if (path.equalsIgnoreCase(installedPath) && !path.equals(installedPath)) {
                        throw new ControllerException("Package path must retain its installed case: " + installedPath);
                    }
                }
            }
            Map<String, List<MetaData>> after = new LinkedHashMap<>(before);
            after.putAll(incoming);
            String error = getExtensionChangeError(before, after, flatten(incoming), null, false);
            if (error != null) {
                throw new VersionMismatchException(error);
            }
        });
        return new InstallationResult(result.cause(), result.metadata());
    }

    private Map<String, List<MetaData>> getPlannedExtensions() throws ControllerException {
        File root = extensionsRoot;
        Map<String, List<MetaData>> inventory = extensionLoader.readExtensionMetaData(root);
        File uninstall = new File(root, EXTENSIONS_UNINSTALL_FILE);
        try {
            if (uninstall.exists()) {
                for (String path : FileUtils.readLines(uninstall)) {
                    inventory.remove(normalizeExtensionPath(path));
                }
            }
            // The launcher applies pending installations after pending removals.
            inventory.putAll(extensionLoader.readExtensionMetaData(new File(root, "install_temp")));
            return inventory;
        } catch (IOException e) {
            throw new ControllerException("Could not read pending extension changes.", e);
        }
    }

    private String normalizeExtensionPath(String path) throws ControllerException {
        if (path == null || path.isEmpty() || path.contains("\\")) {
            throw new ControllerException("A valid extension package path is required.");
        }
        try {
            Path relative = Paths.get(path);
            for (Path part : relative) {
                if (part.toString().equals("..")) {
                    throw new ControllerException("Invalid extension package path: " + path);
                }
            }
            relative = relative.normalize();
            if (relative.isAbsolute() || relative.getNameCount() != 1 || relative.toString().isEmpty()
                    || ExtensionInstaller.isReservedPath(relative.toString())) {
                throw new ControllerException("Invalid extension package path: " + path);
            }
            String name = relative.toString();
            File root = extensionsRoot;
            for (File directory : new File[] { root, new File(root, "install_temp") }) {
                File[] packages = directory.listFiles(File::isDirectory);
                if (packages != null) {
                    for (File extension : packages) {
                        if (name.equalsIgnoreCase(extension.getName()) && !name.equals(extension.getName())) {
                            throw new ControllerException("Package path must retain its installed case: " + extension.getName());
                        }
                    }
                }
            }
            // Preserve the package basename; resolving a symlink could uninstall its target.
            return name;
        } catch (InvalidPathException e) {
            throw new ControllerException("Invalid extension package path: " + path, e);
        }
    }

    private List<MetaData> flatten(Map<String, List<MetaData>> inventory) {
        List<MetaData> metadata = new ArrayList<>();
        for (List<MetaData> extensions : inventory.values()) {
            metadata.addAll(extensions);
        }
        return metadata;
    }

    private String getExtensionChangeError(Map<String, List<MetaData>> before, Map<String, List<MetaData>> after,
            Collection<MetaData> required, String changedName, boolean changedEnabled) throws ControllerException {
        List<MetaData> previousMetadata = flatten(before);
        List<MetaData> proposedMetadata = flatten(after);
        Map<String, Boolean> previousStatus = new HashMap<>();
        List<MetaData> inventory = new ArrayList<>(previousMetadata);
        inventory.addAll(proposedMetadata);
        try {
            for (MetaData metadata : inventory) {
                previousStatus.computeIfAbsent(metadata.getName(), extensionStatuses::isEnabled);
            }
        } catch (RuntimeException e) {
            throw new ControllerException("Could not read extension status; no extension changes were applied.", e);
        }
        Map<String, Boolean> proposedStatus = new HashMap<>(previousStatus);
        if (changedName != null) {
            proposedStatus.put(changedName, changedEnabled);
        }
        Map<MetaData, String> previousErrors = extensionLoader.getCompatibilityErrors(previousMetadata, previousStatus::get);
        Map<MetaData, String> errors = extensionLoader.getCompatibilityErrors(proposedMetadata, proposedStatus::get);
        for (Map.Entry<MetaData, String> error : errors.entrySet()) {
            MetaData metadata = error.getKey();
            if (required.contains(metadata) || proposedStatus.get(metadata.getName()) && !previousErrors.containsKey(metadata)) {
                return "Extension \"" + metadata.getName() + "\": " + error.getValue();
            }
        }
        return null;
    }

    /**
     * Adds the specified plugin path to a list of plugins that should be deleted on next server
     * startup. Also deletes the schema version property from the database. If this function fails
     * to add the extension path to the uninstall file, it will still continue to remove add the
     * database uninstall scripts, and the folder must be deleted manually.
     * 
     */
    @Override
    public synchronized void prepareExtensionForUninstallation(String pluginPath) throws ControllerException {
        pluginPath = normalizeExtensionPath(pluginPath);
        Map<String, List<MetaData>> before = getPlannedExtensions();
        Map<String, List<MetaData>> after = new LinkedHashMap<>(before);
        if (!new File(new File(extensionsRoot, "install_temp"), pluginPath).isDirectory()) {
            after.remove(pluginPath);
        }
        String error = getExtensionChangeError(before, after, Collections.emptyList(), null, false);
        if (error != null) {
            throw new ControllerException(error);
        }
        addExtensionToUninstallFile(pluginPath);

        for (PluginMetaData plugin : getPluginMetaData().values()) {
            if (plugin.getPath().equals(pluginPath)) {
                addExtensionToUninstallPropertiesFile(plugin.getName());

                if (plugin.getMigratorClass() != null) {
                    try {
                        Migrator migrator = (Migrator) Class.forName(plugin.getMigratorClass()).newInstance();
                        migrator.setDatabaseType(ConfigurationController.getInstance().getDatabaseType());
                        migrator.setDefaultScriptPath("extensions/" + plugin.getPath());
                        appendToUninstallScript(migrator.getUninstallStatements());
                    } catch (Exception e) {
                        logger.error("Failed to retrieve uninstall database statements for plugin: " + pluginPath, e);
                    }
                }
            }
        }
    }

    /*
     * Parses the uninstallation script and returns a list of statements.
     */
    private List<String> parseUninstallScript(String script) {
        List<String> scriptList = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        boolean blankLine = false;
        Scanner scanner = new Scanner(script);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            if (StringUtils.isNotBlank(line)) {
                sb.append(line + " ");
            } else {
                blankLine = true;
            }

            if (blankLine || !scanner.hasNextLine()) {
                scriptList.add(sb.toString().trim());
                blankLine = false;
                sb.delete(0, sb.length());
            }
        }

        return scriptList;
    }

    /*
     * append the extension path name to a list of extensions that should be deleted on next startup
     * by MirthLauncher
     */
    private void addExtensionToUninstallFile(String pluginPath) {
        File uninstallFile = new File(extensionsRoot, EXTENSIONS_UNINSTALL_FILE);
        FileWriter writer = null;

        try {
            writer = new FileWriter(uninstallFile, true);
            writer.write(pluginPath + System.getProperty("line.separator"));
        } catch (IOException e) {
            logger.error("Error adding extension to uninstall file: " + pluginPath, e);
        } finally {
            ResourceUtil.closeResourceQuietly(writer);
        }
    }

    private void addExtensionToUninstallPropertiesFile(String pluginName) {
        File uninstallFile = new File(extensionsRoot, EXTENSIONS_UNINSTALL_PROPERTIES_FILE);
        FileWriter writer = null;

        try {
            writer = new FileWriter(uninstallFile, true);
            writer.write(pluginName + System.getProperty("line.separator"));
        } catch (IOException e) {
            logger.error("Error adding extension to uninstall properties file: " + pluginName, e);
        } finally {
            ResourceUtil.closeResourceQuietly(writer);
        }
    }

    private String getUninstallScriptForCurrentDatabase(String pluginSqlScripts) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    	Document document = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(pluginSqlScripts)));
        Element uninstallElement = (Element) document.getElementsByTagName("uninstall").item(0);
        String databaseType = ControllerFactory.getFactory().createConfigurationController().getDatabaseType();
        NodeList scriptNodes = uninstallElement.getElementsByTagName("script");
        String script = null;

        for (int i = 0; i < scriptNodes.getLength(); i++) {
            Node scriptNode = scriptNodes.item(i);
            Node scriptType = scriptNode.getAttributes().getNamedItem("type");
            String[] databaseTypes = scriptType.getTextContent().split(",");

            for (int j = 0; j < databaseTypes.length; j++) {
                if (databaseTypes[j].equals("all") || databaseTypes[j].equals(databaseType)) {
                    script = scriptNode.getTextContent().trim();
                }
            }
        }

        return script;
    }

    @Override
    public void setPluginProperties(String pluginName, Properties properties, boolean mergeProperties) throws ControllerException {
        if (!mergeProperties) {
            configurationController.removePropertiesForGroup(pluginName);
        }

        for (Object name : properties.keySet()) {
            configurationController.saveProperty(pluginName, (String) name, (String) properties.get(name));
        }
    }

    @Override
    public Properties getPluginProperties(String pluginName, Set<String> propertyKeys) throws ControllerException {
        return ControllerFactory.getFactory().createConfigurationController().getPropertiesForGroup(pluginName, propertyKeys);
    }

    @Override
    public Map<String, ConnectorMetaData> getConnectorMetaData() {
        return extensionLoader.getConnectorMetaData();
    }

    @Override
    public Map<String, PluginMetaData> getPluginMetaData() {
        return extensionLoader.getPluginMetaData();
    }

    @Override
    public ConnectorMetaData getConnectorMetaDataByProtocol(String protocol) {
        return extensionLoader.getConnectorProtocols().get(protocol);
    }

    @Override
    public ConnectorMetaData getConnectorMetaDataByTransportName(String transportName) {
        return extensionLoader.getConnectorMetaData().get(transportName);
    }

    @Override
    public Map<String, MetaData> getInvalidMetaData() {
        return extensionLoader.getInvalidMetaData();
    }

    /**
     * Executes the script that removes that database tables for plugins that are marked for
     * removal. The actual removal of the plugin directory happens in MirthLauncher.java, before
     * they can be added to the server classpath.
     * 
     */
    @Override
    public void uninstallExtensions() {
        try {
            DatabaseUtil.executeScript(readUninstallScript(), true);
        } catch (Exception e) {
            logger.error("Error uninstalling extensions.", e);
        }

        // delete the uninstall scripts file
        FileUtils.deleteQuietly(new File(extensionsRoot, EXTENSIONS_UNINSTALL_SCRIPTS_FILE));
    }

    private void appendToUninstallScript(List<String> uninstallStatements) throws IOException {
        if (uninstallStatements != null) {
            List<String> uninstallScripts = readUninstallScript();
            uninstallScripts.addAll(uninstallStatements);
            File uninstallScriptsFile = new File(extensionsRoot, EXTENSIONS_UNINSTALL_SCRIPTS_FILE);
            FileUtils.writeStringToFile(uninstallScriptsFile, serializer.serialize(uninstallScripts));
        }
    }

    /*
     * This MUST return an empty list if there is no uninstall file.
     */
    @SuppressWarnings("unchecked")
    private List<String> readUninstallScript() throws IOException {
        File uninstallScriptsFile = new File(extensionsRoot, EXTENSIONS_UNINSTALL_SCRIPTS_FILE);
        List<String> scripts = new ArrayList<String>();

        if (uninstallScriptsFile.exists()) {
            scripts = serializer.deserializeList(FileUtils.readFileToString(uninstallScriptsFile), String.class);
        }

        return scripts;
    }

    public List<String> getClientLibraries() {
        List<String> clientLibFilenames = new ArrayList<String>();
        File clientLibDir = new File("client-lib");

        if (!clientLibDir.exists() || !clientLibDir.isDirectory()) {
            clientLibDir = new File("build/client-lib");
        }

        if (clientLibDir.exists() && clientLibDir.isDirectory()) {
            Collection<File> clientLibs = FileUtils.listFiles(clientLibDir, new SuffixFileFilter(".jar"), FileFilterUtils.falseFileFilter());

            for (File clientLib : clientLibs) {
                clientLibFilenames.add(FilenameUtils.getName(clientLib.getName()));
            }
        } else {
            logger.error("Could not find client-lib directory: " + clientLibDir.getAbsolutePath());
        }

        return clientLibFilenames;
    }

    public List<ServerPlugin> getServerPlugins() {
        // Copied into a List so the ExtensionController signature stays unchanged for extensions.
        return new ArrayList<ServerPlugin>(serverPlugins);
    }
}
