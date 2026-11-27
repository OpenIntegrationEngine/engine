// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.client.core.api;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.reflections.Reflections;

/**
 * Every API operation declares the permission it needs, so an authorization controller can enforce
 * it. An operation that needs none goes in {@link #OPEN_ON_PURPOSE} with the reason.
 */
public class MirthOperationPermissionTest {

    /** Operations that declare no permission on purpose, keyed by interface and method name. */
    private static final Map<String, String> OPEN_ON_PURPOSE = new TreeMap<>();

    static {
        open("ConfigurationServletInterface.getServerId", "Server identity");
        open("ConfigurationServletInterface.getVersion", "Server version");
        open("ConfigurationServletInterface.getBuildDate", "Server version");
        open("ConfigurationServletInterface.getStatus", "Server status");
        open("ConfigurationServletInterface.getServerTimezone", "Server clock");
        open("ConfigurationServletInterface.getServerTime", "Server clock");
        open("ConfigurationServletInterface.getJVMName", "Server runtime name");
        open("ConfigurationServletInterface.getAvailableCharsetEncodings", "Character sets the server supports");
        open("ConfigurationServletInterface.getPublicServerSettings", "The public subset of the server settings");
        open("ConfigurationServletInterface.getUpdateSettings", "Usage statistics switch and timestamp");
        open("ConfigurationServletInterface.getLicenseInfo", "License information");
        open("ConfigurationServletInterface.getGuid", "Generates a random ID");
        open("ConfigurationServletInterface.getDatabaseDrivers", "Database drivers the server has");
        open("ConfigurationServletInterface.getPasswordRequirements", "Password rules");
        open("ConfigurationServletInterface.getProtocolsAndCipherSuites", "TLS protocols and cipher suites the server supports");
        open("ConfigurationServletInterface.getRhinoLanguageVersion", "JavaScript language version");
        open("ConfigurationServletInterface.getAbout", "Lists every channel; limiting it per channel is separate from a permission");
        open("ConfigurationServletInterface.getChannelDependencies", "Lists every channel; limiting it per channel is separate from a permission");
        open("ConfigurationServletInterface.getChannelMetadata", "Lists every channel; limiting it per channel is separate from a permission");
        open("ConfigurationServletInterface.getEncryptionSettings", "Needed by the Administrator for every user");
        open("ExtensionServletInterface.getExtensionMetaData", "Extension metadata");
        open("ExtensionServletInterface.getConnectorMetaData", "Extension metadata");
        open("ExtensionServletInterface.getPluginMetaData", "Extension metadata");
        open("ExtensionServletInterface.isExtensionEnabled", "Extension metadata");
        open("ExtensionServletInterface.getDisabledExtensions", "Extension metadata");
        open("ExtensionServletInterface.getPluginProperties", "Checks the extension's own permission inside the method");
        open("ExtensionServletInterface.setPluginProperties", "Checks the extension's own permission inside the method");
        open("MessageServletInterface.auditAccessedPHIMessage", "Called by the client to record an action it already performed");
        open("MessageServletInterface.auditQueriedPHIMessage", "Called by the client to record an action it already performed");
        open("MessageServletInterface.auditExportMessages", "Called by the client to record an action it already performed");
        open("MessageServletInterface.auditExportMessagesSuccess", "Called by the client to record an action it already performed");
        open("SystemServletInterface.getInfo", "Server runtime and system information");
        open("SystemServletInterface.getStats", "Server system statistics");
        open("UsageServletInterface.getUsageData", "Usage statistics report");
        open("UserServletInterface.login", "Runs before the user is known");
        open("UserServletInterface.logout", "Ends the caller's own session");
        open("UserServletInterface.inactivityLogout", "Ends the caller's own session");
        open("UserServletInterface.getCurrentUser", "Returns the caller's own user");
        open("UserServletInterface.getUserPreferences", "Every user reads and writes their own preferences");
        open("UserServletInterface.getUserPreference", "Every user reads and writes their own preferences");
        open("UserServletInterface.setUserPreferences", "Every user reads and writes their own preferences");
        open("UserServletInterface.setUserPreference", "Every user reads and writes their own preferences");
        open("UserServletInterface.setUserNotificationAcknowledged", "Every user reads and writes their own preferences");
        open("DirectoryResourceServletInterface.getLibraries", "Libraries of a resource");
    }

