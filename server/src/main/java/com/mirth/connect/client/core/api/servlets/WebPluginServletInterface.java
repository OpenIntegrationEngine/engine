// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Chris Gibson <cgibson@outlook.com>

package com.mirth.connect.client.core.api.servlets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.client.core.api.Param;

/**
 * Serves the browser (web administrator) half of installed extensions.
 *
 * An extension may ship a web UI alongside its engine code as a {@code webadmin/} folder
 * (containing a {@code plugin.json} manifest and its compiled ES-module assets). This servlet
 * lets the web administrator discover and fetch those web halves directly from the engine that
 * has them installed — so a plugin's UI follows the engine, not the web-admin install. Both
 * endpoints require only a valid session (any authenticated user) and are not audited: they
 * serve non-sensitive static UI code and are hit once per page load.
 */
@Path("/webplugins")
@Tag(name = "Web Plugins")
@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
public interface WebPluginServletInterface extends BaseServletInterface {

    @GET
    @Path("/")
    @Operation(summary = "Returns the install-directory paths of all enabled extensions that ship a web administrator UI (i.e. contain webadmin/plugin.json).")
    @MirthOperation(name = "getWebPluginPaths", display = "Get web plugin paths", auditable = false)
    public List<String> getWebPluginPaths() throws ClientException;

    @GET
    @Path("/{extensionPath}/{resourcePath:.*}")
    @Produces(MediaType.WILDCARD)
    @Operation(summary = "Serves a static file from an extension's webadmin/ folder (the browser half of the plugin).")
    @MirthOperation(name = "getWebPluginResource", display = "Get web plugin resource", auditable = false)
    public Response getWebPluginResource(// @formatter:off
            @Param("extensionPath") @Parameter(description = "The extension's install-directory name.", required = true) @PathParam("extensionPath") String extensionPath,
            @Param("resourcePath") @Parameter(description = "The file path within the extension's webadmin/ folder.", required = true) @PathParam("resourcePath") String resourcePath) throws ClientException;
    // @formatter:on
}
