/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.client.core;

import java.util.regex.Pattern;

/** Shared by the launcher and engine; must not depend on engine or third-party classes. */
public final class ExtensionCompatibility {
    // Independent of the product release. See docs/extension-compatibility.md before changing.
    public static final String API_VERSION = "1.0.0";

    private static final Pattern API_VERSION_PATTERN =
            Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");

    private ExtensionCompatibility() {}

    public static boolean isCompatible(String mirthVersions, String minExtensionApiVersion,
            String serverVersion) {
        if (minExtensionApiVersion != null) {
            // An invalid explicit requirement must never fall back to the legacy release check.
            return isApiCompatible(minExtensionApiVersion, API_VERSION);
        }

        if (mirthVersions == null || serverVersion == null) {
            return false;
        }

        // Preserve legacy matching, including ignoring the server's fourth (build) component.
        if (serverVersion.split("\\.").length == 4) {
            serverVersion = serverVersion.substring(0, serverVersion.lastIndexOf('.'));
        }
        for (String version : mirthVersions.split(",")) {
            if (version.trim().equals(serverVersion)) {
                return true;
            }
        }
        return false;
    }

    static boolean isApiCompatible(String minimumVersion, String currentVersion) {
        int[] minimum = parseApiVersion(minimumVersion);
        int[] current = parseApiVersion(currentVersion);
        return minimum != null && current != null && minimum[0] == current[0]
                && (current[1] > minimum[1]
                        || (current[1] == minimum[1] && current[2] >= minimum[2]));
    }

    private static int[] parseApiVersion(String version) {
        if (version == null) {
            return null;
        }

        String normalizedVersion = version.trim();
        if (!API_VERSION_PATTERN.matcher(normalizedVersion).matches()) {
            return null;
        }

        String[] parts = normalizedVersion.split("\\.");
        try {
            return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]) };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
