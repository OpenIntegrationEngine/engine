/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.MetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.extprops.ExtensionStatuses;
import com.mirth.connect.server.tools.ClassPathResource;

public class ExtensionLoaderTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private final ObjectXMLSerializer serializer = new ObjectXMLSerializer(getClass().getClassLoader());
    private final ExtensionLoader loader = new ExtensionLoader(serializer) {
        @Override
        protected String getServerVersion() {
            return "4.6.0.123";
        }
    };

    @Test
    public void validatesCanonicalPluginAndConnectorMetadata() throws Exception {
        for (String root : List.of("pluginMetaData", "connectorMetaData")) {
            for (String version : List.of("1.0.0", "1.0.1", "2.0.0", "", "invalid")) {
                MetaData metadata = parse(root, "Example", engine(version));
                assertEquals(version, version.equals("1.0.0"), loader.isExtensionCompatible(metadata));
                assertEquals(version, version.equals("1.0.0"),
                        loader.getCompatibilityErrors(List.of(metadata), name -> true).isEmpty());
            }
            MetaData legacy = parse(root, "Legacy", "");
            assertTrue(loader.isExtensionCompatible(legacy));
            legacy.setMirthVersion("4.5.2");
            assertFalse(loader.isExtensionCompatible(legacy));
        }
    }

    @Test
    public void invalidDependenciesAreIsolatedFromUnrelatedMetadata() throws Exception {
        for (String invalid : List.of("<null/>", "<string>not a dependency</string>",
                "<dependency type=\"engine-api\" minVersion=\"invalid\"/>")) {
            MetaData broken = parse("pluginMetaData", "Broken", invalid);
            MetaData consumer = parse("pluginMetaData", "Consumer", engine("1.0.0") + requires("Broken"));
            MetaData good = parse("pluginMetaData", "Good", engine("1.0.0"));
            assertEquals(Set.of("Broken", "Consumer"),
                    names(loader.getCompatibilityErrors(List.of(broken, consumer, good), name -> true)));
        }
    }

    @Test
    public void providerIdentityUsesTheDeserializedType() throws Exception {
        MetaData provider = serializer.deserialize(xml("pluginMetaData", "Provider", engine("1.0.0"))
                .replace("<pluginMetaData>", "<pluginMetaData resolves-to=\"connectorMetaData\">"), MetaData.class);
        MetaData consumer = parse("pluginMetaData", "Consumer", engine("1.0.0") + requires("Provider"));
        assertFalse(provider instanceof PluginMetaData);
        assertEquals(Set.of("Consumer"), names(loader.getCompatibilityErrors(List.of(provider, consumer), name -> true)));
    }

    @Test
    public void statusFailureRejectsAffectedProviderAndConsumersOnly() throws Exception {
        MetaData broken = parse("pluginMetaData", "Broken", engine("1.0.0"));
        MetaData consumer = parse("pluginMetaData", "Consumer", engine("1.0.0") + requires("Broken"));
        MetaData good = parse("pluginMetaData", "Good", engine("1.0.0"));
        assertEquals(Set.of("Broken", "Consumer"), names(loader.getCompatibilityErrors(
                List.of(broken, consumer, good), name -> {
                    if (name.equals("Broken")) {
                        throw new IllegalStateException("Unavailable status");
                    }
                    return true;
                })));
    }

    @Test
    public void unavailableVersionFailsValidation() throws Exception {
        ExtensionLoader unavailable = new ExtensionLoader(serializer) {
            @Override
            protected String getServerVersion() throws FileNotFoundException {
                throw new FileNotFoundException("Unavailable release metadata");
            }
        };
        MetaData metadata = parse("pluginMetaData", "Example", engine("1.0.0"));
        assertFalse(unavailable.isExtensionCompatible(metadata));
        assertThrows(ControllerException.class, () -> unavailable.getCompatibilityErrors(List.of(metadata), name -> true));
    }

    @Test
    public void startupExposesOnlyAcceptedMetadataAndIsolatesUnreadableDescriptors() throws Exception {
        Path root = temporary.newFolder("extensions").toPath();
        write(root, "good/plugin.xml", xml("pluginMetaData", "Good", engine("1.0.0")));
        write(root, "provider/plugin.xml", xml("pluginMetaData", "Provider", engine("2.0.0")));
        write(root, "consumer/plugin.xml", xml("pluginMetaData", "Consumer", engine("1.0.0") + requires("Provider")));
        write(root, "connector/SOURCE.XML", xml("connectorMetaData", "Connector", engine("1.0.0") + requires("Good")));
        write(root, "broken/plugin.xml", xml("pluginMetaData", "Broken", engine("1.0.0")));
        write(root, "null/plugin.xml", "<null/>");
        write(root, "foreign/plugin.xml", "<string>not metadata</string>");
        write(root, "nameless/plugin.xml", xml("pluginMetaData", " ", engine("1.0.0")));
        write(root, "install_temp/plugin.xml", xml("pluginMetaData", "Pending", engine("1.0.0")));
        write(root, ".install-work/plugin.xml", xml("pluginMetaData", "Incomplete", engine("1.0.0")));
        try (MockedStatic<ClassPathResource> paths = mockStatic(ClassPathResource.class);
                MockedStatic<ExtensionStatuses> statuses = mockStatic(ExtensionStatuses.class)) {
            paths.when(() -> ClassPathResource.getResourceURI("extensions")).thenReturn(root.toUri());
            ExtensionStatuses status = mock(ExtensionStatuses.class);
            when(status.isEnabled(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
            when(status.isEnabled("Broken")).thenThrow(new IllegalStateException("Unavailable status"));
            statuses.when(ExtensionStatuses::getInstance).thenReturn(status);
            assertEquals(Set.of("Good"), loader.getPluginMetaData().keySet());
            assertEquals(Set.of("Connector"), loader.getConnectorMetaData().keySet());
            assertEquals(Set.of("example"), loader.getConnectorProtocols().keySet());
            assertEquals(Set.of("Provider", "Consumer", "Broken"), loader.getInvalidMetaData().keySet());
        }
    }

    private void write(Path root, String path, String xml) throws Exception {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, xml);
    }

    private Set<String> names(Map<MetaData, String> errors) {
        return errors.keySet().stream().map(MetaData::getName).collect(Collectors.toSet());
    }

    private MetaData parse(String root, String name, String dependencies) {
        return serializer.deserialize(xml(root, name, dependencies), MetaData.class);
    }

    private String xml(String root, String name, String dependencies) {
        return "<" + root + "><name>" + name + "</name><pluginVersion>1.0.0</pluginVersion>"
                + "<mirthVersion>4.6.0</mirthVersion><dependencies>" + dependencies + "</dependencies>"
                + (root.equals("connectorMetaData") ? "<protocol>example</protocol>" : "") + "</" + root + ">";
    }

    private String engine(String version) {
        return "<dependency type=\"engine-api\" minVersion=\"" + version + "\"/>";
    }

    private String requires(String name) {
        return "<dependency type=\"plugin\" name=\"" + name + "\" minVersion=\"1.0.0\"/>";
    }
}
