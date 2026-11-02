// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package org.openintegrationengine.smoketest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openintegrationengine.smoketest.SharedServer;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.util.ConfigurationProperty;

/**
 * Adds an entry to the configuration map, reads it back and restores the original map. By
 * default the server stores the map in a file rather than the database, so this covers only the
 * servlet.
 */
@DisplayName("Configuration map")
class ConfigurationMapTest {

    @Test
    @DisplayName("saves and restores the configuration map")
    void configurationMap() throws Exception {
        Map<String, ConfigurationProperty> original = admin().getConfigurationMap();
        Map<String, ConfigurationProperty> changed = new HashMap<>(original);
        String key = "smoke." + suffix();
        changed.put(key, new ConfigurationProperty("value", "added by the smoke tests"));
        try {
            admin().setConfigurationMap(changed);
            ConfigurationProperty saved = admin().getConfigurationMap().get(key);
            assertNotNull(saved, key + " in the configuration map");
            assertEquals("value", saved.getValue());
            assertEquals("added by the smoke tests", saved.getComment());
        } finally {
            admin().setConfigurationMap(original);
        }

        assertNull(admin().getConfigurationMap().get(key), key + " after restoring");
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Client admin() {
        return SharedServer.get().client();
    }
}
