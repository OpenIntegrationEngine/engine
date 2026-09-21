/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.mirth.connect.model.converters.ObjectXMLSerializer;

public class ExtensionMetaDataTest {
    private final ObjectXMLSerializer serializer = new ObjectXMLSerializer(getClass().getClassLoader());

    @Test
    public void apiLockRoundTripsForPluginsAndConnectors() {
        for (MetaData metadata : new MetaData[] { new PluginMetaData(), new ConnectorMetaData() }) {
            metadata.setName("Custom extension");
            metadata.setMinExtensionApiVersion("1.0.0");
            String xml = serializer.serialize(metadata);
            assertTrue(xml.contains("<minExtensionApiVersion>1.0.0</minExtensionApiVersion>"));
            MetaData restored = serializer.deserialize(xml, MetaData.class);
            assertEquals(metadata.getClass(), restored.getClass());
            assertEquals("1.0.0", restored.getMinExtensionApiVersion());
            assertNull(restored.getMirthVersion());
        }
    }

    @Test
    public void legacyAndEmptyApiLocksRemainDistinctAfterDeserialization() {
        for (String root : new String[] { "pluginMetaData", "connectorMetaData" }) {
            String legacyXml = "<" + root + "><mirthVersion>4.5.2</mirthVersion></" + root + ">";
            MetaData legacy = serializer.deserialize(legacyXml, MetaData.class);
            assertNull(legacy.getMinExtensionApiVersion());
            assertEquals("4.5.2", legacy.getMirthVersion());
            String emptyXml = "<" + root + "><minExtensionApiVersion/></" + root + ">";
            assertEquals("", serializer.deserialize(emptyXml, MetaData.class).getMinExtensionApiVersion());
        }
    }

    @Test
    public void explicitNullApiLocksRoundTripAsAbsentForPluginsAndConnectors() {
        for (String root : new String[] { "pluginMetaData", "connectorMetaData" }) {
            String xml = "<" + root + "><mirthVersion>4.5.2</mirthVersion>"
                    + "<minExtensionApiVersion class=\"null\"/></" + root + ">";
            MetaData metadata = serializer.deserialize(xml, MetaData.class);
            assertNull(metadata.getMinExtensionApiVersion());
            String serialized = serializer.serialize(metadata);
            assertFalse(serialized.contains("minExtensionApiVersion"));
            MetaData restored = serializer.deserialize(serialized, MetaData.class);
            assertNull(restored.getMinExtensionApiVersion());
            assertEquals("4.5.2", restored.getMirthVersion());
        }
    }
}
