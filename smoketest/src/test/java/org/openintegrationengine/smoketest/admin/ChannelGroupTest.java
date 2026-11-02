// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package org.openintegrationengine.smoketest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openintegrationengine.smoketest.Harness;
import org.openintegrationengine.smoketest.SharedServer;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ChannelGroup;

/** Saves a channel group with a member channel, reads it back and removes it. */
@DisplayName("Channel groups")
class ChannelGroupTest {

    /**
     * The update replaces the server's whole set of groups and deletes any group it leaves out,
     * so every request sends the existing groups along with the test's own.
     */
    @Test
    @DisplayName("saves, reads and removes a channel group")
    void channelGroup() throws Exception {
        Channel channel = Harness.newChannel("admin/channel.xml", "Smoke group member");
        assertTrue(admin().createChannel(channel), "createChannel");
        ChannelGroup group = new ChannelGroup("Smoke group " + suffix(), "created by the smoke tests");
        group.setChannels(new ArrayList<>(List.of(new Channel(channel.getId()))));
        try {
            Set<ChannelGroup> groups = new HashSet<>(admin().getAllChannelGroups());
            groups.add(group);
            assertTrue(admin().updateChannelGroups(groups, new HashSet<>(), false), "updateChannelGroups");

            ChannelGroup saved = findGroup(group.getId());
            assertNotNull(saved, "group " + group.getId());
            assertEquals(group.getName(), saved.getName());
            assertEquals(channel.getId(), saved.getChannels().get(0).getId(), "member channel");
        } finally {
            try {
                Set<ChannelGroup> others = new HashSet<>();
                for (ChannelGroup existing : admin().getAllChannelGroups()) {
                    if (!existing.getId().equals(group.getId())) {
                        others.add(existing);
                    }
                }
                admin().updateChannelGroups(others, ids(group.getId()), false);
            } finally {
                admin().removeChannel(channel.getId());
            }
        }

        assertNull(findGroup(group.getId()), "group after removal");
    }

    /** Lists every group, because a lookup by id logs an error for an id that doesn't exist. */
    private static ChannelGroup findGroup(String id) throws Exception {
        return admin().getAllChannelGroups().stream().filter(g -> g.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * Request bodies are serialized by XStream, which rejects the JDK's immutable collections, so
     * the sets and lists sent to the server have to be mutable ones.
     */
    private static Set<String> ids(String... ids) {
        return new HashSet<>(List.of(ids));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Client admin() {
        return SharedServer.get().client();
    }
}
