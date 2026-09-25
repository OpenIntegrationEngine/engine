/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.client.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.mirth.connect.model.MetaData;
import com.mirth.connect.model.PluginMetaData;

/** Validates engine compatibility and required plugins before extension activation. */
public final class ExtensionDependencies {
    private ExtensionDependencies() {}

    /** Returns the first declaration or engine-compatibility error, or null on success. */
    public static String getEngineError(MetaData extension, String serverVersion) {
        String minimumApiVersion = null;
        Set<String> pluginRequirements = new HashSet<>();

        for (Object declaration : dependencies(extension)) {
            if (declaration == null) {
                return "Dependency declaration must not be null.";
            }
            if (!(declaration instanceof ExtensionDependency)) {
                return "Dependency declaration must be a dependency element.";
            }
            ExtensionDependency dependency = (ExtensionDependency) declaration;
            if (!ExtensionCompatibility.isValidVersion(dependency.getMinVersion())) {
                return "Dependency minVersion must be a numeric major.minor.patch version.";
            }
            if ("engine-api".equals(dependency.getType())) {
                if (minimumApiVersion != null) {
                    return "Declare the engine API requirement only once.";
                }
                if (dependency.getName() != null) {
                    return "An engine-api dependency must not declare a name.";
                }
                minimumApiVersion = dependency.getMinVersion();
            } else if ("plugin".equals(dependency.getType())) {
                String name = dependency.getName();
                if (name == null || name.trim().isEmpty()) {
                    return "A plugin dependency must declare its exact metadata name.";
                }
                if (!pluginRequirements.add(name)) {
                    return "Plugin dependency '" + name + "' is declared more than once.";
                }
                if (extension instanceof PluginMetaData && name.equals(extension.getName())) {
                    return "A plugin cannot depend on itself: '" + name + "'.";
                }
            } else {
                return "Unknown dependency type '" + dependency.getType() + "'.";
            }
        }

        if (!ExtensionCompatibility.isCompatible(extension.getMirthVersion(), minimumApiVersion, serverVersion)) {
            return minimumApiVersion == null
                    ? "The engine release does not match mirthVersion '" + extension.getMirthVersion() + "'."
                    : "Requires engine API '" + minimumApiVersion + "' with the same major version; current API is '"
                            + ExtensionCompatibility.API_VERSION + "'.";
        }
        return null;
    }

    /** Disabled consumers retain declaration/engine checks, but do not require their plugins. */
    public static Map<MetaData, String> validate(Collection<MetaData> extensions, String serverVersion,
            Predicate<String> enabled) {
        List<MetaData> inventory = new ArrayList<>(extensions);
        Map<MetaData, String> errors = new HashMap<>();
        Map<String, MetaData> plugins = new HashMap<>();
        Set<String> duplicateNames = new HashSet<>();
        Set<MetaData> enabledExtensions = new HashSet<>();

        for (MetaData extension : inventory) {
            String error = getEngineError(extension, serverVersion);
            if (error != null) {
                errors.put(extension, error);
            }
            if (extension instanceof PluginMetaData && plugins.putIfAbsent(extension.getName(), extension) != null) {
                duplicateNames.add(extension.getName());
            }
            if (enabled.test(extension.getName())) {
                enabledExtensions.add(extension);
            }
        }

        // Check direct requirements first, so a missing provider inside a cycle is still reported.
        for (MetaData extension : inventory) {
            if (extension instanceof PluginMetaData && duplicateNames.contains(extension.getName())) {
                errors.put(extension, "More than one plugin declares the name '" + extension.getName() + "'.");
            }
            if (!enabledExtensions.contains(extension) || errors.containsKey(extension)) {
                continue;
            }
            for (ExtensionDependency dependency : dependencies(extension)) {
                if (!"plugin".equals(dependency.getType())) {
                    continue;
                }
                String name = dependency.getName();
                MetaData provider = plugins.get(name);
                String error = null;
                if (provider == null) {
                    error = "Required plugin '" + name + "' is not installed.";
                } else if (duplicateNames.contains(name)) {
                    error = "Required plugin name '" + name + "' is ambiguous.";
                } else if (!enabledExtensions.contains(provider)) {
                    error = "Required plugin '" + name + "' is disabled.";
                } else if (!ExtensionCompatibility.isApiCompatible(dependency.getMinVersion(), provider.getPluginVersion())) {
                    error = "Required plugin '" + name + "' needs version '" + dependency.getMinVersion()
                            + "' or later with the same major version; installed version is '" + provider.getPluginVersion() + "'.";
                }
                if (error != null) {
                    errors.put(extension, error);
                    break;
                }
            }
        }

        Set<MetaData> visiting = new HashSet<>();
        Set<MetaData> checked = new HashSet<>();
        for (MetaData extension : inventory) {
            if (enabledExtensions.contains(extension)) {
                canLoad(extension, plugins, errors, visiting, checked);
            }
        }
        Map<MetaData, String> orderedErrors = new LinkedHashMap<>();
        for (MetaData extension : inventory) {
            if (errors.containsKey(extension)) {
                orderedErrors.put(extension, errors.get(extension));
            }
        }
        return orderedErrors;
    }

    private static boolean canLoad(MetaData extension, Map<String, MetaData> plugins,
            Map<MetaData, String> errors, Set<MetaData> visiting, Set<MetaData> checked) {
        if (errors.containsKey(extension)) {
            return false;
        }
        if (checked.contains(extension)) {
            return true;
        }
        if (!visiting.add(extension)) {
            errors.put(extension, "Circular plugin dependencies prevent loading '" + extension.getName() + "'.");
            return false;
        }
        for (ExtensionDependency dependency : dependencies(extension)) {
            if ("plugin".equals(dependency.getType())
                    && !canLoad(plugins.get(dependency.getName()), plugins, errors, visiting, checked)) {
                errors.putIfAbsent(extension, "Required plugin '" + dependency.getName()
                        + "' cannot be loaded; see its compatibility or dependency error.");
                break;
            }
        }
        visiting.remove(extension);
        checked.add(extension);
        return !errors.containsKey(extension);
    }

    private static List<ExtensionDependency> dependencies(MetaData extension) {
        return extension.getDependencies() == null ? Collections.emptyList() : extension.getDependencies();
    }
}
