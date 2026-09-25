/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.userutil;

import java.util.Set;

import com.mirth.connect.server.util.javascript.MirthContextFactory;

/**
 * Allows the user to retrieve information about the current JavaScript context.
 */
public class ContextFactory {

    private MirthContextFactory delegate;

    /**
     * Instantiates a new ContextFactory object.
     * 
     * @param delegate
     *            The underlying ContextFactory this class will delegate to.
     */
    public ContextFactory(MirthContextFactory delegate) {
        this.delegate = delegate;
    }

    /**
     * Returns the set of custom resource IDs that the current JavaScript context is using. If no
     * custom libraries are being used in the current JavaScript context, this will return an empty
     * set.
     * 
     * @return The set of custom resource IDs that the current JavaScript context is using.
     */
    public Set<String> getResourceIds() {
        return delegate.getResourceIds();
    }

    /**
     * Returns the application classloader that the current JavaScript context is using.
     * 
     * @return The application classloader that the current JavaScript context is using.
     */
    public ClassLoader getClassLoader() {
        return delegate.getApplicationClassLoader();
    }

    /**
     * Returns a classloader containing only the libraries in the custom resources assigned to the
     * current context. Use it to load classes from those libraries in isolation, for example a
     * specific JDBC driver version, without interference from the versions the server itself ships.
     * <p>
     * Core Java classes (for example {@code java.sql} or {@code javax.xml}) are visible through this
     * classloader, but classes from the server or its plugins are not (its parent is
     * {@link ClassLoader#getPlatformClassLoader()}). A class that exists in both a custom resource
     * and the JDK resolves to the JDK's copy. The "Load Parent-First" option on a resource does not
     * affect this classloader; it applies only to the classloader returned by {@link #getClassLoader()}.
     *
     * @return A classloader containing only the custom resource libraries, or null if the current
     *         context has no custom resources.
     */
    public ClassLoader getIsolatedClassLoader() {
        return delegate.getIsolatedClassLoader();
    }
}