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
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.DeployedState;
import com.mirth.connect.donkey.model.channel.DestinationConnectorProperties;
import com.mirth.connect.donkey.model.message.ContentType;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.StartException;
import com.mirth.connect.donkey.server.channel.DestinationChainProvider;
import com.mirth.connect.donkey.server.channel.DestinationConnector;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.controllers.ChannelController;
import com.mirth.connect.donkey.test.util.TestChannel;
import com.mirth.connect.donkey.test.util.TestConnectorProperties;
import com.mirth.connect.donkey.test.util.TestDestinationConnector;
import com.mirth.connect.donkey.test.util.TestDispatcher;
import com.mirth.connect.donkey.test.util.TestDispatcherProperties;
import com.mirth.connect.donkey.test.util.TestPostProcessor;
import com.mirth.connect.donkey.test.util.TestPreProcessor;
import com.mirth.connect.donkey.test.util.TestSourceConnector;
import com.mirth.connect.donkey.test.util.TestUtils;

public class DestinationConnectorTests {
    private static int TEST_SIZE = 10;
    private static String channelId = TestUtils.DEFAULT_CHANNEL_ID;
    private static String serverId = TestUtils.DEFAULT_SERVER_ID;
    private static String testMessage = TestUtils.TEST_HL7_MESSAGE;

    @BeforeClass
    final public static void beforeClass() throws StartException {
        Donkey.getInstance().startEngine(TestUtils.getDonkeyTestConfiguration());
    }

    @AfterClass
    final public static void afterClass() throws StartException {
        Donkey.getInstance().stopEngine();
    }

    /*
     * Create a new channel with a TestDispatcher destination connector Assert that: - The
     * destination connector has not been deployed - The destination connector is not running - The
     * destination connector's queue thread is not running
     * 
     * Call onDeploy(), assert that: - The destination connector has been successfully deployed
     * 
     * Call start(), assert that: - The destination connector is running - The destination
     * connector's queue thread is running
     */
    @Test
    public final void testStart() throws Exception {
        TestUtils.initChannel(channelId);

        TestChannel channel = new TestChannel();

        channel.setChannelId(channelId);
        channel.setServerId(serverId);

        channel.setPreProcessor(new TestPreProcessor());
        channel.setPostProcessor(new TestPostProcessor());

        TestSourceConnector sourceConnector = (TestSourceConnector) TestUtils.createDefaultSourceConnector();
        sourceConnector.setChannelId(channel.getChannelId());
        sourceConnector.setChannel(channel);
        channel.setSourceConnector(sourceConnector);
        channel.getSourceConnector().setFilterTransformerExecutor(TestUtils.createDefaultFilterTransformerExecutor());

        TestDispatcher destinationConnector = new TestDispatcher();
        TestDispatcherProperties connectorProperties = new TestDispatcherProperties();
        connectorProperties.getDestinationConnectorProperties().setQueueEnabled(true);
        TestUtils.initDefaultDestinationConnector(destinationConnector, connectorProperties);
        destinationConnector.setChannelId(channelId);

        DestinationChainProvider chain = new DestinationChainProvider();
        chain.setChannelId(channelId);
        destinationConnector.setMetaDataReplacer(sourceConnector.getMetaDataReplacer());
        destinationConnector.setMetaDataColumns(channel.getMetaDataColumns());
        destinationConnector.setFilterTransformerExecutor(TestUtils.createDefaultFilterTransformerExecutor());
        chain.addDestination(1, destinationConnector);
        channel.addDestinationChainProvider(chain);

        // Assert that the destination connector has not been deployed
        assertFalse(destinationConnector.isDeployed());
        // Assert that the destination connector is not running
        assertFalse(destinationConnector.getCurrentState() != DeployedState.STOPPED);
        // Assert that the destination connector queue thread is not running
        assertFalse(destinationConnector.isQueueThreadRunning());

        destinationConnector.onDeploy();

        // Assert that the destination connector has been deployed
        assertTrue(destinationConnector.isDeployed());

        destinationConnector.start();
        Thread.sleep(1000);

        // Assert that the destination connector is running
        assertTrue(destinationConnector.getCurrentState() != DeployedState.STOPPED);
        // Assert that the destination connector queue thread is running
        assertTrue(destinationConnector.isQueueThreadRunning());

        destinationConnector.stop();
        destinationConnector.onUndeploy();
        ChannelController.getInstance().removeChannel(channel.getChannelId());
    }

