// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Test;

import com.mirth.connect.donkey.model.message.ContentType;

public class ContentSearchElementToStringStyleTest {

    @Test
    public void testContentCodeFormatting() {
        // Test standard known codes
        assertContentCodeFormat(ContentType.RAW.getContentTypeCode(), "1(Raw)");
        assertContentCodeFormat(ContentType.PROCESSED_RAW.getContentTypeCode(), "2(Processed Raw)");
        assertContentCodeFormat(ContentType.TRANSFORMED.getContentTypeCode(), "3(Transformed)");
        assertContentCodeFormat(ContentType.PROCESSING_ERROR.getContentTypeCode(), "12(Processing Error)");
    }

    @Test
    public void testUnknownContentCode() {
        // Test an integer that doesn't map to a ContentType
        var unknownCode = 999;

        // Cast to Object to ensure we hit the appendDetail(StringBuffer, String, Object) method
        var result = new ToStringBuilder(new Object(), ContentSearchElementToStringStyle.instance())
                .append("contentCode", (Object) unknownCode)
                .toString();

        assertEquals("Object[contentCode=999(UNKNOWN)]", result);
    }

    @Test
    public void testOtherIntegerFields() {
        // Ensure that integer fields NOT named "contentCode" are treated normally
        var result = new ToStringBuilder(new Object(), ContentSearchElementToStringStyle.instance())
                .append("someOtherId", (Object) 1)
                .toString();

        assertEquals("Object[someOtherId=1]", result);
    }

    @Test
    public void testContentCodeNonInteger() {
        // Covers the "else" branch in appendDetail where fieldname is "contentCode"
        // but value is NOT an instanceof Integer.
        var result = new ToStringBuilder(new Object(), ContentSearchElementToStringStyle.instance())
                .append("contentCode", "NotAnInteger")
                .toString();

        assertEquals("Object[contentCode=NotAnInteger]", result);
    }

    @Test
    public void testCollectionFormatting() {
        var list = List.of("A", "B");

        var result = new ToStringBuilder(new Object(), ContentSearchElementToStringStyle.instance())
                .append("list", list)
                .toString();

        assertEquals("Object[list=[A, B]]", result);
    }

    private void assertContentCodeFormat(int code, String expectedValue) {
        // Cast to Object to ensure we hit the appendDetail(StringBuffer, String, Object) method
        var result = new ToStringBuilder(new Object(), ContentSearchElementToStringStyle.instance())
                .append("contentCode", (Object) code)
                .toString();

        assertEquals("Object[contentCode=" + expectedValue + "]", result);
    }
}
