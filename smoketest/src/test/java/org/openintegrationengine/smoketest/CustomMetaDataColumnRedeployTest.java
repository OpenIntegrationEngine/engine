// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.mirth.connect.donkey.model.channel.MetaDataColumn;
import com.mirth.connect.donkey.model.channel.MetaDataColumnType;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

/**
 * The half of custom metadata columns that ci/tests/200-custom-metadata-columns cannot express: a
 * fixture deploys each channel once, so it only ever reaches {@code addMetaDataColumn}. Editing a
 * deployed channel's column list and redeploying it is what reaches {@code removeMetaDataColumn},
 * and what turns a column's type change into a drop followed by an add.
 */
@DisplayName("200-custom-metadata-columns/02-redeploy")
@TestInstance(PER_CLASS)
class CustomMetaDataColumnRedeployTest {

    private static final String CHANNEL = "channels/custom-metadata-columns-redeploy.xml";

    private String channelId;

    @AfterAll
    void undeploy() {
        Harness.undeploy(channelId);
    }

    @Test
    void redeployAddsRemovesAndRetypesColumns() throws Exception {
        OieServer server = SharedServer.get();
        Channel channel = ObjectXMLSerializer.getInstance().deserialize(Harness.resource(CHANNEL), Channel.class);

        // The channel starts with KEPT, DROPPED and RETYPED, all STRING.
        channelId = server.deployChannel(channel, CHANNEL);
        long first = submit(server, "kept", "one", "dropped", "one", "retyped", "1.5");

        Map<String, Object> firstMetaData = metaData(first);
        assertEquals("one", firstMetaData.get("KEPT"));
        assertEquals("one", firstMetaData.get("DROPPED"));
        assertEquals("1.5", firstMetaData.get("RETYPED"), "a STRING column stores the value verbatim");

        // Drop one column, retype another, add a third, and redeploy the same channel.
        List<MetaDataColumn> columns = channel.getProperties().getMetaDataColumns();
        assertTrue(columns.removeIf(column -> "DROPPED".equals(column.getName())),
                "Channel fixture has no DROPPED column");
        column(columns, "RETYPED").setType(MetaDataColumnType.NUMBER);
        columns.add(new MetaDataColumn("ADDED", MetaDataColumnType.BOOLEAN, "added"));
        server.deployChannel(channel, CHANNEL);

        long second = submit(server, "kept", "two", "dropped", "two", "retyped", "1.5", "added", "true");
        Map<String, Object> secondMetaData = metaData(second);
        assertEquals("two", secondMetaData.get("KEPT"));
        assertEquals(true, secondMetaData.get("ADDED"), "the column added on redeploy stores a BOOLEAN");
        assertFalse(secondMetaData.containsKey("DROPPED"),
                () -> "DROPPED was removed from the channel but is still a column: " + secondMetaData.keySet());
        assertEquals(0, new BigDecimal("1.5").compareTo((BigDecimal) secondMetaData.get("RETYPED")),
                "the retyped column now stores a NUMBER");

        // The table survived both ALTER TABLEs, so the message sent before the redeploy is still
        // readable - minus the column that was dropped, and minus the value in the column that was
        // retyped, which a type change drops and re-adds.
        Map<String, Object> firstAfterRedeploy = metaData(first);
        assertEquals("one", firstAfterRedeploy.get("KEPT"), "an untouched column kept its stored value");
        assertFalse(firstAfterRedeploy.containsKey("DROPPED"));
        assertNull(firstAfterRedeploy.get("RETYPED"), "a retyped column is dropped and re-added, losing old values");
    }

    /** Submits a message whose source map is the given key/value pairs. */
    private long submit(OieServer server, String... sourceMapEntries) throws Exception {
        Map<String, Object> sourceMap = new LinkedHashMap<>();
        for (int i = 0; i < sourceMapEntries.length; i += 2) {
            sourceMap.put(sourceMapEntries[i], sourceMapEntries[i + 1]);
        }
        return server.submitMessage(channelId, "Hello world!", sourceMap);
    }

    /** Reads one message's source custom metadata back off the server. */
    private Map<String, Object> metaData(long messageId) throws Exception {
        return Harness.awaitConnectorStatus(channelId, messageId, 0, Status.TRANSFORMED).getMetaDataMap();
    }

    private static MetaDataColumn column(List<MetaDataColumn> columns, String name) {
        return columns.stream().filter(column -> name.equals(column.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("Channel fixture has no " + name + " column"));
    }
}
