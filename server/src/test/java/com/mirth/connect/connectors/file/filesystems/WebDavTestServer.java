package com.mirth.connect.connectors.file.filesystems;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * Minimal file-backed WebDAV server for WebDavConnection tests. Implements just
 * enough of RFC 4918 (PROPFIND depth 0/1, GET, PUT, DELETE, MKCOL, MOVE) and
 * records every request so tests can assert on the wire traffic.
 */
class WebDavTestServer {

    enum Auth {
        NONE, BASIC, DIGEST,
        /** Basic, but GET, PROPFIND and OPTIONS are allowed anonymously */
        BASIC_WRITES
    }

    static final String USER = "dav user";
    static final String PASSWORD = "p@ss:word";

    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    final File root;
    final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());
    final Set<String> lockedPaths = Collections.synchronizedSet(new HashSet<>());
    final List<String> destinations = Collections.synchronizedList(new ArrayList<>());
    final List<String> putExpectHeaders = Collections.synchronizedList(new ArrayList<>());
    /** When set, returned verbatim for every PROPFIND */
    volatile String propfindOverride;
    /** Indent the multistatus and put whitespace around status lines */
    volatile boolean prettyPrint;
    /** Delay before answering any request */
    volatile long delayMillis;

    private Server server;
    private int port;
    private SSLContext clientSslContext;

    WebDavTestServer(File root) {
        this.root = root;
    }

    void start(Auth auth, boolean tls) throws Exception {
        server = new Server();

        ServerConnector connector;
        if (tls) {
            KeyStore keyStore = selfSignedKeyStore();
            SslContextFactory.Server ssl = new SslContextFactory.Server();
            ssl.setKeyStore(keyStore);
            ssl.setKeyStorePassword("changeit");
            HttpConfiguration config = new HttpConfiguration();
            config.addCustomizer(new SecureRequestCustomizer(false));
            connector = new ServerConnector(server, new SslConnectionFactory(ssl, "http/1.1"), new HttpConnectionFactory(config));

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            clientSslContext = SSLContext.getInstance("TLS");
            clientSslContext.init(null, tmf.getTrustManagers(), null);
        } else {
            connector = new ServerConnector(server);
        }
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new DavServlet()), "/*");

        if (auth != Auth.NONE) {
            UserStore userStore = new UserStore();
            userStore.addUser(USER, Credential.getCredential(PASSWORD), new String[] { "dav" });
            HashLoginService loginService = new HashLoginService("dav");
            loginService.setUserStore(userStore);

            boolean digest = auth == Auth.DIGEST;
            Constraint constraint = new Constraint(digest ? Constraint.__DIGEST_AUTH : Constraint.__BASIC_AUTH, "dav");
            constraint.setAuthenticate(true);
            ConstraintMapping mapping = new ConstraintMapping();
            mapping.setConstraint(constraint);
            mapping.setPathSpec("/*");
            if (auth == Auth.BASIC_WRITES) {
                mapping.setMethodOmissions(new String[] { "GET", "PROPFIND", "OPTIONS" });
            }

            ConstraintSecurityHandler security = new ConstraintSecurityHandler();
            security.setLoginService(loginService);
            security.setAuthenticator(digest ? new DigestAuthenticator() : new BasicAuthenticator());
            security.addConstraintMapping(mapping);
            context.setSecurityHandler(security);
        }

        // Record before the security handler so 401s show up too
        server.setHandler(new org.eclipse.jetty.server.handler.HandlerWrapper() {
            {
                setHandler(context);
            }

            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, javax.servlet.ServletException {
                String authorization = request.getHeader("Authorization");
                authorizations.add(authorization == null ? "" : authorization.split(" ")[0]);
                if ("PUT".equals(request.getMethod())) {
                    putExpectHeaders.add(String.valueOf(request.getHeader("Expect")));
                }
                super.handle(target, baseRequest, request, response);
            }
        });

        server.start();
        port = connector.getLocalPort();
    }

    void stop() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    int port() {
        return port;
    }

    /** host:port as it appears in URLs */
    String authority() {
        return "localhost:" + port;
    }

    SSLContext clientSslContext() {
        return clientSslContext;
    }

    /** Requests seen by the servlet (i.e. after authentication), as "METHOD /decoded/path". */
    List<String> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    private static KeyStore selfSignedKeyStore() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        X500Name name = new X500Name("CN=localhost");
        Date notBefore = new Date(System.currentTimeMillis() - 60_000);
        Date notAfter = new Date(System.currentTimeMillis() + 86_400_000);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(System.nanoTime()), notBefore, notAfter, name, keyPair.getPublic());
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName[] {
                new GeneralName(GeneralName.dNSName, "localhost"),
                new GeneralName(GeneralName.iPAddress, "127.0.0.1") }));
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", keyPair.getPrivate(), "changeit".toCharArray(), new X509Certificate[] { cert });
        return keyStore;
    }

    private class DavServlet extends HttpServlet {

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String path = decode(req.getRequestURI());
            requests.add(req.getMethod() + " " + path);
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    return;
                }
            }
            File file = resolve(path);

            switch (req.getMethod()) {
                case "PROPFIND":
                    propfind(req, resp, path, file);
                    break;
                case "GET":
                    if (!file.isFile()) {
                        resp.sendError(404);
                        return;
                    }
                    try (OutputStream out = resp.getOutputStream()) {
                        Files.copy(file.toPath(), out);
                    }
                    break;
                case "PUT":
                    if (!file.getParentFile().isDirectory()) {
                        resp.sendError(409);
                        return;
                    }
                    boolean existed = file.exists();
                    try (InputStream in = req.getInputStream()) {
                        Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    resp.setStatus(existed ? 204 : 201);
                    break;
                case "MKCOL":
                    if (file.exists()) {
                        resp.sendError(405);
                    } else if (!file.getParentFile().isDirectory()) {
                        resp.sendError(409);
                    } else {
                        file.mkdir();
                        resp.setStatus(201);
                    }
                    break;
                case "DELETE":
                    if (!file.exists()) {
                        resp.sendError(404);
                        return;
                    }
                    FileUtils.forceDelete(file);
                    resp.setStatus(204);
                    break;
                case "MOVE":
                    String destination = req.getHeader("Destination");
                    destinations.add(destination);
                    File target = resolve(decode(URI.create(destination).getRawPath()));
                    if (!file.exists()) {
                        resp.sendError(404);
                    } else if (!target.getParentFile().isDirectory()) {
                        resp.sendError(409);
                    } else if (target.exists() && "F".equals(req.getHeader("Overwrite"))) {
                        resp.sendError(412);
                    } else {
                        boolean replaced = target.exists();
                        Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        resp.setStatus(replaced ? 204 : 201);
                    }
                    break;
                default:
                    resp.sendError(405);
            }
        }

        private void propfind(HttpServletRequest req, HttpServletResponse resp, String path, File file) throws IOException {
            if (!file.exists()) {
                resp.sendError(404);
                return;
            }
            // Drain the request body
            req.getInputStream().readAllBytes();
            if (propfindOverride != null) {
                resp.setStatus(207);
                resp.getOutputStream().write(propfindOverride.getBytes(StandardCharsets.UTF_8));
                return;
            }

            // Mixed prefixes on purpose: clients must match on namespace, not prefix
            StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            xml.append("<D:multistatus xmlns:D=\"DAV:\" xmlns:lp1=\"DAV:\">");
            appendResponse(xml, path, file);
            if (file.isDirectory() && !"0".equals(req.getHeader("Depth"))) {
                try (Stream<Path> children = Files.list(file.toPath())) {
                    for (Path child : (Iterable<Path>) children.sorted()::iterator) {
                        appendResponse(xml, (path.endsWith("/") ? path : path + "/") + child.getFileName(), child.toFile());
                    }
                }
            }
            xml.append("</D:multistatus>");

            resp.setStatus(207);
            resp.setContentType("application/xml; charset=utf-8");
            resp.getOutputStream().write(xml.toString().getBytes(StandardCharsets.UTF_8));
        }

        private void appendResponse(StringBuilder xml, String path, File file) {
            String href = encode(path);
            if (file.isDirectory() && !href.endsWith("/")) {
                href += "/";
            }
            xml.append("<D:response><D:href>").append(href).append("</D:href><D:propstat><D:prop>");
            if (file.isDirectory()) {
                xml.append("<lp1:resourcetype><D:collection/></lp1:resourcetype>");
            } else {
                xml.append("<lp1:resourcetype/>");
                xml.append("<lp1:getcontentlength>").append(file.length()).append("</lp1:getcontentlength>");
            }
            xml.append("<lp1:getlastmodified>").append(RFC_1123.format(new Date(file.lastModified()).toInstant())).append("</lp1:getlastmodified>");
            String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
            if (lockedPaths.contains(normalized)) {
                xml.append("<D:lockdiscovery><D:activelock><D:locktype><D:write/></D:locktype><D:lockscope><D:exclusive/></D:lockscope>");
                xml.append("<D:depth>0</D:depth><D:owner>someone</D:owner><D:timeout>Second-3600</D:timeout>");
                xml.append("<D:locktoken><D:href>opaquelocktoken:1234</D:href></D:locktoken></D:activelock></D:lockdiscovery>");
            } else {
                xml.append("<D:lockdiscovery/>");
            }
            xml.append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>");
            if (prettyPrint) {
                String pretty = xml.toString().replace("><", ">\n  <").replace("<D:status>HTTP/1.1 200 OK</D:status>", "<D:status>\n      HTTP/1.1 200 OK\n    </D:status>");
                xml.setLength(0);
                xml.append(pretty);
            }
        }

        private File resolve(String path) {
            return new File(root, path);
        }
    }

    private static String decode(String rawPath) {
        try {
            return URLDecoder.decode(rawPath.replace("+", "%2B"), "UTF-8");
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String encode(String path) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : path.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "/-._~".indexOf(c) >= 0) {
                encoded.append((char) c);
            } else {
                encoded.append('%').append(String.format("%02X", c));
            }
        }
        return encoded.toString();
    }
}
