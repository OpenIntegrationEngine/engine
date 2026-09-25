/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

import com.mirth.connect.client.core.ExtensionDependency;
import com.mirth.connect.client.core.ExtensionDependencies;
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
    public void absentEmptyAndNullListsRetainLegacyMatching() {
        for (String root : new String[] { "pluginMetaData", "connectorMetaData" }) {
            for (String dependencies : new String[] { "", "<dependencies/>", "<dependencies class=\"null\"/>" }) {
                String xml = "<" + root + "><mirthVersion>4.6.0</mirthVersion>" + dependencies + "</" + root + ">";
                MetaData metadata = serializer.deserialize(xml, MetaData.class);
                assertNull(ExtensionDependencies.getEngineError(metadata, "4.6.0.123"));
                assertNotNull(ExtensionDependencies.getEngineError(metadata, "4.7.0"));
            }
        }
    }

    @Test
    public void malformedRequirementsCannotFallBackToMatchingRelease() {
        for (String root : new String[] { "pluginMetaData", "connectorMetaData" }) {
            for (String dependency : new String[] {
                    "<dependency type=\"engine-api\"/>",
                    "<dependency type=\"engine-api\" minVersion=\"invalid\"/>",
                    "<dependency type=\"engine\" minVersion=\"1.0.0\"/>",
                    "<dependency type=\"plugin\" minVersion=\"1.0.0\"/>",
                    "<null/>", "<string>not a dependency</string>" }) {
                String xml = "<" + root + "><mirthVersion>4.6.0</mirthVersion><dependencies>"
                        + dependency + "</dependencies></" + root + ">";
                MetaData metadata = serializer.deserialize(xml, MetaData.class);
                assertNotNull(xml, ExtensionDependencies.getEngineError(metadata, "4.6.0"));
            }
        }
    }
}
