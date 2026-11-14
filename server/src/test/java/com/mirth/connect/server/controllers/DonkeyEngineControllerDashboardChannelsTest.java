// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine Contributors

package com.mirth.connect.server.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.mirth.connect.donkey.server.channel.Channel;

/**
 * A request for specific channels must not return other channels that are mid-deploy or
 * mid-undeploy. See OpenIntegrationEngine/engine#456.
 */
public class DonkeyEngineControllerDashboardChannelsTest {

    private static Channel channel(String id) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        return channel;
    }

    private static Set<Channel> setOf(Channel... channels) {
        return Collections.synchronizedSet(new HashSet<Channel>(Arrays.asList(channels)));
    }

    @Test
    public void filteredRequestIgnoresOtherDeployingChannel() {
        Channel a = channel("A");
        Map<String, Channel> deployed = new HashMap<String, Channel>();
        deployed.put("A", a);

        Map<String, Channel> result = DonkeyEngineController.getDashboardChannels(deployed, setOf(channel("B")), setOf(), Collections.singleton("A"));

        assertEquals(Collections.singleton("A"), result.keySet());
        assertSame(a, result.get("A"));
    }

    @Test
    public void filteredRequestIgnoresOtherUndeployingChannel() {
        Map<String, Channel> result = DonkeyEngineController.getDashboardChannels(new HashMap<String, Channel>(), setOf(), setOf(channel("B")), Collections.singleton("A"));

        assertEquals(Collections.<String> emptySet(), result.keySet());
    }

    @Test
    public void filteredRequestIncludesRequestedChannelWhileItDeploys() {
        Channel a = channel("A");

        Map<String, Channel> result = DonkeyEngineController.getDashboardChannels(new HashMap<String, Channel>(), setOf(a, channel("B")), setOf(channel("C")), Collections.singleton("A"));

        assertEquals(Collections.singleton("A"), result.keySet());
        assertSame(a, result.get("A"));
    }

    @Test
    public void unfilteredRequestIncludesDeployingAndUndeployingChannels() {
        Map<String, Channel> deployed = new HashMap<String, Channel>();
        deployed.put("A", channel("A"));

        Map<String, Channel> result = DonkeyEngineController.getDashboardChannels(deployed, setOf(channel("B")), setOf(channel("C")), null);

        assertEquals(new HashSet<String>(Arrays.asList("A", "B", "C")), result.keySet());
    }

    @Test
    public void deployedChannelWinsOverSameIdInTransition() {
        Channel deployedA = channel("A");
        Map<String, Channel> deployed = new HashMap<String, Channel>();
        deployed.put("A", deployedA);

        Map<String, Channel> result = DonkeyEngineController.getDashboardChannels(deployed, setOf(channel("A")), setOf(), Collections.singleton("A"));

        assertSame(deployedA, result.get("A"));
    }
}
