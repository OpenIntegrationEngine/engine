/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.client.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/** Shared dependency validation; must remain usable before engine classes are loaded. */
public final class ExtensionDependencies {
    private ExtensionDependencies() {}

    /** One descriptor in the proposed extension inventory. Identity is per descriptor. */
    public static final class Extension {
        private final String name;
        private final boolean plugin;
        private final String pluginVersion;
        private final String mirthVersion;
        private final String minExtensionApiVersion;
        private final List<ExtensionDependency> dependencies;
        private final boolean enabled;

        public Extension(String name, boolean plugin, String pluginVersion, String mirthVersion,
                String minExtensionApiVersion, List<ExtensionDependency> dependencies, boolean enabled) {
            this.name = name;
            this.plugin = plugin;
            this.pluginVersion = pluginVersion;
            this.mirthVersion = mirthVersion;
            this.minExtensionApiVersion = minExtensionApiVersion;
            this.dependencies = dependencies == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(dependencies));
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public boolean isPlugin() {
            return plugin;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    /** Returns the first declaration or engine-compatibility error, or null on success. */
    public static String getEngineError(Extension extension, String serverVersion) {
        String minimumApiVersion = extension.minExtensionApiVersion;
        boolean engineRequirement = false;
        Set<String> pluginRequirements = new HashSet<>();

        for (ExtensionDependency dependency : extension.dependencies) {
            if (dependency == null) {
                return "Dependency declaration must not be null.";
            }
            if (!ExtensionCompatibility.isValidVersion(dependency.getMinVersion())) {
                return "Dependency minVersion must be a numeric major.minor.patch version.";
            }
            if ("engine-api".equals(dependency.getType())) {
                if (engineRequirement || extension.minExtensionApiVersion != null) {
                    return "Declare the engine API requirement only once.";
                }
                if (dependency.getName() != null) {
                    return "An engine-api dependency must not declare a name.";
                }
                engineRequirement = true;
                minimumApiVersion = dependency.getMinVersion();
            } else if ("plugin".equals(dependency.getType())) {
                String name = dependency.getName();
                if (name == null || name.trim().isEmpty()) {
                    return "A plugin dependency must declare its exact metadata name.";
                }
                if (!pluginRequirements.add(name)) {
                    return "Plugin dependency '" + name + "' is declared more than once.";
                }
                if (extension.plugin && name.equals(extension.name)) {
                    return "A plugin cannot depend on itself: '" + name + "'.";
                }
            } else {
                return "Unknown dependency type '" + dependency.getType() + "'.";
            }
        }

        if (!ExtensionCompatibility.isCompatible(extension.mirthVersion, minimumApiVersion, serverVersion)) {
            return minimumApiVersion == null
                    ? "The engine release does not match mirthVersion '" + extension.mirthVersion + "'."
                    : "Requires engine API '" + minimumApiVersion + "' with the same major version; current API is '"
                            + ExtensionCompatibility.API_VERSION + "'.";
        }
        return null;
    }

    /**
     * Checks the complete inventory. Disabled descriptors retain declaration/engine checks, but
     * only enabled consumers require their plugins to be available. Results follow inventory order.
     */
    public static Map<Extension, String> validate(Collection<Extension> extensions, String serverVersion) {
        List<Extension> inventory = new ArrayList<>(extensions);
        Map<Extension, String> errors = new HashMap<>();
        Map<String, Extension> plugins = new HashMap<>();
        Set<String> duplicateNames = new HashSet<>();

        for (Extension extension : inventory) {
            String error = getEngineError(extension, serverVersion);
            if (error != null) {
                errors.put(extension, error);
            }
            if (extension.plugin && plugins.putIfAbsent(extension.name, extension) != null) {
                duplicateNames.add(extension.name);
            }
        }
        for (Extension extension : inventory) {
            if (extension.plugin && duplicateNames.contains(extension.name)) {
                errors.put(extension, "More than one plugin declares the name '" + extension.name + "'.");
            }
        }

        Map<Extension, List<Extension>> consumers = new HashMap<>();
        Map<Extension, Integer> remaining = new HashMap<>();
        for (Extension extension : inventory) {
            remaining.put(extension, 0);
            if (!extension.enabled || errors.containsKey(extension)) {
                continue;
            }
            for (ExtensionDependency dependency : extension.dependencies) {
                if (!"plugin".equals(dependency.getType())) {
                    continue;
                }
                String name = dependency.getName();
                Extension provider = plugins.get(name);
                String error = null;
                if (provider == null) {
                    error = "Required plugin '" + name + "' is not installed.";
                } else if (duplicateNames.contains(name)) {
                    error = "Required plugin name '" + name + "' is ambiguous.";
                } else if (!provider.enabled) {
                    error = "Required plugin '" + name + "' is disabled.";
                } else if (!ExtensionCompatibility.isApiCompatible(dependency.getMinVersion(), provider.pluginVersion)) {
                    error = "Required plugin '" + name + "' needs version '" + dependency.getMinVersion()
                            + "' or later with the same major version; installed version is '" + provider.pluginVersion + "'.";
                }
                if (error != null) {
                    errors.put(extension, error);
                    break;
                }
                consumers.computeIfAbsent(provider, key -> new ArrayList<>()).add(extension);
                remaining.put(extension, remaining.get(extension) + 1);
            }
        }

        // Resolve providers before consumers. Failed providers propagate immediately, including
        // into cycles. Any nodes left afterward belong to, or depend on, a dependency cycle.
        Queue<Extension> ready = new ArrayDeque<>();
        Set<Extension> resolved = new HashSet<>();
        for (Extension extension : inventory) {
            if (errors.containsKey(extension) || remaining.get(extension) == 0) {
                ready.add(extension);
            }
        }
        while (!ready.isEmpty()) {
            Extension provider = ready.remove();
            if (!resolved.add(provider)) {
                continue;
            }
            for (Extension consumer : consumers.getOrDefault(provider, Collections.emptyList())) {
                if (errors.containsKey(provider) && !errors.containsKey(consumer)) {
                    errors.put(consumer, "Required plugin '" + provider.name
                            + "' cannot be loaded; see its compatibility or dependency error.");
                    ready.add(consumer);
                }
                int count = remaining.get(consumer) - 1;
                remaining.put(consumer, count);
                if (count == 0) {
                    ready.add(consumer);
                }
            }
        }

        Map<Extension, String> orderedErrors = new LinkedHashMap<>();
        for (Extension extension : inventory) {
            if (!resolved.contains(extension)) {
                errors.put(extension, "Circular plugin dependencies prevent loading '" + extension.name + "'.");
            }
            if (errors.containsKey(extension)) {
                orderedErrors.put(extension, errors.get(extension));
            }
        }
        return orderedErrors;
    }
}
