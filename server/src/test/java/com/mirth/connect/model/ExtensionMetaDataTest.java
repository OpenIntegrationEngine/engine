/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

import com.mirth.connect.client.core.ExtensionDependency;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

public class ExtensionMetaDataTest {
    private final ObjectXMLSerializer serializer = new ObjectXMLSerializer(getClass().getClassLoader());

    @Test
    public void dependenciesRoundTripForPluginsAndConnectors() {
        for (MetaData metadata : new MetaData[] { new PluginMetaData(), new ConnectorMetaData() }) {
            metadata.setDependencies(new ArrayList<>(Arrays.asList(
                    new ExtensionDependency("engine-api", null, "1.0.0"),
                    new ExtensionDependency("plugin", "Provider & Support", "2.1.0"))));
            String xml = serializer.serialize(metadata);
            assertTrue(xml.contains("<dependencies>"));
            assertTrue(xml.contains("<dependency type=\"engine-api\" minVersion=\"1.0.0\"/>"));
            MetaData restored = serializer.deserialize(xml, MetaData.class);
            assertEquals(2, restored.getDependencies().size());
            assertEquals("engine-api", restored.getDependencies().get(0).getType());
            assertNull(restored.getDependencies().get(0).getName());
            assertEquals("Provider & Support", restored.getDependencies().get(1).getName());
            assertEquals("2.1.0", restored.getDependencies().get(1).getMinVersion());
        }
    }

    @Test
    public void dependencySchemaRejectsTyposAndNestedFields() {
        for (String dependency : new String[] {
                "<dependency type=\"plugin\" name=\"Provider\" minVersion=\"1.0.0\" optional=\"true\"/>",
                "<dependency><type>plugin</type><name>Provider</name><minVersion>1.0.0</minVersion></dependency>",
                "<plugin name=\"Provider\" minVersion=\"1.0.0\"/>",
                "<dependency type=\"engine-api\" minVersion=\"1.0.0\">unexpected</dependency>",
                "unexpected", "<null/>" }) {
            for (String root : new String[] { "pluginMetaData", "connectorMetaData" }) {
                String xml = "<" + root + "><dependencies>" + dependency + "</dependencies></" + root + ">";
                assertThrows(xml, RuntimeException.class, () -> serializer.deserialize(xml, MetaData.class));
            }
        }
    }

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
