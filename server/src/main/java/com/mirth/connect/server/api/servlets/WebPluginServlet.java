// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Chris Gibson <cgibson@outlook.com>

package com.mirth.connect.server.api.servlets;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.apache.commons.lang3.StringUtils;

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.client.core.api.servlets.WebPluginServletInterface;
import com.mirth.connect.model.MetaData;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;

public class WebPluginServlet extends MirthServlet implements WebPluginServletInterface {

    private static final ExtensionController extensionController = ControllerFactory.getFactory().createExtensionController();

    private static final String WEBADMIN_DIR = "webadmin";
    private static final String MANIFEST = "plugin.json";
    // An extension's install-directory name — a single, safe path segment. Guards
    // both discovery and asset serving against traversal via the extension name.
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");

    private final ServletContext servletContext;

    public WebPluginServlet(@Context HttpServletRequest request, @Context ServletContext servletContext, @Context SecurityContext sc) {
        super(request, sc);
        this.servletContext = servletContext;
    }

    @Override
    public List<String> getWebPluginPaths() {
        Set<String> paths = new LinkedHashSet<String>();
        File extRoot = new File(ExtensionController.getExtensionsPath());
        addWebPluginPaths(paths, extRoot, extensionController.getPluginMetaData());
        addWebPluginPaths(paths, extRoot, extensionController.getConnectorMetaData());
        return new ArrayList<String>(paths);
    }

    private void addWebPluginPaths(Set<String> paths, File extRoot, Map<String, ? extends MetaData> metaDataMap) {
        if (metaDataMap == null) {
            return;
        }
        for (MetaData metaData : metaDataMap.values()) {
            if (metaData == null) {
                continue;
            }
            String path = metaData.getPath();
            if (StringUtils.isBlank(path) || !SAFE_SEGMENT.matcher(path).matches()) {
                continue;
            }
            // A disabled extension's engine half is inactive, so hide its web half too.
            if (!extensionController.isExtensionEnabled(metaData.getName())) {
                continue;
            }
            File manifest = new File(new File(new File(extRoot, path), WEBADMIN_DIR), MANIFEST);
            if (manifest.isFile()) {
                paths.add(path);
            }
        }
    }

    @Override
    public Response getWebPluginResource(String extensionPath, String resourcePath) {
        if (StringUtils.isBlank(extensionPath) || !SAFE_SEGMENT.matcher(extensionPath).matches()) {
            throw new MirthApiException(Status.NOT_FOUND);
        }
        // A bare .../webadmin request serves the manifest (mirrors an index).
        if (StringUtils.isBlank(resourcePath)) {
            resourcePath = MANIFEST;
        }

        try {
            File webadminRoot = new File(new File(new File(ExtensionController.getExtensionsPath()), extensionPath), WEBADMIN_DIR).getCanonicalFile();
            File target = new File(webadminRoot, resourcePath).getCanonicalFile();

            // Confine the resolved file to the extension's webadmin/ folder: canonicalizing
            // first collapses any ".." and follows symlinks, so an entry that escapes the
            // folder (traversal or a planted symlink) is rejected here rather than served.
            String rootPath = webadminRoot.getPath();
            if (!target.getPath().equals(rootPath) && !target.getPath().startsWith(rootPath + File.separator)) {
                throw new MirthApiException(Status.FORBIDDEN);
            }
            if (!target.isFile()) {
                throw new MirthApiException(Status.NOT_FOUND);
            }

            byte[] data = Files.readAllBytes(target.toPath());
            return Response.ok(data)
                    .type(contentType(target.getName()))
                    // Plugin code changes only on install/restart, but revalidate so an
                    // updated web half is never served stale after an extension upgrade.
                    .header("Cache-Control", "no-cache")
                    .build();
        } catch (MirthApiException e) {
            throw e;
        } catch (IOException e) {
            throw new MirthApiException(e);
        }
    }

    /**
     * MIME type for a served asset. ES modules MUST be a JavaScript type with the right charset
     * or the browser refuses to execute them, so .js/.mjs (and .json/.map, which some containers
     * mislabel) are pinned here; everything else defers to the servlet container's own MIME
     * table ({@link ServletContext#getMimeType}) rather than a hand-rolled extension map.
     */
    private String contentType(String fileName) {
        String lower = StringUtils.lowerCase(fileName);
        if (lower == null) {
            return "application/octet-stream";
        }
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
            return "text/javascript; charset=utf-8";
        }
        if (lower.endsWith(".json") || lower.endsWith(".map")) {
            return "application/json; charset=utf-8";
        }
        String mapped = servletContext == null ? null : servletContext.getMimeType(lower);
        if (mapped != null) {
            return mapped.startsWith("text/") ? mapped + "; charset=utf-8" : mapped;
        }
        return "application/octet-stream";
    }
}
