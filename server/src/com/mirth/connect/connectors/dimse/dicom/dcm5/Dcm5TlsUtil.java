/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

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
