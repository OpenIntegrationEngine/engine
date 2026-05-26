// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation

package com.mirth.connect.model.filters.elements;

import java.util.Objects;
import java.io.Serializable;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.mirth.connect.model.SearchElementToStringStyle;
import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("metaDataSearchCriteria")
public class MetaDataSearchElement implements Serializable {

    private String columnName;
    private String operator;
    private Object value;
    private Boolean ignoreCase;

    public MetaDataSearchElement(String columnName, String operator, Object value, Boolean ignoreCase) {
        this.setColumnName(columnName);
        this.setOperator(operator);
        this.setValue(value);
        this.setIgnoreCase(ignoreCase);
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Boolean getIgnoreCase() {
        return ignoreCase;
    }

    public void setIgnoreCase(Boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, SearchElementToStringStyle.instance());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MetaDataSearchElement element && equals(element);
    }

    /** Check if the element has logically identical criteria */
    public boolean equals(MetaDataSearchElement element) {
        return element != null
            && Objects.equals(columnName, element.columnName)
            && Objects.equals(operator, element.operator)
            && Objects.equals(value, element.value)
            && Objects.equals(ignoreCase, element.ignoreCase);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnName, operator, value, ignoreCase);
    }
}
