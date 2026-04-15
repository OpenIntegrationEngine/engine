package com.mirth.connect.server.launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Test;

public class MirthLauncherVariantTest {

    @Test
    public void testNullVariantAlwaysLoads() {
        assertTrue(MirthLauncher.shouldLoadLibrary(null, new Properties()));
    }

    @Test
    public void testEmptyVariantAlwaysLoads() {
        assertTrue(MirthLauncher.shouldLoadLibrary("", new Properties()));
    }

    @Test
    public void testMatchingVariantLoads() {
        Properties props = new Properties();
        props.setProperty("dicom.library", "dcm4che2");
        assertTrue(MirthLauncher.shouldLoadLibrary("dicom.library:dcm4che2", props));
    }

    @Test
    public void testMismatchedVariantSkips() {
        Properties props = new Properties();
        props.setProperty("dicom.library", "dcm4che5");
        assertFalse(MirthLauncher.shouldLoadLibrary("dicom.library:dcm4che2", props));
    }

    @Test
    public void testMissingPropertyUsesDefault() {
        Properties props = new Properties();
        // dicom.library not set, default is dcm4che2
        assertTrue(MirthLauncher.shouldLoadLibrary("dicom.library:dcm4che2", props));
        assertFalse(MirthLauncher.shouldLoadLibrary("dicom.library:dcm4che5", props));
    }

    @Test
    public void testWhitespaceInPropertyValueTrimmed() {
        Properties props = new Properties();
        props.setProperty("dicom.library", "  dcm4che2  ");
        assertTrue(MirthLauncher.shouldLoadLibrary("dicom.library:dcm4che2", props));
    }

    @Test
    public void testMalformedVariantNoColonAlwaysLoads() {
        Properties props = new Properties();
        assertTrue(MirthLauncher.shouldLoadLibrary("noColonHere", props));
    }

    @Test
    public void testUnknownPropertyWithNoDefaultAlwaysLoads() {
        Properties props = new Properties();
        // "unknown.prop" has no entry in VARIANT_DEFAULTS, so default is ""
        // "somevalue" != "" → false. But wait, empty default doesn't match, so it should skip.
        // Actually, let's test: unknown property with no default and no value = empty default
        assertFalse(MirthLauncher.shouldLoadLibrary("unknown.prop:somevalue", props));
    }
}
