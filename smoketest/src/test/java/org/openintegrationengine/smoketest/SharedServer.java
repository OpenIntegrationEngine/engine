// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

/**
 * The one server connection shared by every generated test class in a JVM. The JUnit
 * launcher runs one configuration per JVM and then exits, so a single login is reused
 * for the whole run and closed by a shutdown hook rather than per test class.
 */
final class SharedServer {

    private static OieServer instance;

    private SharedServer() {
    }

    static synchronized OieServer get() {
        if (instance == null) {
            OieServer server = OieServer.connect();
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "oie-smoketest-logout"));
            instance = server;
        }
        return instance;
    }
}
