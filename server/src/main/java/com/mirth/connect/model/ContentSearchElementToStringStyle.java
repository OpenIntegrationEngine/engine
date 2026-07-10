// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.model;

import java.util.Collection;

import com.mirth.connect.donkey.model.message.ContentType;

public class ContentSearchElementToStringStyle extends SearchElementToStringStyle {
    public ContentSearchElementToStringStyle() {
        super();
        setArraySeparator(", ");
        setArrayStart("[");
        setArrayEnd("]");
    }

    @Override
    protected void appendDetail(StringBuffer buffer, String fieldName, Collection<?> coll) {
        appendDetail(buffer, fieldName, (Object[]) coll.toArray());
    }

    @Override
    protected void appendDetail(StringBuffer buffer, String fieldname, Object value) {
        if (fieldname.equals("contentCode") && value instanceof Integer code) {
            ContentType type = ContentType.fromCode(code);
            String typeName = (type != null) ? type.toString() : "UNKNOWN";
            String formatted = String.format("%d(%s)", code, typeName);
            buffer.append(formatted);
        } else {
            super.appendDetail(buffer, fieldname, value);
        }
    }

    public static ContentSearchElementToStringStyle instance() {
        return new ContentSearchElementToStringStyle();
    }
}
