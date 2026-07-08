// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.client.core;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.apache.http.conn.ssl.TrustStrategy;

import org.apache.commons.lang3.StringUtils;

/** A "trust all" strategy for localhost hosts. */
public class LocalhostTrustStrategy implements TrustStrategy, HostnameVerifier {

    private final boolean localhostConnection;

    public LocalhostTrustStrategy(String serverHost) {
        localhostConnection = isLocalhost(serverHost);
    }

    @Override
    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        return localhostConnection;
    }

    @Override
    public boolean verify(String host, SSLSession session) {
        return localhostConnection && isLocalhost(host);
    }

    private static boolean isLocalhost(String host) {
        return StringUtils.equalsAnyIgnoreCase(host, "localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    }
}