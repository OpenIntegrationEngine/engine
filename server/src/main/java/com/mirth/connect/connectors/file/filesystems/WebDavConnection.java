/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.file.filesystems;

import java.io.ByteArrayInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;

import com.github.sardine.DavResource;
import com.github.sardine.impl.SardineImpl;
import com.github.sardine.impl.SardineRedirectStrategy;
import com.github.sardine.impl.handler.MultiStatusResponseHandler;
import com.github.sardine.impl.methods.HttpPropFind;
import com.github.sardine.model.Activelock;
import com.github.sardine.model.Prop;
import com.github.sardine.model.Propstat;
import com.github.sardine.model.Response;
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

    /** Sardine leaves lockdiscovery out of DavResource, so keep the raw responses */
    private static class Client extends SardineImpl {
        private static final String PROPFIND = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + "<D:propfind xmlns:D=\"DAV:\"><D:prop>" + "<D:resourcetype/><D:getcontentlength/><D:getlastmodified/><D:lockdiscovery/>" + "</D:prop></D:propfind>";

        Client(HttpClientBuilder builder) {
            super(builder);
        }

        List<Response> propfind(String url, int depth) throws IOException {
            HttpPropFind propfind = new HttpPropFind(url);
            propfind.setDepth(String.valueOf(depth));
            propfind.setEntity(new StringEntity(PROPFIND, ContentType.create("application/xml", StandardCharsets.UTF_8)));
            return execute(propfind, new MultiStatusResponseHandler()).getResponse();
        }
    }

    /**
     * Streams a ByteArrayInputStream, which is what FileDispatcher passes, and resends it by
     * resetting the stream. The PUT can then be retried after an authentication challenge without
     * copying the payload, even when a proxy has already read the body.
     */
    private static class ResettableEntity extends InputStreamEntity {
        private final ByteArrayInputStream in;

        ResettableEntity(ByteArrayInputStream in, long length) {
            super(in, length, ContentType.APPLICATION_OCTET_STREAM);
            this.in = in;
            in.mark(0);
        }

        @Override
        public boolean isRepeatable() {
            return true;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            in.reset();
            super.writeTo(out);
        }
    }

    private static final Pattern SUCCESS_STATUS = Pattern.compile("^HTTP/\\S+\\s+2\\d\\d");

    private static transient Log logger = LogFactory.getLog(WebDavConnection.class);

    /** The WebDAV client instance */
    private Client client = null;
    private String baseUrl;
    private InputStream readStream = null;
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
        RequestConfig requestConfig = RequestConfig.custom().setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM, AuthSchemes.DIGEST, AuthSchemes.BASIC)).setConnectTimeout(timeout).setSocketTimeout(timeout).setExpectContinueEnabled(false).build();

        client = new Client(HttpClients.custom().setDefaultCredentialsProvider(credentialsProvider).setDefaultRequestConfig(requestConfig).setRedirectStrategy(new SardineRedirectStrategy()).setSSLSocketFactory(new SSLConnectionSocketFactory(SSLContexts.createSystemDefault())));

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
            directories.add(normalize(getFullPath(directory.getParent(), directory.getName())));
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
            WebDavFileInfo file = new WebDavFileInfo(fromDir, name, url(getFullPath(fromDir, name)), resource.collection, resource.length, resource.lastModified);

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
        try {
            readStream = client.get(url(fullPath));
        } catch (HttpResponseException e) {
            throw new IOException("Couldn't get file", e);
        }
        return readStream;
    }

    @Override
    public void closeReadFile() throws Exception {
        if (readStream != null) {
            readStream.close();
            readStream = null;
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
            createDirectory(toDir);
            logger.info("Destination directory does not exist. Creating directory: '" + toDir + "'");
        } else if (!dir.collection) {
            // make sure it's a directory, not a file.
            throw new Exception("The destination directory path is invalid: '" + toDir + "'");
        }

        try {
            if (is instanceof ByteArrayInputStream) {
                client.put(url(fullPath), new ResettableEntity((ByteArrayInputStream) is, contentLength), null, false);
            } else {
                // Other streams can't be resent. Expect: 100-continue usually gets an authentication
                // challenge before the body, but a buffering proxy may still consume it first.
                client.put(url(fullPath), new InputStreamEntity(is, contentLength, ContentType.APPLICATION_OCTET_STREAM), null, true);
            }
        } catch (HttpResponseException e) {
            logger.error("Unable to write file: '" + fullPath, e);
        }
    }

    @Override
    public void delete(String file, String fromDir, boolean mayNotExist) throws Exception {
        String fullPath = getFullPath(fromDir, file);

        try {
            client.delete(url(fullPath));
        } catch (HttpResponseException e) {
            if (!mayNotExist) {
                logger.error("Unable to delete file: '" + fullPath + "'", e);
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
            createDirectory(toDir);
            logger.info("Move-To directory does not exist. Creating directory: '" + toDir + "'");
        } else if (!dir.collection) {
            // make sure it's a directory, not a file.
            throw new Exception("The move-to directory path is invalid: '" + toDir + "'");
        }

        try {
            // Slide never overwrote an existing target
            client.move(url(sourcePath), url(targetPath), false);
        } catch (HttpResponseException e) {
            logger.error("Unable to move file: '" + sourcePath + "' to '" + targetPath + "'", e);
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
                client.shutdown();
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
        List<Response> responses;
        try {
            responses = client.propfind(url(path), depth);
            exists = true;
        } catch (HttpResponseException e) {
            exists = false;
            lastStatus = e.getMessage();
            return null;
        }

        List<Resource> resources = new ArrayList<Resource>();
        for (Response response : responses) {
            DavResource davResource;
            try {
                davResource = new DavResource(response);
            } catch (URISyntaxException e) {
                continue;
            }

            // A response-level status means there are no properties, e.g. 403
            int status = davResource.getStatusCode();
            if (status < 200 || status >= 300) {
                continue;
            }

            // Only count responses with a 2xx propstat, or none at all
            boolean found = response.getStatus() != null;
            Resource resource = new Resource();
            for (Propstat propstat : response.getPropstat()) {
                Prop prop = propstat.getProp();
                if (prop == null || !isSuccess(propstat.getStatus())) {
                    continue;
                }
                found = true;

                if (prop.getLockdiscovery() != null) {
                    for (Activelock activeLock : prop.getLockdiscovery().getActivelock()) {
                        if (activeLock.getLocktype() != null && activeLock.getLocktype().getWrite() != null) {
                            resource.locked = true;
                        }
                    }
                }
            }
            if (!found) {
                continue;
            }

            resource.path = davResource.getPath();
            resource.collection = davResource.isDirectory();
            resource.length = Math.max(davResource.getContentLength(), 0);
            Date modified = davResource.getModified();
            resource.lastModified = modified == null ? 0 : modified.getTime();
            resources.add(resource);
        }

        return resources;
    }

    private static boolean isSuccess(String status) {
        return status == null || SUCCESS_STATUS.matcher(status.trim()).find();
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

    private void createDirectory(String path) throws IOException {
        try {
            client.createDirectory(url(path));
        } catch (HttpResponseException e) {
            logger.debug(e);
        }
    }

    private String url(String path) throws IOException {
        try {
            return new URIBuilder(baseUrl).setPath(path).build().toASCIIString();
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
