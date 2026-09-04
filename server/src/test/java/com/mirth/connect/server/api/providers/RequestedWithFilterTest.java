package com.mirth.connect.server.api.providers;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.Test;
import org.mockito.Mockito;

import com.mirth.connect.client.core.PropertiesConfigurationUtil;

import junit.framework.TestCase;

public class RequestedWithFilterTest extends TestCase {
    
    private PropertiesConfiguration mirthProperties = PropertiesConfigurationUtil.create();
    
    @Test
    //assert that if property is set to false, isRequestedWithHeaderRequired = false
    public void testConstructor() {
       
        mirthProperties.setProperty("server.api.require-requested-with", "false");
        RequestedWithFilter requestedWithFilter = new RequestedWithFilter(mirthProperties);
        assertEquals(requestedWithFilter.isRequestedWithHeaderRequired(), false);
    }
    
    @Test
    //assert that HttpServletResponse.sendError() is called when X-Requested-With is required but not present 
    public void testDoFilterRequestedWithTrue() {
        
        mirthProperties.setProperty("server.api.require-requested-with", "true");
        RequestedWithFilter testFilter = new RequestedWithFilter(mirthProperties);
        
        HttpServletRequest mockReq = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse mockResp = Mockito.mock(HttpServletResponse.class);
        FilterChain mockFilterChain = Mockito.mock(FilterChain.class);
        
        try {
            testFilter.doFilter(mockReq, mockResp, mockFilterChain);
            verify(mockResp).sendError(HttpServletResponse.SC_BAD_REQUEST, "All requests must have 'X-Requested-With' header");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Test
    //assert that HttpServletResponse.sendError() is NOT called when X-Requested-With is not required and not present 
    public void testDoFilterRequestedWithFalse() {
        
        mirthProperties.setProperty("server.api.require-requested-with", "false");
        RequestedWithFilter testFilter = new RequestedWithFilter(mirthProperties);
        
        HttpServletRequest mockReq = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse mockResp = Mockito.mock(HttpServletResponse.class);
        FilterChain mockFilterChain = Mockito.mock(FilterChain.class);
        
        try {
            testFilter.doFilter(mockReq, mockResp, mockFilterChain);
            verify(mockResp, never()).sendError(HttpServletResponse.SC_BAD_REQUEST, "All requests must have 'X-Requested-With' header");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    // A web administrator loads plugin UI modules with <script>/import(), which cannot set
    // headers, so a GET for a web plugin asset passes without X-Requested-With.
    public void testDoFilterAllowsWebPluginAssetGetWithoutHeader() throws Exception {
        mirthProperties.setProperty("server.api.require-requested-with", "true");
        RequestedWithFilter testFilter = new RequestedWithFilter(mirthProperties);

        HttpServletRequest mockReq = mockRequest("GET", "/webplugins/demo/web/plugin.js", null);
        HttpServletResponse mockResp = Mockito.mock(HttpServletResponse.class);
        FilterChain mockFilterChain = Mockito.mock(FilterChain.class);

        testFilter.doFilter(mockReq, mockResp, mockFilterChain);
        verify(mockResp, never()).sendError(Mockito.anyInt(), Mockito.anyString());
        verify(mockFilterChain).doFilter(mockReq, mockResp);
    }

    @Test
    // Without path info (a configured context path), the full request URI still identifies a
    // web plugin asset.
    public void testDoFilterAllowsWebPluginAssetGetByRequestUriWithoutHeader() throws Exception {
        mirthProperties.setProperty("server.api.require-requested-with", "true");
        RequestedWithFilter testFilter = new RequestedWithFilter(mirthProperties);

        HttpServletRequest mockReq = mockRequest("GET", null, "/oie/api/webplugins/demo/plugin.json");
        HttpServletResponse mockResp = Mockito.mock(HttpServletResponse.class);
        FilterChain mockFilterChain = Mockito.mock(FilterChain.class);

        testFilter.doFilter(mockReq, mockResp, mockFilterChain);
        verify(mockResp, never()).sendError(Mockito.anyInt(), Mockito.anyString());
        verify(mockFilterChain).doFilter(mockReq, mockResp);
    }

    @Test
    // Only GET is exempt: any other method on the same path still needs the header.
    public void testDoFilterRejectsWebPluginPostWithoutHeader() throws Exception {
        mirthProperties.setProperty("server.api.require-requested-with", "true");
        RequestedWithFilter testFilter = new RequestedWithFilter(mirthProperties);

        HttpServletRequest mockReq = mockRequest("POST", "/webplugins/demo/web/plugin.js", null);
        HttpServletResponse mockResp = Mockito.mock(HttpServletResponse.class);
        FilterChain mockFilterChain = Mockito.mock(FilterChain.class);

        testFilter.doFilter(mockReq, mockResp, mockFilterChain);
        verify(mockResp).sendError(HttpServletResponse.SC_BAD_REQUEST, "All requests must have 'X-Requested-With' header");
        verify(mockFilterChain, never()).doFilter(mockReq, mockResp);
    }

    @Test
    // The discovery list (/webplugins without a trailing segment) is not an asset and keeps
    // the header requirement.
    public void testDoFilterRejectsWebPluginListWithoutHeader() throws Exception {
        mirthProperties.setProperty("server.api.require-requested-with", "true");
        RequestedWithFilter testFilter = new RequestedWithFilter(mirthProperties);

        HttpServletRequest mockReq = mockRequest("GET", "/webplugins", null);
        HttpServletResponse mockResp = Mockito.mock(HttpServletResponse.class);
        FilterChain mockFilterChain = Mockito.mock(FilterChain.class);

        testFilter.doFilter(mockReq, mockResp, mockFilterChain);
        verify(mockResp).sendError(HttpServletResponse.SC_BAD_REQUEST, "All requests must have 'X-Requested-With' header");
        verify(mockFilterChain, never()).doFilter(mockReq, mockResp);
    }

    @Test
    // Every other GET keeps the header requirement.
    public void testDoFilterRejectsOtherGetWithoutHeader() throws Exception {
        mirthProperties.setProperty("server.api.require-requested-with", "true");
        RequestedWithFilter testFilter = new RequestedWithFilter(mirthProperties);

        HttpServletRequest mockReq = mockRequest("GET", "/channels", null);
        HttpServletResponse mockResp = Mockito.mock(HttpServletResponse.class);
        FilterChain mockFilterChain = Mockito.mock(FilterChain.class);

        testFilter.doFilter(mockReq, mockResp, mockFilterChain);
        verify(mockResp).sendError(HttpServletResponse.SC_BAD_REQUEST, "All requests must have 'X-Requested-With' header");
        verify(mockFilterChain, never()).doFilter(mockReq, mockResp);
    }

    private static HttpServletRequest mockRequest(String method, String pathInfo, String requestUri) {
        HttpServletRequest mockReq = Mockito.mock(HttpServletRequest.class);
        Mockito.when(mockReq.getMethod()).thenReturn(method);
        Mockito.when(mockReq.getPathInfo()).thenReturn(pathInfo);
        Mockito.when(mockReq.getRequestURI()).thenReturn(requestUri);
        return mockReq;
    }

}
