// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.connectors.http;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.event.EventDispatcher;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

/**
 * Covers what HttpReceiver.onStart() does to the configured request header size before Jetty ever
 * sees it: template resolution, the fallback for values that cannot be parsed, and the clamp that
 * keeps a non-positive value from silently removing the header limit.
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

    @Test
    public void testUnparseableValueFallsBackToJettyDefault() throws Exception {
        assertEquals(HttpReceiverProperties.DEFAULT_REQUEST_HEADER_SIZE, startWith("not a number"));
    }

    /**
     * An unresolved template arrives at NumberUtils as the literal ${...} text, so it has to land on
     * the default rather than zero.
     */
    @Test
    public void testUnresolvedTemplateFallsBackToJettyDefault() throws Exception {
        assertEquals(HttpReceiverProperties.DEFAULT_REQUEST_HEADER_SIZE, startWith("${nothingDefinesThis}"));
    }

    /**
     * Jetty treats a non-positive request header size as no limit at all. The connector panel rejects
     * those values, but a channel imported or pushed through the REST API never runs that check, so
     * the receiver has to clamp them itself.
     */
    @Test
    public void testZeroIsClampedToJettyDefault() throws Exception {
        assertEquals(HttpReceiverProperties.DEFAULT_REQUEST_HEADER_SIZE, startWith("0"));
    }

    @Test
    public void testNegativeIsClampedToJettyDefault() throws Exception {
        assertEquals(HttpReceiverProperties.DEFAULT_REQUEST_HEADER_SIZE, startWith("-1"));
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
