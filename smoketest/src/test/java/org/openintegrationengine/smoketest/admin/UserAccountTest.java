// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package org.openintegrationengine.smoketest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openintegrationengine.smoketest.OieServer;
import org.openintegrationengine.smoketest.SharedServer;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.UnauthorizedException;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.model.User;

/**
 * Creates a user, then exercises the user and preference statements the administrator runs at
 * login and when it saves its settings.
 */
@DisplayName("User accounts and preferences")
class UserAccountTest {

    private static final String PASSWORD = "Smoke-test-password-1";

    private static String username;
    private static Integer userId;

    @BeforeAll
    static void createUser() throws Exception {
        username = "smoke-" + UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setUsername(username);
        user.setFirstName("Smoke");
        user.setLastName("Test");
        user.setEmail(username + "@example.invalid");
        admin().createUser(user);

        User created = admin().getUser(username);
        assertNotNull(created, "user " + username + " after createUser");
        userId = created.getId();

        List<String> problems = admin().updateUserPassword(userId, PASSWORD);
        assertTrue(problems == null || problems.isEmpty(), () -> "password rejected: " + problems);
    }

    @AfterAll
    static void removeUser() throws Exception {
        if (userId != null) {
            admin().removeUser(userId);
        }
    }

    @Test
    @DisplayName("stores, replaces and reads back preferences")
    void storesAndReadsPreferences() throws Exception {
        Properties preferences = new Properties();
        preferences.setProperty("smoke.first", "one");
        preferences.setProperty("smoke.second", "two");
        admin().setUserPreferences(userId, preferences);
        admin().setUserPreference(userId, "smoke.first", "replaced");

        Properties all = admin().getUserPreferences(userId, null);
        assertEquals("replaced", all.getProperty("smoke.first"), "smoke.first in " + all);
        assertEquals("two", all.getProperty("smoke.second"), "smoke.second in " + all);

        Properties selected = admin().getUserPreferences(userId, new HashSet<>(Set.of("smoke.second")));
        assertEquals(1, selected.size(), "preferences selected by name: " + selected);
        assertEquals("two", selected.getProperty("smoke.second"));

        assertEquals("replaced", admin().getUserPreference(userId, "smoke.first"));
    }

    @Test
    @DisplayName("updates the user's details")
    void updatesDetails() throws Exception {
        User user = admin().getUser(userId);
        user.setFirstName("Renamed");
        admin().updateUser(user);

        assertEquals("Renamed", admin().getUser(userId).getFirstName());
    }

    @Test
    @DisplayName("rejects a wrong password and accepts the right one")
    void logsIn() throws Exception {
        Client client = OieServer.newClient();
        try {
            UnauthorizedException wrong = assertThrows(UnauthorizedException.class,
                    () -> client.login(username, PASSWORD + "-wrong"), "login with a wrong password");
            assertEquals(LoginStatus.Status.FAIL, ((LoginStatus) wrong.getResponse()).getStatus(), "status of the rejected login");

            LoginStatus right = client.login(username, PASSWORD);
            assertTrue(right.isSuccess(), () -> "login with the right password: " + right.getStatus() + " " + right.getMessage());
            client.logout();
        } finally {
            client.close();
        }

        assertNotNull(admin().getUser(userId).getLastLogin(), "last login after a successful login");
    }

    private static Client admin() {
        return SharedServer.get().client();
    }
}
