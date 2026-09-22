/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.donkey.model.channel.DeployedState;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.StartException;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.channel.ChannelException;
import com.mirth.connect.donkey.server.channel.DestinationChainProvider;
import com.mirth.connect.donkey.server.channel.DestinationConnector;
import com.mirth.connect.donkey.server.controllers.ChannelController;
import com.mirth.connect.donkey.test.util.TestChannel;
import com.mirth.connect.donkey.test.util.TestDestinationConnector;
import com.mirth.connect.donkey.test.util.TestSourceConnector;
import com.mirth.connect.donkey.test.util.TestUtils;

public class ChannelTests {
    final public static int TEST_SIZE = 50;

    private static String channelId = TestUtils.DEFAULT_CHANNEL_ID;
    private static String channelName = TestUtils.DEFAULT_CHANNEL_ID;
    private static String serverId = TestUtils.DEFAULT_SERVER_ID;
    private static String testMessage = TestUtils.TEST_HL7_MESSAGE;

    @BeforeClass
    final public static void beforeClass() throws StartException {
        Donkey donkey = Donkey.getInstance();
        donkey.startEngine(TestUtils.getDonkeyTestConfiguration());
    }

    @AfterClass
    final public static void afterClass() throws StartException {
        Donkey.getInstance().stopEngine();
    }

