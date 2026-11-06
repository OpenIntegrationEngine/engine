// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine Contributors

package com.mirth.connect.client.core.api.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.Calendar;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ParamConverter;

import org.junit.BeforeClass;
import org.junit.Test;

public class CalendarParamConverterProviderTest {

    private static ParamConverter<Calendar> converter;

    @BeforeClass
    public static void setup() {
        converter = new CalendarParamConverterProvider().getConverter(Calendar.class, null, null);
    }

    @Test
    public void testNullReturnsNull() {
        assertNull(converter.fromString(null));
    }

    @Test
    public void testValidDateRoundTrips() {
        String value = "1985-10-26T09:00:00.000-0700";
        Calendar calendar = converter.fromString(value);
        assertNotNull(calendar);
        assertEquals(value, converter.toString(calendar));
    }

    @Test
    public void testInvalidDateThrowsBadRequest() {
        try {
            converter.fromString("1985");
            fail("Expected BadRequestException for a malformed date");
        } catch (BadRequestException e) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), e.getResponse().getStatus());
        }
    }
}
