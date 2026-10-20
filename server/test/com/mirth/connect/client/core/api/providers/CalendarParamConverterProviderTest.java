/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core.api.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.TimeZone;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.ext.ParamConverter;

import org.junit.BeforeClass;
import org.junit.Test;

public class CalendarParamConverterProviderTest {

    private static ParamConverter<Calendar> converter;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @BeforeClass
    @SuppressWarnings("unchecked")
    public static void setup() {
        converter = (ParamConverter<Calendar>) new CalendarParamConverterProvider().getConverter(Calendar.class, null,
                null);
    }

    @Test
    public void testGetConverter() {
        CalendarParamConverterProvider provider = new CalendarParamConverterProvider();
        assertNotNull(provider.getConverter(Calendar.class, null, null));
        assertNull(provider.getConverter(String.class, null, null));
    }

    @Test
    public void testFromStringValid() {
        String dateString = "2023-10-27T10:30:00.000-0400";
        Calendar calendar = converter.fromString(dateString);
        assertNotNull(calendar);

        ZonedDateTime zdt = ZonedDateTime.parse(dateString, FORMATTER);
        assertEquals(zdt.toInstant().toEpochMilli(), calendar.getTimeInMillis());
        assertEquals(zdt.getZone().getId(), calendar.getTimeZone().getID());
    }

    @Test
    public void testFromStringNull() {
        assertNull(converter.fromString(null));
    }

    @Test(expected = ProcessingException.class)
    public void testFromStringInvalidFormat() {
        converter.fromString("invalid-date-string");
    }

    @Test
    public void testRoundTrip() {
        Calendar original = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        original.set(2023, Calendar.DECEMBER, 25, 9, 0, 0);
        original.set(Calendar.MILLISECOND, 0);

        String stringValue = converter.toString(original);
        Calendar result = converter.fromString(stringValue);

        assertEquals(original.getTimeInMillis(), result.getTimeInMillis());
        assertEquals(original.getTimeZone().getID(), result.getTimeZone().getID());
    }

    @Test
    public void testTimezonePreservation() {
        String[] zoneIds = { "UTC", "America/Los_Angeles", "Europe/London", "Asia/Tokyo" };

        for (String zoneId : zoneIds) {
            Calendar original = Calendar.getInstance(TimeZone.getTimeZone(zoneId));
            String stringValue = converter.toString(original);
            Calendar result = converter.fromString(stringValue);

            assertEquals("Failed for timezone: " + zoneId, original.getTimeZone().getID(),
                    result.getTimeZone().getID());
            assertEquals("Failed for timezone: " + zoneId, original.getTimeInMillis(), result.getTimeInMillis());
        }
    }
}