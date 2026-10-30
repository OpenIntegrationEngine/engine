/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.file.filesystems;

import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.mirth.connect.connectors.file.FileSystemConnectionOptions;
import com.mirth.connect.connectors.file.filters.RegexFilenameFilter;

public class WebDavConnection implements FileSystemConnection {
    public class WebDavFileInfo implements FileInfo {
        private String thePath;
        private String name;
        private String url;
        private boolean directory;
        private long size;
        private long lastModified;

        public WebDavFileInfo(String path, String name, String url, boolean directory, long size, long lastModified) {
            this.thePath = path;
            this.name = name;
            this.url = url;
            this.directory = directory;
            this.size = size;
            this.lastModified = lastModified;
        }

        public long getLastModified() {
            return lastModified;
        }

        public String getName() {
            return name;
        }

        /** Gets the absolute pathname of the file */
        public String getAbsolutePath() {
            return url;
        }

        public String getCanonicalPath() throws IOException {
            return url;
        }

        /** Gets the absolute pathname of the directory holding the file */
        public String getParent() {
            return this.thePath;
        }

        public long getSize() {
            return size;
        }

        public boolean isDirectory() {
            return directory;
        }

        public boolean isFile() {
            return !directory;
        }

        public boolean isReadable() {
            return true;
        }

        @Override
        public void populateSourceMap(Map<String, Object> sourceMap) {}
    }

    /** One response element of a PROPFIND multistatus */
    private static class Resource {
        private String path;
        private boolean collection;
        private long length;
        private long lastModified;
        private boolean locked;
    }

    private static class HttpPropfind extends HttpEntityEnclosingRequestBase {
        private static final String BODY = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + "<D:propfind xmlns:D=\"DAV:\"><D:prop>" + "<D:resourcetype/><D:getcontentlength/><D:getlastmodified/><D:lockdiscovery/>" + "</D:prop></D:propfind>";

        HttpPropfind(URI uri, int depth) {
            setURI(uri);
            setHeader("Depth", String.valueOf(depth));
            setEntity(new StringEntity(BODY, ContentType.create("application/xml", StandardCharsets.UTF_8)));
        }

        @Override
        public String getMethod() {
            return "PROPFIND";
        }
    }

    private static class HttpMkcol extends HttpRequestBase {
        HttpMkcol(URI uri) {
            setURI(uri);
        }

        @Override
        public String getMethod() {
            return "MKCOL";
        }
    }

    private static class HttpMove extends HttpRequestBase {
        HttpMove(URI source, URI destination) {
            setURI(source);
            setHeader("Destination", destination.toASCIIString());
            // Slide never overwrote an existing target
            setHeader("Overwrite", "F");
        }

        @Override
        public String getMethod() {
            return "MOVE";
        }
    }

    private static final String DAV = "DAV:";
    private static final Pattern SUCCESS_STATUS = Pattern.compile("^HTTP/\\S+\\s+2\\d\\d");

    private static transient Log logger = LogFactory.getLog(WebDavConnection.class);

    /** The WebDAV client instance */
    private CloseableHttpClient client = null;
    /** Shared across requests so an authenticated session is reused */
    private HttpClientContext context = HttpClientContext.create();
    private RequestConfig requestConfig;
    private String baseUrl;
    private CloseableHttpResponse readResponse = null;
    /** Whether the last path looked up exists, as WebdavResource.exists() reported it */
    private boolean exists = false;
    /** Status line of the last failed PROPFIND */
    private String lastStatus = null;

