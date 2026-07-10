// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Test;

public class SearchElementToStringStyleTest {

    @Test
    public void testStringQuoting() {
        var result = new ToStringBuilder(new Object(), SearchElementToStringStyle.instance())
                .append("field", "value")
                .toString();

        assertEquals("Object[field=value]", result);
    }

    @Test
    public void testNonStringNoQuotes() {
        var val = 123;
        var result = new ToStringBuilder(new Object(), SearchElementToStringStyle.instance())
                .append("number", val)
                .toString();

        assertEquals("Object[number=123]", result);
    }

    @Test
    public void testNullValue() {
        var result = new ToStringBuilder(new Object(), SearchElementToStringStyle.instance())
                .append("nullField", (Object) null)
                .toString();

        assertEquals("Object[nullField=<null>]", result);
    }
}
