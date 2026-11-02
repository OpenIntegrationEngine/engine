// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package org.openintegrationengine.smoketest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openintegrationengine.smoketest.Harness;
import org.openintegrationengine.smoketest.SharedServer;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ChannelMetadata;
import com.mirth.connect.model.ServerEvent;
import com.mirth.connect.model.filters.EventFilter;
import com.mirth.connect.plugins.datapruner.DataPrunerServletInterface;

/**
 * Runs the data pruner over a channel that has pruning turned on. Its messages are new, so
 * nothing is old enough to prune: the pruner runs its queries that select messages to prune and
 * reports the result as an event, but never reaches its delete statements. The pruner prunes
 * every channel with pruning settings, so don't run this against a server whose data matters.
 */
@DisplayName("Data pruner")
class DataPrunerTest {

    private static String channelId;
    private static String channelName;

    @BeforeAll
    static void deployWithPruning() throws Exception {
        Channel channel = Harness.newChannel("admin/channel.xml", "Smoke pruner");
        channelName = channel.getName();
        channelId = SharedServer.get().deployChannel(channel, channelName, "");
        SharedServer.get().submitMessage(channelId, "prune me later", new HashMap<>());

        Map<String, ChannelMetadata> metadata = admin().getChannelMetadata();
        ChannelMetadata channelMetadata = metadata.computeIfAbsent(channelId, id -> new ChannelMetadata());
        channelMetadata.getPruningSettings().setPruneMetaDataDays(30);
        channelMetadata.getPruningSettings().setPruneContentDays(7);
        channelMetadata.getPruningSettings().setArchiveEnabled(false);
        admin().setChannelMetadata(metadata);
    }

    @AfterAll
    static void remove() {
        Harness.undeploy(channelId);
    }

    @Test
    @DisplayName("prunes a channel and reports success")
    void prunesChannel() throws Exception {
        DataPrunerServletInterface pruner = SharedServer.get().servlet(DataPrunerServletInterface.class);
        awaitIdle(pruner);
        pruner.start();
        awaitIdle(pruner);

        EventFilter filter = new EventFilter();
        filter.setName("Data Pruner");
        filter.setAttributeSearch(channelName);

        List<ServerEvent> events = Harness.awaitAssertedValue(() -> {
            List<ServerEvent> found = admin().getEvents(filter, 0, 10);
            assertEquals(1, found.size(), "pruner events for " + channelName);
            return found;
        });
        ServerEvent event = events.get(0);
        assertEquals(ServerEvent.Outcome.SUCCESS, event.getOutcome(), "outcome of " + event.getAttributes());
        assertEquals("0", event.getAttributes().get("Messages Pruned"), "attributes " + event.getAttributes());
    }

    private static void awaitIdle(DataPrunerServletInterface pruner) throws Exception {
        Harness.awaitAsserted(() -> assertNotEquals("true", pruner.getStatusMap().get("isRunning"), "the pruner is still running"));
    }

    private static Client admin() {
        return SharedServer.get().client();
    }
}
