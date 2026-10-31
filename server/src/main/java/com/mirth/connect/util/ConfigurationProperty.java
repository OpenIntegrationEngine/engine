/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class ConfigurationProperty implements Serializable {

    private String value;
    private String comment;

    public ConfigurationProperty(String value, String comment) {
        this.value = value;
        this.comment = comment;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Returns the version of a configuration map entry, for use as the expected version when
     * updating that entry. The version depends only on the entry's contents: it is the lowercase
     * hex SHA-256 of the UTF-8 bytes of the value, a unit separator (U+001F), and the comment. A null value
     * and a null or blank comment are treated as empty strings. A missing entry has the empty
     * string as its version.
     * 
     * The version is calculated from what the server currently holds, so a client can calculate it
     * from the entries returned by the server. Saving the map to a file can change some values and
     * comments when the server next starts (for example by trimming whitespace), so a version
     * calculated before a restart may no longer match.
     * 
     * @param property
     *            The entry, or null if there is no entry for the key.
     * @return The entry's version.
     */
    public static String versionOf(ConfigurationProperty property) {
        if (property == null) {
            return "";
        }

        String value = property.getValue() == null ? "" : property.getValue();
        String comment = property.getComment() == null || property.getComment().isBlank() ? "" : property.getComment();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value + '\u001F' + comment).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to support SHA-256
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getClass().getName()).append('[');
        builder.append("value=").append(value).append(", ");
        builder.append("comment=").append(comment).append(']');
        return builder.toString();
    }
}