    /*
     * Create a new channel with a TestDispatcher destination connector Call onDeploy() and start(),
     * assert that: - The destination connector has been successfully deployed - The destination
     * connector is running - The destination connector's queue thread is running
     * 
     * Call stop() and onUndeploy(), assert that: - The destination connector has been successfully
     * undeployed - The destination connector is not running - The destination connector's queue
     * thread is not running
     * 
     * Do the same steps as above, except call stop(true) instead of stop()
     */
    @Test
    public final void testStop() throws Exception {
        ChannelController.getInstance().getLocalChannelId(channelId);

        TestChannel channel = new TestChannel();

        channel.setChannelId(channelId);
        channel.setServerId(serverId);

        channel.setPreProcessor(new TestPreProcessor());
        channel.setPostProcessor(new TestPostProcessor());

        TestSourceConnector sourceConnector = (TestSourceConnector) TestUtils.createDefaultSourceConnector();
        sourceConnector.setChannelId(channel.getChannelId());
        sourceConnector.setChannel(channel);
        channel.setSourceConnector(sourceConnector);
        channel.getSourceConnector().setFilterTransformerExecutor(TestUtils.createDefaultFilterTransformerExecutor());

        TestDispatcher destinationConnector = new TestDispatcher();
        TestDispatcherProperties connectorProperties = new TestDispatcherProperties();
        connectorProperties.getDestinationConnectorProperties().setQueueEnabled(true);
        TestUtils.initDefaultDestinationConnector(destinationConnector, connectorProperties);
        destinationConnector.setChannelId(channelId);

        destinationConnector.setMetaDataReplacer(sourceConnector.getMetaDataReplacer());
        destinationConnector.setMetaDataColumns(channel.getMetaDataColumns());
        destinationConnector.setFilterTransformerExecutor(TestUtils.createDefaultFilterTransformerExecutor());

        DestinationChainProvider chain = new DestinationChainProvider();
        chain.setChannelId(channelId);
        chain.addDestination(1, destinationConnector);
        channel.addDestinationChainProvider(chain);

        channel.deploy();
        channel.start(null);
        Thread.sleep(1000);

        // Assert that the destination connector has been deployed
        assertTrue(destinationConnector.isDeployed());
        // Assert that the destination connector is running
        assertTrue(destinationConnector.getCurrentState() != DeployedState.STOPPED);
        // Assert that the destination connector queue thread is running
        assertTrue(destinationConnector.isQueueThreadRunning());

        destinationConnector.stop();
        destinationConnector.onUndeploy();

        // Assert that the destination connector has been undeployed
        assertFalse(destinationConnector.isDeployed());
        // Assert that the destination connector is not running
        assertFalse(destinationConnector.getCurrentState() != DeployedState.STOPPED);
        // Assert that the destination connector queue thread is not running
        assertFalse(destinationConnector.isQueueThreadRunning());

        destinationConnector.onDeploy();
        destinationConnector.start();
        Thread.sleep(1000);

        // Assert that the destination connector has been deployed
        assertTrue(destinationConnector.isDeployed());
        // Assert that the destination connector is running
        assertTrue(destinationConnector.getCurrentState() != DeployedState.STOPPED);
        // Assert that the destination connector queue thread is running
        assertTrue(destinationConnector.isQueueThreadRunning());

        destinationConnector.halt();
        destinationConnector.onUndeploy();

        // Assert that the destination connector has been undeployed
        assertFalse(destinationConnector.isDeployed());
        // Assert that the destination connector is not running
        assertFalse(destinationConnector.getCurrentState() != DeployedState.STOPPED);
        // Assert that the destination connector queue thread is not running
        assertFalse(destinationConnector.isQueueThreadRunning());

        ChannelController.getInstance().removeChannel(channel.getChannelId());
    }

    /*
     * Create a new channel, where the destination connector is either a TestDispatcher or a
     * TestDestinationConnector, depending on whether it should have its queueProperties null or
     * not.
     * 
     * Send messages, assert that: - If the queue properties is null, the queuing is disabled,
     * queuing is set to send first, or queuing is set to not regenerate the template, then the sent
     * content was stored - If the queue properties is null, queuing is disabled, or queuing is set
     * to send first, then the send attempts is at least one - If the queue properties is not null
     * and queuing is enabled, then the message was added to the destination connector queue
     * 
     * Repeat the above steps for all applicable combinations of queueNull, queueEnabled,
     * queueSendFirst, and queueRegenerate
     */

    // This test is all sorts of broken. It needs to be completely rewritten 
//    @Test
//    public final void testProcess() throws Exception {
//        // Test for null queueProperties
//        testProcess(true, false, false, false, 0);
//
//        // Tests for non-null queueProperties
//        testProcess(false, false, false, false, 0);
//        testProcess(false, false, false, true, 0);
//        testProcess(false, false, true, false, 0);
//        testProcess(false, false, true, true, 0);
//        testProcess(false, true, false, false, 0);
//        testProcess(false, true, false, true, 0);
//        testProcess(false, true, true, false, 0);
//        testProcess(false, true, true, true, 0);
//
//        // test retryOnFailure option
//        testProcess(false, false, false, false, 5);
//        testProcess(false, true, true, false, 5);
//    }

