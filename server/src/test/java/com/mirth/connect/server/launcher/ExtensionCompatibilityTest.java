/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.server.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.mirth.connect.model.MetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.ExtensionLoader;
import com.mirth.connect.server.util.ResourceUtil;

public class ExtensionCompatibilityTest {

    @Test
    public void launcherAndLoaderAgreeForPluginAndConnectorMetadata() throws Exception {
        ObjectXMLSerializer serializer = new ObjectXMLSerializer(getClass().getClassLoader());
        Object[][] cases = {
                { "<mirthVersion>4.5.1, 4.5.2 </mirthVersion>", "4.5.2.123", true },
                { "<mirthVersion>4.5.2</mirthVersion>", "99.0.0", false },
                { "<mirthVersion>4.5.2</mirthVersion><minExtensionApiVersion>1.0.0</minExtensionApiVersion>", "99.0.0", true },
                { "<minExtensionApiVersion> 1.0.0 </minExtensionApiVersion>", "99.0.0", true },
                { "<mirthVersion>4.5.2</mirthVersion><minExtensionApiVersion/>", "4.5.2", false },
                { "<mirthVersion>4.5.2</mirthVersion><minExtensionApiVersion>invalid</minExtensionApiVersion>", "4.5.2", false },
                { "<mirthVersion>4.5.2</mirthVersion><minExtensionApiVersion>1.0.1</minExtensionApiVersion>", "4.5.2", false },
                { "<mirthVersion>4.5.2</mirthVersion><minExtensionApiVersion class=\"null\"/>", "4.5.2", true },
                { "<mirthVersion>4.5.2</mirthVersion><minExtensionApiVersion class=\"null\"/>", "99.0.0", false },
                { "<mirthVersion>4.5.2</mirthVersion><minExtensionApiVersion class=\"null\">2.0.0</minExtensionApiVersion>", "4.5.2", true },
                { "<mirthVersion>4.5.2</mirthVersion><minExtensionApiVersion><child>1.0.0</child></minExtensionApiVersion>", "4.5.2", false },
                { "<minExtensionApiVersion>1.0<child/>.0</minExtensionApiVersion>", "99.0.0", true },
                { "<minExtensionApiVersion><![CDATA[1.0.0]]></minExtensionApiVersion>", "99.0.0", true },
                { "<mirthVersion>4.5.2</mirthVersion><minExtensionApiVersion class=\"string\" resolves-to=\"null\"/>", "4.5.2", true },
                { "<mirthVersion>4.5.2</mirthVersion><minExtensionApiVersion class=\"com.thoughtworks.xstream.mapper.Mapper$Null\"/>", "4.5.2", true },
                { "<mirthVersion>4.5.2</mirthVersion><minExtensionApiVersion class=\"null\" resolves-to=\"string\">2.0.0</minExtensionApiVersion>", "4.5.2", false },
                { "<mirthVersion>4.5.2</mirthVersion><?minExtensionApiVersion 2.0.0?>", "4.5.2", true },
                { "<minExtensionApiVersion>2.0.0</minExtensionApiVersion>", "99.0.0", false },
                { "", "4.5.2", false }
        };

        try (MockedStatic<ResourceUtil> resources = mockStatic(ResourceUtil.class)) {
            for (Object[] testCase : cases) {
                String serverVersion = (String) testCase[1];
                resources.when(() -> ResourceUtil.getResourceStream(ExtensionLoader.class, "version.properties"))
                        .thenAnswer(invocation -> new ByteArrayInputStream(("mirth.version=" + serverVersion).getBytes(StandardCharsets.UTF_8)));
                for (String root : new String[] { "pluginMetaData", "connectorMetaData" }) {
                    String xml = "<" + root + ">" + testCase[0] + "</" + root + ">";
                    assertEquals(xml, testCase[2], MirthLauncher.isExtensionCompatible(parse(xml), serverVersion));
                    assertEquals(xml, testCase[2], ExtensionLoader.getInstance().isExtensionCompatible(serializer.deserialize(xml, MetaData.class)));
                }
            }
        }
    }

    @Test
    public void launcherOnlyReadsDirectMetadataChildren() throws Exception {
        assertFalse(MirthLauncher.isExtensionCompatible(parse("<pluginMetaData><nested>"
                + "<mirthVersion>4.5.2</mirthVersion><minExtensionApiVersion>1.0.0</minExtensionApiVersion>"
                + "</nested></pluginMetaData>"), "4.5.2"));
        assertTrue(MirthLauncher.isExtensionCompatible(parse("<pluginMetaData><mirthVersion>4.5.2</mirthVersion>"
                + "<nested><minExtensionApiVersion>invalid</minExtensionApiVersion></nested></pluginMetaData>"), "4.5.2"));
    }

    @Test
    public void unavailableReleaseMetadataRetainsLoaderFailureBehavior() throws Exception {
        try (MockedStatic<ResourceUtil> resources = mockStatic(ResourceUtil.class)) {
            resources.when(() -> ResourceUtil.getResourceStream(ExtensionLoader.class, "version.properties"))
                    .thenThrow(new FileNotFoundException("Simulated unavailable release metadata"));
            PluginMetaData metadata = new PluginMetaData();
            metadata.setMirthVersion("4.5.2");
            assertFalse(ExtensionLoader.getInstance().isExtensionCompatible(metadata));
            metadata.setMinExtensionApiVersion("1.0.0");
            assertFalse(ExtensionLoader.getInstance().isExtensionCompatible(metadata));
        }
    }

    private Element parse(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }
}
