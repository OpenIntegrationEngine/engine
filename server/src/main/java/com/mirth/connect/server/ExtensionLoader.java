/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.ExtensionDependencies;
import com.mirth.connect.client.core.ExtensionDependencies.Extension;
import com.mirth.connect.client.core.PropertiesConfigurationUtil;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.MetaData;
import com.mirth.connect.model.PluginClass;
import com.mirth.connect.model.PluginClassCondition;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.extprops.ExtensionStatuses;
import com.mirth.connect.server.tools.ClassPathResource;
import com.mirth.connect.server.util.ResourceUtil;

public class ExtensionLoader {
    @Inject
    private static ExtensionLoader instance = new ExtensionLoader();

    public static ExtensionLoader getInstance() {
        return instance;
    }

    private Map<String, ConnectorMetaData> connectorMetaDataMap = new HashMap<String, ConnectorMetaData>();
    private Map<String, PluginMetaData> pluginMetaDataMap = new HashMap<String, PluginMetaData>();
    private Map<String, ConnectorMetaData> connectorProtocolsMap = new HashMap<String, ConnectorMetaData>();
    private Map<String, MetaData> invalidMetaDataMap = new HashMap<String, MetaData>();
    private boolean loadedExtensions = false;
    private ObjectXMLSerializer serializer = ObjectXMLSerializer.getInstance();
    private static Logger logger = LogManager.getLogger(ExtensionLoader.class);

    private ExtensionLoader() {}

    public Map<String, ConnectorMetaData> getConnectorMetaData() {
        loadExtensions();
        return connectorMetaDataMap;
    }

    public Map<String, PluginMetaData> getPluginMetaData() {
        loadExtensions();
        return pluginMetaDataMap;
    }

    public Map<String, ConnectorMetaData> getConnectorProtocols() {
        loadExtensions();
        return connectorProtocolsMap;
    }