    private void testProcess(boolean queueNull, boolean queueEnabled, boolean queueSendFirst, boolean queueRegenerate, int retryCount) throws Exception {
        ChannelController.getInstance().getLocalChannelId(channelId);

        TestChannel channel = new TestChannel();

        channel.setChannelId(channelId);
        channel.setServerId(serverId);

        channel.setPreProcessor(new TestPreProcessor());
        channel.setPostProcessor(new TestPostProcessor());

        TestSourceConnector sourceConnector = (TestSourceConnector) TestUtils.createDefaultSourceConnector();
        sourceConnector.setChannelId(channel.getChannelId());
        sourceConnector.setChannel(channel);
        channel.setSourceConnector(sourceConnector);
        channel.getSourceConnector().setFilterTransformerExecutor(TestUtils.createDefaultFilterTransformerExecutor());

        DestinationConnector destinationConnector = null;
        ConnectorProperties connectorProperties = null;
        DestinationConnectorProperties dispatcherConnectorProperties = null;

        if (queueNull) {
            destinationConnector = new TestDestinationConnector();
            connectorProperties = new TestConnectorProperties();
        } else {
            destinationConnector = new TestDispatcher();
            connectorProperties = new TestDispatcherProperties();

            dispatcherConnectorProperties = ((TestDispatcherProperties) connectorProperties).getDestinationConnectorProperties();
            dispatcherConnectorProperties.setQueueEnabled(queueEnabled);
            dispatcherConnectorProperties.setSendFirst(queueSendFirst);
            dispatcherConnectorProperties.setRegenerateTemplate(queueRegenerate);
            dispatcherConnectorProperties.setRetryIntervalMillis(100);

            if (retryCount > 0) {
                dispatcherConnectorProperties.setRetryCount(retryCount);
                ((TestDispatcher) destinationConnector).setReturnStatus(Status.QUEUED);
            }
        }

        TestUtils.initDefaultDestinationConnector(destinationConnector, connectorProperties);
        destinationConnector.setChannelId(channelId);
        destinationConnector.setMetaDataReplacer(sourceConnector.getMetaDataReplacer());
        destinationConnector.setMetaDataColumns(channel.getMetaDataColumns());
        destinationConnector.setFilterTransformerExecutor(TestUtils.createDefaultFilterTransformerExecutor());

        DestinationChainProvider chain = new DestinationChainProvider();
        chain.setChannelId(channelId);
        chain.addDestination(1, destinationConnector);
        channel.addDestinationChainProvider(chain);

        channel.deploy();
        channel.start(null);
        ChannelController.getInstance().deleteAllMessages(channel.getChannelId());

        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet result = null;

        try {
            connection = TestUtils.getConnection();

            for (int i = 1; i <= TEST_SIZE; i++) {
                DispatchResult dispatchResult = sourceConnector.readTestMessage(testMessage);
                sourceConnector.finishDispatch(dispatchResult);

                if (retryCount > 0) {
                    // assert that the connector attempted to send the message the correct number of times
                    assertEquals(Integer.valueOf(dispatcherConnectorProperties.getRetryCount() + 1), TestUtils.getSendAttempts(channel.getChannelId(), dispatchResult.getMessageId()));
                } else {
                    if (queueNull || !queueEnabled || queueSendFirst) {
                        // Assert that the sent content was stored
                        long localChannelId = ChannelController.getInstance().getLocalChannelId(channel.getChannelId());
                        statement = connection.prepareStatement("SELECT * FROM d_mc" + localChannelId + " WHERE message_id = ? AND metadata_id = ? AND content_type = ?");
                        statement.setLong(1, dispatchResult.getMessageId());
                        statement.setInt(2, 1);
                        statement.setInt(3, ContentType.SENT.getContentTypeCode());
                        result = statement.executeQuery();
                        assertTrue(result.next());
                        result.close();
                        statement.close();

                        // Assert that the send attempts is at least one
                        assertTrue(TestUtils.getSendAttempts(channel.getChannelId(), dispatchResult.getMessageId()) > 0);
                    }
                }

                if (!queueNull && queueEnabled) {
                    // Assert that the message was placed in the destination connector queue
                    assertEquals(i, destinationConnector.getQueue().size());
                }
            }
        } finally {
            TestUtils.close(result);
            TestUtils.close(statement);
            TestUtils.close(connection);
        }

        channel.stop();
        channel.undeploy();
        ChannelController.getInstance().removeChannel(channel.getChannelId());
    }

    /*
     * testAfterSend and testRunResponseTransformer are re-implemented against the CI harness:
     * ci/tests/190-response-handling for the response transformer, and
     * smoketest BlockingResponseTransformerTest for a transformer that has not returned yet.
     */
}
