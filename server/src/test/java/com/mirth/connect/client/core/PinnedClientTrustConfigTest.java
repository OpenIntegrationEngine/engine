// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.client.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PinnedClientTrustConfigTest {

    private static final String THUMBPRINT = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    public void testParseDefaultValue() {
        PinnedClientTrustConfig config = PinnedClientTrustConfig.parse(null);

        assertTrue(config.isPkiTrusted());
        assertTrue(config.isLocalhostTrusted());
        assertFalse(config.isTrustAllCertificates());
        assertTrue(config.getPinnedThumbprints().isEmpty());
    }

    @Test
    public void testParseMixedValues() {
        PinnedClientTrustConfig config = PinnedClientTrustConfig.parse("pki, localhost, " + THUMBPRINT.toUpperCase());

        assertTrue(config.isPkiTrusted());
        assertTrue(config.isLocalhostTrusted());
        assertFalse(config.isTrustAllCertificates());
        assertTrue(config.getPinnedThumbprints().contains(THUMBPRINT));
    }

    @Test
    public void testParseTrustAll() {
        PinnedClientTrustConfig config = PinnedClientTrustConfig.parse("insecure_trust_all_certs");

        assertFalse(config.isPkiTrusted());
        assertFalse(config.isLocalhostTrusted());
        assertTrue(config.isTrustAllCertificates());
    }

    @Test
    public void testParseThumbprintDisablesPkiUnlessExplicitlyEnabled() {
        PinnedClientTrustConfig config = PinnedClientTrustConfig.parse(THUMBPRINT);

        assertFalse(config.isPkiTrusted());
        assertFalse(config.getPinnedThumbprints().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectInvalidToken() {
        PinnedClientTrustConfig.parse("nopki");
    }

    @Test
    public void testRecognizeLocalhostHosts() {
        LocalhostTrustStrategy strategy = new LocalhostTrustStrategy("localhost");

        assertTrue(strategy.verify("localhost", null));
        assertTrue(strategy.verify("127.0.0.1", null));
        assertTrue(strategy.verify("::1", null));
        assertFalse(strategy.verify("example.com", null));
    }
}