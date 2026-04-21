// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.dcm5;

/**
 * Shared TLS utilities for dcm5 sender and receiver.
 */
final class Dcm5TlsUtil {

    private Dcm5TlsUtil() {}

    /**
     * Infers keystore/truststore type from a URL's file extension.
     * Returns "PKCS12" for .p12/.pfx files, "JKS" otherwise.
     */
    static String inferStoreType(String url) {
        if (url != null) {
            String lower = url.toLowerCase();
            if (lower.endsWith(".p12") || lower.endsWith(".pfx")) {
                return "PKCS12";
            }
        }
        return "JKS";
    }
}
