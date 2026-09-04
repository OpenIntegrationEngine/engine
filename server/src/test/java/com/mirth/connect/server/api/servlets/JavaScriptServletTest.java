// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Chris Gibson <cgibson@outlook.com>

package com.mirth.connect.server.api.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ControllerFactory;

public class JavaScriptServletTest extends ServletTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JavaScriptServlet servlet;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);
    }

    @Before
    public void beforeTest() {
        servlet = new JavaScriptServlet(request, sc);
    }

    @Test
    public void testValidScriptReportsNoError() throws Exception {
        Response response = servlet.validateScript("var a = 1;\nvar b = a + 1;");

        assertEquals(200, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        JsonNode body = MAPPER.readTree((String) response.getEntity());
        assertTrue(body.get("error").isNull());
    }

    @Test
    public void testInvalidScriptReportsErrorText() throws Exception {
        Response response = servlet.validateScript("var a = ;");

        assertEquals(200, response.getStatus());
        JsonNode body = MAPPER.readTree((String) response.getEntity());
        assertTrue(body.get("error").isTextual());
        assertTrue(body.get("error").asText().length() > 0);
    }

    @Test
    // The error text can carry quotes and newlines; the body must still parse as JSON.
    public void testErrorTextIsValidJson() throws Exception {
        Response response = servlet.validateScript("function f() {\n  return \"unterminated;\n}");

        JsonNode body = MAPPER.readTree((String) response.getEntity());
        assertTrue(body.get("error").isTextual());
    }

    @Test
    public void testNullScriptIsTreatedAsEmpty() throws Exception {
        Response response = servlet.validateScript(null);

        JsonNode body = MAPPER.readTree((String) response.getEntity());
        assertTrue(body.get("error").isNull());
    }
}
