/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;

public class TemplateValueReplacerTest {

    private TemplateValueReplacer templateReplacer;

    private static AutoCloseable factoryOverride;

    /** replaceValues reads the configuration map off the ControllerFactory singleton. */
    @BeforeClass
    public static void setUpBeforeClass() {
        ConfigurationController configurationController = mock(ConfigurationController.class);
        when(configurationController.getConfigurationMap()).thenReturn(new HashMap<String, String>());

        ControllerFactory controllerFactory = mock(ControllerFactory.class);
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        factoryOverride = ControllerFactory.setFactoryForTesting(controllerFactory);
    }

    @AfterClass
    public static void restoreControllerFactory() throws Exception {
        factoryOverride.close();
    }

    @Before
    public void setUp() throws Exception {
        templateReplacer = new TestTemplateValueReplacer();
    }

    @After
    public void tearDown() throws Exception {
        templateReplacer = null;
    }

    @Test
    public void testReplaceValues() {
        // replaceValues(String template, String channelId, String channelname) calls loadContextFromMap(VelocityContext context, Map<String, ?> map) below
        assertEquals("value1", templateReplacer.replaceValues("value1", "channelId", "channelName"));
        assertEquals("valueOfVelocity1", templateReplacer.replaceValues("$velocity1", "channelId", "channelName"));
        assertEquals("$velocityUnknown", templateReplacer.replaceValues("$velocityUnknown", "channelId", "channelName"));
        assertEquals("unusedVelocity1", templateReplacer.replaceValues("unusedVelocity1", "channelId", "channelName"));
        assertEquals("valueOfVelocityHyphen", templateReplacer.replaceValues("$velocity-hyphen", "channelId", "channelName"));
    }
    
    private class TestTemplateValueReplacer extends TemplateValueReplacer {
        @Override
        protected void loadContextFromMap(VelocityContext context, Map<String, ?> map) {
            Map<String, String> velocityMap = new HashMap<String, String>();
            velocityMap.put("velocity1", "valueOfVelocity1");
            velocityMap.put("unusedVelocity1", "valueOfUnusedVelocity1");
            velocityMap.put("velocity-hyphen", "valueOfVelocityHyphen");
            super.loadContextFromMap(context, velocityMap);
        }
    }
}

