// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.model.filters.elements;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.mirth.connect.donkey.model.message.ContentType;

public class ContentSearchElementTest {

    @Test
    public void testAccessors() {
        var code = 1;
        var searches = List.of("search1", "search2");

        var element = new ContentSearchElement(code, searches);

        assertEquals(code, element.getContentCode());
        assertEquals(searches, element.getSearches());

        element.setContentCode(2);
        assertEquals(2, element.getContentCode());

        var newSearches = List.of("new1");
        element.setSearches(newSearches);
        assertEquals(newSearches, element.getSearches());
    }

    @Test
    public void testToStringIntegration() {
        // contentCode 1 = RAW
        var element = new ContentSearchElement(ContentType.RAW.getContentTypeCode(), List.of("foo", "bar"));

        var stringVal = element.toString();

        var expected = "ContentSearchElement[contentCode=1(Raw), searches=[foo, bar]]";

        assertEquals(expected, stringVal);
    }
}
