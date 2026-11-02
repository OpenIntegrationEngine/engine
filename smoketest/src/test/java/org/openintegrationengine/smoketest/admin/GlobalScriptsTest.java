// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package org.openintegrationengine.smoketest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openintegrationengine.smoketest.SharedServer;

import com.mirth.connect.client.core.Client;

/** Saves a changed global script, reads it back and restores the original scripts. */
@DisplayName("Global scripts")
class GlobalScriptsTest {

    @Test
    @DisplayName("saves and restores the global scripts")
    void globalScripts() throws Exception {
        Map<String, String> original = admin().getGlobalScripts();
        Map<String, String> changed = new HashMap<>(original);
        String script = "// smoke test " + suffix() + "\nreturn;";
        changed.put("Deploy", script);
        try {
            admin().setGlobalScripts(changed);
            assertEquals(script, admin().getGlobalScripts().get("Deploy"));
        } finally {
            admin().setGlobalScripts(original);
        }

        assertEquals(original, admin().getGlobalScripts(), "global scripts after restoring");
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Client admin() {
        return SharedServer.get().client();
    }
}
