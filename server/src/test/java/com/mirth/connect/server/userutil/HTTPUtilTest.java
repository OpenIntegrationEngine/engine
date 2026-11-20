// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Nico Piel <nico.piel@hotmail.de>

package com.mirth.connect.server.userutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Characterization tests for {@link HTTPUtil#parseHeaders(String)}, pinning the behaviour of the
 * original commons-httpclient 3 HttpParser based implementation.
 */
public class HTTPUtilTest {

    private static Map<String, String> map(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    public void testEmptyString() throws Exception {
        assertEquals(map(), HTTPUtil.parseHeaders(""));
    }

    @Test
    public void testCrlfLines() throws Exception {
        assertEquals(map("Content-Type", "text/plain", "Content-Length", "42"), HTTPUtil.parseHeaders("Content-Type: text/plain\r\nContent-Length: 42\r\n"));
    }

    @Test
    public void testLfLinesAndNoTrailingNewline() throws Exception {
        assertEquals(map("A", "1", "B", "2"), HTTPUtil.parseHeaders("A: 1\nB: 2"));
    }

    @Test
    public void testNameAndValueAreTrimmed() throws Exception {
        assertEquals(map("Name", "some value"), HTTPUtil.parseHeaders("Name \t:  \t some value \t \r\n"));
        assertEquals(map("X", "a"), HTTPUtil.parseHeaders("X:\u000ba\u0001\r\n"));
    }

    @Test
    public void testValueKeepsFurtherColons() throws Exception {
        assertEquals(map("Host", "example.com:8080"), HTTPUtil.parseHeaders("Host: example.com:8080\r\n"));
    }

    @Test
    public void testEmptyNameAndEmptyValue() throws Exception {
        assertEquals(map("", "v", "X", ""), HTTPUtil.parseHeaders(":v\r\nX:\r\n"));
    }

    @Test
    public void testFoldedContinuationLines() throws Exception {
        assertEquals(map("X", "a b c", "Y", "d"), HTTPUtil.parseHeaders("X: a\r\n   b  \r\n\tc\r\nY: d\r\n"));
    }

    @Test
    public void testContinuationOfEmptyValue() throws Exception {
        assertEquals(map("X", " b"), HTTPUtil.parseHeaders("X:\r\n b\r\n"));
    }

    @Test
    public void testLeadingContinuationLineIsIgnored() throws Exception {
        assertEquals(map("X", "a"), HTTPUtil.parseHeaders(" orphan\r\nX: a\r\n"));
    }

    @Test
    public void testDuplicateNamesLastWins() throws Exception {
        assertEquals(map("X", "second"), HTTPUtil.parseHeaders("X: first\r\nX: second\r\n"));
    }

    @Test
    public void testNamesAreCaseSensitive() throws Exception {
        assertEquals(map("Foo", "1", "foo", "2"), HTTPUtil.parseHeaders("Foo: 1\r\nfoo: 2\r\n"));
    }

    @Test
    public void testStopsAtBlankLine() throws Exception {
        assertEquals(map("A", "1"), HTTPUtil.parseHeaders("A: 1\r\n\r\nB: 2\r\nnot a header\r\n"));
        assertEquals(map("A", "1"), HTTPUtil.parseHeaders("A: 1\n\nB: 2\n"));
    }

    @Test
    public void testStopsAtWhitespaceOnlyLine() throws Exception {
        assertEquals(map("A", "1"), HTTPUtil.parseHeaders("A: 1\r\n \t \r\nB: 2\r\n"));
        assertEquals(map("A", "1"), HTTPUtil.parseHeaders("A: 1\r\n\r\r\nB: 2\r\n"));
    }

    @Test
    public void testLeadingBlankLineYieldsNothing() throws Exception {
        assertEquals(map(), HTTPUtil.parseHeaders("\r\nA: 1\r\n"));
    }

    @Test
    public void testCarriageReturnHandling() throws Exception {
        // Only a single CR directly before the LF is stripped; others are trimmed or kept
        assertEquals(map("A", "x\ry", "B", "w", "C", "z"), HTTPUtil.parseHeaders("A: x\ry\r\nB: w\r\r\nC: z\r"));
    }

    @Test
    public void testHttpStatusLineIsRejected() {
        assertParseFailure("HTTP/1.1 200 OK\r\nA: 1\r\n", "HTTP/1.1 200 OK");
    }

    @Test
    public void testLineWithoutColonIsRejected() {
        assertParseFailure("A: 1\r\nbogus line\r\n", "bogus line");
    }

    @Test
    public void testNonAsciiValue() throws Exception {
        // The string is encoded with the platform charset and decoded as UTF-8
        assumeTrue(Charset.defaultCharset().equals(StandardCharsets.UTF_8));
        assertEquals(map("X-Name", "M\u00fcller \u20ac"), HTTPUtil.parseHeaders("X-Name: M\u00fcller \u20ac\r\n"));
    }

    @Test(expected = NullPointerException.class)
    public void testNullInput() throws Exception {
        HTTPUtil.parseHeaders(null);
    }

    private static void assertParseFailure(String input, String badLine) {
        try {
            HTTPUtil.parseHeaders(input);
            fail("Expected an IOException");
        } catch (Exception e) {
            assertTrue(e.getClass().getName(), e instanceof IOException);
            assertEquals("Unable to parse header: " + badLine, e.getMessage());
        }
    }
}
