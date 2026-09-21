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
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.mirth.connect.client.core.ExtensionDependencies.Extension;

public class ExtensionDependenciesTest {
    private static final String SERVER_VERSION = "4.5.2";

    @Test
    public void engineRequirementUsesIndependentApiVersion() {
        Extension extension = descriptor("Consumer", true, "1.0.0", "old release", null, true,
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
        Extension provider = plugin("Provider");
        Extension empty = descriptor("Empty", true, "1.0.0", "old release", null, true);
        Extension consumer = descriptor("Consumer", true, "1.0.0", "old release", null, true,
                pluginRequirement("Provider", "1.0.0"));
        Map<Extension, String> errors = validate(provider, empty, consumer);
        assertEquals(2, errors.size());
        assertTrue(errors.get(empty).contains("engine release"));
        assertTrue(errors.get(consumer).contains("engine release"));
        Extension legacy = descriptor("Legacy", true, "arbitrary", SERVER_VERSION, null, true,
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
    public void pluginRequirementsUseNumericSameMajorMinimumVersions() {
        Extension consumer = plugin("Consumer", pluginRequirement("Provider", "1.2.3"));
        for (String version : new String[] { "1.2.3", "1.2.10", "1.10.0", " 1.3.0 " }) {
            assertTrue(version, validate(consumer, versionedPlugin("Provider", version)).isEmpty());
        }
        for (String version : new String[] { null, "", "1.2.2", "1.1.99", "0.99.99", "2.0.0", "1.2.3.4",
                "1.2.3-beta", "01.2.3", "1.2147483648.0" }) {
            Map<Extension, String> errors = validate(consumer, versionedPlugin("Provider", version));
            assertEquals(String.valueOf(version), 1, errors.size());
            assertTrue(errors.get(consumer).contains("installed version"));
        }
        // An unreferenced plugin does not need to change its existing version format.
        assertTrue(validate(versionedPlugin("Unreferenced", "release-five")).isEmpty());
    }

    @Test
    public void dependenciesRequireAnEnabledPluginWithTheExactName() {
        Extension consumer = plugin("Consumer", pluginRequirement("Provider", "1.0.0"));
        assertTrue(validate(consumer).get(consumer).contains("not installed"));
        assertTrue(validate(consumer, plugin("provider")).get(consumer).contains("not installed"));
        assertTrue(validate(consumer, plugin(" Provider ")).get(consumer).contains("not installed"));
        Extension connector = descriptor("Provider", false, "1.0.0", null, "1.0.0", true);
        assertTrue(validate(consumer, connector).get(consumer).contains("not installed"));
        Extension disabled = descriptor("Provider", true, "1.0.0", null, "1.0.0", false);
        assertTrue(validate(consumer, disabled).get(consumer).contains("disabled"));

        Extension connectorConsumer = descriptor("Provider", false, "1.0.0", null, "1.0.0", true,
                pluginRequirement("Provider", "1.0.0"));
        assertTrue(validate(connectorConsumer, plugin("Provider")).isEmpty());
    }

    @Test
    public void allRequirementsMustBeSatisfied() {
        Extension consumer = plugin("Consumer", pluginRequirement("First", "1.0.0"),
                pluginRequirement("Second", "2.0.0"));
        Extension first = plugin("First");
        assertTrue(validate(consumer, first).get(consumer).contains("Second"));
        assertTrue(validate(consumer, first, versionedPlugin("Second", "2.1.0")).isEmpty());
        assertTrue(validate(consumer, first, versionedPlugin("Second", "1.0.0")).get(consumer).contains("Second"));
    }

    @Test
    public void duplicatePluginNamesRejectEveryProviderAndTheirConsumers() {
        Extension first = plugin("Provider");
        Extension second = descriptor("Provider", true, "1.0.0", null, "1.0.0", false);
        Extension consumer = plugin("Consumer", pluginRequirement("Provider", "1.0.0"));
        Map<Extension, String> errors = validate(consumer, first, second);
        assertEquals(3, errors.size());
        assertTrue(errors.get(first).contains("More than one plugin"));
        assertTrue(errors.get(second).contains("More than one plugin"));
        assertTrue(errors.get(consumer).contains("ambiguous"));
    }

    @Test
    public void disabledConsumersKeepDeclarationAndEngineChecksButMayHaveMissingPlugins() {
        Extension disabled = descriptor("Disabled", true, "1.0.0", null, "1.0.0", false,
                pluginRequirement("Missing", "1.0.0"));
        assertTrue(validate(disabled).isEmpty());
        Extension badDeclaration = descriptor("Disabled", true, "1.0.0", null, "1.0.0", false,
                pluginRequirement("Missing", "bad version"));
        assertTrue(validate(badDeclaration).get(badDeclaration).contains("numeric major.minor.patch"));
        Extension badEngine = descriptor("Disabled", true, "1.0.0", null, "2.0.0", false,
                pluginRequirement("Missing", "1.0.0"));
        assertTrue(validate(badEngine).get(badEngine).contains("Requires engine API"));
    }

    @Test
    public void incompatibleProvidersInvalidateTransitiveConsumersInAnyInventoryOrder() {
        Extension first = plugin("First", pluginRequirement("Second", "1.0.0"));
        Extension second = plugin("Second", pluginRequirement("Third", "1.0.0"));
        for (Extension third : Arrays.asList(
                descriptor("Third", true, "1.0.0", null, "2.0.0", true),
                plugin("Third", pluginRequirement("Missing", "1.0.0")),
                plugin("Third", requirement("invalid", null, "1.0.0")))) {
            Map<Extension, String> forward = validate(first, second, third);
            Map<Extension, String> reverse = validate(third, second, first);
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
        Extension first = plugin("First", pluginRequirement("Second", "1.0.0"));
        Extension second = plugin("Second", pluginRequirement("First", "1.0.0"));
        Extension consumer = plugin("Consumer", pluginRequirement("First", "1.0.0"));
        Extension independent = plugin("Independent");
        Map<Extension, String> errors = validate(consumer, first, second, independent);
        assertEquals(3, errors.size());
        for (String error : errors.values()) {
            assertTrue(error.contains("Circular plugin dependencies"));
        }
        assertFalse(errors.containsKey(independent));
    }

    @Test
    public void failureInsideACyclePropagatesAndDiamondDependenciesResolve() {
        Extension first = plugin("First", pluginRequirement("Second", "1.0.0"));
        Extension second = plugin("Second", pluginRequirement("First", "1.0.0"),
                pluginRequirement("Missing", "1.0.0"));
        Map<Extension, String> errors = validate(first, second);
        assertTrue(errors.get(first).contains("Second"));
        assertTrue(errors.get(second).contains("Missing"));

        Extension base = plugin("Base");
        Extension left = plugin("Left", pluginRequirement("Base", "1.0.0"));
        Extension right = plugin("Right", pluginRequirement("Base", "1.0.0"));
        Extension top = plugin("Top", pluginRequirement("Left", "1.0.0"), pluginRequirement("Right", "1.0.0"));
        assertTrue(validate(top, left, right, base).isEmpty());
    }

    @Test
    public void longDependencyChainsDoNotUseTheCallStack() {
        List<Extension> chain = new ArrayList<>();
        int length = 10000;
        for (int index = 0; index < length - 1; index++) {
            chain.add(plugin("Plugin " + index, pluginRequirement("Plugin " + (index + 1), "1.0.0")));
        }
        chain.add(plugin("Plugin " + (length - 1)));
        assertTrue(ExtensionDependencies.validate(chain, SERVER_VERSION).isEmpty());
        chain.set(length - 1, plugin("Plugin " + (length - 1), pluginRequirement("Missing", "1.0.0")));
        assertEquals(length, ExtensionDependencies.validate(chain, SERVER_VERSION).size());
    }

    private static ExtensionDependency requirement(String type, String name, String version) {
        return new ExtensionDependency(type, name, version);
    }

    private static ExtensionDependency pluginRequirement(String name, String version) {
        return requirement("plugin", name, version);
    }

    private static Extension plugin(String name, ExtensionDependency... dependencies) {
        return descriptor(name, true, "1.0.0", null, "1.0.0", true, dependencies);
    }

    private static Extension versionedPlugin(String name, String version) {
        return descriptor(name, true, version, null, "1.0.0", true);
    }

    private static Extension descriptor(String name, boolean plugin, String pluginVersion, String mirthVersion,
            String minApiVersion, boolean enabled, ExtensionDependency... dependencies) {
        return new Extension(name, plugin, pluginVersion, mirthVersion, minApiVersion,
                dependencies == null ? null : Arrays.asList(dependencies), enabled);
    }

    private static Map<Extension, String> validate(Extension... extensions) {
        return ExtensionDependencies.validate(Arrays.asList(extensions), SERVER_VERSION);
    }

    private static void assertError(Extension extension, String expected) {
        String error = ExtensionDependencies.getEngineError(extension, SERVER_VERSION);
        assertTrue(String.valueOf(error), error != null && error.contains(expected));
    }
}
