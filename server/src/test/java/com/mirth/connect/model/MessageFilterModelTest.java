// SPDX-License-Identifier: MPL-2.0

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.mirth.connect.donkey.model.message.ContentType;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.model.filters.MessageFilter;
import com.mirth.connect.model.filters.elements.ContentSearchElement;
import com.mirth.connect.model.filters.elements.MetaDataSearchElement;

public class MessageFilterModelTest {

    @Test
    public void testMessageFilterEqualityAndToString() {
        var left = new MessageFilter();
        var right = new MessageFilter();

        assertTrue(left.isEmpty());
        assertEquals(left, right);
        assertEquals("(no criteria)", left.toDisplayString());
        assertFalse(left.equals((Object) null));

        left.setMaxMessageId(200L);
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setMaxMessageId(200L);
        assertEquals(left, right);

        left.setMinMessageId(100L);
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setMinMessageId(100L);
        assertEquals(left, right);

        left.setOriginalIdLower(10L);
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setOriginalIdLower(10L);
        assertEquals(left, right);

        left.setOriginalIdUpper(20L);
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setOriginalIdUpper(20L);
        assertEquals(left, right);

        left.setImportIdLower(25L);
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setImportIdLower(25L);
        assertEquals(left, right);

        left.setImportIdUpper(30L);
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setImportIdUpper(30L);
        assertEquals(left, right);

        left.setStartDate(calendar(2024, 1, 2, 3, 4, 0));
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setStartDate(calendar(2024, 1, 2, 3, 4, 0));
        assertEquals(left, right);

        left.setEndDate(calendar(2024, 2, 3, 4, 5, 0));
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setEndDate(calendar(2024, 2, 3, 4, 5, 0));
        assertEquals(left, right);

        left.setTextSearch("alpha");
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setTextSearch("alpha");
        assertEquals(left, right);

        left.setTextSearchRegex(false);
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setTextSearchRegex(false);
        assertEquals(left, right);

        left.setStatuses(EnumSet.of(Status.RECEIVED, Status.ERROR));
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setStatuses(EnumSet.of(Status.RECEIVED, Status.ERROR));
        assertEquals(left, right);

        left.setIncludedMetaDataIds(List.of(1, 2));
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setIncludedMetaDataIds(List.of(1, 2));
        assertEquals(left, right);

        left.setExcludedMetaDataIds(List.of(3));
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setExcludedMetaDataIds(List.of(3));
        assertEquals(left, right);

        left.setServerId("server-1");
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setServerId("server-1");
        assertEquals(left, right);

        left.setContentSearch(List.of(new ContentSearchElement(ContentType.RAW.getContentTypeCode(), List.of("needle", "haystack"))));
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setContentSearch(List.of(new ContentSearchElement(ContentType.RAW.getContentTypeCode(), List.of("needle", "haystack"))));
        assertEquals(left, right);

        left.setMetaDataSearch(List.of(new MetaDataSearchElement("mirth_type", "EQUAL", "asdf", null)));
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setMetaDataSearch(List.of(new MetaDataSearchElement("mirth_type", "EQUAL", "asdf", null)));
        assertEquals(left, right);

        left.setTextSearchMetaDataColumns(List.of("columnA", "columnB"));
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setTextSearchMetaDataColumns(List.of("columnA", "columnB"));
        assertEquals(left, right);

        left.setSendAttemptsLower(1);
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setSendAttemptsLower(1);
        assertEquals(left, right);

        left.setSendAttemptsUpper(5);
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setSendAttemptsUpper(5);
        assertEquals(left, right);

        left.setAttachment(true);
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setAttachment(true);
        assertEquals(left, right);

        left.setError(true);
        assertNotEquals(left, right);
        assertNotNull(left.toDisplayString());
        right.setError(true);
        assertEquals(left, right);

        var expected = String.join("\n",
            "Max Message Id: 200",
            "Min Message Id: 100",
            "Date Range: 2024-01-02 03:04 to 2024-02-03 04:05",
            "Statuses: RECEIVED, ERROR",
            "Text Search: alpha",
            "Connectors: Source, Destination except Filtered",
            "Original Id: Between 10 and 20",
            "Import Id: Between 25 and 30",
            "Server Id: server-1",
            "# of Send Attempts: 1 - 5",
            "Raw contains \"needle\"",
            "Raw contains \"haystack\"",
            "mirth_type = asdf",
            "Has Attachment",
            "Has Error"
        );

        assertEquals(expected, left.toDisplayString(Map.of(1, "Source", 2, "Destination", 3, "Filtered"), "\n", false));
    }

    @Test
    public void testMessageFilterToStringFallsBackToConnectorIds() {
        var filter = new MessageFilter();
        filter.setIncludedMetaDataIds(List.of(1, 99));
        filter.setExcludedMetaDataIds(List.of(3, 42));

        assertEquals(
            "Connectors: Source, 99 except Filtered, 42",
            filter.toDisplayString(Map.of(1, "Source", 3, "Filtered"), "\n", false)
        );
    }

    @Test
    public void testMessageFilterToStringIncludesEmptyCriteria() {
        var filter = new MessageFilter();
        filter.setMaxMessageId(1L);

        assertEquals(
            String.join("\n",
                "Max Message Id: 1",
                "Date Range: (any) to (any)",
                "Statuses: (any)",
                "Connectors: (any)"
            ),
            filter.toDisplayString(Map.of(), "\n", true)
        );
    }

    private GregorianCalendar calendar(int year, int month, int day, int hour, int minute, int second) {
        GregorianCalendar calendar = new GregorianCalendar(year, month - 1, day, hour, minute, second);
        calendar.set(GregorianCalendar.MILLISECOND, 0);
        return calendar;
    }
}