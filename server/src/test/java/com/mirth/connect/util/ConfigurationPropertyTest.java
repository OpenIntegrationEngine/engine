// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class ConfigurationPropertyTest {

    // Expected values calculated with: printf '%s\037%s' '<value>' '<comment>' | sha256sum

    @Test
    public void versionOf_ValueAndComment() {
        assertEquals("c1dcc868b7a3de969e0d671b3fce131d00d46d3da2a79805d91774ccfabaa8de", ConfigurationProperty.versionOf(new ConfigurationProperty("1", "comment")));
    }

    @Test
    public void versionOf_NonAscii() {
        assertEquals("b736f6189175d479edd12794d68abfdc2832ca4c2e3d5c87cc9e9ebe5c08a131", ConfigurationProperty.versionOf(new ConfigurationProperty("é", "✓")));
    }

    @Test
    public void versionOf_NullAndBlankCommentsAreEmpty() {
        String expected = "2b8d9cc6944cb101828f20db1b3c405182192eaf2800cf6687ae528349ec1d73";
        assertEquals(expected, ConfigurationProperty.versionOf(new ConfigurationProperty("1", null)));
        assertEquals(expected, ConfigurationProperty.versionOf(new ConfigurationProperty("1", "")));
        assertEquals(expected, ConfigurationProperty.versionOf(new ConfigurationProperty("1", " \n")));
    }

    @Test
    public void versionOf_NullValueIsEmpty() {
        String expected = "ffe679bb831c95b67dc17819c63c5090d221aac6f4c7bf530f594ab43d21fa1e";
        assertEquals(expected, ConfigurationProperty.versionOf(new ConfigurationProperty(null, null)));
        assertEquals(expected, ConfigurationProperty.versionOf(new ConfigurationProperty("", null)));
    }

    @Test
    public void versionOf_MissingEntryIsEmptyString() {
        assertEquals("", ConfigurationProperty.versionOf(null));
    }

    @Test
    public void versionOf_ValueAndCommentAreSeparated() {
        assertNotEquals(ConfigurationProperty.versionOf(new ConfigurationProperty("ab", null)), ConfigurationProperty.versionOf(new ConfigurationProperty("a", "b")));
    }
}
