// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package org.openintegrationengine.smoketest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openintegrationengine.smoketest.SharedServer;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.model.alert.AlertActionGroup;
import com.mirth.connect.model.alert.AlertModel;
import com.mirth.connect.model.alert.DefaultTrigger;

/** Creates an alert, updates it, reads it back and removes it. */
@DisplayName("Alerts")
class AlertTest {

    @Test
    @DisplayName("creates, updates, reads and removes an alert")
    void alert() throws Exception {
        AlertModel alert = new AlertModel(new DefaultTrigger(), new AlertActionGroup());
        alert.setName("Smoke alert " + suffix());
        admin().createAlert(alert);
        try {
            assertEquals(alert.getName(), admin().getAlert(alert.getId()).getName(), "alert after create");

            alert.setName(alert.getName() + " renamed");
            admin().updateAlert(alert);
            assertEquals(alert.getName(), admin().getAlert(alert.getId()).getName(), "alert after update");

            assertTrue(admin().getAlerts(null).stream().anyMatch(a -> a.getId().equals(alert.getId())),
                    "alert listed among all alerts");
        } finally {
            admin().removeAlert(alert.getId());
        }

        ClientException missing = assertThrows(ClientException.class, () -> admin().getAlert(alert.getId()), "alert after removal");
        assertTrue(missing.getMessage().contains("404"), () -> "error for the removed alert: " + missing.getMessage());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Client admin() {
        return SharedServer.get().client();
    }
}