    private static void open(String operation, String reason) {
        OPEN_ON_PURPOSE.put(operation, reason);
    }

    /** "Interface.method" of every @MirthOperation that declares no permission. */
    private static final Set<String> unlabelled = new TreeSet<>();
    /** How many @MirthOperation methods share each "Interface.method" (overloads share one). */
    private static final Map<String, Integer> methodsPerKey = new HashMap<>();
    private static int core;
    private static int connectors;
    private static int plugins;

    @BeforeClass
    public static void scan() {
        for (Class<?> servletInterface : new Reflections("com.mirth.connect").getSubTypesOf(BaseServletInterface.class)) {
            for (Method method : servletInterface.getDeclaredMethods()) {
                MirthOperation operation = method.getAnnotation(MirthOperation.class);
                if (operation == null) {
                    continue;
                }
                String key = servletInterface.getSimpleName() + "." + method.getName();
                methodsPerKey.merge(key, 1, Integer::sum);
                String pkg = servletInterface.getPackage().getName();
                if (pkg.startsWith("com.mirth.connect.client.core.api.servlets")) {
                    core++;
                } else if (pkg.startsWith("com.mirth.connect.connectors.")) {
                    connectors++;
                } else if (pkg.startsWith("com.mirth.connect.plugins.")) {
                    plugins++;
                }
                if (StringUtils.isBlank(operation.permission())) {
                    unlabelled.add(key);
                }
            }
        }
    }

    @Test
    public void scanFindsCoreConnectorAndPluginOperations() {
        // An empty or partial scan would let every other check pass with nothing checked.
        assertTrue("core API operations found: " + core, core > 100);
        assertTrue("connector operations found: " + connectors, connectors >= 17);
        assertTrue("bundled plugin operations found: " + plugins, plugins >= 1);
    }

    @Test
    public void everyOperationDeclaresAPermissionOrIsOpenOnPurpose() {
        List<String> missing = new ArrayList<>();
        for (String operation : unlabelled) {
            if (!OPEN_ON_PURPOSE.containsKey(operation)) {
                missing.add(operation);
            }
        }
        if (!missing.isEmpty()) {
            fail("These operations declare no permission. Add one to @MirthOperation, or add the operation to OPEN_ON_PURPOSE in this test with the reason it needs none:\n  " + StringUtils.join(missing, "\n  "));
        }
    }

    @Test
    public void openOnPurposeListsOnlyUnlabelledOperations() {
        List<String> stale = new ArrayList<>(OPEN_ON_PURPOSE.keySet());
        stale.removeAll(unlabelled);
        if (!stale.isEmpty()) {
            fail("These OPEN_ON_PURPOSE entries match no operation without a permission (renamed, removed or now labelled); remove them:\n  " + StringUtils.join(stale, "\n  "));
        }
    }

    @Test
    public void openOnPurposeEntriesNameOneMethodEach() {
        // Overloads share a key, so a new overload of a listed method would otherwise be covered by
        // the existing entry without anyone deciding it needs no permission.
        List<String> ambiguous = new ArrayList<>();
        for (String operation : OPEN_ON_PURPOSE.keySet()) {
            if (methodsPerKey.getOrDefault(operation, 0) > 1) {
                ambiguous.add(operation);
            }
        }
        if (!ambiguous.isEmpty()) {
            fail("These OPEN_ON_PURPOSE entries match more than one overloaded method; give the new overload a permission or rename it:\n  " + StringUtils.join(ambiguous, "\n  "));
        }
    }
}
