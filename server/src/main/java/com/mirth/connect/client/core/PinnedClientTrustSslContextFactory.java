// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.client.core;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.ssl.SSLContexts;

/** Entrypoint for interpreting the trust configuration for the client */
public class PinnedClientTrustSslContextFactory {

    /** Creates an SSL context based on the pinned client trust configuration. */
    public SSLContext createSslContext(PinnedClientTrustConfig pinnedClientTrustConfig, String serverHost) {
        try {
            if (pinnedClientTrustConfig.isTrustAllCertificates()) {
                return SSLContexts.custom().loadTrustMaterial(null, new TrustAllStrategy()).build();
            }

            KeyStore trustStore = null;
            if (!pinnedClientTrustConfig.isPkiTrusted()) {
                trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                trustStore.load(null, null);
            }

            var trustStrategy = createTrustStrategy(pinnedClientTrustConfig, serverHost);
            return SSLContexts.custom().loadTrustMaterial(trustStore, trustStrategy).build();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build SSL context.", e);
        }
    }

    /** Creates a hostname verifier based on the pinned client trust configuration. */
    public HostnameVerifier createHostnameVerifier(PinnedClientTrustConfig pinnedClientTrustConfig, String serverHost) {
        if (pinnedClientTrustConfig.isTrustAllCertificates()) {
            return NoopHostnameVerifier.INSTANCE;
        }

        List<HostnameVerifier> hostnameVerifiers = new ArrayList<HostnameVerifier>();
        if (pinnedClientTrustConfig.isLocalhostTrusted()) {
            hostnameVerifiers.add(new LocalhostTrustStrategy(serverHost));
        }
        var pinned = pinnedClientTrustConfig.getPinnedThumbprints();
        if (!pinned.isEmpty()) {
            hostnameVerifiers.add(new PinnedCertificateTrustStrategy(pinned));
        }
        if (pinnedClientTrustConfig.isPkiTrusted()) {
            hostnameVerifiers.add(new DefaultHostnameVerifier());
        }

        return new CompositeHostnameVerifier(hostnameVerifiers);
    }

    private TrustStrategy createTrustStrategy(PinnedClientTrustConfig pinnedClientTrustConfig, String serverHost) {
        List<TrustStrategy> trustStrategies = new ArrayList<TrustStrategy>();
        if (pinnedClientTrustConfig.isLocalhostTrusted()) {
            trustStrategies.add(new LocalhostTrustStrategy(serverHost));
        }
        var pinned = pinnedClientTrustConfig.getPinnedThumbprints();
        if (!pinned.isEmpty()) {
            trustStrategies.add(new PinnedCertificateTrustStrategy(pinned));
        }

        return new CompositeTrustStrategy(trustStrategies);
    }
}
