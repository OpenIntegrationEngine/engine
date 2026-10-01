/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.client.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.MetaData;
import com.mirth.connect.model.PluginMetaData;

public class ExtensionDependenciesTest {
    private static final String SERVER_VERSION = "4.5.2";
    private final Set<String> disabledNames = new HashSet<>();

    @Test
    public void engineRequirementUsesIndependentApiVersion() {
        MetaData extension = descriptor("Consumer", true, "1.0.0", "old release", null, true,
                requirement("engine-api", null, "1.0.0"));
        assertNull(ExtensionDependencies.getEngineError(extension, "99.0.0"));
        assertError(descriptor("Consumer", true, "1.0.0", SERVER_VERSION, null, true,
                requirement("engine-api", null, "1.0.1")), "Requires engine API");
        assertError(descriptor("Consumer", true, "1.0.0", SERVER_VERSION, null, true,
                requirement("engine-api", null, "2.0.0")), "same major version");
        assertNull(ExtensionDependencies.getEngineError(plugin("Consumer"), "99.0.0"));
    }

    @Test
    public void emptyAndPluginOnlyDependenciesPreserveLegacyEngineGate() {
        MetaData provider = plugin("Provider");
        MetaData empty = descriptor("Empty", true, "1.0.0", "old release", null, true);
        MetaData consumer = descriptor("Consumer", true, "1.0.0", "old release", null, true,
                pluginRequirement("Provider", "1.0.0"));
        Map<MetaData, String> errors = validate(provider, empty, consumer);
        assertEquals(2, errors.size());
        assertTrue(errors.get(empty).contains("engine release"));
        assertTrue(errors.get(consumer).contains("engine release"));
        MetaData legacy = descriptor("Legacy", true, "arbitrary", SERVER_VERSION, null, true,
                pluginRequirement("Provider", "1.0.0"));
        assertTrue(validate(legacy, provider).isEmpty());
        assertNull(ExtensionDependencies.getEngineError(legacy, SERVER_VERSION + ".123"));
    }

