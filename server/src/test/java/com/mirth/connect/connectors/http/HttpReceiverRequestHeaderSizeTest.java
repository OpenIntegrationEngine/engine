// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.connectors.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.event.EventDispatcher;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

/**
 * Covers what HttpReceiver.onStart() does with the configured request header size before Jetty ever
 * sees it: template resolution, the default applied when nothing is configured, and the failure
 * raised for anything that does not resolve to a positive number.
 */
public class HttpReceiverRequestHeaderSizeTest {

    private static final String TEST_CHANNEL_ID = UUID.randomUUID().toString();
    private static final String TEST_CHANNEL_NAME = "Test HTTP Listener Channel";

    private HttpReceiver receiver;

    @BeforeClass
    public static void setupBeforeClass() {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);
        when(controllerFactory.createConfigurationController()).thenReturn(mock(ConfigurationController.class));
        when(controllerFactory.createEventController()).thenReturn(mock(EventController.class));

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);
    }

    @After
    public void tearDown() throws Exception {
        if (receiver != null) {
            receiver.stop();
            receiver.onUndeploy();
            receiver = null;
        }
    }

    @Test
    public void testConfiguredValueIsUsed() throws Exception {
        assertEquals(32768, startWith("32768"));
    }

    @Test
    public void testMissingValueFallsBackToJettyDefault() throws Exception {
        assertEquals(HttpReceiverProperties.DEFAULT_REQUEST_HEADER_SIZE, startWith(null));
    }

    /**
     * A value that cannot be parsed would otherwise restore a cap the user was trying to lower, so it
     * fails the connector rather than falling back.
     */
    @Test
    public void testUnparseableValueFailsToStart() throws Exception {
        assertFailsToStart("not a number");
    }

    /**
     * An unresolved template arrives at NumberUtils as the literal ${...} text. Failing tells the user
     * their substitution is broken instead of quietly running on the default.
     */
    @Test
    public void testUnresolvedTemplateFailsToStart() throws Exception {
        assertFailsToStart("${nothingDefinesThis}");
    }

    /**
     * Jetty treats a non-positive request header size as no limit at all. The connector panel rejects
     * those values, but a channel imported or pushed through the REST API never runs that check.
     */
    @Test
    public void testZeroFailsToStart() throws Exception {
        assertFailsToStart("0");
    }

    @Test
    public void testNegativeFailsToStart() throws Exception {
        assertFailsToStart("-1");
    }

    /**
     * Asserts the connector refuses to start and that the message carries the value as it resolved, so
     * the cause is visible without reading the channel configuration.
     */
    private void assertFailsToStart(String requestHeaderSize) throws Exception {
        try {
            startWith(requestHeaderSize);
            fail("Expected ConnectorTaskException for request header size \"" + requestHeaderSize + "\"");
        } catch (ConnectorTaskException e) {
            assertTrue("Message should contain the resolved value but was: " + e.getMessage(), e.getMessage().contains(requestHeaderSize));
        }
    }

    private int startWith(String requestHeaderSize) throws Exception {
        HttpReceiverProperties properties = new HttpReceiverProperties();
        properties.getListenerConnectorProperties().setHost("127.0.0.1");
        properties.getListenerConnectorProperties().setPort("0");
        properties.setRequestHeaderSize(requestHeaderSize);

        receiver = new TestHttpReceiver(properties);

        Channel channel = new TestChannel();
        channel.setChannelId(TEST_CHANNEL_ID);
        channel.setName(TEST_CHANNEL_NAME);
        receiver.setChannel(channel);

        receiver.onDeploy();
        receiver.start();

        return receiver.getRequestHeaderSize();
    }

    private static class TestHttpReceiver extends HttpReceiver {

        public TestHttpReceiver(HttpReceiverProperties properties) {
            super();
            setChannelId(TEST_CHANNEL_ID);
            setMetaDataId(0);
            setConnectorProperties(properties);
        }

        @Override
        protected String getConfigurationClass() {
            return DefaultHttpConfiguration.class.getName();
        }
    }

    private static class TestChannel extends Channel {

        @Override
        protected EventDispatcher getEventDispatcher() {
            return mock(EventDispatcher.class);
        }
    }
}
