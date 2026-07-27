// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.Test;

import com.mirth.connect.donkey.model.channel.MetaDataColumn;
import com.mirth.connect.donkey.model.channel.MetaDataColumnType;
import com.mirth.connect.model.filters.MessageFilter;
import com.mirth.connect.model.filters.elements.MetaDataSearchElement;

public class MetaDataColumnValidatorTest {

    private static List<MetaDataColumn> definedColumns(String... names) {
        List<MetaDataColumn> columns = new ArrayList<MetaDataColumn>();
        for (String name : names) {
            columns.add(new MetaDataColumn(name, MetaDataColumnType.STRING, null));
        }
        return columns;
    }

    private static Supplier<List<MetaDataColumn>> supplier(List<MetaDataColumn> columns, AtomicBoolean invoked) {
        return () -> {
            invoked.set(true);
            return columns;
        };
    }

    @Test
    public void nullFilterReturnsNull() {
        assertEquals(Optional.empty(), MetaDataColumnValidator.findUnknownColumn(null, () -> definedColumns("STATUS")));
    }

    @Test
    public void noReferencedColumnsReturnsNullAndSkipsLookup() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MessageFilter filter = new MessageFilter();

        assertEquals(Optional.empty(), MetaDataColumnValidator.findUnknownColumn(filter, supplier(definedColumns("STATUS"), invoked)));
        assertFalse("Channel columns must not be looked up when the filter references none", invoked.get());
    }

    @Test
    public void definedMetaDataSearchColumnReturnsNull() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MessageFilter filter = new MessageFilter();
        filter.setMetaDataSearch(Arrays.asList(new MetaDataSearchElement("STATUS", "EQUAL", "x", false)));

        assertEquals(Optional.empty(), MetaDataColumnValidator.findUnknownColumn(filter, supplier(definedColumns("STATUS"), invoked)));
        assertTrue("A referenced column must trigger the lookup", invoked.get());
    }

    @Test
    public void unknownMetaDataSearchColumnIsReturned() {
        MessageFilter filter = new MessageFilter();
        filter.setMetaDataSearch(Arrays.asList(new MetaDataSearchElement("EVIL\" OR '1'='1", "EQUAL", "x", false)));

        assertEquals(Optional.of("EVIL\" OR '1'='1"), MetaDataColumnValidator.findUnknownColumn(filter, () -> definedColumns("STATUS")));
    }

    @Test
    public void nonUpperCaseColumnIsReturned() {
        MessageFilter filter = new MessageFilter();
        filter.setMetaDataSearch(Arrays.asList(new MetaDataSearchElement("status", "EQUAL", "x", false)));

        assertEquals(Optional.of("status"), MetaDataColumnValidator.findUnknownColumn(filter, () -> definedColumns("STATUS")));
    }

    @Test
    public void nullColumnNameIsReturnedAsNullString() {
        MessageFilter filter = new MessageFilter();
        filter.setMetaDataSearch(Arrays.asList(new MetaDataSearchElement(null, "EQUAL", "x", false)));

        assertEquals(Optional.of("null"), MetaDataColumnValidator.findUnknownColumn(filter, () -> definedColumns("STATUS")));
    }

    @Test
    public void definedTextSearchColumnReturnsNull() {
        MessageFilter filter = new MessageFilter();
        filter.setTextSearchMetaDataColumns(new ArrayList<String>(Arrays.asList("STATUS")));

        assertEquals(Optional.empty(), MetaDataColumnValidator.findUnknownColumn(filter, () -> definedColumns("STATUS")));
    }

    @Test
    public void unknownTextSearchColumnIsReturned() {
        MessageFilter filter = new MessageFilter();
        filter.setTextSearchMetaDataColumns(new ArrayList<String>(Arrays.asList("BOGUS")));

        assertEquals(Optional.of("BOGUS"), MetaDataColumnValidator.findUnknownColumn(filter, () -> definedColumns("STATUS")));
    }

    @Test
    public void channelWithNoColumnsRejectsAnyReferencedColumn() {
        MessageFilter filter = new MessageFilter();
        filter.setMetaDataSearch(Arrays.asList(new MetaDataSearchElement("STATUS", "EQUAL", "x", false)));

        assertEquals(Optional.of("STATUS"), MetaDataColumnValidator.findUnknownColumn(filter, () -> null));
    }

    @Test
    public void nullSearchElementIsRejectedNotThrown() {
        MessageFilter filter = new MessageFilter();
        List<MetaDataSearchElement> elements = new ArrayList<MetaDataSearchElement>();
        elements.add(null);
        filter.setMetaDataSearch(elements);

        // A null element in the list must be treated as unknown (returned), never an NPE.
        assertEquals(Optional.of("null"), MetaDataColumnValidator.findUnknownColumn(filter, () -> definedColumns("STATUS")));
    }

    @Test
    public void nullDefinedColumnEntryIsIgnoredNotThrown() {
        MessageFilter filter = new MessageFilter();
        filter.setMetaDataSearch(Arrays.asList(new MetaDataSearchElement("STATUS", "EQUAL", "x", false)));

        List<MetaDataColumn> columns = new ArrayList<MetaDataColumn>();
        columns.add(null);
        columns.add(new MetaDataColumn("STATUS", MetaDataColumnType.STRING, null));

        // A null entry in the channel's columns must be skipped, not cause an NPE; STATUS still validates.
        assertEquals(Optional.empty(), MetaDataColumnValidator.findUnknownColumn(filter, () -> columns));
    }
}
