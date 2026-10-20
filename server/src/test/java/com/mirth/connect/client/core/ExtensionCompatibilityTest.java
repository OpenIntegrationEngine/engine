/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.client.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExtensionCompatibilityTest {

    @Test
    public void legacyMetadataKeepsExactReleaseMatching() {
        assertTrue(ExtensionCompatibility.isCompatible("4.5.1, 4.5.2 ", null, "4.5.2"));
        assertTrue(ExtensionCompatibility.isCompatible("4.5.2", null, "4.5.2.123"));
        assertFalse(ExtensionCompatibility.isCompatible("4.5.2", null, "4.5.3"));
        assertFalse(ExtensionCompatibility.isCompatible("4.5.2.123", null, "4.5.2.123"));
        assertFalse(ExtensionCompatibility.isCompatible("4.5.*", null, "4.5.2"));
        assertFalse(ExtensionCompatibility.isCompatible(null, null, "4.5.2"));
        assertFalse(ExtensionCompatibility.isCompatible("", null, "4.5.2"));
        assertFalse(ExtensionCompatibility.isCompatible("4.5.2", null, null));
    }

    @Test
    public void apiLockDoesNotDependOnTheProductRelease() {
        assertTrue(ExtensionCompatibility.isCompatible("4.5.2", "1.0.0", "99.0.0"));
        assertTrue(ExtensionCompatibility.isCompatible(null, " 1.0.0 ", null));
    }

    @Test
    public void presentApiLockNeverFallsBackToMatchingLegacyRelease() {
        for (String minimum : new String[] { "", " ", "invalid", "1.0.1", "2.0.0" }) {
            assertFalse(minimum, ExtensionCompatibility.isCompatible("4.5.2", minimum, "4.5.2"));
        }
    }

    @Test
    public void apiComparisonIsNumericAndRequiresTheSameMajorVersion() {
        assertTrue(ExtensionCompatibility.isApiCompatible("1.2.3", "1.2.3"));
        assertTrue(ExtensionCompatibility.isApiCompatible("1.2.3", "1.2.10"));
        assertTrue(ExtensionCompatibility.isApiCompatible("1.2.99", "1.10.0"));
        assertTrue(ExtensionCompatibility.isApiCompatible(" 1.2.3 ", " 1.2.4 "));
        assertTrue(ExtensionCompatibility.isApiCompatible("1.0.0", "1.2147483647.2147483647"));
        assertFalse(ExtensionCompatibility.isApiCompatible("1.2.4", "1.2.3"));
        assertFalse(ExtensionCompatibility.isApiCompatible("1.10.0", "1.2.99"));
        assertFalse(ExtensionCompatibility.isApiCompatible("1.0.0", "2.0.0"));
        assertFalse(ExtensionCompatibility.isApiCompatible("2.0.0", "1.0.0"));
    }

    @Test
    public void invalidApiVersionsFailClosedOnEitherSide() {
        for (String invalid : new String[] { null, "", " ", "1", "1.0", "1.0.0.0", "1.0.0.",
                "01.0.0", "1.00.0", "1.0.00", "-1.0.0", "1.-1.0", "1.0.-1", "+1.0.0",
                "1.0.0-beta", "1.0.0+build", "1. 0.0", "1.0.*", "1.0.0,1.0.1",
                "2147483648.0.0", "1.2147483648.0", "1.0.2147483648" }) {
            assertFalse(String.valueOf(invalid), ExtensionCompatibility.isApiCompatible(invalid, "1.0.0"));
            assertFalse(String.valueOf(invalid), ExtensionCompatibility.isApiCompatible("1.0.0", invalid));
        }
    }
}
