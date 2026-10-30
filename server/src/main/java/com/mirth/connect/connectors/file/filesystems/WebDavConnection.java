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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.impl.SardineImpl;
import com.github.sardine.impl.SardineRedirectStrategy;

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

    private static transient Log logger = LogFactory.getLog(WebDavConnection.class);

    /** The WebDAV client instance */
    private Sardine client = null;
    private String baseUrl;
    private InputStream readStream = null;
    /** Whether the last path looked up exists, as WebdavResource.exists() reported it */
    private boolean exists = false;
    /** Status of the last failed PROPFIND */
    private String lastStatus = null;

    public WebDavConnection(String host, int port, boolean secure, FileSystemConnectionOptions fileSystemOptions, int timeout) throws Exception {
        this(createClient(host, port, secure, fileSystemOptions, timeout), new HttpHost(host, port, secure ? "https" : "http").toURI());
    }

    WebDavConnection(Sardine client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;

        try {
            stat("/");
        } catch (IOException e) {
            logger.debug(e);
        }
    }

    private static Sardine createClient(String host, int port, boolean secure, FileSystemConnectionOptions fileSystemOptions, int timeout) {
        String username = fileSystemOptions.getUsername();
        String password = fileSystemOptions.getPassword();
        int authPort = port > 0 ? port : (secure ? 443 : 80);

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        if (!username.equals("null")) {
            // Scope credentials to this port so a redirect elsewhere doesn't get them
            credentialsProvider.setCredentials(new AuthScope(host, authPort), new UsernamePasswordCredentials(username, password));

            // NTLM needs the domain split out of DOMAIN\\user
            int separator = username.indexOf('\\');
            String domain = separator < 0 ? null : username.substring(0, separator);
            credentialsProvider.setCredentials(new AuthScope(host, authPort, AuthScope.ANY_REALM, AuthSchemes.NTLM), new NTCredentials(username.substring(separator + 1), password, null, domain));
        }

        // The schemes httpclient 3 negotiated, in its order of preference
        RequestConfig requestConfig = RequestConfig.custom().setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM, AuthSchemes.DIGEST, AuthSchemes.BASIC)).setConnectTimeout(timeout).setSocketTimeout(timeout).setExpectContinueEnabled(false).build();

        SardineImpl sardine = new SardineImpl(HttpClients.custom().setDefaultCredentialsProvider(credentialsProvider).setDefaultRequestConfig(requestConfig).setRedirectStrategy(new SardineRedirectStrategy()).setSSLSocketFactory(new SSLConnectionSocketFactory(SSLContexts.createSystemDefault())));
        if (secure && !username.equals("null")) {
            // Send Basic up front so a streamed PUT doesn't have to be resent. Only over TLS, so a
            // server that would have negotiated Digest never sees the password in cleartext.
            sardine.enablePreemptiveAuthentication(host, authPort, authPort);
        }
        return sardine;
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
        List<DavResource> resources = propfind(fromDir.isEmpty() ? "/" : fromDir, 1);
        if (resources == null) {
            throw new IOException("Unable to list directory: '" + fromDir + "': " + lastStatus);
        }

        // The directory itself is part of a Depth 1 response
        int self = selfIndex(resources, dirPath);

        List<FileInfo> fileInfoList = new ArrayList<FileInfo>(resources.size());
        for (int i = 0; i < resources.size(); i++) {
            DavResource resource = resources.get(i);
            // Skip the directory itself and entries without properties, e.g. 403
            if (i == self || resource.getStatusCode() < 200 || resource.getStatusCode() >= 300) {
                continue;
            }

            String name = resource.getName();
            long size = Math.max(resource.getContentLength(), 0);
            long lastModified = resource.getModified() == null ? 0 : resource.getModified().getTime();
            WebDavFileInfo file = new WebDavFileInfo(fromDir, name, url(getFullPath(fromDir, name)), resource.isDirectory(), size, lastModified);

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

        DavResource resource = stat(fullPath);
        if (resource != null && resource.isDirectory()) {
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
        DavResource dir = stat(toDir);

        if (dir == null) {
            // create the directory.
            createDirectory(toDir);
            logger.info("Destination directory does not exist. Creating directory: '" + toDir + "'");
        } else if (!dir.isDirectory()) {
            // make sure it's a directory, not a file.
            throw new Exception("The destination directory path is invalid: '" + toDir + "'");
        }

        try {
            client.put(url(fullPath), is, "application/octet-stream", true, contentLength);
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
        DavResource dir = stat(toDir);

        if (dir == null) {
            // create the directory. and then move.
            createDirectory(toDir);
            logger.info("Move-To directory does not exist. Creating directory: '" + toDir + "'");
        } else if (!dir.isDirectory()) {
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
        return isDirectory(readDir);
    }

    @Override
    public boolean canWrite(String writeDir) {
        // A failed write is reported when it happens
        return isDirectory(writeDir);
    }

    private boolean isDirectory(String path) {
        try {
            DavResource dir = stat(path);
            return dir != null && dir.isDirectory();
        } catch (IOException e) {
            logger.debug(e);
            return false;
        }
    }

    /** Returns the resource at path, or null if it can't be found */
    private DavResource stat(String path) throws IOException {
        List<DavResource> resources = propfind(path, 0);
        return resources == null || resources.isEmpty() ? null : resources.get(0);
    }

    /** Returns the resources of a PROPFIND, or null if the request failed */
    private List<DavResource> propfind(String path, int depth) throws IOException {
        try {
            List<DavResource> resources = client.list(url(path), depth, Collections.emptySet());
            exists = true;
            return resources;
        } catch (HttpResponseException e) {
            exists = false;
            lastStatus = e.getMessage();
            return null;
        }
    }

    /**
     * Finds the requested directory's own entry. Behind a rewriting proxy its href carries a
     * different prefix, so fall back to the entry every other entry lives under.
     */
    private static int selfIndex(List<DavResource> resources, String dirPath) {
        for (int i = 0; i < resources.size(); i++) {
            if (normalize(resources.get(i).getPath()).equalsIgnoreCase(dirPath)) {
                return i;
            }
        }

        for (int i = 0; i < resources.size(); i++) {
            String path = normalize(resources.get(i).getPath());
            String prefix = path.endsWith("/") ? path : path + "/";
            boolean parent = true;
            for (int j = 0; j < resources.size() && parent; j++) {
                parent = j == i || normalize(resources.get(j).getPath()).startsWith(prefix);
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
