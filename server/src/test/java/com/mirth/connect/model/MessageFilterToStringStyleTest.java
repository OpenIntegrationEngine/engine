// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Test;

public class MessageFilterToStringStyleTest {

    @Test
    public void testDateFormatting() {
        var cal = GregorianCalendar.from(ZonedDateTime.of(2023, 10, 25, 14, 30, 0, 0, ZoneId.of("America/New_York")));

        var result = new ToStringBuilder(new Object(), MessageFilterToStringStyle.instance())
                .append("date", cal)
                .toString();

        // Format: uuuu-MM-dd HH:mmXXX'['VV']'
        var expected = "Object[" + System.lineSeparator() +
                "  date=2023-10-25 14:30-04:00[America/New_York]" + System.lineSeparator() +
                "]";
        assertEquals(expected, result);
    }

    @Test
    public void testRegularObjectField() {
        var result = new ToStringBuilder(new Object(), MessageFilterToStringStyle.instance())
                .append("myField", "myValue")
                .toString();

        var expected = "Object[" + System.lineSeparator() +
                "  myField=myValue" + System.lineSeparator() +
                "]";
        assertEquals(expected, result);
    }

    @Test
    public void testFlattenedFields() {
        // "excludedMetaDataIds" is in the flatten set
        var ids = List.of("ID1", "ID2");

        var result = new ToStringBuilder(new Object(), MessageFilterToStringStyle.instance())
                .append("excludedMetaDataIds", ids)
                .toString();

        var expected = "Object[" + System.lineSeparator() +
                "  excludedMetaDataIds=[ID1, ID2]" + System.lineSeparator() +
                "]";
        assertEquals(expected, result);
    }

    @Test
    public void testNonFlattenedCollectionIndentation() {
        var list = List.of("Item1", "Item2");

        var result = new ToStringBuilder(new Object(), MessageFilterToStringStyle.instance())
                .append("regularList", list)
                .toString();

        var expected = """
                Object[
                  regularList=[
                    Item1,
                    Item2
                  ]
                ]""";

        // Normalize line endings for comparison
        assertEquals(expected.replace("\n", System.lineSeparator()), result);
    }

    @Test
    public void testNestedArrayIndentation() {
        var nested = new Object[] { "Inner" };
        var outer = new Object[] { nested };

        var result = new ToStringBuilder(new Object(), MessageFilterToStringStyle.instance())
                .append("nested", outer)
                .toString();

        var expected = """
                Object[
                  nested=[
                    [
                      Inner
                    ]
                  ]
                ]""";

        assertEquals(expected.replace("\n", System.lineSeparator()), result);
    }
}
