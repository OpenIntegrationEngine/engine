/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core.api.servlets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.client.core.api.Param;

/**
 * JavaScript utilities the script editors need — validation and formatting — exposed over REST
 * so a browser client can use the engine's own Rhino compiler/formatter (the same ones the Swing
 * client uses in-process) instead of shipping its own. Both take a raw script body, require only a
 * valid session, and are not audited (editor-support calls, hit frequently).
 */
@Path("/javascript")
@Tag(name = "JavaScript")
@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
public interface JavaScriptServletInterface extends BaseServletInterface {

    @POST
    @Path("/_validate")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Validates a JavaScript script with the engine's Rhino compiler. Returns { \"error\": <message|null> }.")
    @MirthOperation(name = "validateScript", display = "Validate JavaScript", auditable = false)
    public Response validateScript(@Param("script") String script) throws ClientException;

    @POST
    @Path("/_prettyPrint")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Formats a JavaScript script with the engine's Rhino-AST pretty-printer (E4X-safe). Returns the formatted script.")
    @MirthOperation(name = "prettyPrintScript", display = "Format JavaScript", auditable = false)
    public Response prettyPrintScript(@Param("script") String script) throws ClientException;
}