    @Test
    public void invalidAndConflictingDeclarationsFailClosed() {
        for (String invalid : new String[] { null, "", "1", "1.0", "1.0.0.0", "01.0.0", "1.0.0-beta",
                "1.0.0+build", "1.0.*", "-1.0.0", "2147483648.0.0" }) {
            assertError(plugin("Consumer", pluginRequirement("Provider", invalid)), "numeric major.minor.patch");
        }
        assertError(plugin("Consumer", (ExtensionDependency) null), "must not be null");
        for (String type : new String[] { null, "", "Plugin", "engine", "optional" }) {
            assertError(plugin("Consumer", requirement(type, "Provider", "1.0.0")), "Unknown dependency type");
        }
        for (String name : new String[] { null, "", " \t " }) {
            assertError(plugin("Consumer", pluginRequirement(name, "1.0.0")), "exact metadata name");
        }
        assertError(descriptor("Consumer", true, "1.0.0", SERVER_VERSION, null, true,
                requirement("engine-api", "", "1.0.0")), "must not declare a name");
        assertError(plugin("Consumer", requirement("engine-api", null, "1.0.0")), "only once");
        assertError(descriptor("Consumer", true, "1.0.0", SERVER_VERSION, null, true,
                requirement("engine-api", null, "1.0.0"), requirement("engine-api", null, "1.0.0")), "only once");
        assertError(plugin("Consumer", pluginRequirement("Provider", "1.0.0"),
                pluginRequirement("Provider", "1.2.0")), "more than once");
        assertError(plugin("Consumer", pluginRequirement("Consumer", "1.0.0")), "depend on itself");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void unexpectedDeserializedDependencyTypesOnlyRejectTheirDescriptor() {
        MetaData malformed = plugin("Malformed");
        malformed.setDependencies((List) Arrays.asList("not a dependency"));
        MetaData independent = plugin("Independent");
        Map<MetaData, String> errors = validate(malformed, independent);
        assertEquals(1, errors.size());
        assertTrue(errors.get(malformed).contains("dependency element"));
    }

    @Test
    public void pluginRequirementsUseNumericSameMajorMinimumVersions() {
        MetaData consumer = plugin("Consumer", pluginRequirement("Provider", "1.2.3"));
        for (String version : new String[] { "1.2.3", "1.2.10", "1.10.0", " 1.3.0 " }) {
            assertTrue(version, validate(consumer, versionedPlugin("Provider", version)).isEmpty());
        }
        for (String version : new String[] { null, "", "1.2.2", "1.1.99", "0.99.99", "2.0.0", "1.2.3.4",
                "1.2.3-beta", "01.2.3", "1.2147483648.0" }) {
            Map<MetaData, String> errors = validate(consumer, versionedPlugin("Provider", version));
            assertEquals(String.valueOf(version), 1, errors.size());
            assertTrue(errors.get(consumer).contains("installed version"));
        }
        // An unreferenced plugin does not need to change its existing version format.
        assertTrue(validate(versionedPlugin("Unreferenced", "release-five")).isEmpty());
    }

    @Test
    public void dependenciesRequireAnEnabledPluginWithTheExactName() {
        MetaData consumer = plugin("Consumer", pluginRequirement("Provider", "1.0.0"));
        assertTrue(validate(consumer).get(consumer).contains("not installed"));
        assertTrue(validate(consumer, plugin("provider")).get(consumer).contains("not installed"));
        assertTrue(validate(consumer, plugin(" Provider ")).get(consumer).contains("not installed"));
        MetaData connector = descriptor("Provider", false, "1.0.0", null, "1.0.0", true);
        assertTrue(validate(consumer, connector).get(consumer).contains("not installed"));
        MetaData disabled = descriptor("Provider", true, "1.0.0", null, "1.0.0", false);
        assertTrue(validate(consumer, disabled).get(consumer).contains("disabled"));

        MetaData connectorConsumer = descriptor("Provider", false, "1.0.0", null, "1.0.0", true,
                pluginRequirement("Provider", "1.0.0"));
        assertTrue(validate(connectorConsumer, plugin("Provider")).isEmpty());
    }

    @Test
    public void allRequirementsMustBeSatisfied() {
        MetaData consumer = plugin("Consumer", pluginRequirement("First", "1.0.0"),
                pluginRequirement("Second", "2.0.0"));
        MetaData first = plugin("First");
        assertTrue(validate(consumer, first).get(consumer).contains("Second"));
        assertTrue(validate(consumer, first, versionedPlugin("Second", "2.1.0")).isEmpty());
        assertTrue(validate(consumer, first, versionedPlugin("Second", "1.0.0")).get(consumer).contains("Second"));
    }

    @Test
    public void duplicatePluginNamesRejectEveryProviderAndTheirConsumers() {
        MetaData first = plugin("Provider");
        MetaData second = descriptor("Provider", true, "1.0.0", null, "1.0.0", false);
        MetaData consumer = plugin("Consumer", pluginRequirement("Provider", "1.0.0"));
        Map<MetaData, String> errors = validate(consumer, first, second);
        assertEquals(3, errors.size());
        assertTrue(errors.get(first).contains("More than one plugin"));
        assertTrue(errors.get(second).contains("More than one plugin"));
        assertTrue(errors.get(consumer).contains("ambiguous"));
    }

    @Test
    public void disabledConsumersKeepDeclarationAndEngineChecksButMayHaveMissingPlugins() {
        MetaData disabled = descriptor("Disabled", true, "1.0.0", null, "1.0.0", false,
                pluginRequirement("Missing", "1.0.0"));
        assertTrue(validate(disabled).isEmpty());
        MetaData badDeclaration = descriptor("Disabled", true, "1.0.0", null, "1.0.0", false,
                pluginRequirement("Missing", "bad version"));
        assertTrue(validate(badDeclaration).get(badDeclaration).contains("numeric major.minor.patch"));
        MetaData badEngine = descriptor("Disabled", true, "1.0.0", null, "2.0.0", false,
                pluginRequirement("Missing", "1.0.0"));
        assertTrue(validate(badEngine).get(badEngine).contains("Requires engine API"));
    }

    @Test
    public void incompatibleProvidersInvalidateTransitiveConsumersInAnyInventoryOrder() {
        MetaData first = plugin("First", pluginRequirement("Second", "1.0.0"));
        MetaData second = plugin("Second", pluginRequirement("Third", "1.0.0"));
        for (MetaData third : Arrays.asList(
                descriptor("Third", true, "1.0.0", null, "2.0.0", true),
                plugin("Third", pluginRequirement("Missing", "1.0.0")),
                plugin("Third", requirement("invalid", null, "1.0.0")))) {
            Map<MetaData, String> forward = validate(first, second, third);
            Map<MetaData, String> reverse = validate(third, second, first);
            assertEquals(3, forward.size());
            assertEquals(forward, reverse);
            assertTrue(forward.get(first).contains("Second"));
            assertTrue(forward.get(second).contains("Third"));
            assertEquals(Arrays.asList(first, second, third), new ArrayList<>(forward.keySet()));
            assertEquals(Arrays.asList(third, second, first), new ArrayList<>(reverse.keySet()));
        }
    }

    @Test
    public void cyclesAndTheirConsumersFailWithoutBlockingIndependentPlugins() {
        MetaData first = plugin("First", pluginRequirement("Second", "1.0.0"));
        MetaData second = plugin("Second", pluginRequirement("First", "1.0.0"));
        MetaData consumer = plugin("Consumer", pluginRequirement("First", "1.0.0"));
        MetaData independent = plugin("Independent");
        Map<MetaData, String> errors = validate(consumer, first, second, independent);
        assertEquals(3, errors.size());
        assertTrue(errors.values().stream().anyMatch(error -> error.contains("Circular plugin dependencies")));
        List<MetaData> inventory = Arrays.asList(consumer, first, second, independent);
        for (int index = 0; index < inventory.size(); index++) {
            Collections.rotate(inventory, 1);
            Map<MetaData, String> reordered = validate(inventory.toArray(new MetaData[0]));
            assertEquals(errors.keySet(), reordered.keySet());
            assertTrue(reordered.values().stream().anyMatch(error -> error.contains("Circular plugin dependencies")));
        }
        assertFalse(errors.containsKey(independent));
    }

    @Test
    public void failureInsideACyclePropagatesAndDiamondDependenciesResolve() {
        MetaData first = plugin("First", pluginRequirement("Second", "1.0.0"));
        MetaData second = plugin("Second", pluginRequirement("First", "1.0.0"),
                pluginRequirement("Missing", "1.0.0"));
        Map<MetaData, String> errors = validate(first, second);
        assertTrue(errors.get(first).contains("Second"));
        assertTrue(errors.get(second).contains("Missing"));

        MetaData base = plugin("Base");
        MetaData left = plugin("Left", pluginRequirement("Base", "1.0.0"));
        MetaData right = plugin("Right", pluginRequirement("Base", "1.0.0"));
        MetaData top = plugin("Top", pluginRequirement("Left", "1.0.0"), pluginRequirement("Right", "1.0.0"));
        List<MetaData> diamond = Arrays.asList(top, left, right, base);
        for (int index = 0; index < diamond.size(); index++) {
            Collections.rotate(diamond, 1);
            assertTrue(validate(diamond.toArray(new MetaData[0])).isEmpty());
        }
    }

    @Test
    public void dependencyChainsResolveInEitherOrder() {
        List<MetaData> chain = new ArrayList<>();
        int length = 10;
        for (int index = 0; index < length - 1; index++) {
            chain.add(plugin("Plugin " + index, pluginRequirement("Plugin " + (index + 1), "1.0.0")));
        }
        chain.add(plugin("Plugin " + (length - 1)));
        assertTrue(validate(chain.toArray(new MetaData[0])).isEmpty());
        Collections.reverse(chain);
        assertTrue(validate(chain.toArray(new MetaData[0])).isEmpty());
        Collections.reverse(chain);
        chain.set(length - 1, plugin("Plugin " + (length - 1), pluginRequirement("Missing", "1.0.0")));
        assertEquals(length, validate(chain.toArray(new MetaData[0])).size());
        Collections.reverse(chain);
        assertEquals(length, validate(chain.toArray(new MetaData[0])).size());
    }

    private static ExtensionDependency requirement(String type, String name, String version) {
        return new ExtensionDependency(type, name, version);
    }

    private static ExtensionDependency pluginRequirement(String name, String version) {
        return requirement("plugin", name, version);
    }

    private MetaData plugin(String name, ExtensionDependency... dependencies) {
        return descriptor(name, true, "1.0.0", null, "1.0.0", true, dependencies);
    }

    private MetaData versionedPlugin(String name, String version) {
        return descriptor(name, true, version, null, "1.0.0", true);
    }

    private MetaData descriptor(String name, boolean plugin, String pluginVersion, String mirthVersion,
            String minApiVersion, boolean enabled, ExtensionDependency... dependencies) {
        MetaData extension = plugin ? new PluginMetaData() : new ConnectorMetaData();
        extension.setName(name);
        extension.setPluginVersion(pluginVersion);
        extension.setMirthVersion(mirthVersion);
        List<ExtensionDependency> requirements = new ArrayList<>();
        if (minApiVersion != null) {
            requirements.add(requirement("engine-api", null, minApiVersion));
        }
        if (dependencies != null) {
            requirements.addAll(Arrays.asList(dependencies));
        }
        extension.setDependencies(requirements);
        if (enabled) {
            disabledNames.remove(name);
        } else {
            disabledNames.add(name);
        }
        return extension;
    }

    private Map<MetaData, String> validate(MetaData... extensions) {
        return ExtensionDependencies.validate(Arrays.asList(extensions), SERVER_VERSION,
                name -> !disabledNames.contains(name));
    }

    private static void assertError(MetaData extension, String expected) {
        String error = ExtensionDependencies.getEngineError(extension, SERVER_VERSION);
        assertTrue(String.valueOf(error), error != null && error.contains(expected));
    }
}
