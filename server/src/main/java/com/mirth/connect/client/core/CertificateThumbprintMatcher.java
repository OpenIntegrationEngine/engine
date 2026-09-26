// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.client.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.apache.commons.lang3.StringUtils;

/** A collection of trusted certificate thumbprints. */
final class CertificateThumbprintMatcher {

    private final Set<String> pinnedThumbprints;

    CertificateThumbprintMatcher(Set<String> pinnedThumbprints) {
        this.pinnedThumbprints = pinnedThumbprints;
    }

    boolean matches(SSLSession session) {
        try {
            return matches(session.getPeerCertificates());
        } catch (SSLPeerUnverifiedException e) {
            return false;
        }
    }

    boolean matches(Certificate[] certificates) {
        if (pinnedThumbprints.isEmpty() || certificates == null || certificates.length == 0) {
            return false;
        }

        // Leaf only. A match here skips path validation, so nothing proves the rest of
        // the chain signed the leaf, and the peer chooses what it sends: matching any
        // other element would let an attacker append the (public) pinned certificate.
        if (!(certificates[0] instanceof X509Certificate)) {
            return false;
        }

        try {
            return pinnedThumbprints.contains(getThumbprint((X509Certificate) certificates[0]));
        } catch (CertificateException e) {
            return false;
        }
    }

    static String normalize(String thumbprint) {
        return StringUtils.lowerCase(StringUtils.deleteWhitespace(StringUtils.trimToEmpty(thumbprint)), Locale.ROOT);
    }

    private String getThumbprint(X509Certificate certificate) throws CertificateException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(certificate.getEncoded()));
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new CertificateException("Unable to calculate certificate thumbprint.", e);
        }
    }
}