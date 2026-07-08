// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.client.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.junit.Test;

public class PinnedClientTrustSslContextFactoryTest {

    private static final byte[] CERT_BYTES = new byte[] { 97, 98, 99 };
    private static final String THUMBPRINT = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private final PinnedClientTrustSslContextFactory factory = new PinnedClientTrustSslContextFactory();

    @Test
    public void testHostnameVerifierAllowsPinnedCertificateWithoutHostnameMatch() throws Exception {
        HostnameVerifier verifier = factory.createHostnameVerifier(PinnedClientTrustConfig.parse(THUMBPRINT), "example.com");

        assertTrue(verifier.verify("different-host", mockPinnedSession()));
    }

    @Test
    public void testHostnameVerifierAllowsLocalhostConnections() {
        HostnameVerifier verifier = factory.createHostnameVerifier(PinnedClientTrustConfig.parse("localhost"), "localhost");

        assertTrue(verifier.verify("localhost", mock(SSLSession.class)));
    }

    @Test
    public void testHostnameVerifierRejectsNonLocalhostWhenOnlyLocalhostIsAllowed() {
        HostnameVerifier verifier = factory.createHostnameVerifier(PinnedClientTrustConfig.parse("localhost"), "example.com");

        assertFalse(verifier.verify("example.com", mock(SSLSession.class)));
    }

    @Test
    public void testHostnameVerifierAllowsTrustAll() {
        HostnameVerifier verifier = factory.createHostnameVerifier(PinnedClientTrustConfig.parse("insecure_trust_all_certs"), "example.com");

        assertTrue(verifier.verify("example.com", mock(SSLSession.class)));
    }

    @Test
    public void testTrustStrategyAllowsPinnedCertificate() throws Exception {
        PinnedCertificateTrustStrategy strategy = new PinnedCertificateTrustStrategy(PinnedClientTrustConfig.parse(THUMBPRINT).getPinnedThumbprints());

        assertTrue(strategy.isTrusted(new X509Certificate[] { mockCertificate() }, "RSA"));
    }

    @Test
    public void testTrustStrategyAllowsLocalhostConnectionWithoutCertificateChecks() throws Exception {
        LocalhostTrustStrategy strategy = new LocalhostTrustStrategy("127.0.0.1");

        assertTrue(strategy.isTrusted(null, "RSA"));
    }

    @Test
    public void testTrustStrategyDefersWhenCertificateIsNotPinned() throws Exception {
        PinnedCertificateTrustStrategy strategy = new PinnedCertificateTrustStrategy(PinnedClientTrustConfig.parse(THUMBPRINT).getPinnedThumbprints());

        assertFalse(strategy.isTrusted(new X509Certificate[] { mockDifferentCertificate() }, "RSA"));
    }

    private SSLSession mockPinnedSession() throws Exception {
        SSLSession session = mock(SSLSession.class);
        Certificate certificate = mockCertificate();
        when(session.getPeerCertificates()).thenReturn(new Certificate[] { certificate });
        return session;
    }

    private X509Certificate mockCertificate() throws Exception {
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getEncoded()).thenReturn(CERT_BYTES);
        return certificate;
    }

    private X509Certificate mockDifferentCertificate() throws Exception {
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getEncoded()).thenReturn(new byte[] { 100, 101, 102 });
        return certificate;
    }
}