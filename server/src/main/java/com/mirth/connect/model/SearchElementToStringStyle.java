// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2005-2024 NextGen Healthcare
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.model;

import org.apache.commons.lang3.builder.ToStringStyle;

public class SearchElementToStringStyle extends ToStringStyle {
    public SearchElementToStringStyle() {
        super();
        setUseShortClassName(true);
        setUseIdentityHashCode(false);
        setFieldSeparator(", ");
    }

    public static SearchElementToStringStyle instance() {
        return new SearchElementToStringStyle();
    }
}
