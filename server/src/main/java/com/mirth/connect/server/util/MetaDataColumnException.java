// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.server.util;

/**
 * Thrown when a message search filter references a custom metadata column that is not defined on the
 * channel. Those column names are interpolated into SQL as identifiers by the search mappers (MyBatis
 * ${} substitution), so an undefined name must never reach the data layer.
 *
 * <p>
 * This is a plain domain exception. It is thrown from the single validation gate in
 * DonkeyMessageController - the FilterOptions constructor, which every message search path builds
 * before its batch loop - and is left to bubble up. On the synchronous API paths the engine's
 * standard handling turns any uncaught controller exception into a 500, the same way every other
 * controller-layer rejection surfaces; deliberately not mapped to a 400, because the engine has no
 * central exception-to-status convention and coupling this data-layer type to an HTTP status would be
 * the only such case in the codebase. The injection is blocked either way, since the throw happens
 * before any query runs. The reprocess path is asynchronous and catches this to log and abort the run.
 * </p>
 *
 * <p>
 * The offending channel and column are kept as fields and the message is deliberately generic, so
 * callers log the values with parameterized logging rather than concatenating untrusted input into a
 * message string.
 * </p>
 */
public class MetaDataColumnException extends RuntimeException {

    private final String channelId;
    private final String columnName;

    public MetaDataColumnException(String channelId, String columnName) {
        super("Message search referenced an undefined metadata column");
        this.channelId = channelId;
        this.columnName = columnName;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getColumnName() {
        return columnName;
    }
}
