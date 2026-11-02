// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package org.openintegrationengine.smoketest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openintegrationengine.smoketest.Harness;
import org.openintegrationengine.smoketest.SharedServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.api.servlets.UsageServletInterface;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.UpdateSettings;

/**
 * Deploying a channel with debug options records the use in the DEBUGGER_USAGE table, and
 * generating usage data reads those rows and deletes them. This runs every DebugUsage statement.
 * Both paths swallow database errors, so the test checks the counts and that the row is gone,
 * rather than relying on a request failing.
 */
@DisplayName("Debugger usage statistics")
class DebugUsageTest {

    /** Debug only the source connector scripts, which a Channel Reader doesn't have. */
    private static final String DEBUG_SOURCE_CONNECTOR = "f,f,t,f,f,f,f";

    private static UpdateSettings originalUpdateSettings;
    private static String channelId;

    @BeforeAll
    static void enableUsageData() throws Exception {
        originalUpdateSettings = admin().getUpdateSettings();

        // Usage data is only generated when statistics are enabled and none were sent in the
        // last day.
        UpdateSettings settings = new UpdateSettings();
        settings.setStatsEnabled(true);
        settings.setLastStatsTime(0L);
        admin().setUpdateSettings(settings);
    }

    @AfterAll
    static void restore() throws Exception {
        Harness.undeploy(channelId);
        if (originalUpdateSettings == null) {
            // Reading the settings failed, so nothing was changed and that failure is the one to report.
            return;
        }
        // A setting that was never saved can't be removed again, so an unset last-sent time
        // is restored as 0, which the server treats the same way.
        UpdateSettings settings = new UpdateSettings();
        settings.setStatsEnabled(originalUpdateSettings.getStatsEnabled() == null ? Boolean.TRUE : originalUpdateSettings.getStatsEnabled());
        settings.setLastStatsTime(originalUpdateSettings.getLastStatsTime() == null ? 0L : originalUpdateSettings.getLastStatsTime());
        admin().setUpdateSettings(settings);
    }

    @Test
    @DisplayName("counts debug deployments and clears them once reported")
    void countsAndClears() throws Exception {
        String serverId = admin().getServerId();
        // Reading the stats deletes them (see debugStats), so this clears rows left by earlier
        // debug deployments.
        debugStats();

        Channel channel = Harness.newChannel("admin/channel.xml", "Smoke debug usage");
        channelId = SharedServer.get().deployChannel(channel, channel.getName(), DEBUG_SOURCE_CONNECTOR);
        SharedServer.get().deployChannel(channel, channel.getName(), DEBUG_SOURCE_CONNECTOR);

        List<JsonNode> stats = debugStats();
        assertEquals(1, stats.size(), "debug statistics: " + stats);
        JsonNode row = stats.get(0);
        assertEquals(serverId, row.path("serverId").asText(), "server id in " + row);
        // The second deployment updates the first one's row, which needs the lookup by server id.
        assertEquals(2, row.path("invocationCount").asInt(), "invocations in " + row);
        assertEquals(2, row.path("sourceConnectorCount").asInt(), "source connector debugs in " + row);
        assertEquals(0, row.path("duppCount").asInt(), "script debugs in " + row);

        assertEquals(List.of(), debugStats(), "debug statistics after they were reported");
    }

    /**
     * Generates the usage data and returns its debugStats entries. Generating it also deletes the
     * DEBUGGER_USAGE row of every server it reports (DefaultUsageController.getDebugData), so a
     * second call returns only what was recorded in between. The server generates usage data
     * only while stats are enabled and not sent in the last day, as enableUsageData sets up.
     */
    private static List<JsonNode> debugStats() throws Exception {
        String usage = SharedServer.get().servlet(UsageServletInterface.class).getUsageData(new HashMap<>());
        assertNotNull(usage, "usage data");

        List<JsonNode> stats = new ArrayList<>();
        new ObjectMapper().readTree(usage).path("debugStats").forEach(stats::add);
        return stats;
    }

    private static Client admin() {
        return SharedServer.get().client();
    }
}
