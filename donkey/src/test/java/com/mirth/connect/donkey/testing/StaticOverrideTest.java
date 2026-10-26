// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.donkey.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class StaticOverrideTest {

    private static class Holder {
        private static String instance;
        private static volatile String volatileInstance;
        private static final String CONSTANT = "constant";
        private String nonStatic;
    }

    @After
    public void reset() {
        Holder.instance = null;
        Holder.volatileInstance = null;
    }

    @Test
    public void installsAndRestoresPreviousValue() throws Exception {
        Holder.instance = "original";

        AutoCloseable handle = StaticOverride.install(Holder.class, "instance", "replacement");
        assertEquals("replacement", Holder.instance);

        handle.close();
        assertEquals("original", Holder.instance);
    }

    @Test
    public void restoresNull() throws Exception {
        AutoCloseable handle = StaticOverride.install(Holder.class, "instance", "replacement");
        handle.close();

        assertNull(Holder.instance);
    }

    @Test
    public void worksOnVolatileField() throws Exception {
        try (AutoCloseable handle = StaticOverride.install(Holder.class, "volatileInstance", "replacement")) {
            assertEquals("replacement", Holder.volatileInstance);
        }

        assertNull(Holder.volatileInstance);
    }

    @Test
    public void nestedOverridesRestoreInOrder() throws Exception {
        Holder.instance = "original";

        AutoCloseable outer = StaticOverride.install(Holder.class, "instance", "outer");
        AutoCloseable inner = StaticOverride.install(Holder.class, "instance", "inner");

        inner.close();
        assertEquals("outer", Holder.instance);
        outer.close();
        assertEquals("original", Holder.instance);
    }

    @Test
    public void secondCloseDoesNothing() throws Exception {
        Holder.instance = "original";
        AutoCloseable handle = StaticOverride.install(Holder.class, "instance", "replacement");
        handle.close();

        Holder.instance = "set later";
        handle.close();

        assertEquals("set later", Holder.instance);
    }

    @Test
    public void closeFailsWhenValueWasReplaced() throws Exception {
        AutoCloseable handle = StaticOverride.install(Holder.class, "instance", "replacement");
        Holder.instance = "someone else";

        assertThrows(IllegalStateException.class, handle::close);
        assertEquals("someone else", Holder.instance);
    }

    @Test
    public void closingOutOfOrderFails() throws Exception {
        AutoCloseable outer = StaticOverride.install(Holder.class, "instance", "outer");
        StaticOverride.install(Holder.class, "instance", "inner");

        assertThrows(IllegalStateException.class, outer::close);
    }

    @Test
    public void rejectsFinalField() {
        assertThrows(IllegalArgumentException.class, () -> StaticOverride.install(Holder.class, "CONSTANT", "x"));
        assertSame("constant", Holder.CONSTANT);
    }

    @Test
    public void rejectsNonStaticField() {
        assertThrows(IllegalArgumentException.class, () -> StaticOverride.install(Holder.class, "nonStatic", "x"));
    }

    @Test
    public void rejectsMissingField() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> StaticOverride.install(Holder.class, "missing", "x"));
        assertTrue(e.getCause() instanceof NoSuchFieldException);
    }

    @Test
    public void rejectsWrongType() {
        assertThrows(IllegalArgumentException.class, () -> StaticOverride.install(Holder.class, "instance", 42));
        assertNull(Holder.instance);
    }
}
