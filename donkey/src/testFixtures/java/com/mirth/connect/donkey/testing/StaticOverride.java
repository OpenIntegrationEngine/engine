// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.donkey.testing;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temporarily replaces the value of a static field, such as a singleton instance, for the duration
 * of a test.
 */
public final class StaticOverride implements AutoCloseable {
    private final Class<?> owner;
    private final Field field;
    private final Object value;
    private final Object previous;
    private final AtomicBoolean closed = new AtomicBoolean();

    private StaticOverride(Class<?> owner, Field field, Object value, Object previous) {
        this.owner = owner;
        this.field = field;
        this.value = value;
        this.previous = previous;
    }

    /**
     * Sets a static field to {@code value} until the returned handle is closed, then puts the
     * previous value back. Closing more than once does nothing.
     *
     * @throws IllegalArgumentException
     *             if the field does not exist, is not a non-final static field, or cannot hold
     *             {@code value}
     */
    public static StaticOverride install(Class<?> owner, String fieldName, Object value) {
        Field field;
        try {
            field = owner.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(owner.getName() + " has no field named " + fieldName, e);
        }

        int modifiers = field.getModifiers();
        if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
            throw new IllegalArgumentException(owner.getName() + "." + fieldName + " is not a non-final static field");
        }
        field.setAccessible(true);

        synchronized (owner) {
            Object previous = get(field);
            set(field, value);
            return new StaticOverride(owner, field, value, previous);
        }
    }

    /**
     * Puts back the value the field had before this override was installed.
     *
     * @throws IllegalStateException
     *             if something else replaced the field's value after this override was installed
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        synchronized (owner) {
            Object current = get(field);
            if (current != value) {
                throw new IllegalStateException(owner.getName() + "." + field.getName() + " was replaced by " + current + " while " + value + " was installed");
            }
            set(field, previous);
        }
    }

    private static Object get(Field field) {
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Field field, Object value) {
        try {
            field.set(null, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
