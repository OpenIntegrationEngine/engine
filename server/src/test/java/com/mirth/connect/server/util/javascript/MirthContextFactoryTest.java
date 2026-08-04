// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.server.util.javascript;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.util.HashSet;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.server.builders.JavaScriptBuilder;
import com.mirth.connect.server.controllers.CodeTemplateController;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.controllers.ExtensionController;

public class MirthContextFactoryTest {

    @BeforeClass
    public static void setUpBeforeClass() {
        // Same mocked ControllerFactory pattern as JavaScriptUtilTest, so this class is
        // self-sufficient regardless of which test classes ran (and injected) before it.
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        EventController eventController = mock(EventController.class);
        when(controllerFactory.createEventController()).thenReturn(eventController);

        ConfigurationController configurationController = mock(ConfigurationController.class);
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        ExtensionController extensionController = mock(ExtensionController.class);
        when(controllerFactory.createExtensionController()).thenReturn(extensionController);

        CodeTemplateController codeTemplateController = mock(CodeTemplateController.class);
        when(controllerFactory.createCodeTemplateController()).thenReturn(codeTemplateController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);

        JavaScriptBuilder.setControllersForTesting(extensionController, codeTemplateController);
    }

    /*
     * Regression test for #338: with a null parent, the isolated classloader cannot see java.sql
     * on Java 9+, so custom driver resources failed to deploy. The parent must be the platform
     * classloader: JRE classes visible, server classpath not.
     */
    @Test
    public void isolatedClassLoaderResolvesPlatformButNotServerClasses() throws Exception {
        // This jar URL never gets read; it only exists because getIsolatedClassLoader() returns null on empty array.
        URL dummyJar = new File("build/tmp/mirth-context-factory-test-dummy.jar").toURI().toURL();
        MirthContextFactory contextFactory = new MirthContextFactory(new URL[] { dummyJar }, new HashSet<>(), false);

        ClassLoader isolated = contextFactory.getIsolatedClassLoader();
        assertNotNull(isolated);

        // Fails with ClassNotFoundException if the parent ever goes back to null
        isolated.loadClass("java.sql.Driver");

        // Fails if the parent is ever widened to a loader that can see the server classpath
        assertThrows(ClassNotFoundException.class, () -> isolated.loadClass(MirthContextFactory.class.getName()));
    }

    @Test
    public void isolatedClassLoaderIsNullWithoutResources() {
        MirthContextFactory contextFactory = new MirthContextFactory(new URL[0], new HashSet<>(), false);
        assertNull(contextFactory.getIsolatedClassLoader());
    }
}
