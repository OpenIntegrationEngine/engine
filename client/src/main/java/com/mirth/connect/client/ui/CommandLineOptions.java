// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation

package com.mirth.connect.client.ui;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import org.apache.commons.lang3.StringUtils;

/**
 * Immutable holder for command line options used by the Mirth client.
 */
public class CommandLineOptions {
    private final String server;
    private final String version;
    private final String username;
    private final String password;
    private final String protocols;
    private final String cipherSuites;
    private final String pinnedClientTrust;

    /**
     * Parse command line arguments for Mirth client.
     */
    public CommandLineOptions(String[] args) {
        if (args == null) {
            args = new String[0];
        }

        String server = "https://localhost:8443";
        String version = "";
        String username = "";
        String password = "";
        String protocols = "";
        String cipherSuites = "";
        String pinnedClientTrust = "";

        Deque<String> remaining = new ArrayDeque<String>(Arrays.asList(args));
        int idx = 0;
        while (true) {
            String arg = remaining.pollFirst();
            if (arg == null) {
                break;
            }

            if (StringUtils.equalsIgnoreCase(arg, "-ssl")) {
                protocols = StringUtils.defaultString(remaining.pollFirst());
                cipherSuites = StringUtils.defaultString(remaining.pollFirst());
            } else if (StringUtils.equalsIgnoreCase(arg, "-trust")) {
                pinnedClientTrust = StringUtils.defaultString(remaining.pollFirst());
            } else {
                switch (idx) {
                    case 0 -> server = arg;
                    case 1 -> version = arg;
                    case 2 -> username = arg;
                    case 3 -> password = arg;
                    default -> {} // Explicitly ignore extra arguments
                }
                idx++;
            }
        }

        this.server = server;
        this.version = version;
        this.username = username;
        this.password = password;
        this.protocols = StringUtils.defaultString(protocols);
        this.cipherSuites = StringUtils.defaultString(cipherSuites);
        this.pinnedClientTrust = StringUtils.defaultString(pinnedClientTrust);
    }

    public String getServer() {
        return server;
    }

    public String getVersion() {
        return version;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getProtocols() {
        return protocols;
    }

    public String getCipherSuites() {
        return cipherSuites;
    }

    public String getPinnedClientTrust() {
        return pinnedClientTrust;
    }
}