    /*
     * Deploys a channel, asserts that: - The channel was successfully deployed - The channel is not
     * running - The source connector was successfully deployed - The source connector is not
     * running - Each destination connector was successfully deployed - Each destination connector
     * is not running Then sends messages and asserts that: - Each message is not received by the
     * source connector
     */
    @Test
    public final void testDeployChannel() throws Exception {
        TestChannel channel = (TestChannel) TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();

        assertTrue(channel.isDeployed());
        assertFalse(channel.getCurrentState() == DeployedState.STARTED);
        assertTrue(sourceConnector.isDeployed());
        assertFalse(sourceConnector.getCurrentState() != DeployedState.STOPPED);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertTrue(((TestDestinationConnector) destinationConnector).isDeployed());
                    assertFalse(destinationConnector.getCurrentState() != DeployedState.STOPPED);
                }
            }
        }

        channel.undeploy();
    }

    /*
     * Deploys and undeploys a channel, asserts that: - The channel was successfully undeployed -
     * The channel is not running - The source connector was successfully undeployed - The source
     * connector is not running - Each destination connector was successfully undeployed - Each
     * destination connector is not running Then sends messages and asserts that: - Each message is
     * not received by the source connector
     */
    @Test
    public final void testUndeployChannel() throws Exception {
        TestChannel channel = (TestChannel) TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.undeploy();

        assertFalse(channel.isDeployed());
        assertFalse(channel.getCurrentState() == DeployedState.STARTED);
        assertFalse(sourceConnector.isDeployed());
        assertFalse(sourceConnector.getCurrentState() != DeployedState.STOPPED);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertFalse(((TestDestinationConnector) destinationConnector).isDeployed());
                    assertFalse(destinationConnector.getCurrentState() != DeployedState.STOPPED);
                }
            }
        }
    }

    /*
     * Deploys and starts a channel, asserts that: - The channel is running - The source queue is
     * created - The source connector is running - Each destination connector is running Then sends
     * messages and asserts that: - Each message is received by the destination connectors
     */
    @Test
    public final void testStartChannel() throws Exception {
        Channel channel = TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.start(null);

        assertTrue(channel.getCurrentState() == DeployedState.STARTED);
        assertNotNull(channel.getSourceQueue());
        assertTrue(sourceConnector.getCurrentState() != DeployedState.STOPPED);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertTrue(destinationConnector.getCurrentState() != DeployedState.STOPPED);
                }
            }
        }

        for (int i = 1; i <= TEST_SIZE; i++) {
            sourceConnector.readTestMessage(testMessage);
        }

        Thread.sleep(1000);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertEquals(((TestDestinationConnector) destinationConnector).getMessageIds().size(), TEST_SIZE);
                }
            }
        }

        channel.stop();
        channel.undeploy();
    }

    /*
     * Deploys, starts and pauses a channel, asserts that: - The source connector is not running
     * Then sends messages and asserts that: - Each message is not received by the destination
     * connectors
     */
    @Test
    public final void testPauseChannel() throws Exception {
        Channel channel = TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.start(null);
        channel.pause();

        assertFalse(sourceConnector.getCurrentState() != DeployedState.STOPPED);

        ChannelException exception = null;

        try {
            sourceConnector.readTestMessage(testMessage);
        } catch (ChannelException e) {
            exception = e;
        }

        assertNotNull(exception);
        Thread.sleep(1000);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertEquals(((TestDestinationConnector) destinationConnector).getMessageIds().size(), 0);
                }
            }
        }

        channel.stop();
        channel.undeploy();
    }

    /*
     * Deploys and starts a channel, sends messages, stops the channel and asserts that: - The
     * channel is not running - The source connector is not running - Each destination connector is
     * not running - Each message is received by each destination connector Then sends messages and
     * asserts that: - Each dispatch returns a null MessageResponse
     */
    @Test
    public final void testStopChannel() throws Exception {
        Channel channel = TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.start(null);

        for (int i = 1; i <= TEST_SIZE; i++) {
            sourceConnector.readTestMessage(testMessage);
        }

        channel.stop();
        assertFalse(channel.getCurrentState() == DeployedState.STARTED);
        assertFalse(sourceConnector.getCurrentState() != DeployedState.STOPPED);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertFalse(destinationConnector.getCurrentState() != DeployedState.STOPPED);
                    assertEquals(((TestDestinationConnector) destinationConnector).getMessageIds().size(), TEST_SIZE);
                }
            }
        }

        ChannelException exception = null;

        try {
            assertNull(sourceConnector.readTestMessage(testMessage));
        } catch (ChannelException e) {
            exception = e;
        }

        assertNotNull(exception);
        channel.undeploy();
    }

    /*
     * Deploys and starts a channel, sends messages, stops the channel and asserts that: - The
     * channel is not running - The source connector is not running - Each destination connector is
     * not running - Each message is received by the source connector Then sends messages and
     * asserts that: - Each dispatch returns a null MessageResponse
     */
    @Test
    public final void testHardStop() throws Exception {
        Channel channel = TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.start(null);

        for (int i = 1; i <= TEST_SIZE; i++) {
            sourceConnector.readTestMessage(testMessage);
        }

        channel.halt();
        assertFalse(channel.getCurrentState() == DeployedState.STARTED);
        assertFalse(sourceConnector.getCurrentState() != DeployedState.STOPPED);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertFalse(destinationConnector.getCurrentState() != DeployedState.STOPPED);
                }
            }
        }

        assertEquals(sourceConnector.getMessageIds().size(), TEST_SIZE);

        ChannelException exception = null;

        try {
            assertNull(sourceConnector.readTestMessage(testMessage));
        } catch (ChannelException e) {
            exception = e;
        }

        assertNotNull(exception);
        channel.undeploy();
    }

    /*
     * Creates a channel and asserts that: - The channel exists Then removes the channel and asserts
     * that: - The channel does not exist
     */
    @Test
    public final void testControllerRemoveChannel() throws Exception {
        ChannelController channelController = ChannelController.getInstance();

        // Create Channel
        TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestUtils.assertChannelExists(channelId);

        // Delete Channel
        channelController.removeChannel(channelId);
        TestUtils.assertChannelDoesNotExist(channelId);
    }

    /*
     * Replaced by EncryptionAtRestTest: a channel with encryptData set stores every content stage
     * as ciphertext, and the decrypting read path hands the original back.
     */

    /*
     * Replaced by the ci/tests/200-custom-metadata-columns fixtures (a column of each type,
     * value casting, and values that are absent or uncastable) and
     * CustomMetaDataColumnRedeployTest (adding, removing and retyping a deployed channel's
     * columns, which is what testUpdateMetaDataColumns redeployed for).
     */

    /*
     * Replaced by the ci/tests/160-message-storage-levels fixtures (DEVELOPMENT, PRODUCTION, RAW
     * and METADATA), MessageStorageDisabledTest (DISABLED) and the ci/tests/170-content-removal
     * fixtures (removeContentOnCompletion, with and without a queued destination).
     */
}
