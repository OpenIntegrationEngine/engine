// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.connectors.http;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;

/**
 * Exercises the request header size setting against a real Jetty connector, since the whole point
 * of the property is behavior Jetty enforces before any channel code runs.
 */
public class DefaultHttpConfigurationTest {

    private static final int LARGE_HEADER_BYTES = 16384;

    private Server server;

    @BeforeClass
    public static void setupBeforeClass() {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);
        when(controllerFactory.createConfigurationController()).thenReturn(mock(ConfigurationController.class));

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
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    public void testJettyDefaultRejectsOversizedHeader() throws Exception {
        assertEquals(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE_431, sendLargeHeaderRequest(HttpReceiverProperties.DEFAULT_REQUEST_HEADER_SIZE));
    }

    @Test
    public void testRaisedRequestHeaderSizeAcceptsOversizedHeader() throws Exception {
        assertEquals(HttpStatus.OK_200, sendLargeHeaderRequest(65536));
    }

    private int sendLargeHeaderRequest(int requestHeaderSize) throws Exception {
        int port = startReceiver(requestHeaderSize);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet("http://127.0.0.1:" + port + "/");
            get.addHeader("X-Large-Header", StringUtils.repeat('a', LARGE_HEADER_BYTES));

            try (CloseableHttpResponse response = client.execute(get)) {
                return response.getStatusLine().getStatusCode();
            }
        }
    }

    private int startReceiver(int requestHeaderSize) throws Exception {
        server = new Server();

        HttpReceiver receiver = mock(HttpReceiver.class);
        when(receiver.getServer()).thenReturn(server);
        when(receiver.getHost()).thenReturn("127.0.0.1");
        when(receiver.getPort()).thenReturn(0);
        when(receiver.getTimeout()).thenReturn(30000);
        when(receiver.getRequestHeaderSize()).thenReturn(requestHeaderSize);

        new DefaultHttpConfiguration().configureReceiver(receiver);

        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                baseRequest.setHandled(true);
                response.setStatus(HttpServletResponse.SC_OK);
            }
        });

        server.start();
        return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }
}
