// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.api.servlets.ConfigurationServletInterface;
import com.mirth.connect.util.ConfigurationProperty;

/**
 * Exercises the single-entry configuration map update and its expected-version check through the
 * Java client and a live server. The matching rules themselves are covered by
 * DefaultConfigurationControllerTest; these tests cover what only a live server can show.
 */
@DisplayName("Configuration map entry update")
class ConfigurationMapEntryTest {

    /** Unique per run, so the test never touches entries it didn't create. */
    private static final String PREFIX = "smoketest." + UUID.randomUUID() + ".";

    private static final String KEY = PREFIX + "key";

    @AfterEach
    void removeTestEntries() throws Exception {
        Map<String, ConfigurationProperty> map = configuration().getConfigurationMap();
        if (map.keySet().removeIf(key -> key.startsWith(PREFIX))) {
            configuration().setConfigurationMap(map);
        }
    }

    /** The key check runs in the servlet, so only a request to a live server reaches it. */
    @Test
    @DisplayName("rejects a blank key")
    void rejectsBlankKey() throws Exception {
        assertThrows(ClientException.class, () -> configuration().setConfigurationProperty(" ", new ConfigurationProperty("1", null), null));

        assertFalse(configuration().getConfigurationMap().containsKey(" "));
    }

    /**
     * A null expected version has to be left out of the request. Sent as an empty string, it would
     * require that the key doesn't exist.
     */
    @Test
    @DisplayName("updates unconditionally without an expected version")
    void updatesWithoutExpectedVersion() throws Exception {
        assertTrue(configuration().setConfigurationProperty(KEY, new ConfigurationProperty("1", null), null));

        assertTrue(configuration().setConfigurationProperty(KEY, new ConfigurationProperty("2", null), null));

        assertEquals("2", value(KEY));
    }

    /** An empty expected version has to reach the server as an empty string, not as null. */
    @Test
    @DisplayName("creates an entry with an empty expected version only if the key is missing")
    void createsOnlyIfMissing() throws Exception {
        assertTrue(configuration().setConfigurationProperty(KEY, new ConfigurationProperty("1", null), ""));

        assertFalse(configuration().setConfigurationProperty(KEY, new ConfigurationProperty("2", null), ""));
        assertEquals("1", value(KEY));
    }

    /**
     * The version a client calculates from what the server returned has to match the one the server
     * calculates, including for multi-line and non-ASCII content.
     */
    @Test
    @DisplayName("updates when the expected version matches the entry read from the server")
    void updatesWhenVersionMatches() throws Exception {
        configuration().setConfigurationProperty(KEY, new ConfigurationProperty("line 1\nline 2 é", "comment ✓"), null);
        ConfigurationProperty current = configuration().getConfigurationMap().get(KEY);

        assertTrue(configuration().setConfigurationProperty(KEY, new ConfigurationProperty("2", null), ConfigurationProperty.versionOf(current)));

        assertEquals("2", value(KEY));
    }

    /** A rejected update has to come back as false and leave the stored entry unchanged. */
    @Test
    @DisplayName("rejects an update based on a stale version")
    void rejectsStaleVersion() throws Exception {
        configuration().setConfigurationProperty(KEY, new ConfigurationProperty("1", null), null);
        String staleVersion = ConfigurationProperty.versionOf(configuration().getConfigurationMap().get(KEY));
        assertTrue(configuration().setConfigurationProperty(KEY, new ConfigurationProperty("2", null), staleVersion));

        assertFalse(configuration().setConfigurationProperty(KEY, new ConfigurationProperty("3", null), staleVersion));
        assertEquals("2", value(KEY));
    }

    private static ConfigurationServletInterface configuration() {
        return SharedServer.get().servlet(ConfigurationServletInterface.class);
    }

    private static String value(String key) throws Exception {
        return configuration().getConfigurationMap().get(key).getValue();
    }
}
