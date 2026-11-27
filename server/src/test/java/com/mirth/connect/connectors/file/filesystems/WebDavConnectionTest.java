package com.mirth.connect.connectors.file.filesystems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.http.HttpEntity;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.github.sardine.DavResource;
import com.github.sardine.impl.SardineException;
import com.github.sardine.impl.SardineImpl;
import com.mirth.connect.connectors.file.FileSystemConnectionOptions;

public class WebDavConnectionTest {

    private static final String BASE = "http://dav.example:8080";

    private SardineImpl sardine;
    private WebDavConnection connection;

    @Before
    public void setUp() throws Exception {
        sardine = mock(SardineImpl.class);
        // Anything not set up below doesn't exist
        when(sardine.list(anyString(), anyInt(), any())).thenThrow(new SardineException("Unexpected response", 404, "Not Found"));
        propfind("/", 0, dir("/"));
        connection = new WebDavConnection(sardine, BASE);
    }

    @Test
    public void listFiles() throws Exception {
        DavResource forbidden = file("/in/forbidden.txt", 0);
        when(forbidden.getStatusCode()).thenReturn(403);
        propfind("/in", 1, dir("/in/"), file("/in/a.txt", 5), file("/in/b.hl7", 6), file("/in/.hidden.txt", 1), dir("/in/sub/"), forbidden);

        List<FileInfo> files = connection.listFiles("/in", "*.txt", false, true);
        assertEquals(Arrays.asList("a.txt"), names(files));
        FileInfo a = files.get(0);
        assertEquals(5, a.getSize());
        assertEquals(1000, a.getLastModified());
        assertEquals("/in", a.getParent());
        assertEquals(BASE + "/in/a.txt", a.getAbsolutePath());

        assertEquals(Arrays.asList("a.txt", "b.hl7"), names(connection.listFiles("/in", "^[ab]\\..*", true, false)));
        assertEquals(Arrays.asList("/in/sub"), connection.listDirectories("/in"));
    }

    @Test
    public void listRoot() throws Exception {
        propfind("/", 1, dir("/"), dir("/in/"));
        assertEquals(Arrays.asList("/in"), connection.listDirectories("/"));
    }

    @Test
    public void selfEntryBehindRewritingProxy() throws Exception {
        propfind("/in", 1, dir("/dav/in/"), file("/dav/in/a.txt", 1), dir("/dav/in/sub/"));
        assertEquals(Arrays.asList("a.txt"), names(connection.listFiles("/in", "*", false, false)));
        assertEquals(Arrays.asList("/in/sub"), connection.listDirectories("/in"));
    }

