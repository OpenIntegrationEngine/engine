package com.mirth.connect.client.ui.browsers.message;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.mirth.connect.donkey.model.channel.MetaDataColumnType;
import com.mirth.connect.model.filters.elements.MetaDataSearchOperator;

public class MessageBrowserAdvancedFilterTest {

    @Test
    public void shouldIncludeBlankStringEqualitySearches() {
        assertTrue(MessageBrowserAdvancedFilter.shouldIncludeMetaDataSearch(MetaDataColumnType.STRING, MetaDataSearchOperator.EQUAL, ""));
        assertTrue(MessageBrowserAdvancedFilter.shouldIncludeMetaDataSearch(MetaDataColumnType.STRING, MetaDataSearchOperator.NOT_EQUAL, ""));
    }

    @Test
    public void shouldIgnoreBlankNonEqualitySearches() {
        assertFalse(MessageBrowserAdvancedFilter.shouldIncludeMetaDataSearch(MetaDataColumnType.STRING, MetaDataSearchOperator.CONTAINS, ""));
        assertFalse(MessageBrowserAdvancedFilter.shouldIncludeMetaDataSearch(MetaDataColumnType.NUMBER, MetaDataSearchOperator.EQUAL, ""));
    }

    @Test
    public void shouldIncludeNonBlankSearches() {
        assertTrue(MessageBrowserAdvancedFilter.shouldIncludeMetaDataSearch(MetaDataColumnType.STRING, MetaDataSearchOperator.CONTAINS, "value"));
        assertTrue(MessageBrowserAdvancedFilter.shouldIncludeMetaDataSearch(MetaDataColumnType.NUMBER, MetaDataSearchOperator.EQUAL, "1"));
    }
}