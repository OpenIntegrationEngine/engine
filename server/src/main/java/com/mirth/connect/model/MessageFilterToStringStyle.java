// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2005-2024 NextGen Healthcare
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.model;

import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.lang3.builder.ToStringStyle;

public class MessageFilterToStringStyle extends ToStringStyle {
    private static final int INDENT = 2;
    private static final Set<String> flatten = Set.of("excludedMetaDataIds", "includedMetaDataIds", "statuses",
            "textSearchMetaDataColumns");
    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mmXXX'['VV']'");

    private int level = 1;

    public MessageFilterToStringStyle() {
        super();
        setUseShortClassName(true);
        setUseIdentityHashCode(false);
        resetIndent();
    }

    public static MessageFilterToStringStyle instance() {
        return new MessageFilterToStringStyle();
    }

    @Override
    protected void appendDetail(StringBuffer buffer, String fieldName, Object value) {
        if (value instanceof GregorianCalendar cal) {
            value = cal.toZonedDateTime().format(dateFormat);
        }

        buffer.append(value);
    }
    
    @Override
    protected void appendDetail(StringBuffer buffer, String fieldName, Collection<?> coll) {
        if (flatten.contains(fieldName)) {
            appendDetail(buffer, fieldName, coll.toString());
        } else {
            appendDetail(buffer, fieldName, (Object[]) coll.toArray());
        }
    }

    @Override
    protected void appendDetail(StringBuffer buffer, String fieldName, Object[] array) {
        level += 1;
        resetIndent();
        super.appendDetail(buffer, fieldName, array);
        level -= 1;
        resetIndent();
    }

    private void resetIndent() {
        setArrayStart("[" + System.lineSeparator() + " ".repeat(INDENT * level));
        setArraySeparator("," + System.lineSeparator() + " ".repeat(INDENT * level));
        setArrayEnd(System.lineSeparator() + " ".repeat(INDENT * (level - 1)) + "]");
        setContentStart("[" + System.lineSeparator() + " ".repeat(INDENT * level));
        setFieldSeparator("," + System.lineSeparator() + " ".repeat(INDENT * level));
        setContentEnd(System.lineSeparator() + " ".repeat(INDENT * (level - 1)) + "]");
    }
}
