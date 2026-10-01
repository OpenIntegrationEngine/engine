/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.api.providers;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ext.Provider;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;

@Provider
public class RequestedWithFilter implements Filter {

    private boolean isRequestedWithHeaderRequired = true; 


    public RequestedWithFilter(PropertiesConfiguration mirthProperties) {
        
        isRequestedWithHeaderRequired = mirthProperties.getBoolean("server.api.require-requested-with", true);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse res = (HttpServletResponse) response;
        
        HttpServletRequest servletRequest = (HttpServletRequest)request;
        String requestedWithHeader = (String) servletRequest.getHeader("X-Requested-With");

        //if header is required and not present, send an error
        if(isRequestedWithHeaderRequired && StringUtils.isBlank(requestedWithHeader) && !isWebPluginAssetRequest(servletRequest)) {
            res.sendError(400, "All requests must have 'X-Requested-With' header");
        }
        else {
            chain.doFilter(request, response);
        }

    }

    /**
     * A web administrator loads a plugin's browser assets from /api/webplugins/... using
     * &lt;script&gt;/import(), which cannot set request headers. Those GETs serve only static
     * UI code (no state change, nothing sensitive), so they are exempt from the CSRF header
     * requirement. State-changing requests and all other endpoints still require the header.
     */
    private static boolean isWebPluginAssetRequest(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        // getPathInfo() is context-relative (e.g. "/webplugins/..."); fall back to the full
        // URI so a configured http.contextpath doesn't defeat the check.
        String path = request.getPathInfo();
        if (StringUtils.isBlank(path)) {
            path = request.getRequestURI();
        }
        return path != null && (path.startsWith("/webplugins/") || path.contains("/api/webplugins/"));
    }
    
    public boolean isRequestedWithHeaderRequired() {
        return isRequestedWithHeaderRequired;
    }

    @Override
    public void destroy() {}
}