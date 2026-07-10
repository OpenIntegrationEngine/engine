/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core.api.providers;

import javax.inject.Singleton;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Calendar;

@Provider
@Singleton
public class CalendarParamConverterProvider implements ParamConverterProvider {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType.getName().equals(Calendar.class.getName())) {
            return new ParamConverter<T>() {
                @Override
                public T fromString(String value) {
                    if (value == null) {
                        return null;
                    }

                    try {
                        // Parse the incoming string as a ZonedDateTime using the given pattern
                        ZonedDateTime zdt = ZonedDateTime.parse(value, FORMATTER);

                        Calendar calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone(zdt.getZone()));
                        calendar.setTimeInMillis(zdt.toInstant().toEpochMilli());

                        @SuppressWarnings("unchecked")
                        T result = (T) calendar;
                        return result;
                    } catch (DateTimeParseException e) {
                        throw new ProcessingException(e);
                    }
                }

                @Override
                public String toString(T value) {
                    if (value == null) {
                        return null;
                    }

                    Calendar calendar = (Calendar) value;

                    // Build a ZonedDateTime from the Calendar (date/time + time zone)
                    Instant instant = calendar.toInstant();
                    ZoneId zoneId = calendar.getTimeZone().toZoneId();
                    ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, zoneId);

                    return FORMATTER.format(zdt);
                }
            };
        }

        return null;
    }
}