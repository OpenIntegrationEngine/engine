// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.server.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.commons.collections4.CollectionUtils;

import com.mirth.connect.donkey.model.channel.MetaDataColumn;
import com.mirth.connect.model.filters.MessageFilter;
import com.mirth.connect.model.filters.elements.MetaDataSearchElement;

/**
 * Validates the custom metadata column names referenced by a message search filter against the
 * columns actually defined on a channel. The message search mappers interpolate these names into SQL
 * as identifiers (MyBatis ${} substitution), so an unvalidated name is a SQL injection vector.
 *
 * <p>
 * This is a pure check: it never throws and never looks anything up. Callers pass in the channel's
 * defined columns and decide what to do with an unknown column - the REST layer returns 400, the
 * controller layer throws as a last-resort backstop for callers that bypass the REST layer.
 * </p>
 */
public final class MetaDataColumnValidator {

    private MetaDataColumnValidator() {}

    /**
     * Returns the first metadata column name referenced by the filter that is not defined on the
     * channel, or {@code null} if every referenced column is valid. Column names are matched exactly
     * against the channel's (upper-cased) column names; a {@code null} referenced name is treated as
     * unknown and returned as the string {@code "null"} so the result stays unambiguous.
     *
     * <p>
     * The defined columns are supplied lazily and are only requested when the filter actually
     * references a custom column, so a search that uses none costs no channel lookup.
     * </p>
     */
    public static String findUnknownColumn(MessageFilter filter, Supplier<List<MetaDataColumn>> definedColumnsSupplier) {
        if (filter == null) {
            return null;
        }

        boolean hasMetaDataSearch = CollectionUtils.isNotEmpty(filter.getMetaDataSearch());
        boolean hasTextSearchColumns = CollectionUtils.isNotEmpty(filter.getTextSearchMetaDataColumns());
        if (!hasMetaDataSearch && !hasTextSearchColumns) {
            return null;
        }

        List<MetaDataColumn> definedColumns = definedColumnsSupplier.get();
        Set<String> allowedColumns = new HashSet<String>();
        if (definedColumns != null) {
            for (MetaDataColumn column : definedColumns) {
                if (column.getName() != null) {
                    allowedColumns.add(column.getName());
                }
            }
        }

        if (hasMetaDataSearch) {
            for (MetaDataSearchElement element : filter.getMetaDataSearch()) {
                if (element.getColumnName() == null || !allowedColumns.contains(element.getColumnName())) {
                    return String.valueOf(element.getColumnName());
                }
            }
        }

        if (hasTextSearchColumns) {
            for (String columnName : filter.getTextSearchMetaDataColumns()) {
                if (columnName == null || !allowedColumns.contains(columnName)) {
                    return String.valueOf(columnName);
                }
            }
        }

        return null;
    }
}
