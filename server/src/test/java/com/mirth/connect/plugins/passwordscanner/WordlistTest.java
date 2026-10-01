// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.plugins.passwordscanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class WordlistTest {

    @Test
    public void testParse() {
        assertEquals(Arrays.asList("admin", "password"), Wordlist.parse("admin,password").getPasswords());
    }

    @Test
    public void testParseTrimsUnescapedWhitespace() {
        assertEquals(Arrays.asList("admin", "password"), Wordlist.parse("  admin ,\tpassword\t").getPasswords());
    }

    @Test
    public void testParseKeepsInteriorWhitespace() {
        assertEquals(Collections.singletonList("correct horse"), Wordlist.parse("correct horse").getPasswords());
    }

    @Test
    public void testParseDropsEmptyEntries() {
        assertEquals(Arrays.asList("admin", "password"), Wordlist.parse(",admin,, ,password,").getPasswords());
    }

    @Test
    public void testParseEmptyValue() {
        assertTrue(Wordlist.parse("").isEmpty());
        assertTrue(Wordlist.parse("   ").isEmpty());
        assertTrue(Wordlist.parse(null).isEmpty());
    }

    @Test
    public void testParseEscapedSeparator() {
        assertEquals(Arrays.asList("a,b", "c"), Wordlist.parse("a\\,b,c").getPasswords());
    }

    @Test
    public void testParseEscapedEscape() {
        assertEquals(Arrays.asList("a\\b", "c"), Wordlist.parse("a\\\\b,c").getPasswords());
    }

    @Test
    public void testParseEscapedWhitespaceIsKept() {
        assertEquals(Collections.singletonList(" admin "), Wordlist.parse("\\ admin\\ ").getPasswords());
    }

    @Test
    public void testParseTrailingEscapeIsLiteral() {
        assertEquals(Collections.singletonList("admin\\"), Wordlist.parse("admin\\").getPasswords());
    }

    @Test
    public void testRoundTrip() {
        List<String> passwords = Arrays.asList("admin", "a,b", "a\\b", " padded ", "correct horse", "\\", ",");
        Wordlist wordlist = new Wordlist(passwords);

        assertEquals(passwords, Wordlist.parse(wordlist.toString()).getPasswords());
    }

    @Test
    public void testDefaultRoundTrips() {
        assertEquals(Wordlist.DEFAULT.getPasswords(), Wordlist.parse(Wordlist.DEFAULT.toString()).getPasswords());
        assertTrue(Wordlist.DEFAULT.getPasswords().contains("admin"));
    }
}