    public Map<String, MetaData> getInvalidMetaData() {
        loadExtensions();
        return invalidMetaDataMap;
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> getControllerClass(Class<T> abstractClass) {
        Class<T> overrideClass = null;
        PluginClass highestPluginClassModel = null;

        ExtensionStatuses extensionStatuses = ExtensionStatuses.getInstance();

        for (PluginMetaData pluginMetaData : getPluginMetaData().values()) {
            if (extensionStatuses.isEnabled(pluginMetaData.getName())) {
                List<PluginClass> controllerClasses = pluginMetaData.getControllerClasses();

                if (controllerClasses != null) {
                    for (PluginClass controllerClassModel : controllerClasses) {
                        boolean accept = true;
                        String conditionClass = controllerClassModel.getConditionClass();
                        if (StringUtils.isNotBlank(conditionClass)) {
                            try {
                                accept = ((PluginClassCondition) Class.forName(conditionClass).newInstance()).accept(controllerClassModel);
                            } catch (Exception e) {
                                logger.warn("Error instantiating plugin condition class \"" + conditionClass + "\".");
                            }
                        }

                        if (accept) {
                            try {
                                Class<?> pluginClass = Class.forName(controllerClassModel.getName());

                                if (abstractClass.isAssignableFrom(pluginClass) && (highestPluginClassModel == null || highestPluginClassModel.getWeight() < controllerClassModel.getWeight())) {
                                    highestPluginClassModel = controllerClassModel;
                                    overrideClass = (Class<T>) pluginClass;
                                }
                            } catch (Exception e) {
                                logger.error("An error occurred while attempting to load \"" + controllerClassModel.getName() + "\" from plugin: " + pluginMetaData.getName(), e);
                            }
                        }
                    }
                }
            }
        }

        return overrideClass;
    }

    public <T> T getControllerInstance(Class<T> abstractClass) {
        Class<T> overrideClass = getControllerClass(abstractClass);

        if (overrideClass != null) {
            try {
                T instance = overrideClass.newInstance();
                logger.debug("Using custom " + abstractClass.getSimpleName() + ": " + overrideClass.getName());
                return instance;
            } catch (Exception e) {
                logger.error("An error occurred while attempting to instantiate " + abstractClass.getSimpleName() + " implementation: " + overrideClass.getName(), e);
            }
        }

        logger.debug("Using default " + abstractClass.getSimpleName());
        return null;
    }

    /** Checks declarations and the engine requirement; plugin requirements need the full inventory. */
    public boolean isExtensionCompatible(MetaData metaData) {
        try {
            return ExtensionDependencies.getEngineError(describe(metaData, true), getServerVersion()) == null;
        } catch (Exception e) {
            logger.error("An error occurred while attempting to determine extension compatibility.", e);
            return false;
        }
    }

    /** Validates a complete inventory, including providers rejected by their own requirements. */
    public Map<MetaData, String> getCompatibilityErrors(Collection<MetaData> metadata, Predicate<String> enabled) throws ControllerException {
        Map<Extension, MetaData> descriptors = new LinkedHashMap<>();
        Map<MetaData, String> errors = new LinkedHashMap<>();
        for (MetaData extension : metadata) {
            try {
                descriptors.put(describe(extension, enabled.test(extension.getName())), extension);
            } catch (Exception e) {
                errors.put(extension, "Could not read extension status or metadata: " + e.getMessage());
            }
        }
        try {
            for (Map.Entry<Extension, String> error : ExtensionDependencies.validate(descriptors.keySet(), getServerVersion()).entrySet()) {
                errors.put(descriptors.get(error.getKey()), error.getValue());
            }
        } catch (Exception e) {
            logger.error("An error occurred while attempting to determine extension compatibility.", e);
            throw new ControllerException("Could not determine extension compatibility.", e);
        }
        return errors;
    }

    private Extension describe(MetaData metadata, boolean enabled) {
        return new Extension(metadata.getName(), metadata instanceof PluginMetaData, metadata.getPluginVersion(),
                metadata.getMirthVersion(), metadata.getMinExtensionApiVersion(), metadata.getDependencies(), enabled);
    }

    /** Reads the same package/descriptor layout as the launcher, without including pending installs. */
    public Map<String, List<MetaData>> readExtensionMetaData(File extensionPath) {
        Map<String, List<MetaData>> packages = new TreeMap<>();
        File[] directories = extensionPath.listFiles(File::isDirectory);
        if (directories == null) {
            return packages;
        }
        for (File directory : directories) {
            if (directory.getName().equals("install_temp") || directory.getName().startsWith(".install-")) {
                continue;
            }
            List<MetaData> metadata = new ArrayList<>();
            File[] files = directory.listFiles(file -> file.isFile() && isMetaDataFile(file.getName()));
            if (files == null) {
                continue;
            }
            Arrays.sort(files);
            for (File file : files) {
                try {
                    MetaData extension = serializer.deserialize(FileUtils.readFileToString(file), MetaData.class);
                    if (!(extension instanceof PluginMetaData || extension instanceof ConnectorMetaData)
                            || StringUtils.isBlank(extension.getName())) {
                        throw new IllegalArgumentException("Expected named plugin or connector metadata.");
                    }
                    metadata.add(extension);
                } catch (Exception e) {
                    logger.error("Error reading or parsing extension metadata file: {}", file, e);
                }
            }
            packages.put(directory.getName(), metadata);
        }
        return packages;
    }

    public static boolean isMetaDataFile(String name) {
        return "plugin.xml".equalsIgnoreCase(name) || "source.xml".equalsIgnoreCase(name) || "destination.xml".equalsIgnoreCase(name);
    }

    private synchronized void loadExtensions() {
        if (!loadedExtensions) {
            try {
                List<MetaData> metadata = new ArrayList<>();
                for (List<MetaData> extensions : readExtensionMetaData(new File(getExtensionsPath())).values()) {
                    metadata.addAll(extensions);
                }
                Map<MetaData, String> errors = getCompatibilityErrors(metadata, ExtensionStatuses.getInstance()::isEnabled);
                for (MetaData metaData : metadata) {
                    if (errors.containsKey(metaData)) {
                        logger.error("Extension \"{}\" was not loaded: {}", metaData.getName(), errors.get(metaData));
                        invalidMetaDataMap.put(metaData.getName(), metaData);
                    } else if (metaData instanceof ConnectorMetaData) {
                        ConnectorMetaData connectorMetaData = (ConnectorMetaData) metaData;
                        connectorMetaDataMap.put(connectorMetaData.getName(), connectorMetaData);
                        if (StringUtils.contains(connectorMetaData.getProtocol(), ":")) {
                            for (String protocol : connectorMetaData.getProtocol().split(":")) {
                                connectorProtocolsMap.put(protocol, connectorMetaData);
                            }
                        } else {
                            connectorProtocolsMap.put(connectorMetaData.getProtocol(), connectorMetaData);
                        }
                    } else if (metaData instanceof PluginMetaData) {
                        pluginMetaDataMap.put(metaData.getName(), (PluginMetaData) metaData);
                    }
                }
            } catch (Exception e) {
                logger.error("Error loading extension metadata.", e);
            } finally {
                loadedExtensions = true;
            }
        }
    }

    /**
     * If in an IDE, extensions will be on the classpath as a resource. If that's the case, use that
     * directory. Otherwise, use the mirth home directory and append extensions.
     * 
     * @return
     */
    private String getExtensionsPath() {
        if (ClassPathResource.getResourceURI("extensions") != null) {
            return ClassPathResource.getResourceURI("extensions").getPath() + File.separator;
        } else {
            return new File(ClassPathResource.getResourceURI("mirth.properties")).getParentFile().getParent() + File.separator + "extensions" + File.separator;
        }
    }

    private String getServerVersion() throws FileNotFoundException, ConfigurationException {
        PropertiesConfiguration versionConfig = PropertiesConfigurationUtil.create();
        
        InputStream versionPropertiesStream = null;
        try {
            versionPropertiesStream = ResourceUtil.getResourceStream(ExtensionLoader.class, "version.properties");
            versionConfig = PropertiesConfigurationUtil.create(versionPropertiesStream);
        } finally {
            ResourceUtil.closeResourceQuietly(versionPropertiesStream);
        }
        
        return versionConfig.getString("mirth.version");
    }
}
