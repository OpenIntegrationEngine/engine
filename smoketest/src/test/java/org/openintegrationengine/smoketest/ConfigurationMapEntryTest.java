// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.api.servlets.ConfigurationServletInterface;
import com.mirth.connect.util.ConfigurationProperty;

/** Exercises the single-entry configuration map update through the Java client and a live server. */
@DisplayName("Configuration map entry update")
class ConfigurationMapEntryTest {

    /** The key check runs in the servlet, so only a request to a live server reaches it. */
    @Test
    @DisplayName("rejects a blank key")
    void rejectsBlankKey() throws Exception {
        assertThrows(ClientException.class, () -> configuration().setConfigurationProperty(" ", new ConfigurationProperty("1", null)));

        assertFalse(configuration().getConfigurationMap().containsKey(" "));
    }

    private static ConfigurationServletInterface configuration() {
        return SharedServer.get().servlet(ConfigurationServletInterface.class);
    }
}
