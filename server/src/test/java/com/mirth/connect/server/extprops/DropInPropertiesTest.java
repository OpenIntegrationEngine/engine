package com.mirth.connect.server.extprops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DropInPropertiesTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void overlayReturnsBaseWhenDirIsNull() throws Exception {
        PropertiesConfiguration base = new PropertiesConfiguration();
        assertSame(base, DropInProperties.overlay(base, null));
    }

    @Test
    public void overlayReturnsBaseWhenDirIsMissing() throws Exception {
        PropertiesConfiguration base = new PropertiesConfiguration();
        assertSame(base, DropInProperties.overlay(base, new File(tempFolder.getRoot(), "missing.d")));
    }

    @Test
    public void overlayIgnoresNonPropertiesEntries() throws Exception {
        File dir = tempFolder.newFolder();
        writeFile(dir, "README", "not a properties file");
        new File(dir, "subdir.properties").mkdir();

        PropertiesConfiguration base = new PropertiesConfiguration();
        assertSame(base, DropInProperties.overlay(base, dir));
    }

    @Test
    public void overlayOverridesAndAddsWithoutModifyingBase() throws Exception {
        PropertiesConfiguration base = new PropertiesConfiguration();
        base.setProperty("a", "base");
        base.setProperty("b", "kept");

        File dir = tempFolder.newFolder();
        writeFile(dir, "10-first.properties", "a = first\nc = added\n");
        writeFile(dir, "20-second.properties", "a = second\n");

        PropertiesConfiguration merged = DropInProperties.overlay(base, dir);

        assertEquals("second", merged.getString("a"));
        assertEquals("kept", merged.getString("b"));
        assertEquals("added", merged.getString("c"));
        assertEquals("base", base.getString("a"));
        assertFalse(base.containsKey("c"));
    }

    @Test
    public void overlayResultIsIndependentOfBase() throws Exception {
        PropertiesConfiguration base = new PropertiesConfiguration();
        base.setProperty("a", "base");

        File dir = tempFolder.newFolder();
        writeFile(dir, "10-first.properties", "b = dropin\n");

        PropertiesConfiguration merged = DropInProperties.overlay(base, dir);

        // Writing to the merged result (as the controller does when it re-encrypts a password or
        // generates a keystore password) must not leak back into the base configuration that
        // saveMirthConfig() writes out to mirth.properties.
        merged.setProperty("a", "changed");
        merged.setProperty("c", "new");

        assertEquals("base", base.getString("a"));
        assertFalse(base.containsKey("c"));
    }

    @Test
    public void overlayKeepsCommaValuesWhole() throws Exception {
        PropertiesConfiguration base = new PropertiesConfiguration();

        File dir = tempFolder.newFolder();
        writeFile(dir, "10-list.properties", "https.client.protocols = TLSv1.3,TLSv1.2\n");

        PropertiesConfiguration merged = DropInProperties.overlay(base, dir);

        assertEquals("TLSv1.3,TLSv1.2", merged.getString("https.client.protocols"));
    }

    @Test
    public void loadOverridesInLexicalOrder() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("a", "base");

        File dir = tempFolder.newFolder();
        writeFile(dir, "20-second.properties", "a = second\n");
        writeFile(dir, "10-first.properties", "a = first\nb = added\n");

        DropInProperties.load(properties, dir, new LoggerWrapper(null));

        assertEquals("second", properties.getProperty("a"));
        assertEquals("added", properties.getProperty("b"));
    }

    @Test
    public void loadIsNoOpWhenDirIsMissing() {
        Properties properties = new Properties();
        properties.setProperty("a", "base");

        DropInProperties.load(properties, new File(tempFolder.getRoot(), "missing.d"), new LoggerWrapper(null));

        assertEquals("base", properties.getProperty("a"));
        assertEquals(1, properties.size());
    }

    private void writeFile(File dir, String name, String content) throws Exception {
        Files.write(new File(dir, name).toPath(), content.getBytes(StandardCharsets.ISO_8859_1));
    }
}
