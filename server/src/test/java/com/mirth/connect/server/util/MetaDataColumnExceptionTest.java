// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MetaDataColumnExceptionTest {

    /**
     * A plain domain exception (bubbles up to the engine's standard 500 on the synchronous API
     * paths). It must not carry the untrusted column name in its message; that goes in a field so
     * the reprocess path can log it with parameterized logging.
     */
    @Test
    public void isPlainRuntimeExceptionWithGenericMessage() {
        MetaDataColumnException e = new MetaDataColumnException("channelId", "EVIL\" OR '1'='1");
        assertTrue(e instanceof RuntimeException);
        assertEquals("Message search referenced an undefined metadata column", e.getMessage());
    }

    @Test
    public void retainsChannelAndColumn() {
        MetaDataColumnException e = new MetaDataColumnException("channelId", "EVIL");
        assertEquals("channelId", e.getChannelId());
        assertEquals("EVIL", e.getColumnName());
    }
}
