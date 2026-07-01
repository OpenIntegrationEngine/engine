// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation

package com.mirth.connect.connectors.http;

import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.util.ConnectorUtil;
import com.mirth.connect.server.util.TemplateValueReplacer;
import com.mirth.connect.util.ConnectionTestResponse;

public class HttpConnectorServlet extends MirthServlet implements HttpConnectorServletInterface {

    protected static final int TIMEOUT = 5000;
    protected static final TemplateValueReplacer replacer = new TemplateValueReplacer();

    public HttpConnectorServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, PLUGIN_POINT);
    }

    @Override
    public ConnectionTestResponse testConnection(String channelId, String channelName, HttpDispatcherProperties properties) {
        try {
            URL url = new URL(replacer.replaceValues(properties.getHost(), channelId, channelName));
            // If no port was provided, default to port 80 or 443.
            final int port;
            if (url.getPort() != -1) {
                port = url.getPort();
            } else if ("https".equalsIgnoreCase(url.getProtocol())) {
                port = 443;
            } else {
                port = 80;
            }

            if (!properties.isOverrideLocalBinding()) {
                return ConnectorUtil.testConnection(url.getHost(), port, TIMEOUT);
            } else {
                String localAddr = replacer.replaceValues(properties.getLocalAddress(), channelId, channelName);
                return ConnectorUtil.testConnection(url.getHost(), port, TIMEOUT, localAddr);
            }
        } catch (Exception e) {
            throw new MirthApiException(e);
        }
    }
}