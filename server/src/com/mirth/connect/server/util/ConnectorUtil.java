// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation

package com.mirth.connect.server.util;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.apache.commons.lang3.StringUtils;

import com.mirth.connect.util.ConnectionTestResponse;

public class ConnectorUtil {
    public static ConnectionTestResponse testConnection(String host, int port, int timeout) throws Exception {
        return testConnection(host, port, timeout, null, 0);
    }

    public static ConnectionTestResponse testConnection(String host, int port, int timeout, String localAddr) throws Exception {
        return testConnection(host, port, timeout, localAddr, 0);
    }

    public static ConnectionTestResponse testConnection(String host, int port, int timeout, String localAddr, int localPort) throws Exception {
        Socket socket = null;
        InetSocketAddress address = null;
        InetSocketAddress localAddress = null;

        try {
            address = new InetSocketAddress(host, port);

            if (StringUtils.isBlank(address.getAddress().getHostAddress()) || (address.getPort() < 0) || (address.getPort() > 65534)) {
                throw new Exception();
            }
        } catch (Exception e) {
            return new ConnectionTestResponse(ConnectionTestResponse.Type.FAILURE, "Invalid host or port.");
        }

        if (localAddr != null) {
            try {
                localAddress = new InetSocketAddress(localAddr, localPort);

                if (StringUtils.isBlank(localAddress.getAddress().getHostAddress()) || (localAddress.getPort() < 0) || (localAddress.getPort() > 65534)) {
                    throw new Exception();
                }
            } catch (Exception e) {
                return new ConnectionTestResponse(ConnectionTestResponse.Type.FAILURE, "Invalid local host or port.");
            }
        }

        try {
            socket = new Socket();

            if (localAddress != null) {
                try {
                    socket.bind(localAddress);
                } catch (Exception e) {
                    return new ConnectionTestResponse(ConnectionTestResponse.Type.FAILURE, "Could not bind to local address: " + localAddress.getAddress().getHostAddress() + ":" + localAddress.getPort());
                }
            }

            socket.connect(address, timeout);
            String connectionInfo = socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() + " -> " + address.getAddress().getHostAddress() + ":" + address.getPort();
            return new ConnectionTestResponse(ConnectionTestResponse.Type.SUCCESS, "Successfully connected to host: " + connectionInfo, connectionInfo);
        } catch (SocketTimeoutException ste) {
            return new ConnectionTestResponse(ConnectionTestResponse.Type.TIME_OUT, "Timed out connecting to host: " + address.getAddress().getHostAddress() + ":" + address.getPort());
        } catch (Exception e) {
            return new ConnectionTestResponse(ConnectionTestResponse.Type.FAILURE, "Could not connect to host: " + address.getAddress().getHostAddress() + ":" + address.getPort());
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
}
