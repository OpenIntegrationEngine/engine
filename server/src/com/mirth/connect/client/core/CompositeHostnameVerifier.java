// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.client.core;

import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/** A composite hostname verifier that delegates to multiple implementations. */
public class CompositeHostnameVerifier implements HostnameVerifier {

    private final List<HostnameVerifier> hostnameVerifiers;

    public CompositeHostnameVerifier(List<HostnameVerifier> hostnameVerifiers) {
        this.hostnameVerifiers = new ArrayList<HostnameVerifier>(hostnameVerifiers);
    }

    @Override
    public boolean verify(String host, SSLSession session) {
        for (HostnameVerifier hostnameVerifier : hostnameVerifiers) {
            if (hostnameVerifier.verify(host, session)) {
                return true;
            }
        }

        return false;
    }
}