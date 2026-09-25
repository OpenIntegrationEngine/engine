// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Chris Gibson <cgibson@outlook.com>

package com.mirth.connect.server.api.servlets;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mirth.connect.client.core.api.servlets.JavaScriptServletInterface;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.util.JavaScriptSharedUtil;

public class JavaScriptServlet extends MirthServlet implements JavaScriptServletInterface {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public JavaScriptServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc);
    }

    @Override
    public Response validateScript(String script) {
        // JavaScriptSharedUtil.validateScript returns null when the script compiles, else the
        // error text ("Error on line N: ..."). Report it as { "error": <string|null> }.
        String error = JavaScriptSharedUtil.validateScript(script == null ? "" : script);
        ObjectNode out = MAPPER.createObjectNode();
        if (error == null) {
            out.putNull("error");
        } else {
            out.put("error", error);
        }
        return Response.ok(out.toString()).type(MediaType.APPLICATION_JSON).build();
    }
}