    public WebDavConnection(String host, int port, boolean secure, FileSystemConnectionOptions fileSystemOptions, int timeout) throws Exception {
        String username = fileSystemOptions.getUsername();
        String password = fileSystemOptions.getPassword();

        baseUrl = new HttpHost(host, port, secure ? "https" : "http").toURI();

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        if (!username.equals("null")) {
            // Scope credentials to this port so a redirect elsewhere doesn't get them
            int authPort = port > 0 ? port : (secure ? 443 : 80);
            credentialsProvider.setCredentials(new AuthScope(host, authPort), new UsernamePasswordCredentials(username, password));

            // NTLM needs the domain split out of DOMAIN\\user
            int separator = username.indexOf('\\');
            String domain = separator < 0 ? null : username.substring(0, separator);
            credentialsProvider.setCredentials(new AuthScope(host, authPort, AuthScope.ANY_REALM, AuthSchemes.NTLM), new NTCredentials(username.substring(separator + 1), password, null, domain));
        }

        // The schemes httpclient 3 negotiated, in its order of preference
        requestConfig = RequestConfig.custom().setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM, AuthSchemes.DIGEST, AuthSchemes.BASIC)).setConnectTimeout(timeout).setSocketTimeout(timeout).build();

        client = HttpClients.custom().setDefaultCredentialsProvider(credentialsProvider).setDefaultRequestConfig(requestConfig).setSSLSocketFactory(new SSLConnectionSocketFactory(SSLContexts.createSystemDefault())).build();

        try {
            stat("/");
        } catch (IOException e) {
            logger.debug(e);
        }
    }

    @Override
    public List<FileInfo> listFiles(String fromDir, String filenamePattern, boolean isRegex, boolean ignoreDot) throws Exception {
        FilenameFilter filenameFilter;

        if (isRegex) {
            filenameFilter = new RegexFilenameFilter(filenamePattern);
        } else {
            filenameFilter = new WildcardFileFilter(filenamePattern.trim().split("\\s*,\\s*"));
        }

        return list(fromDir, true, filenameFilter, ignoreDot);
    }

    @Override
    public List<String> listDirectories(String fromDir) throws Exception {
        List<String> directories = new ArrayList<String>();
        for (FileInfo directory : list(fromDir, false, null, false)) {
            directories.add(getFullPath(directory.getParent(), directory.getName()));
        }
        return directories;
    }

    private List<FileInfo> list(String fromDir, boolean files, FilenameFilter filenameFilter, boolean ignoreDot) throws Exception {
        String dirPath = normalize(getFullPath(fromDir, ""));
        List<Resource> resources = propfind(fromDir.isEmpty() ? "/" : fromDir, 1);
        if (resources == null) {
            throw new IOException("Unable to list directory: '" + fromDir + "': " + lastStatus);
        }

        // The directory itself is part of a Depth 1 response
        int self = selfIndex(resources, dirPath);

        List<FileInfo> fileInfoList = new ArrayList<FileInfo>(resources.size());
        for (int i = 0; i < resources.size(); i++) {
            if (i == self) {
                continue;
            }

            Resource resource = resources.get(i);
            String path = normalize(resource.path);

            String name = path.substring(path.lastIndexOf('/') + 1);
            WebDavFileInfo file = new WebDavFileInfo(fromDir, name, uri(getFullPath(fromDir, name)).toString(), resource.collection, resource.length, resource.lastModified);

            if (files) {
                if (file.isFile() && filenameFilter.accept(null, file.getName()) && !(ignoreDot && file.getName().startsWith("."))) {
                    fileInfoList.add(file);
                }
            } else if (file.isDirectory()) {
                fileInfoList.add(file);
            }
        }

        return fileInfoList;
    }

    @Override
    public boolean exists(String file, String path) {
        try {
            return stat(getFullPath(path, file)) != null;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public InputStream readFile(String file, String fromDir, Map<String, Object> sourceMap) throws Exception {
        String fullPath = getFullPath(fromDir, file);

        Resource resource = stat(fullPath);
        if (resource != null && resource.collection) {
            logger.error("Invalid filepath: " + fullPath);
            throw new Exception("Invalid Path");
        }

        closeReadFile();
        CloseableHttpResponse response = client.execute(new HttpGet(uri(fullPath)), context);
        if (!isSuccess(response)) {
            response.close();
            throw new IOException("Couldn't get file");
        }

        readResponse = response;
        return response.getEntity().getContent();
    }

    @Override
    public void closeReadFile() throws Exception {
        if (readResponse != null) {
            readResponse.close();
            readResponse = null;
        }
    }

    @Override
    public boolean canAppend() {
        return false;
    }

    @Override
    public void writeFile(String file, String toDir, boolean append, InputStream is, long contentLength, Map<String, Object> connectorMap) throws Exception {
        String fullPath = getFullPath(toDir, file);

        // first check if the toDir exists.
        Resource dir = stat(toDir);

        if (dir == null) {
            // create the directory.
            execute(new HttpMkcol(uri(toDir)));
            logger.info("Destination directory does not exist. Creating directory: '" + toDir + "'");
        } else if (!dir.collection) {
            // make sure it's a directory, not a file.
            throw new Exception("The destination directory path is invalid: '" + toDir + "'");
        }

        HttpPut put = new HttpPut(uri(fullPath));
        // The body is streamed, so it can't be sent twice. With Expect: 100-continue an
        // authentication challenge arrives before the body, and the PUT can still be retried.
        put.setConfig(RequestConfig.copy(requestConfig).setExpectContinueEnabled(true).build());
        put.setEntity(new InputStreamEntity(is, contentLength));
        if (!execute(put)) {
            logger.error("Unable to write file: '" + fullPath);
        }
    }

    @Override
    public void delete(String file, String fromDir, boolean mayNotExist) throws Exception {
        String fullPath = getFullPath(fromDir, file);

        if (!execute(new HttpDelete(uri(fullPath)))) {
            if (!mayNotExist) {
                logger.error("Unable to delete file: '" + fullPath + "'");
            }
        }
    }

    @Override
    public void move(String fromName, String fromDir, String toName, String toDir) throws Exception {
        String sourcePath = getFullPath(fromDir, fromName);
        String targetPath = getFullPath(toDir, toName);

        // first check if the toDir exists.
        Resource dir = stat(toDir);

        if (dir == null) {
            // create the directory. and then move.
            execute(new HttpMkcol(uri(toDir)));
            logger.info("Move-To directory does not exist. Creating directory: '" + toDir + "'");
        } else if (!dir.collection) {
            // make sure it's a directory, not a file.
            throw new Exception("The move-to directory path is invalid: '" + toDir + "'");
        }

        if (!execute(new HttpMove(uri(sourcePath), uri(targetPath)))) {
            logger.error("Unable to move file: '" + sourcePath + "' to '" + targetPath + "'");
        }
    }

    @Override
    public boolean isConnected() {
        return client != null && exists;
    }

    @Override
    public void disconnect() {}

    @Override
    public void activate() {
        // irrelevant
    }

    @Override
    public void passivate() {
        // irrelevant
    }

    @Override
    public void destroy() {
        try {
            closeReadFile();
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            logger.debug(e);
        }
    }

    @Override
    public boolean isValid() {
        return client != null && exists;
    }

    @Override
    public boolean canRead(String readDir) {
        try {
            Resource dir = stat(readDir);
            return dir != null && dir.collection;
        } catch (IOException e) {
            logger.debug(e);
            return false;
        }
    }

    @Override
    public boolean canWrite(String writeDir) {
        try {
            Resource dir = stat(writeDir);
            return dir != null && dir.collection && !dir.locked;
        } catch (IOException e) {
            logger.debug(e);
            return false;
        }
    }

    /** Returns the resource at path, or null if it can't be found */
    private Resource stat(String path) throws IOException {
        List<Resource> resources = propfind(path, 0);
        return resources == null || resources.isEmpty() ? null : resources.get(0);
    }

    /** Returns the resources of a PROPFIND multistatus, or null if the request failed */
    private List<Resource> propfind(String path, int depth) throws IOException {
        try (CloseableHttpResponse response = client.execute(new HttpPropfind(uri(path), depth), context)) {
            exists = response.getStatusLine().getStatusCode() == HttpStatus.SC_MULTI_STATUS;
            if (!exists) {
                lastStatus = response.getStatusLine().toString();
                EntityUtils.consume(response.getEntity());
                return null;
            }

            try (InputStream in = response.getEntity().getContent()) {
                return parseMultistatus(newDocumentBuilder().parse(in));
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Invalid PROPFIND response for '" + path + "'", e);
            }
        }
    }

    private static List<Resource> parseMultistatus(Document document) {
        List<Resource> resources = new ArrayList<Resource>();
        NodeList responses = document.getElementsByTagNameNS(DAV, "response");

        for (int i = 0; i < responses.getLength(); i++) {
            Element response = (Element) responses.item(i);
            Element href = child(response, "href");
            if (href == null) {
                continue;
            }

            // A response-level status means there are no properties, e.g. 403
            Element responseStatus = child(response, "status");
            if (responseStatus != null && !isSuccess(responseStatus)) {
                continue;
            }
            boolean found = responseStatus != null;

            Resource resource = new Resource();
            resource.path = hrefPath(href.getTextContent().trim());

            // Only properties from a 2xx propstat are present
            for (Element propstat : children(response, "propstat")) {
                Element prop = child(propstat, "prop");
                if (prop == null || !isSuccess(child(propstat, "status"))) {
                    continue;
                }
                found = true;

                Element resourceType = child(prop, "resourcetype");
                if (resourceType != null && child(resourceType, "collection") != null) {
                    resource.collection = true;
                }

                Element contentLength = child(prop, "getcontentlength");
                if (contentLength != null) {
                    try {
                        resource.length = Long.parseLong(contentLength.getTextContent().trim());
                    } catch (NumberFormatException e) {}
                }

                Element lastModified = child(prop, "getlastmodified");
                if (lastModified != null) {
                    Date date = DateUtils.parseDate(lastModified.getTextContent().trim());
                    if (date != null) {
                        resource.lastModified = date.getTime();
                    }
                }

                Element lockDiscovery = child(prop, "lockdiscovery");
                if (lockDiscovery != null) {
                    for (Element activeLock : children(lockDiscovery, "activelock")) {
                        Element lockType = child(activeLock, "locktype");
                        if (lockType != null && child(lockType, "write") != null) {
                            resource.locked = true;
                        }
                    }
                }
            }

            if (found) {
                resources.add(resource);
            }
        }

        return resources;
    }

    private static boolean isSuccess(Element status) {
        return status == null || SUCCESS_STATUS.matcher(status.getTextContent().trim()).find();
    }

    /**
     * Finds the requested directory's own entry. Behind a rewriting proxy its href carries a
     * different prefix, so fall back to the entry every other entry lives under.
     */
    private static int selfIndex(List<Resource> resources, String dirPath) {
        for (int i = 0; i < resources.size(); i++) {
            if (normalize(resources.get(i).path).equalsIgnoreCase(dirPath)) {
                return i;
            }
        }

        for (int i = 0; i < resources.size(); i++) {
            String path = normalize(resources.get(i).path);
            String prefix = path.endsWith("/") ? path : path + "/";
            boolean parent = true;
            for (int j = 0; j < resources.size() && parent; j++) {
                parent = j == i || normalize(resources.get(j).path).startsWith(prefix);
            }
            if (parent) {
                return i;
            }
        }

        return -1;
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private static Element child(Element parent, String localName) {
        List<Element> children = children(parent, localName);
        return children.isEmpty() ? null : children.get(0);
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> children = new ArrayList<Element>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE && DAV.equals(node.getNamespaceURI()) && localName.equals(node.getLocalName())) {
                children.add((Element) node);
            }
        }
        return children;
    }

    /** Decodes an href, which may be an absolute URL or an absolute path */
    private static String hrefPath(String href) {
        int schemeEnd = href.indexOf("://");
        if (schemeEnd >= 0) {
            int pathStart = href.indexOf('/', schemeEnd + 3);
            href = pathStart < 0 ? "/" : href.substring(pathStart);
        }
        try {
            // Paths don't use form encoding, so keep '+' as is
            return URLDecoder.decode(href.replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return href;
        }
    }

    private boolean execute(HttpUriRequest request) throws IOException {
        try (CloseableHttpResponse response = client.execute(request, context)) {
            EntityUtils.consume(response.getEntity());
            return isSuccess(response);
        }
    }

    private static boolean isSuccess(HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        return statusCode >= 200 && statusCode < 300;
    }

    private URI uri(String path) throws IOException {
        try {
            return new URIBuilder(baseUrl).setPath(path).build();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private static String normalize(String path) {
        path = ("/" + path).replaceAll("/+", "/");
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private String getFullPath(String dir, String file) {
        return ("/" + dir + "/" + file).replaceAll("//", "/");
    }
}
