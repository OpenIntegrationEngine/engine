// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.model.filters.elements;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MetaDataSearchElementTest {

    @Test
    public void testAccessors() {
        var element = new MetaDataSearchElement("col", "=", "val", true);

        assertEquals("col", element.getColumnName());
        assertEquals("=", element.getOperator());
        assertEquals("val", element.getValue());
        assertEquals(true, element.getIgnoreCase());

        element.setColumnName("col2");
        assertEquals("col2", element.getColumnName());

        element.setOperator("!=");
        assertEquals("!=", element.getOperator());

        element.setValue(100);
        assertEquals(100, element.getValue());

        element.setIgnoreCase(false);
        assertEquals(false, element.getIgnoreCase());
    }

    @Test
    public void testToStringIntegration() {
        var element = new MetaDataSearchElement("MyCol", "EQUALS", "MyVal", true);

        var stringVal = element.toString();

        // ReflectionToStringBuilder sorts fields by name
        var expected = "MetaDataSearchElement[columnName=MyCol, ignoreCase=true, operator=EQUALS, value=MyVal]";

        assertEquals(expected, stringVal);
    }
}
