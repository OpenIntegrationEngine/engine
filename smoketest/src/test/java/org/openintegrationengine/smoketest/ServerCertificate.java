// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Reads the certificate a live server presents, so a test can pin it.
 *
 * <p>The stack's server generates its own self-signed certificate on first boot, so its
 * thumbprint is not knowable ahead of time and has to be discovered at runtime. This
 * recomputes the thumbprint from the DER encoding rather than reusing the client's own
 * matcher, which keeps it an independent oracle for the code under test.
 */
final class ServerCertificate {

    private ServerCertificate() {
    }

    /**
     * Connects to {@code baseUrl} trusting anything, and returns the SHA-256 thumbprint of
     * the leaf certificate the server presents as lowercase hex, the form
     * {@code PinnedClientTrustConfig} accepts.
     */
    static String discoverThumbprint(String baseUrl, int timeoutMillis) throws Exception {
        URI uri = new URI(baseUrl);
        int port = uri.getPort() == -1 ? 443 : uri.getPort();

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] { TRUST_ANYTHING }, null);

        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket()) {
            // The InetSocketAddress keeps the hostname, so SNI still works. A raw SSLSocket
            // does no endpoint identification, so the certificate's hostname is irrelevant here.
            socket.connect(new InetSocketAddress(uri.getHost(), port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            socket.startHandshake();

            X509Certificate leaf = (X509Certificate) socket.getSession().getPeerCertificates()[0];
            return thumbprintOf(leaf);
        }
    }

    /** SHA-256 over the certificate's DER encoding, lowercase hex. */
    private static String thumbprintOf(X509Certificate certificate) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        return HexFormat.of().formatHex(digest);
    }

    /**
     * Accepts every certificate: this reads what the server presents, it does not judge it.
     */
    private static final X509TrustManager TRUST_ANYTHING = new X509TrustManager() {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };
}
