// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package org.openintegrationengine.smoketest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openintegrationengine.smoketest.SharedServer;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.model.ServerSettings;

/** Saves a changed server setting, reads it back and restores the original. */
@DisplayName("Server settings")
class ServerSettingsTest {

    @Test
    @DisplayName("saves and restores the server settings")
    void serverSettings() throws Exception {
        ServerSettings settings = admin().getServerSettings();
        String original = settings.getSmtpTimeout();
        try {
            settings.setSmtpTimeout("54321");
            admin().setServerSettings(settings);
            assertEquals("54321", admin().getServerSettings().getSmtpTimeout());
        } finally {
            settings.setSmtpTimeout(original);
            admin().setServerSettings(settings);
        }

        assertEquals(original, admin().getServerSettings().getSmtpTimeout(), "SMTP timeout after restoring");
    }

    private static Client admin() {
        return SharedServer.get().client();
    }
}
