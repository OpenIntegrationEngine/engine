/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.tcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.util.TcpUtil;

import jdk.net.ExtendedSocketOptions;

public class SocketUtil {

    private static final Logger logger = LogManager.getLogger(SocketUtil.class);

    static final String KEEPALIVE_IDLE_PROPERTY = "oie.tcp.keepalive.idle";
    static final int DEFAULT_KEEPALIVE_IDLE = 120;
    private static final Optional<Integer> KEEPALIVE_IDLE = initKeepAliveIdle();
    private static final AtomicBoolean keepAliveIdleFailed = new AtomicBoolean();

    public static Socket createSocket(TcpConfiguration configuration) throws UnknownHostException, IOException {
        return createSocket(configuration, null);
    }

    public static Socket createSocket(TcpConfiguration configuration, String localAddr) throws UnknownHostException, IOException {
        return createSocket(configuration, localAddr, 0);
    }

    /**
     * Creates a socket and connects it to the specified remote host on the specified remote port.
     * 
     * @param host
     *            - The remote host to connect on.
     * @param port
     *            - The remote port to connect on.
     * @param localAddr
     *            - The local address to bind the socket to.
     * @param localPort
     *            - The local port to bind the socket to.
     * @param timeout
     *            - The socket timeout to use when connecting.
     * @return The bound and connected Socket.
     * @throws UnknownHostException
     *             if the IP address of the host could not be determined
     * @throws IOException
     *             if an I/O error occurs when creating the socket
     */
    public static Socket createSocket(TcpConfiguration configuration, String localAddr, int localPort) throws UnknownHostException, IOException {
        Socket socket = configuration.createSocket();

        if (StringUtils.isNotEmpty(localAddr)) {
            InetAddress localAddress = InetAddress.getByName(TcpUtil.getFixedHost(localAddr));
            socket.bind(new InetSocketAddress(localAddress, localPort));
        }

        return socket;
    }

    public static Socket createResponseSocket(TcpConfiguration configuration) throws UnknownHostException, IOException {
        return configuration.createResponseSocket();
    }

    public static void connectSocket(Socket socket, String host, int port, int timeout) throws UnknownHostException, IOException {
        socket.connect(new InetSocketAddress(InetAddress.getByName(TcpUtil.getFixedHost(host)), port), timeout);
    }

    /**
     * Loads this class, which decides and logs the keepalive idle time. Called when the TCP plugin
     * starts so the decision, and any error in the oie.tcp.keepalive.idle system property, is
     * logged at server startup instead of at the first TCP connection.
     */
    public static void init() {}

    /**
     * Enables or disables TCP keepalive on a socket. When enabling, also lowers the idle time before
     * the first keepalive probe to the oie.tcp.keepalive.idle system property (in seconds) if the
     * OS default was higher when this class was loaded and the socket supports it. A value of 0 or
     * less leaves the OS default.
     */
    public static void setKeepAlive(Socket socket, boolean keepAlive) throws SocketException {
        socket.setKeepAlive(keepAlive);

        if (keepAlive && KEEPALIVE_IDLE.isPresent() && socket.supportedOptions().contains(ExtendedSocketOptions.TCP_KEEPIDLE)) {
            Integer idle = KEEPALIVE_IDLE.get();
            try {
                socket.setOption(ExtendedSocketOptions.TCP_KEEPIDLE, idle);
            } catch (IOException | IllegalArgumentException e) {
                if (keepAliveIdleFailed.compareAndSet(false, true)) {
                    logger.error("Failed to set TCP_KEEPIDLE to {} seconds; using the OS default. Further failures will be logged at DEBUG.", idle, e);
                } else {
                    logger.debug("Failed to set TCP_KEEPIDLE to {} seconds.", idle, e);
                }
            }
        }
    }

    private static Optional<Integer> initKeepAliveIdle() {
        int idle = DEFAULT_KEEPALIVE_IDLE;
        String value = System.getProperty(KEEPALIVE_IDLE_PROPERTY);
        if (value != null) {
            try {
                idle = Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                logger.error("Invalid value '{}' for {}; using {} seconds.", value, KEEPALIVE_IDLE_PROPERTY, DEFAULT_KEEPALIVE_IDLE);
            }
        }

        if (idle <= 0) {
            logger.info("TCP connectors use the OS keepalive idle time; {} is {}.", KEEPALIVE_IDLE_PROPERTY, idle);
            return Optional.empty();
        }

        try (Socket socket = new Socket()) {
            if (!socket.supportedOptions().contains(ExtendedSocketOptions.TCP_KEEPIDLE)) {
                logger.info("TCP connectors use the OS keepalive idle time; TCP_KEEPIDLE is not supported.");
                return Optional.empty();
            }

            int osIdle = socket.getOption(ExtendedSocketOptions.TCP_KEEPIDLE);
            if (osIdle <= idle) {
                logger.info("TCP connectors use the OS keepalive idle time; its current value of {} seconds is not higher than {} ({} seconds). Later changes to the OS value still apply, but {} is not compared again until the server restarts.", osIdle, KEEPALIVE_IDLE_PROPERTY, idle, KEEPALIVE_IDLE_PROPERTY);
                return Optional.empty();
            }

            logger.info("TCP connectors use a keepalive idle time of {} seconds instead of the current OS value of {} seconds. Later changes to the OS value are ignored until the server restarts.", idle, osIdle);
        } catch (IOException e) {
            logger.info("TCP connectors use a keepalive idle time of {} seconds; the current OS value could not be read. Later changes to the OS value are ignored until the server restarts.", idle);
        }

        return Optional.of(idle);
    }

    public static void closeSocket(Socket socket) throws IOException {
        if (socket != null) {
            /*
             * MIRTH-2984: The shutdownInput() and shutdownOutput() methods are no longer being
             * called. On Windows this causes a RST packet to be sent to the remote side if there
             * are still bytes available in the socket's input stream.
             */
            socket.close();
        }
    }

    public static String getInetAddress(Socket socket) {
        String inetAddress = socket == null || socket.getInetAddress() == null ? "" : socket.getInetAddress().toString() + ":" + socket.getPort();

        if (inetAddress.startsWith("/")) {
            inetAddress = inetAddress.substring(1);
        }

        return inetAddress;
    }

    public static String getLocalAddress(Socket socket) {
        String localAddress = socket == null || socket.getLocalAddress() == null ? "" : socket.getLocalAddress().toString() + ":" + socket.getLocalPort();

        // If addresses begin with a slash "/", remove it.
        if (localAddress.startsWith("/")) {
            localAddress = localAddress.substring(1);
        }

        return localAddress;
    }
}
