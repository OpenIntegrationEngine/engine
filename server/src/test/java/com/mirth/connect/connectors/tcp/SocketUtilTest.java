// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>
package com.mirth.connect.connectors.tcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.Socket;

import org.junit.Assume;
import org.junit.Test;

import jdk.net.ExtendedSocketOptions;

public class SocketUtilTest {

    @Test
    public void setKeepAliveEnablesKeepAliveAndCapsIdle() throws Exception {
        assertNull("Test expects " + SocketUtil.KEEPALIVE_IDLE_PROPERTY + " to be unset", System.getProperty(SocketUtil.KEEPALIVE_IDLE_PROPERTY));

        try (Socket socket = new Socket()) {
            Assume.assumeTrue(socket.supportedOptions().contains(ExtendedSocketOptions.TCP_KEEPIDLE));
            int osIdle = socket.getOption(ExtendedSocketOptions.TCP_KEEPIDLE);

            SocketUtil.setKeepAlive(socket, true);

            assertTrue(socket.getKeepAlive());
            assertEquals(Math.min(osIdle, SocketUtil.DEFAULT_KEEPALIVE_IDLE), (int) socket.getOption(ExtendedSocketOptions.TCP_KEEPIDLE));
        }
    }

    @Test
    public void setKeepAliveDisablesKeepAliveAndLeavesIdle() throws Exception {
        try (Socket socket = new Socket()) {
            socket.setKeepAlive(true);
            Integer idle = socket.supportedOptions().contains(ExtendedSocketOptions.TCP_KEEPIDLE) ? socket.getOption(ExtendedSocketOptions.TCP_KEEPIDLE) : null;

            SocketUtil.setKeepAlive(socket, false);

            assertFalse(socket.getKeepAlive());
            if (idle != null) {
                assertEquals(idle, socket.getOption(ExtendedSocketOptions.TCP_KEEPIDLE));
            }
        }
    }
}