    @Test
    public void listingErrorIncludesStatus() throws Exception {
        try {
            connection.listFiles("/missing", "*", false, false);
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage(), e.getMessage().contains("404"));
        }
    }

    @Test
    public void existsAndCanRead() throws Exception {
        propfind("/in", 0, dir("/in/"));
        propfind("/in/a.txt", 0, file("/in/a.txt", 1));
        assertTrue(connection.exists("a.txt", "/in"));
        assertFalse(connection.exists("b.txt", "/in"));
        assertTrue(connection.canRead("/in"));
        assertTrue(connection.canWrite("/in"));
        assertFalse(connection.canRead("/in/a.txt"));
        assertFalse(connection.canWrite("/missing"));
    }

    @Test
    public void readFile() throws Exception {
        propfind("/in/sub", 0, dir("/in/sub/"));
        try {
            connection.readFile("sub", "/in", new HashMap<>());
            fail();
        } catch (Exception e) {
            assertEquals("Invalid Path", e.getMessage());
        }
    }

    @Test
    public void writeFileCreatesMissingDirectory() throws Exception {
        connection.writeFile("a.txt", "/out", false, new ByteArrayInputStream("abc".getBytes()), 3, new HashMap<>());
        verify(sardine).createDirectory(BASE + "/out");

        // The body can be resent if a proxy consumed it before the authentication challenge
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(sardine).put(eq(BASE + "/out/a.txt"), entity.capture(), isNull(), eq(true));
        assertTrue(entity.getValue().isRepeatable());
        assertEquals("abc", body(entity.getValue()));
        assertEquals("abc", body(entity.getValue()));
    }

    @Test
    public void otherStreamsAreNotResent() throws Exception {
        propfind("/out", 0, dir("/out/"));
        InputStream in = new FilterInputStream(new ByteArrayInputStream("abc".getBytes())) {};
        connection.writeFile("a.txt", "/out", false, in, 3, new HashMap<>());
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(sardine).put(eq(BASE + "/out/a.txt"), entity.capture(), isNull(), eq(true));
        assertFalse(entity.getValue().isRepeatable());
    }

    @Test
    public void writeFileIntoFileFails() throws Exception {
        propfind("/out", 0, file("/out", 1));
        try {
            connection.writeFile("a.txt", "/out", false, new ByteArrayInputStream(new byte[0]), 0, new HashMap<>());
            fail();
        } catch (Exception e) {
            assertEquals("The destination directory path is invalid: '/out'", e.getMessage());
        }
        verify(sardine, never()).put(anyString(), any(HttpEntity.class), any(), anyBoolean());
    }

    @Test
    public void moveDoesNotOverwrite() throws Exception {
        propfind("/done", 0, dir("/done/"));
        connection.move("a.txt", "/in", "a.txt", "/done");
        verify(sardine, never()).createDirectory(anyString());
        verify(sardine).move(BASE + "/in/a.txt", BASE + "/done/a.txt", false);
    }

    @Test
    public void pathsAreEncoded() throws Exception {
        connection.delete("a b#?.txt", "/in", false);
        verify(sardine).delete(BASE + "/in/a%20b%23%3F.txt");
    }

    @Test
    public void missingFileKeepsConnectionValid() throws Exception {
        assertTrue(connection.isValid());
        assertFalse(connection.exists("a.txt", "/in"));
        assertTrue(connection.isValid());

        when(sardine.list(eq(BASE + "/in/b.txt"), eq(0), any())).thenThrow(new IOException("Connection reset"));
        assertFalse(connection.exists("b.txt", "/in"));
        assertFalse(connection.isValid());
    }

    @Test
    public void unreachableServerIsInvalid() throws Exception {
        WebDavConnection unreachable = new WebDavConnection("localhost", 1, false, new FileSystemConnectionOptions(false, "null", "null", null), 1000);
        assertFalse(unreachable.isValid());
        assertFalse(unreachable.exists("a.txt", "/in"));
    }

    private void propfind(String path, int depth, DavResource... resources) throws Exception {
        when(sardine.list(eq(BASE + path), eq(depth), any())).thenReturn(Arrays.asList(resources));
    }

    private static DavResource dir(String path) {
        DavResource resource = resource(path);
        when(resource.isDirectory()).thenReturn(true);
        when(resource.getContentLength()).thenReturn(-1L);
        return resource;
    }

    private static DavResource file(String path, long length) {
        DavResource resource = resource(path);
        when(resource.getContentLength()).thenReturn(length);
        when(resource.getModified()).thenReturn(new Date(1000));
        return resource;
    }

    private static DavResource resource(String path) {
        DavResource resource = mock(DavResource.class);
        String name = path.replaceAll("/$", "");
        when(resource.getPath()).thenReturn(path);
        when(resource.getName()).thenReturn(name.substring(name.lastIndexOf('/') + 1));
        when(resource.getStatusCode()).thenReturn(200);
        return resource;
    }

    private static String body(HttpEntity entity) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        return out.toString("UTF-8");
    }

    private static List<String> names(List<FileInfo> files) {
        List<String> names = new ArrayList<>();
        for (FileInfo file : files) {
            names.add(file.getName());
        }
        return names;
    }
}
