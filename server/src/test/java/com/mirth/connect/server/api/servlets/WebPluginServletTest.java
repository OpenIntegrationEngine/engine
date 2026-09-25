// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Chris Gibson <cgibson@outlook.com>

package com.mirth.connect.server.api.servlets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;

/**
 * Covers the two guarantees of WebPluginServlet: discovery lists only enabled extensions that
 * ship a webadmin/plugin.json, and asset serving never leaves an extension's webadmin/ folder.
 */
public class WebPluginServletTest extends ServletTestBase {

    private static final String MANIFEST_JSON = "{\"id\":\"demo\",\"client\":{\"entry\":\"web/plugin.js\"}}";
    private static final String MODULE_JS = "export function register(platform) {}\n";

    private static Path baseDir;
    private static Path extensionsDir;
    private static ExtensionController extensionController;

    private WebPluginServlet servlet;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();

        baseDir = Files.createTempDirectory("webplugins");
        extensionsDir = baseDir.resolve("extensions");
        when(configurationController.getBaseDir()).thenReturn(baseDir.toString());

        extensionController = mock(ExtensionController.class);
        when(controllerFactory.createExtensionController()).thenReturn(extensionController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);

        // The servlet resolves extensions through the configuration controller's base
        // directory; anything else means the fixture below is not what gets served.
        assertTrue("Extensions path must resolve under the test base directory", new File(ExtensionController.getExtensionsPath()).getCanonicalPath().startsWith(baseDir.toRealPath().toString()));

