/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.server.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.mirth.connect.client.core.ExtensionDependencies;
import com.mirth.connect.client.core.ExtensionDependencies.Extension;
import com.mirth.connect.model.MetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.extprops.LoggerWrapper;

public class ExtensionDependenciesTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private Object originalLogger;

    @Before
    public void captureLogging() throws Exception {
        Field field = MirthLauncher.class.getDeclaredField("logger");
        field.setAccessible(true);
        originalLogger = field.get(null);
        field.set(null, mock(LoggerWrapper.class));
    }

    @After
    public void restoreLogging() throws Exception {
        Field field = MirthLauncher.class.getDeclaredField("logger");
        field.setAccessible(true);
        field.set(null, originalLogger);
    }

    @Test
    public void serializedMetadataAndLauncherResolveTheSameGraph() throws Exception {
        ObjectXMLSerializer serializer = new ObjectXMLSerializer(getClass().getClassLoader());
        List<String> xml = Arrays.asList(
                metadata("pluginMetaData", "Consumer", "1.0.0", engine() + requires("Provider", "1.1.0")),
                metadata("pluginMetaData", "Provider", "1.2.0", engine()),
                metadata("connectorMetaData", "Connector", "1.0.0", engine() + requires("Consumer", "1.0.0")));
        for (boolean providerEnabled : new boolean[] { true, false }) {
            List<Extension> fromModel = new ArrayList<>();
            List<Extension> fromLauncher = new ArrayList<>();
            for (String source : xml) {
                MetaData metadata = serializer.deserialize(source, MetaData.class);
                boolean enabled = providerEnabled || !"Provider".equals(metadata.getName());
                fromModel.add(new Extension(metadata.getName(), metadata instanceof PluginMetaData,
                        metadata.getPluginVersion(), metadata.getMirthVersion(), metadata.getMinExtensionApiVersion(),
                        metadata.getDependencies(), enabled));
                fromLauncher.add(MirthLauncher.readExtension(parse(serializer.serialize(metadata)), enabled));
            }
            Set<String> expected = providerEnabled ? Collections.emptySet() : new HashSet<>(Arrays.asList("Consumer", "Connector"));
            assertEquals(expected, invalidNames(fromModel));
            assertEquals(expected, invalidNames(fromLauncher));
        }
    }

    @Test
    public void typedEngineRequirementsUseTheSameValidationAsSerializedMetadata() throws Exception {
        ObjectXMLSerializer serializer = new ObjectXMLSerializer(getClass().getClassLoader());
        String[] requirements = {
                engine(),
                "<dependency type=\"engine-api\" minVersion=\"2.0.0\"/>",
                "<dependency type=\"engine-api\" minVersion=\"1.0.1\"/>",
                "<dependency type=\"engine-api\" minVersion=\"01.0.0\"/>",
                "<dependency type=\"engine-api\" name=\"Engine\" minVersion=\"1.0.0\"/>",
                engine() + engine(),
                "<dependency type=\"unknown\" minVersion=\"1.0.0\"/>",
                "<dependency type=\"plugin\" name=\"Provider\"/>"
        };
        for (String requirement : requirements) {
            for (String root : new String[] { "pluginMetaData", "connectorMetaData" }) {
                String xml = metadata(root, "Example", "1.0.0", requirement);
                MetaData model = serializer.deserialize(xml, MetaData.class);
                Extension extension = new Extension(model.getName(), model instanceof PluginMetaData,
                        model.getPluginVersion(), model.getMirthVersion(), model.getMinExtensionApiVersion(),
                        model.getDependencies(), true);
                assertEquals(xml, ExtensionDependencies.getEngineError(extension, "99.0.0") == null,
                        MirthLauncher.isExtensionCompatible(parse(xml), "99.0.0"));
            }
        }
        assertTrue(MirthLauncher.isExtensionCompatible(parse(metadata("pluginMetaData", "Example", "1.0.0", engine())), "99.0.0"));
        assertFalse(MirthLauncher.isExtensionCompatible(parse(metadata("pluginMetaData", "Example", "1.0.0", "")), "99.0.0"));
    }

    @Test
    public void malformedDeclarationsCannotDisappearDuringBootstrap() throws Exception {
        ObjectXMLSerializer serializer = new ObjectXMLSerializer(getClass().getClassLoader());
        String[] declarations = {
                "<null/>",
                "<dependency class=\"null\"/>",
                "unexpected" + engine(),
                "<dependency type=\"engine-api\" minVersion=\"1.0.0\" extra=\"ignored\"/>",
                "<dependency><type>engine-api</type><minVersion>1.0.0</minVersion></dependency>",
                "<dependency type=\"engine-api\" minVersion=\"1.0.0\">unexpected</dependency>",
                "<unknown type=\"engine-api\" minVersion=\"1.0.0\"/>",
                "<dependency type=\"engine-api\" minVersion=\"1.0.0\"><name>ignored</name></dependency>"
        };
        for (String declaration : declarations) {
            String xml = metadata("pluginMetaData", "Example", "1.0.0", declaration);
            assertFalse(declaration, MirthLauncher.isExtensionCompatible(parse(xml), "4.5.2"));
            try {
                serializer.deserialize(xml, MetaData.class);
                fail("Malformed dependencies must also fail server deserialization: " + declaration);
            } catch (com.mirth.connect.donkey.util.xstream.SerializerException expected) {
                // Both metadata readers must fail closed before an extension can load.
            }
        }
        assertFalse(MirthLauncher.isExtensionCompatible(parse("<pluginMetaData><mirthVersion>4.5.2</mirthVersion>"
                + "<dependencies/><dependencies>" + engine() + "</dependencies></pluginMetaData>"), "4.5.2"));
        String nullList = "<pluginMetaData><mirthVersion>4.5.2</mirthVersion>"
                + "<dependencies class=\"null\">" + engine() + "</dependencies></pluginMetaData>";
        assertNull(serializer.deserialize(nullList, MetaData.class).getDependencies());
        assertTrue(MirthLauncher.isExtensionCompatible(parse(nullList), "4.5.2"));
        assertFalse(MirthLauncher.isExtensionCompatible(parse(nullList), "99.0.0"));
    }

    @Test
    public void metadataOverridesAndDuplicateVersionsCannotBypassPluginValidation() throws Exception {
        ObjectXMLSerializer serializer = new ObjectXMLSerializer(getClass().getClassLoader());
        String source = metadata("pluginMetaData", "Provider", "1.0.0", engine());
        String connectorOverride = source.replace("<pluginMetaData>", "<pluginMetaData resolves-to=\"connectorMetaData\">");
        assertFalse(serializer.deserialize(connectorOverride, MetaData.class) instanceof PluginMetaData);
        assertFalse(MirthLauncher.isExtensionCompatible(parse(connectorOverride), "4.5.2"));
        for (String field : new String[] { "name", "pluginVersion", "mirthVersion", "minExtensionApiVersion" }) {
            String duplicates = source.replace("</pluginMetaData>", "<" + field + ">1.0.0</" + field + ">"
                    + "<" + field + ">2.0.0</" + field + "></pluginMetaData>");
            assertFalse(field, MirthLauncher.isExtensionCompatible(parse(duplicates), "4.5.2"));
            try {
                serializer.deserialize(duplicates, MetaData.class);
                fail("Duplicate metadata must fail server deserialization: " + field);
            } catch (com.mirth.connect.donkey.util.xstream.SerializerException expected) {
                // The launcher must not choose one of multiple provider identities or versions.
            }
        }
    }

    @Test
    public void classpathResolutionDoesNotDependOnDiscoveryOrderAndFiltersLibraryTypes() throws Exception {
        for (boolean consumerFirst : new boolean[] { true, false }) {
            File root = temporary.newFolder();
            if (consumerFirst) {
                write(root, "consumer", "Consumer", "1.0.0", engine() + requires("Provider", "1.0.0"));
            }
            write(root, "provider", "Provider", "1.2.0", engine());
            if (!consumerFirst) {
                write(root, "consumer", "Consumer", "1.0.0", engine() + requires("Provider", "1.0.0"));
            }
            assertLibraries(root, Collections.emptySet(), "consumer", "provider");
        }
    }

    @Test
    public void missingDisabledAndIncompatibleProvidersExcludeAllDependents() throws Exception {
        for (String failure : Arrays.asList("missing", "disabled", "plugin-version", "engine-version")) {
            File root = temporary.newFolder();
            write(root, "consumer", "Consumer", "1.0.0", engine() + requires("Middle", "1.0.0"));
            write(root, "middle", "Middle", "1.0.0", engine() + requires("Provider", "1.0.0"));
            write(root, "independent", "Independent", "1.0.0", engine());
            if (!failure.equals("missing")) {
                write(root, "provider", "Provider", failure.equals("plugin-version") ? "2.0.0" : "1.0.0",
                        failure.equals("engine-version") ? "<dependency type=\"engine-api\" minVersion=\"2.0.0\"/>" : engine());
            }
            assertLibraries(root, failure.equals("disabled") ? Collections.singleton("Provider") : Collections.emptySet(),
                    failure.equals("plugin-version") ? new String[] { "independent", "provider" } : new String[] { "independent" });
        }
    }

    @Test
    public void cyclesAndAmbiguousProvidersExcludeTheirDependents() throws Exception {
        File cycle = temporary.newFolder();
        write(cycle, "a", "A", "1.0.0", engine() + requires("B", "1.0.0"));
        write(cycle, "b", "B", "1.0.0", engine() + requires("A", "1.0.0"));
        write(cycle, "consumer", "Consumer", "1.0.0", engine() + requires("A", "1.0.0"));
        write(cycle, "independent", "Independent", "1.0.0", engine());
        assertLibraries(cycle, Collections.emptySet(), "independent");

        File duplicate = temporary.newFolder();
        write(duplicate, "provider1", "Provider", "1.0.0", engine());
        write(duplicate, "provider2", "Provider", "1.0.0", engine());
        write(duplicate, "consumer", "Consumer", "1.0.0", engine() + requires("Provider", "1.0.0"));
        assertLibraries(duplicate, Collections.emptySet());
    }

    @Test
    public void connectorsAndUnparseablePluginsCannotSatisfyPluginRequirements() throws Exception {
        for (boolean connector : new boolean[] { true, false }) {
            File root = temporary.newFolder();
            write(root, "consumer", "Consumer", "1.0.0", engine() + requires("Provider", "1.0.0"));
            File provider = new File(root, "provider");
            assertTrue(provider.mkdir());
            String xml = connector ? metadata("connectorMetaData", "Provider", "1.0.0", engine())
                    : metadata("pluginMetaData", "Provider", "1.0.0", "<dependency type=\"engine-api\" minVersion=\"1.0.0\" unknown=\"x\"/>");
            writeFiles(provider, connector ? "source.xml" : "plugin.xml", xml);
            assertLibraries(root, Collections.emptySet(), connector ? new String[] { "provider" } : new String[0]);
        }
    }

    @Test
    public void malformedDescriptorIdentityDoesNotAffectUnrelatedExtensions() throws Exception {
        String[] invalid = {
                "<null/>",
                "<string>not metadata</string>",
                metadata("pluginMetaData", "Provider", "1.0.0", engine()).replace("<name>Provider</name>", ""),
                metadata("pluginMetaData", " ", "1.0.0", engine())
        };
        for (String descriptor : invalid) {
            File root = temporary.newFolder();
            File broken = new File(root, "broken");
            assertTrue(broken.mkdir());
            writeFiles(broken, "plugin.xml", descriptor);
            write(root, "independent", "Independent", "1.0.0", engine());
            assertLibraries(root, Collections.emptySet(), "independent");
        }
    }

    private Set<String> invalidNames(List<Extension> extensions) {
        return ExtensionDependencies.validate(extensions, "99.0.0").keySet().stream().map(Extension::getName).collect(Collectors.toSet());
    }

    private void assertLibraries(File root, Set<String> disabled, String... names) throws Exception {
        List<URL> urls = new ArrayList<>();
        MirthLauncher.addExtensionsToClasspath(urls, "4.5.2", root, name -> !disabled.contains(name));
        Set<URL> expected = new HashSet<>();
        for (String name : names) {
            expected.add(new File(root, name + "/server.jar").toURI().toURL());
            expected.add(new File(root, name + "/shared.jar").toURI().toURL());
        }
        assertEquals(expected, new HashSet<>(urls));
        assertEquals("Libraries must not be added more than once", expected.size(), urls.size());
    }

    private void write(File root, String path, String name, String version, String dependencies) throws Exception {
        File directory = new File(root, path);
        assertTrue(directory.mkdir());
        writeFiles(directory, "plugin.xml", metadata("pluginMetaData", name, version, dependencies));
    }

    private void writeFiles(File directory, String metadataName, String xml) throws Exception {
        Files.write(new File(directory, metadataName).toPath(), xml.getBytes(StandardCharsets.UTF_8));
        for (String library : Arrays.asList("server.jar", "shared.jar", "client.jar")) {
            Files.write(new File(directory, library).toPath(), new byte[0]);
        }
    }

    private String metadata(String root, String name, String version, String dependencies) {
        return "<" + root + "><name>" + name + "</name><pluginVersion>" + version + "</pluginVersion>"
                + "<mirthVersion>4.5.2</mirthVersion><dependencies>" + dependencies + "</dependencies>"
                + "<library type=\"SERVER\" path=\"server.jar\"/><library type=\"SHARED\" path=\"shared.jar\"/>"
                + "<library type=\"CLIENT\" path=\"client.jar\"/></" + root + ">";
    }

    private String engine() {
        return "<dependency type=\"engine-api\" minVersion=\"1.0.0\"/>";
    }

    private String requires(String name, String minimum) {
        return "<dependency type=\"plugin\" name=\"" + name + "\" minVersion=\"" + minimum + "\"/>";
    }

    private Element parse(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }
}
