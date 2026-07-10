// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation

package com.mirth.connect.client.ui;

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

    /**
     * Parse command line arguments for Mirth client.
     */
    public CommandLineOptions(String[] args) {
        String server = "https://localhost:8443";
        String version = "";
        String username = "";
        String password = "";
        String protocols = "";
        String cipherSuites = "";

        if (args == null) {
            args = new String[0];
        }

        if (args.length > 0) {
            server = args[0];
        }
        if (args.length > 1) {
            version = args[1];
        }
        if (args.length > 2) {
            if (StringUtils.equalsIgnoreCase(args[2], "-ssl")) {
                // <server> <version> -ssl [<protocols> [<ciphersuites> [<username> [<password>]]]]
                if (args.length > 3) {
                    protocols = args[3];
                }
                if (args.length > 4) {
                    cipherSuites = args[4];
                }
                if (args.length > 5) {
                    username = args[5];
                }
                if (args.length > 6) {
                    password = args[6];
                }
            } else {
                // <server> <version> <username> [<password> [-ssl [<protocols> [<ciphersuites>]]]]
                username = args[2];
                if (args.length > 3) {
                    password = args[3];
                }
                if (args.length > 4 && StringUtils.equalsIgnoreCase(args[4], "-ssl")) {
                    if (args.length > 5) {
                        protocols = args[5];
                    }
                    if (args.length > 6) {
                        cipherSuites = args[6];
                    }
                }
            }
        }

        this.server = server;
        this.version = version;
        this.username = username;
        this.password = password;
        this.protocols = protocols;
        this.cipherSuites = cipherSuites;
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
}
