// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.client.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/** Parses trust list for the client */
public class PinnedClientTrustConfig {

    private static final String DEFAULT_VALUE = "pki,localhost";
    private static final String LOCALHOST_VALUE = "localhost";
    private static final String INSECURE_TRUST_ALL_CERTS_VALUE = "insecure_trust_all_certs";
    private static final String PKI_VALUE = "pki";

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final boolean pkiAllowed;
    private final boolean localhostAllowed;
    private final boolean trustAllCertificates;
    private final Set<String> pinnedThumbprints;

    private PinnedClientTrustConfig(boolean pkiAllowed, boolean localhostAllowed, boolean trustAllCertificates, Set<String> pinnedThumbprints) {
        this.pkiAllowed = pkiAllowed;
        this.localhostAllowed = localhostAllowed;
        this.trustAllCertificates = trustAllCertificates;
        this.pinnedThumbprints = pinnedThumbprints;
    }

    /* Create from the argument string or default if null/blank. */
    public static PinnedClientTrustConfig parse(String pinnedClientTrust) {
        String trimmedValue = StringUtils.defaultIfBlank(pinnedClientTrust, DEFAULT_VALUE);

        String[] tokens = StringUtils.split(trimmedValue, ',');
        if (tokens == null || tokens.length == 0) {
            throw new IllegalArgumentException("PinnedClientTrust is invalid: " + pinnedClientTrust);
        }

        boolean pkiAllowed = false;
        boolean localhostAllowed = false;
        boolean trustAllCertificates = false;

        Set<String> pinnedThumbprints = new HashSet<String>();
        for (String token : tokens) {
            String cleanedToken = StringUtils.trimToEmpty(token);
            if (StringUtils.isBlank(cleanedToken)) {
                continue;
            }

            if (StringUtils.equalsIgnoreCase(cleanedToken, PKI_VALUE)) {
                pkiAllowed = true;
            } else if (StringUtils.equalsIgnoreCase(cleanedToken, LOCALHOST_VALUE)) {
                localhostAllowed = true;
            } else if (StringUtils.equalsIgnoreCase(cleanedToken, INSECURE_TRUST_ALL_CERTS_VALUE)) {
                trustAllCertificates = true;
            } else {
                String thumbprint = CertificateThumbprintMatcher.normalize(cleanedToken);
                if (!SHA_256_PATTERN.matcher(thumbprint).matches()) {
                    throw new IllegalArgumentException("PinnedClientTrust contains an invalid token: " + token);
                }
                pinnedThumbprints.add(thumbprint);
            }
        }

        if (!pkiAllowed && !localhostAllowed && !trustAllCertificates && pinnedThumbprints.isEmpty()) {
            throw new IllegalArgumentException("PinnedClientTrust must enable at least one trust mode.");
        }

        return new PinnedClientTrustConfig(pkiAllowed, localhostAllowed, trustAllCertificates, Collections.unmodifiableSet(pinnedThumbprints));
    }

    /** Returns the set of trusted certificate thumbprints. */
    public Set<String> getPinnedThumbprints() {
        return pinnedThumbprints;
    }

    /** Determines whether the public-key infrastructure (PKI) is trusted. */
    public boolean isPkiTrusted() {
        return pkiAllowed;
    }

    /** Determines whether localhost should be trusted without validation. */
    public boolean isLocalhostTrusted() {
        return localhostAllowed;
    }

    /** Determines whether all certificates should be trusted without validation. */
    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }
}