        // demo: enabled, ships a web half.
        write(extensionsDir.resolve("demo/webadmin/plugin.json"), MANIFEST_JSON);
        write(extensionsDir.resolve("demo/webadmin/web/plugin.js"), MODULE_JS);
        write(extensionsDir.resolve("demo/webadmin/styles.css"), "body {}\n");
        // Files that must never be served: inside the extension but outside webadmin/, and
        // outside the extensions directory altogether.
        write(extensionsDir.resolve("demo/secret.txt"), "engine half\n");
        write(baseDir.resolve("outside.txt"), "outside extensions\n");
        // conn: a connector extension with a web half.
        write(extensionsDir.resolve("conn/webadmin/plugin.json"), MANIFEST_JSON);
        // disabled: ships a web half but the extension is disabled.
        write(extensionsDir.resolve("disabled/webadmin/plugin.json"), MANIFEST_JSON);
        // nomanifest: has a webadmin/ folder without a manifest.
        write(extensionsDir.resolve("nomanifest/webadmin/web/plugin.js"), MODULE_JS);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (baseDir != null) {
            FileUtils.deleteDirectory(baseDir.toFile());
        }
    }

    @Before
    public void beforeTest() {
        servlet = new WebPluginServlet(request, null, sc);
    }

    /* ---- discovery -------------------------------------------------------------------- */

    @Test
    public void testDiscoveryListsEnabledExtensionsWithManifest() {
        Map<String, PluginMetaData> plugins = new LinkedHashMap<String, PluginMetaData>();
        plugins.put("Demo", plugin("Demo", "demo"));
        plugins.put("Disabled", plugin("Disabled", "disabled"));
        plugins.put("No Manifest", plugin("No Manifest", "nomanifest"));
        plugins.put("Missing", plugin("Missing", "missing"));
        plugins.put("Evil", plugin("Evil", "../demo"));
        plugins.put("Blank", plugin("Blank", ""));
        Map<String, ConnectorMetaData> connectors = new LinkedHashMap<String, ConnectorMetaData>();
        connectors.put("Conn", connector("Conn", "conn"));

        when(extensionController.getPluginMetaData()).thenReturn(plugins);
        when(extensionController.getConnectorMetaData()).thenReturn(connectors);
        when(extensionController.isExtensionEnabled(anyString())).thenAnswer(invocation -> !"Disabled".equals(invocation.getArgument(0)));

        assertEquals(Arrays.asList("demo", "conn"), servlet.getWebPluginPaths());
    }

    @Test
    public void testDiscoveryWithNoExtensionsIsEmpty() {
        when(extensionController.getPluginMetaData()).thenReturn(null);
        when(extensionController.getConnectorMetaData()).thenReturn(null);

        assertTrue(servlet.getWebPluginPaths().isEmpty());
    }

    /* ---- asset serving ---------------------------------------------------------------- */

    @Test
    public void testServesAssetInsideWebadmin() throws Exception {
        Response response = servlet.getWebPluginResource("demo", "web/plugin.js");

        assertEquals(200, response.getStatus());
        assertArrayEquals(MODULE_JS.getBytes(StandardCharsets.UTF_8), (byte[]) response.getEntity());
        assertMediaType(response, "text", "javascript", "utf-8");
        assertEquals("no-cache", response.getHeaderString("Cache-Control"));
    }

    @Test
    public void testBareRequestServesManifest() throws Exception {
        Response response = servlet.getWebPluginResource("demo", "");

        assertEquals(200, response.getStatus());
        assertArrayEquals(MANIFEST_JSON.getBytes(StandardCharsets.UTF_8), (byte[]) response.getEntity());
        assertMediaType(response, "application", "json", "utf-8");
    }

    @Test
    public void testContainerMimeTypeGetsCharsetForText() throws Exception {
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getMimeType("styles.css")).thenReturn("text/css");
        servlet = new WebPluginServlet(request, servletContext, sc);

        Response response = servlet.getWebPluginResource("demo", "styles.css");

        assertEquals(200, response.getStatus());
        assertMediaType(response, "text", "css", "utf-8");
    }

    @Test
    public void testUnknownTypeFallsBackToOctetStream() throws Exception {
        Response response = servlet.getWebPluginResource("demo", "styles.css");

        assertEquals(200, response.getStatus());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM_TYPE, response.getMediaType());
    }

    @Test
    public void testMissingAssetIsNotFound() {
        assertStatus(Status.NOT_FOUND, "demo", "web/missing.js");
    }

    @Test
    public void testUnknownExtensionIsNotFound() {
        assertStatus(Status.NOT_FOUND, "missing", "plugin.json");
    }

    /* ---- traversal guard -------------------------------------------------------------- */

    @Test
    public void testRejectsTraversalOutOfWebadmin() {
        assertStatus(Status.FORBIDDEN, "demo", "../secret.txt");
    }

    @Test
    public void testRejectsNestedTraversalOutOfWebadmin() {
        assertStatus(Status.FORBIDDEN, "demo", "web/../../secret.txt");
    }

    @Test
    public void testRejectsTraversalOutOfExtensionsDirectory() {
        assertStatus(Status.FORBIDDEN, "demo", "../../../outside.txt");
    }

    @Test
    public void testRejectsTraversalIntoAnotherExtension() {
        assertStatus(Status.FORBIDDEN, "demo", "../../conn/webadmin/plugin.json");
    }

    @Test
    public void testAbsoluteResourcePathStaysInsideWebadmin() {
        // An absolute child path is resolved relative to the webadmin/ folder, so it can only
        // name a file that does not exist there.
        assertStatus(Status.NOT_FOUND, "demo", "/etc/passwd");
    }

    @Test
    public void testRejectsTraversalInExtensionSegment() {
        assertStatus(Status.NOT_FOUND, "../demo", "webadmin/plugin.json");
        assertStatus(Status.NOT_FOUND, "demo/../conn", "plugin.json");
        assertStatus(Status.NOT_FOUND, "demo/webadmin", "plugin.json");
        assertStatus(Status.NOT_FOUND, "", "plugin.json");
        assertStatus(Status.NOT_FOUND, "demo\\..\\conn", "plugin.json");
    }

    @Test
    public void testRejectsSymlinkEscape() throws Exception {
        Path link = extensionsDir.resolve("demo/webadmin/escape.js");
        try {
            Files.createSymbolicLink(link, baseDir.resolve("outside.txt"));
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assume.assumeNoException("Symbolic links are not available here", e);
        }

        assertStatus(Status.FORBIDDEN, "demo", "escape.js");
    }

    /* ---- helpers ---------------------------------------------------------------------- */

    private void assertStatus(Status expected, String extensionPath, String resourcePath) {
        try {
            Response response = servlet.getWebPluginResource(extensionPath, resourcePath);
            fail("Expected " + expected + " for " + extensionPath + " / " + resourcePath + " but got " + response.getStatus());
        } catch (MirthApiException e) {
            assertEquals(extensionPath + " / " + resourcePath, expected.getStatusCode(), e.getResponse().getStatus());
        }
    }

    private static void assertMediaType(Response response, String type, String subtype, String charset) {
        MediaType mediaType = response.getMediaType();
        assertEquals(type, mediaType.getType());
        assertEquals(subtype, mediaType.getSubtype());
        assertTrue(charset.equalsIgnoreCase(mediaType.getParameters().get("charset")));
    }

    private static PluginMetaData plugin(String name, String path) {
        PluginMetaData metaData = new PluginMetaData();
        metaData.setName(name);
        metaData.setPath(path);
        return metaData;
    }

    private static ConnectorMetaData connector(String name, String path) {
        ConnectorMetaData metaData = new ConnectorMetaData();
        metaData.setName(name);
        metaData.setPath(path);
        return metaData;
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
}
