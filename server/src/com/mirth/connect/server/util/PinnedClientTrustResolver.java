// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.server.util;

import java.util.concurrent.Callable;
import java.util.StringJoiner;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;

/** Utility class for interpreting the "administrator.pinnedclienttrust" property */
public class PinnedClientTrustResolver {
    private static final String DEFAULT_VALUE = "pki,webserver";
    private static final String PROPERTY_NAME = "administrator.pinnedclienttrust";

    public String resolve(PropertiesConfiguration mirthProperties, Callable<String> getServerCert) throws Exception {
        String pinnedClientTrust = StringUtils.defaultIfBlank(
            StringUtils.trimToNull(mirthProperties.getString(PROPERTY_NAME)), DEFAULT_VALUE);
            
        var joiner = new StringJoiner(",");
        for (String token : pinnedClientTrust.split(",")) {
            String cleaned = token.strip();
            if (cleaned.isEmpty()) continue;

            if (cleaned.equalsIgnoreCase("webserver")) {
                joiner.add(getServerCert.call());
            } else {
                joiner.add(cleaned);
            }
        }
        return joiner.toString();
    }
}
