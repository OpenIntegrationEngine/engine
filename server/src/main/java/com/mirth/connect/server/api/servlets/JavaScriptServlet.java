// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Chris Gibson <cgibson@outlook.com>

package com.mirth.connect.server.api.servlets;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.mirth.connect.client.core.api.servlets.JavaScriptServletInterface;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.util.JavaScriptSharedUtil;

public class JavaScriptServlet extends MirthServlet implements JavaScriptServletInterface {

    public JavaScriptServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc);
    }

    @Override
    public Response validateScript(String script) {
        // JavaScriptSharedUtil.validateScript returns null when the script compiles, else the
        // error text ("Error on line N: ..."). Report it as { "error": <string|null> }.
        String error = JavaScriptSharedUtil.validateScript(script == null ? "" : script);
        String json = "{\"error\":" + (error == null ? "null" : jsonString(error)) + "}";
        return Response.ok(json).type(MediaType.APPLICATION_JSON).build();
    }

    // Minimal JSON string escaper (the response is a single {"error":"..."} object, so we build it
    // by hand rather than pull in a serializer).
    private static String jsonString(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.append('"').toString();
    }
}
