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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.client.core.api.Param;

/**
 * Serializes a message through a data type's own serializer — the engine's exact toXML()/toJSON()
 * output — so a browser client can build message trees that match the runtime {@code msg}/{@code tmp}
 * without shipping the engine's datatype libraries. Uses the installed datatype plugins, so every
 * data type (and strict/non-strict via serialization-property overrides) is covered. Session-authed,
 * not audited.
 */
@Path("/datatypes")
@Tag(name = "Data Types")
@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
public interface DataTypeServletInterface extends BaseServletInterface {

    @POST
    @Path("/_serialize")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Serializes a message via the given data type's serializer. Returns { format, data, meta: { root, descriptions } }.")
    @MirthOperation(name = "serializeMessage", display = "Serialize message for data type", auditable = false)
    public Response serializeMessage(// @formatter:off
            // dataType is a query param (not a path segment) so values containing a slash — e.g. "EDI/X12" — pass cleanly.
            @Param("dataType") @Parameter(description = "The data type name (HL7V2, XML, JSON, EDI/X12, NCPDP, DELIMITED, RAW, DICOM, HL7V3).", required = true) @QueryParam("dataType") String dataType,
            @Param("props") @Parameter(description = "Optional serialization-property overrides as newline-separated key=value pairs (e.g. useStrictParser=true).", required = false) @QueryParam("props") String props,
            @Param("message") String message) throws ClientException;
    // @formatter:on
}
