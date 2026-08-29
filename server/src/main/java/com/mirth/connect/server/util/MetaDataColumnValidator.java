// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.server.util;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
 * defined columns and decide what to do with an unknown column - the controller rejects the search
 * by throwing a MetaDataColumnException before any query runs.
 * </p>
 */
public final class MetaDataColumnValidator {

    private MetaDataColumnValidator() {}

    /**
     * Finds the first metadata column name referenced by the filter that is not defined on the
     * channel. Column names are matched exactly against the channel's (upper-cased) column names; a
     * {@code null} referenced name is treated as unknown and reported as the string {@code "null"}.
     *
     * <p>
     * The defined columns are supplied lazily and are only requested when the filter actually
     * references a custom column, so a search that uses none costs no channel lookup.
     * </p>
     *
     * @param filter the message search filter to check; may be {@code null}, which validates
     *            trivially
     * @param definedColumnsSupplier supplies the channel's defined metadata columns; only invoked
     *            when the filter references a custom column, and may return {@code null} for a
     *            channel with no columns
     * @return the first unknown column name referenced by the filter, or {@link Optional#empty()}
     *         if every referenced column is defined on the channel
     */
    public static Optional<String> findUnknownColumn(MessageFilter filter, Supplier<List<MetaDataColumn>> definedColumnsSupplier) {
        if (filter == null) {
            return Optional.empty();
        }

        boolean hasMetaDataSearch = CollectionUtils.isNotEmpty(filter.getMetaDataSearch());
        boolean hasTextSearchColumns = CollectionUtils.isNotEmpty(filter.getTextSearchMetaDataColumns());
        if (!hasMetaDataSearch && !hasTextSearchColumns) {
            return Optional.empty();
        }

        List<MetaDataColumn> definedColumns = definedColumnsSupplier.get();
        Set<String> allowedColumns = new HashSet<String>();
        if (definedColumns != null) {
            for (MetaDataColumn column : definedColumns) {
                if (column != null && column.getName() != null) {
                    allowedColumns.add(column.getName());
                }
            }
        }

        if (hasMetaDataSearch) {
            for (MetaDataSearchElement element : filter.getMetaDataSearch()) {
                if (element == null) {
                    return Optional.of("null");
                }
                if (element.getColumnName() == null || !allowedColumns.contains(element.getColumnName())) {
                    return Optional.of(String.valueOf(element.getColumnName()));
                }
            }
        }

        if (hasTextSearchColumns) {
            for (String columnName : filter.getTextSearchMetaDataColumns()) {
                if (columnName == null || !allowedColumns.contains(columnName)) {
                    return Optional.of(String.valueOf(columnName));
                }
            }
        }

        return Optional.empty();
    }
}
