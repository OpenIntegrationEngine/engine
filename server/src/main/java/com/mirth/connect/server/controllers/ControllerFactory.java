/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.controllers;

import com.google.common.annotations.VisibleForTesting;

public abstract class ControllerFactory {
    private static ControllerFactory factory;

    public static ControllerFactory getFactory() {
        synchronized (ControllerFactory.class) {
            if (factory == null) {
                factory = new DefaultControllerFactory();
            }

            return factory;
        }
    }

    /** Replaces the factory {@link #getFactory()} hands out until the returned handle is closed. */
    @VisibleForTesting
    public static AutoCloseable setFactoryForTesting(ControllerFactory newFactory) {
        synchronized (ControllerFactory.class) {
            ControllerFactory previous = factory;
            factory = newFactory;

            return () -> {
                synchronized (ControllerFactory.class) {
                    if (factory != newFactory) {
                        throw new IllegalStateException("The ControllerFactory was replaced by " + factory
                                + " while " + newFactory + " was installed for testing.");
                    }

                    factory = previous;
                }
            };
        }
    }

    public abstract AuthorizationController createAuthorizationController();

    public abstract AlertController createAlertController();

    public abstract ChannelController createChannelController();

    public abstract CodeTemplateController createCodeTemplateController();

    public abstract ConfigurationController createConfigurationController();
    
    public abstract DebugUsageController createDebugUsageController();

    public abstract EngineController createEngineController();

    public abstract EventController createEventController();

    public abstract ExtensionController createExtensionController();

    public abstract MessageController createMessageController();

    public abstract MigrationController createMigrationController();

    public abstract ScriptController createScriptController();

    public abstract UsageController createUsageController();

    public abstract UserController createUserController();

    public abstract DatabaseTaskController createDatabaseTaskController();

    public abstract ContextFactoryController createContextFactoryController();
}
