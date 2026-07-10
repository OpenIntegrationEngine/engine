// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Mitch Gaffigan

package com.mirth.connect.client.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CommandLineOptionsTest {

    @Test
    public void testParseSslForm() {
        String[] args = new String[] { "https://example:8443", "1.0", "-ssl", "TLSv1.2,TLSv1.3", "TLS_RSA_WITH_AES_128_GCM_SHA256", "alice", "secret" };
        CommandLineOptions opts = new CommandLineOptions(args);

        assertEquals("https://example:8443", opts.getServer());
        assertEquals("1.0", opts.getVersion());
        assertEquals("alice", opts.getUsername());
        assertEquals("secret", opts.getPassword());
        assertEquals("TLSv1.2,TLSv1.3", opts.getProtocols());
        assertEquals("TLS_RSA_WITH_AES_128_GCM_SHA256", opts.getCipherSuites());
    }

    @Test
    public void testParseUsernameFormWithSsl() {
        String[] args = new String[] { "https://example:8443", "1.0", "bob", "pw", "-ssl", "TLSv1.2", "CIPHER" };
        CommandLineOptions opts = new CommandLineOptions(args);

        assertEquals("https://example:8443", opts.getServer());
        assertEquals("1.0", opts.getVersion());
        assertEquals("bob", opts.getUsername());
        assertEquals("pw", opts.getPassword());
        assertEquals("TLSv1.2", opts.getProtocols());
        assertEquals("CIPHER", opts.getCipherSuites());
    }

    @Test
    public void testNullArgsUsesDefaults() {
        CommandLineOptions opts = new CommandLineOptions((String[]) null);

        assertEquals("https://localhost:8443", opts.getServer());
        assertEquals("", opts.getVersion());
        assertEquals("", opts.getUsername());
        assertEquals("", opts.getPassword());
        assertEquals("", opts.getProtocols());
        assertEquals("", opts.getCipherSuites());
    }

    @Test
    public void testNormal() {
        String[] args = new String[] { "https://example:8443", "1.0" };
        CommandLineOptions opts = new CommandLineOptions(args);

        assertEquals("https://example:8443", opts.getServer());
        assertEquals("1.0", opts.getVersion());
        assertEquals("", opts.getUsername());
        assertEquals("", opts.getPassword());
        assertEquals("", opts.getProtocols());
        assertEquals("", opts.getCipherSuites());
    }
}
