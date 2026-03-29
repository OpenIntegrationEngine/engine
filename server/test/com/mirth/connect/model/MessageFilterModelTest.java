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

        assertEquals("(no criteria)", left.toDisplayString(Map.of(), "\n", false));

        left.setMaxMessageId(200L);
        left.setMinMessageId(100L);
        left.setOriginalIdLower(10L);
        left.setOriginalIdUpper(20L);
        left.setImportIdLower(25L);
        left.setImportIdUpper(30L);
        left.setStartDate(calendar(2024, 1, 2, 3, 4, 0));
        left.setEndDate(calendar(2024, 2, 3, 4, 5, 0));
        left.setTextSearch("alpha");
        left.setTextSearchRegex(false);
        left.setStatuses(EnumSet.of(Status.RECEIVED, Status.ERROR));
        left.setIncludedMetaDataIds(List.of(1, 2));
        left.setExcludedMetaDataIds(List.of(3));
        left.setServerId("server-1");
        left.setContentSearch(List.of(new ContentSearchElement(ContentType.RAW.getContentTypeCode(), List.of("needle", "haystack"))));
        left.setMetaDataSearch(List.of(new MetaDataSearchElement("mirth_type", "EQUAL", "asdf", null)));
        left.setTextSearchMetaDataColumns(List.of("columnA", "columnB"));
        left.setSendAttemptsLower(1);
        left.setSendAttemptsUpper(5);
        left.setAttachment(true);
        left.setError(true);

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