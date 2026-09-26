// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.client.core;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.apache.http.conn.ssl.TrustStrategy;

/** A trust strategy that validates certificates based on pinned thumbprints, ignoring hostname verification. */
public class PinnedCertificateTrustStrategy implements TrustStrategy, HostnameVerifier {

    private final CertificateThumbprintMatcher certificateThumbprintMatcher;

    public PinnedCertificateTrustStrategy(Set<String> pinnedThumbprints) {
        certificateThumbprintMatcher = new CertificateThumbprintMatcher(pinnedThumbprints);
    }

    @Override
    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null) {
            return false;
        }

        return certificateThumbprintMatcher.matches(chain);
    }

    @Override
    public boolean verify(String host, SSLSession session) {
        return certificateThumbprintMatcher.matches(session);
    }
}
