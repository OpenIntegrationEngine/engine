// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.client.core;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.conn.ssl.TrustStrategy;

/** A composite trust strategy that delegates to multiple implementations. */
public class CompositeTrustStrategy implements TrustStrategy {

    private final List<TrustStrategy> trustStrategies;

    public CompositeTrustStrategy(List<TrustStrategy> trustStrategies) {
        this.trustStrategies = new ArrayList<TrustStrategy>(trustStrategies);
    }

    @Override
    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        for (TrustStrategy trustStrategy : trustStrategies) {
            if (trustStrategy.isTrusted(chain, authType)) {
                return true;
            }
        }

        return false;
    }
}