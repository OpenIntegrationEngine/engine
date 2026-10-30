package com.mirth.connect.connectors.file.filesystems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mirth.connect.connectors.file.FileScheme;
import com.mirth.connect.connectors.file.FileSystemConnectionOptions;
import com.mirth.connect.connectors.file.filesystems.WebDavTestServer.Auth;

public class WebDavConnectionTest {

    private static final long MODIFIED = 1577934245000L; // 2020-01-02T03:04:05Z

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private WebDavTestServer server;
    private WebDavConnection connection;

    @Before
    public void setUp() throws Exception {
        File root = tmp.newFolder("dav");
        write(root, "in/a.txt", "alpha");
        write(root, "in/b.txt", "bravo!");
        write(root, "in/c.hl7", "MSH|");
        write(root, "in/.hidden.txt", "x");
        write(root, "in/sub/d.txt", "delta");
        WebDavTestServer.file(root, "in/sub2").mkdirs();
        WebDavTestServer.file(root, "out").mkdirs();
        server = new WebDavTestServer(root);
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.destroy();
        }
        server.stop();
    }

    private WebDavConnection connect(Auth auth, String username, String password) throws Exception {
        server.start(auth, false);
        return open(false, username, password, 5000);
    }

    /** Goes through the factory so the connector's host/port/timeout handling is covered */
    private WebDavConnection open(boolean secure, String username, String password, int timeout) throws Exception {
        FileSystemConnectionFactory factory = new FileSystemConnectionFactory(FileScheme.WEBDAV, new FileSystemConnectionOptions(false, username, password, null), "localhost", server.port(), false, secure, timeout);
        connection = (WebDavConnection) factory.makeObject().getObject();
        return connection;
    }

    private WebDavConnection connect() throws Exception {
        return connect(Auth.NONE, "null", "null");
    }

    @Test
    public void listFilesWithWildcard() throws Exception {
        connect();
        assertEquals(List.of("a.txt", "b.txt"), names(connection.listFiles("/in", "*.txt", false, true)));
        assertEquals(List.of(".hidden.txt", "a.txt", "b.txt"), names(connection.listFiles("/in", "*.txt", false, false)));
        assertEquals(List.of("a.txt", "b.txt", "c.hl7"), names(connection.listFiles("/in", " *.txt , *.hl7 ", false, true)));
    }

    @Test
    public void listFilesWithRegex() throws Exception {
        connect();
        assertEquals(List.of("a.txt", "c.hl7"), names(connection.listFiles("/in", "[ac]\\..*", true, true)));
    }

    @Test
    public void listFilesExcludesDirectories() throws Exception {
        connect();
        assertEquals(List.of(".hidden.txt", "a.txt", "b.txt", "c.hl7"), names(connection.listFiles("/in", "*", false, false)));
    }

    @Test
    public void listFilesOfEmptyOrMissingDirectory() throws Exception {
        connect();
        assertTrue(connection.listFiles("/in/sub2", "*", false, false).isEmpty());
        try {
            connection.listFiles("/missing", "*", false, false);
            fail();
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void fileInfoAttributes() throws Exception {
        connect();
        FileInfo info = connection.listFiles("/in", "b.txt", false, true).get(0);
        assertEquals("b.txt", info.getName());
        assertEquals("/in", info.getParent());
        assertEquals(6, info.getSize());
        assertEquals(MODIFIED, info.getLastModified());
        assertTrue(info.isFile());
        assertFalse(info.isDirectory());
        assertTrue(info.isReadable());
        assertEquals("http://" + server.authority() + "/in/b.txt", info.getAbsolutePath());
        assertEquals("http://" + server.authority() + "/in/b.txt", info.getCanonicalPath());
    }

    @Test
    public void listFilesInRoot() throws Exception {
        connect();
        write(server.root, "top.txt", "top");
        assertEquals(List.of("top.txt"), names(connection.listFiles("/", "*.txt", false, true)));
        assertEquals(List.of("top.txt"), names(connection.listFiles("", "*.txt", false, true)));
    }

    @Test
    public void listDirectories() throws Exception {
        connect();
        List<String> directories = connection.listDirectories("/in");
        Collections.sort(directories);
        assertEquals(List.of("/in/sub", "/in/sub2"), directories);
    }

    @Test
    public void listDirectoriesRoundTrip() throws Exception {
        connect();
        // FileReceiver feeds listDirectories() results straight back into listFiles()
        String sub = connection.listDirectories("/in").stream().filter(d -> d.endsWith("/sub")).findFirst().get();
        List<FileInfo> files = connection.listFiles(sub, "*", false, false);
        assertEquals(List.of("d.txt"), names(files));
        assertEquals("/in/sub", files.get(0).getParent());
        assertEquals("delta", read(connection.readFile(files.get(0).getName(), files.get(0).getParent(), new HashMap<>())));
    }

    @Test
    public void exists() throws Exception {
        connect();
        assertTrue(connection.exists("a.txt", "/in"));
        assertTrue(connection.exists("sub", "/in"));
        assertTrue(connection.exists("a.txt", "in/"));
        assertFalse(connection.exists("nope.txt", "/in"));
        assertFalse(connection.exists("a.txt", "/missing"));
    }

    @Test
    public void readFile() throws Exception {
        connect();
        assertEquals("alpha", read(connection.readFile("a.txt", "/in", new HashMap<>())));
        connection.closeReadFile();
    }

    @Test
    public void readCollectionFails() throws Exception {
        connect();
        try {
            connection.readFile("sub", "/in", new HashMap<>());
            fail();
        } catch (Exception e) {
            assertEquals("Invalid Path", e.getMessage());
        }
    }

    @Test(expected = Exception.class)
    public void readMissingFileFails() throws Exception {
        connect();
        connection.readFile("nope.txt", "/in", new HashMap<>());
    }

    @Test
    public void writeFileToExistingDirectory() throws Exception {
        connect();
        assertFalse(connection.canAppend());
        writeFile("new.txt", "/out", "hello");
        assertEquals("hello", content("out/new.txt"));
        assertFalse(server.requests().stream().anyMatch(r -> r.startsWith("MKCOL")));

        writeFile("new.txt", "/out", "replaced");
        assertEquals("replaced", content("out/new.txt"));
    }

    @Test
    public void writeFileCreatesMissingDirectory() throws Exception {
        connect();
        writeFile("new.txt", "/created", "hello");
        assertEquals("hello", content("created/new.txt"));
        assertTrue(server.requests().contains("MKCOL /created"));
    }

    @Test
    public void writeFileIntoFileFails() throws Exception {
        connect();
        try {
            writeFile("new.txt", "/in/a.txt", "hello");
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("The destination directory path is invalid: '/in/a.txt'"));
        }
    }

    @Test
    public void writeFileUnknownLength() throws Exception {
        connect();
        byte[] bytes = "streamed".getBytes(StandardCharsets.UTF_8);
        connection.writeFile("s.txt", "/out", false, new ByteArrayInputStream(bytes), -1, new HashMap<>());
        assertEquals("streamed", content("out/s.txt"));
    }

    @Test
    public void delete() throws Exception {
        connect();
        connection.delete("a.txt", "/in", false);
        assertFalse(WebDavTestServer.file(server.root, "in/a.txt").exists());

        // A failed delete is only logged
        connection.delete("a.txt", "/in", false);
        connection.delete("a.txt", "/in", true);
    }

    @Test
    public void moveToExistingDirectory() throws Exception {
        connect();
        connection.move("a.txt", "/in", "moved.txt", "/out");
        assertFalse(WebDavTestServer.file(server.root, "in/a.txt").exists());
        assertEquals("alpha", content("out/moved.txt"));
    }

    @Test
    public void moveCreatesMissingDirectory() throws Exception {
        connect();
        connection.move("a.txt", "/in", "moved.txt", "/archive");
        assertTrue(server.requests().contains("MKCOL /archive"));
        assertEquals("alpha", content("archive/moved.txt"));
    }

    @Test
    public void moveIntoFileFails() throws Exception {
        connect();
        try {
            connection.move("a.txt", "/in", "moved.txt", "/in/b.txt");
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("The move-to directory path is invalid: '/in/b.txt'"));
        }
        assertTrue(WebDavTestServer.file(server.root, "in/a.txt").exists());
    }

    @Test
    public void moveDoesNotOverwrite() throws Exception {
        connect();
        connection.move("a.txt", "/in", "b.txt", "/in");
        assertEquals("alpha", content("in/a.txt"));
        assertEquals("bravo!", content("in/b.txt"));
        assertEquals(List.of("http://" + server.authority() + "/in/b.txt"), server.destinations);
    }

    @Test
    public void moveMissingFileIsOnlyLogged() throws Exception {
        connect();
        connection.move("nope.txt", "/in", "moved.txt", "/out");
        assertFalse(WebDavTestServer.file(server.root, "out/moved.txt").exists());
    }

    @Test
    public void canReadAndCanWrite() throws Exception {
        connect();
        assertTrue(connection.canRead("/in"));
        assertTrue(connection.canWrite("/in"));
        assertFalse(connection.canRead("/in/a.txt"));
        assertFalse(connection.canWrite("/in/a.txt"));
        assertFalse(connection.canRead("/missing"));
        assertFalse(connection.canWrite("/missing"));

        server.lockedPaths.add("/out");
        assertTrue(connection.canRead("/out"));
        assertFalse(connection.canWrite("/out"));
    }

    @Test
    public void externalEntitiesAreNotResolved() throws Exception {
        connect();
        File secret = tmp.newFile("secret.txt");
        Files.write(secret.toPath(), "leaked".getBytes(StandardCharsets.UTF_8));
        server.propfindOverride = "<?xml version=\"1.0\"?><!DOCTYPE m [<!ENTITY x SYSTEM \"" + secret.toURI() + "\">]>"
                + "<D:multistatus xmlns:D=\"DAV:\"><D:response><D:href>/in/&x;</D:href><D:propstat><D:prop><D:resourcetype/></D:prop>"
                + "<D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response></D:multistatus>";
        try {
            List<FileInfo> files = connection.listFiles("/in", "*", false, false);
            assertFalse(names(files).contains("leaked"));
        } catch (Exception e) {
            // rejecting the document outright is fine too
        }
    }

    @Test
    public void putIsRetriedWhenOnlyWritesAreChallenged() throws Exception {
        connect(Auth.BASIC_WRITES, WebDavTestServer.USER, WebDavTestServer.PASSWORD);
        // The PROPFIND before the PUT succeeds anonymously, so the PUT itself gets the 401
        writeFile("challenged.txt", "/out", "body");
        assertEquals("body", content("out/challenged.txt"));
        byte[] bytes = "streamed".getBytes(StandardCharsets.UTF_8);
        connection.writeFile("chunked.txt", "/out", false, new ByteArrayInputStream(bytes), -1, new HashMap<>());
        assertEquals("streamed", content("out/chunked.txt"));
        connection.move("challenged.txt", "/out", "moved.txt", "/out");
        assertEquals("body", content("out/moved.txt"));
        // A ByteArrayInputStream is resent by resetting it, so 100-continue isn't needed
        assertTrue(server.putExpectHeaders.stream().noneMatch("100-continue"::equalsIgnoreCase));
    }

    @Test
    public void putIsRetriedWhenTheBodyIsConsumedBeforeTheChallenge() throws Exception {
        connect(Auth.BASIC_WRITES, WebDavTestServer.USER, WebDavTestServer.PASSWORD);
        server.consumeUnauthenticatedBodies = true;
        // FileDispatcher hands over a ByteArrayInputStream
        writeFile("challenged.txt", "/out", "body");
        assertEquals("body", content("out/challenged.txt"));
    }

    @Test
    public void nonResettableStreamUsesExpectContinue() throws Exception {
        connect(Auth.BASIC_WRITES, WebDavTestServer.USER, WebDavTestServer.PASSWORD);
        byte[] bytes = "streamed".getBytes(StandardCharsets.UTF_8);
        InputStream in = new java.io.FilterInputStream(new ByteArrayInputStream(bytes)) {
            @Override
            public boolean markSupported() {
                return false;
            }
        };
        connection.writeFile("stream.txt", "/out", false, in, bytes.length, new HashMap<>());
        assertEquals("streamed", content("out/stream.txt"));
        assertTrue(server.putExpectHeaders.stream().allMatch("100-continue"::equalsIgnoreCase));
    }

    @Test
    public void prettyPrintedMultistatus() throws Exception {
        connect();
        server.prettyPrint = true;
        FileInfo info = connection.listFiles("/in", "b.txt", false, true).get(0);
        assertEquals(6, info.getSize());
        assertEquals(MODIFIED, info.getLastModified());
        assertEquals(List.of(".hidden.txt", "a.txt", "b.txt", "c.hl7"), names(connection.listFiles("/in", "*", false, false)));
        assertEquals(2, connection.listDirectories("/in").size());
        assertTrue(connection.canWrite("/in"));
        assertFalse(connection.canRead("/in/a.txt"));
    }

    @Test
    public void failedResponsesAreSkipped() throws Exception {
        connect();
        server.propfindOverride = multistatus(
                "<D:response><D:href>/in/</D:href>" + propstat("<D:resourcetype><D:collection/></D:resourcetype>", "200 OK") + "</D:response>",
                "<D:response><D:href>/in/ok.txt</D:href>" + propstat("<D:resourcetype/><D:getcontentlength>3</D:getcontentlength>", "200 OK") + "</D:response>",
                "<D:response><D:href>/in/forbidden.txt</D:href><D:status>HTTP/1.1 403 Forbidden</D:status></D:response>",
                "<D:response><D:href>/in/gone.txt</D:href>" + propstat("<D:resourcetype/>", "404 Not Found") + "</D:response>");
        List<FileInfo> files = connection.listFiles("/in", "*", false, false);
        assertEquals(List.of("ok.txt"), names(files));
        assertEquals(3, files.get(0).getSize());
    }

    @Test
    public void selfEntryBehindRewritingProxy() throws Exception {
        connect();
        server.propfindOverride = multistatus(
                "<D:response><D:href>https://proxy.example/dav/in/</D:href>" + propstat("<D:resourcetype><D:collection/></D:resourcetype>", "200 OK") + "</D:response>",
                "<D:response><D:href>https://proxy.example/dav/in/a.txt</D:href>" + propstat("<D:resourcetype/>", "200 OK") + "</D:response>",
                "<D:response><D:href>https://proxy.example/dav/in/sub/</D:href>" + propstat("<D:resourcetype><D:collection/></D:resourcetype>", "200 OK") + "</D:response>");
        assertEquals(List.of("a.txt"), names(connection.listFiles("/in", "*", false, false)));
        assertEquals(List.of("/in/sub"), connection.listDirectories("/in"));

        // An empty directory behind the proxy only lists itself
        server.propfindOverride = multistatus("<D:response><D:href>/dav/in/</D:href>" + propstat("<D:resourcetype><D:collection/></D:resourcetype>", "200 OK") + "</D:response>");
        assertEquals(List.of(), connection.listDirectories("/in"));
    }

    @Test
    public void listingErrorIncludesStatus() throws Exception {
        connect(Auth.BASIC, WebDavTestServer.USER, "wrong");
        try {
            connection.listFiles("/in", "*", false, false);
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage(), e.getMessage().contains("401"));
        }
    }

    @Test
    public void timeout() throws Exception {
        server.start(Auth.NONE, false);
        open(false, "null", "null", 200);
        server.delayMillis = 2000;
        long start = System.currentTimeMillis();
        try {
            connection.listFiles("/in", "*", false, false);
            fail();
        } catch (java.net.SocketTimeoutException e) {
            // expected
        }
        assertTrue(System.currentTimeMillis() - start < 1500);
    }

    @Test
    public void ipv6LiteralHost() throws Exception {
        connection = new WebDavConnection("[::1]", 1, false, new FileSystemConnectionOptions(false, "null", "null", null), 1000);
        assertFalse(connection.exists("a.txt", "/in"));
    }

    @Test
    public void collectionWithoutTrailingSlashIsRedirected() throws Exception {
        connect(Auth.BASIC, WebDavTestServer.USER, WebDavTestServer.PASSWORD);
        server.slashRedirect = true;
        assertTrue(connection.canRead("/in"));
        assertTrue(connection.canWrite("/in"));
        assertTrue(connection.exists("sub", "/in"));
        assertEquals(List.of("a.txt", "b.txt"), names(connection.listFiles("/in", "*.txt", false, true)));
        assertEquals(List.of("d.txt"), names(connection.listFiles(connection.listDirectories("/in").get(0), "*", false, false)));
        writeFile("redirected.txt", "/out", "ok");
        assertEquals("ok", content("out/redirected.txt"));
        connection.move("redirected.txt", "/out", "moved.txt", "/in/sub");
        assertEquals("ok", content("in/sub/moved.txt"));
        assertTrue(server.requests().contains("PROPFIND /in/"));
    }

    @Test
    public void connectedAndValid() throws Exception {
        connect();
        assertTrue(connection.isConnected());
        assertTrue(connection.isValid());
    }

    @Test
    public void nullUsernameSendsNoCredentials() throws Exception {
        connect(Auth.NONE, "null", "null");
        connection.listFiles("/in", "*", false, false);
        connection.readFile("a.txt", "/in", new HashMap<>()).close();
        assertTrue(server.authorizations.stream().allMatch(String::isEmpty));
    }

    @Test
    public void basicAuth() throws Exception {
        connect(Auth.BASIC, WebDavTestServer.USER, WebDavTestServer.PASSWORD);
        exerciseAuthenticated();
        assertTrue(server.authorizations.contains("Basic"));
    }

    @Test
    public void digestAuth() throws Exception {
        connect(Auth.DIGEST, WebDavTestServer.USER, WebDavTestServer.PASSWORD);
        exerciseAuthenticated();
        assertTrue(server.authorizations.contains("Digest"));
    }

    @Test
    public void wrongPassword() throws Exception {
        connect(Auth.BASIC, WebDavTestServer.USER, "wrong");
        assertFalse(connection.isValid());
        assertFalse(connection.exists("a.txt", "/in"));
        assertFalse(connection.canRead("/in"));
        assertTrue(server.requests().isEmpty());
    }

    @Test
    public void https() throws Exception {
        server.start(Auth.BASIC, true);
        SSLContext previous = SSLContext.getDefault();
        SSLContext.setDefault(server.clientSslContext());
        try {
            open(true, WebDavTestServer.USER, WebDavTestServer.PASSWORD, 5000);
            FileInfo info = connection.listFiles("/in", "a.txt", false, true).get(0);
            assertEquals("https://" + server.authority() + "/in/a.txt", info.getCanonicalPath());
            assertEquals("alpha", read(connection.readFile("a.txt", "/in", new HashMap<>())));
            writeFile("tls.txt", "/out", "secure");
            assertEquals("secure", content("out/tls.txt"));
        } finally {
            SSLContext.setDefault(previous);
        }
    }

    @Test
    public void pathEncoding() throws Exception {
        connect();
        String dir = "/spa ce/ü+ß";
        String name = "a b ü+%.txt";
        WebDavTestServer.file(server.root, dir).mkdirs();
        write(server.root, dir + "/" + name, "encoded");

        assertTrue(connection.exists(name, dir));
        assertTrue(connection.canWrite(dir));
        List<FileInfo> files = connection.listFiles(dir, "*.txt", false, true);
        assertEquals(List.of(name), names(files));
        assertEquals(dir, files.get(0).getParent());
        assertEquals(7, files.get(0).getSize());
        assertEquals("encoded", read(connection.readFile(name, dir, new HashMap<>())));

        writeFile("n ü.txt", "/new dir ü", "made");
        assertEquals("made", content("new dir ü/n ü.txt"));

        connection.move(name, dir, "m ü.txt", "/new dir ü");
        assertEquals("encoded", content("new dir ü/m ü.txt"));

        connection.delete("m ü.txt", "/new dir ü", false);
        assertFalse(WebDavTestServer.file(server.root, "new dir ü/m ü.txt").exists());

        String sub = connection.listDirectories("/spa ce").get(0);
        assertEquals(List.of(), names(connection.listFiles(sub, "*", false, false)));
    }

    private void exerciseAuthenticated() throws Exception {
        assertTrue(connection.isValid());
        assertEquals(List.of("a.txt", "b.txt"), names(connection.listFiles("/in", "*.txt", false, true)));
        assertEquals("alpha", read(connection.readFile("a.txt", "/in", new HashMap<>())));
        writeFile("auth.txt", "/newdir", "authed");
        assertEquals("authed", content("newdir/auth.txt"));
        connection.move("auth.txt", "/newdir", "auth2.txt", "/newdir");
        connection.delete("auth2.txt", "/newdir", false);
        assertFalse(WebDavTestServer.file(server.root, "newdir/auth2.txt").exists());
    }

    private static String multistatus(String... responses) {
        return "<?xml version=\"1.0\"?><D:multistatus xmlns:D=\"DAV:\">" + String.join("", responses) + "</D:multistatus>";
    }

    private static String propstat(String props, String status) {
        return "<D:propstat><D:prop>" + props + "</D:prop><D:status>HTTP/1.1 " + status + "</D:status></D:propstat>";
    }

    private void writeFile(String name, String dir, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        connection.writeFile(name, dir, false, new ByteArrayInputStream(bytes), bytes.length, new HashMap<>());
    }

    private String content(String path) throws Exception {
        return new String(Files.readAllBytes(WebDavTestServer.file(server.root, path).toPath()), StandardCharsets.UTF_8);
    }

    private static String read(InputStream in) throws Exception {
        try (InputStream is = in) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    private static void write(File root, String path, String content) throws Exception {
        File file = WebDavTestServer.file(root, path);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(MODIFIED));
    }

    private static List<String> names(List<FileInfo> files) {
        List<String> names = new ArrayList<>();
        for (FileInfo file : files) {
            names.add(file.getName());
        }
        Collections.sort(names);
        return names;
    }
}
