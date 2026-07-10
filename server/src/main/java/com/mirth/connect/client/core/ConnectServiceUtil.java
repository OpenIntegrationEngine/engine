/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.github.zafarkhaja.semver.Version;

import com.mirth.connect.model.User;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.model.notification.Notification;
import com.mirth.connect.util.MirthSSLUtil;

public class ConnectServiceUtil {
    private final static String URL_CONNECT_SERVER = BrandingConstants.CONNECT_SERVER_URL;
    private final static String URL_REGISTRATION_SERVLET = "/RegistrationServlet";
    private final static String URL_USAGE_SERVLET = "/UsageStatisticsServlet";
    private static String URL_NOTIFICATIONS = BrandingConstants.NOTIFICATIONS_URL;
    private final static int TIMEOUT = 10000;
    public final static Integer MILLIS_PER_DAY = 86400000;

    public static void registerUser(String serverId, String mirthVersion, User user, String[] protocols, String[] cipherSuites) throws ClientException {
        if (!BrandingConstants.CENTRAL_USER_REGISTRATION) {
            throw new UnsupportedOperationException("User Registration is disabled");
        }

        CloseableHttpClient httpClient = null;
        CloseableHttpResponse httpResponse = null;
        NameValuePair[] params = { new BasicNameValuePair("serverId", serverId),
                new BasicNameValuePair("version", mirthVersion),
                new BasicNameValuePair("user", ObjectXMLSerializer.getInstance().serialize(user)) };

        HttpPost post = new HttpPost();
        post.setURI(URI.create(URL_CONNECT_SERVER + URL_REGISTRATION_SERVLET));
        post.setEntity(new UrlEncodedFormEntity(Arrays.asList(params), StandardCharsets.UTF_8));
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(TIMEOUT).setConnectionRequestTimeout(TIMEOUT).setSocketTimeout(TIMEOUT).build();

        try {
            HttpClientContext postContext = HttpClientContext.create();
            postContext.setRequestConfig(requestConfig);
            httpClient = getClient(protocols, cipherSuites);
            httpResponse = httpClient.execute(post, postContext);
            StatusLine statusLine = httpResponse.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if ((statusCode != HttpStatus.SC_OK) && (statusCode != HttpStatus.SC_MOVED_TEMPORARILY)) {
                throw new Exception("Failed to connect to update server: " + statusLine);
            }
        } catch (Exception e) {
            throw new ClientException(e);
        } finally {
            HttpClientUtils.closeQuietly(httpResponse);
            HttpClientUtils.closeQuietly(httpClient);
        }
    }

    /**
     * Query an external source for new releases. Return notifications for each release that's greater than the current version.
     * 
     * @param serverId
     * @param mirthVersion
     * @param extensionVersions
     * @param protocols
     * @param cipherSuites
     * @return a non-null list
     * @throws Exception should anything fail dealing with the web request and the handling of its response
     */
    public static List<Notification> getNotifications(String serverId, String mirthVersion, Map<String, String> extensionVersions, String[] protocols, String[] cipherSuites) throws Exception {
        if (!BrandingConstants.CHECK_FOR_NOTIFICATIONS) {
            throw new UnsupportedOperationException("Checking for Notifications is disabled.");
        }
        
        List<Notification> validNotifications = Collections.emptyList();
        Optional<Version> parsedMirthVersion = Version.tryParse(mirthVersion);
        if (!parsedMirthVersion.isPresent()) {
            return validNotifications;
        }

        CloseableHttpClient httpClient = null;
        CloseableHttpResponse httpResponse = null;
        HttpEntity responseEntity = null;
        try {
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(TIMEOUT).setConnectionRequestTimeout(TIMEOUT).setSocketTimeout(TIMEOUT).build();
            HttpClientContext getContext = HttpClientContext.create();
            getContext.setRequestConfig(requestConfig);
            httpClient = getClient(protocols, cipherSuites);
            HttpGet httpget = new HttpGet(URL_NOTIFICATIONS);
            // adding header makes github send back body as rendered html for the "body_html" field
            httpget.addHeader("Accept", "application/vnd.github.html+json");
            httpResponse = httpClient.execute(httpget, getContext);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                responseEntity = httpResponse.getEntity();

                validNotifications = toJsonStream(responseEntity)
                    .filter(dropOlderThan(parsedMirthVersion.get()))
                    .map(ConnectServiceUtil::toNotification)
                    .collect(Collectors.toList());
            } else {
                throw new ClientException("Status code: " + statusCode);
            }
        } finally {
            EntityUtils.consumeQuietly(responseEntity);
            HttpClientUtils.closeQuietly(httpResponse);
            HttpClientUtils.closeQuietly(httpClient);
        }

        return validNotifications;
    }

    /**
     * Creates a predicate to filter JSON nodes representing releases.
     * The predicate returns true if the "tag_name" of the JSON node, when parsed as a semantic version,
     * is newer than the provided reference version.
     *
     * @param version The reference {@link Version} to compare against
     * @return A {@link Predicate} for {@link JsonNode}s that evaluates to true for newer versions.
     */
    protected static Predicate<JsonNode> dropOlderThan(Version version) {
        return node -> Version.tryParse(node.get("tag_name").asText())
            .filter(version::isLowerThan)
            .isPresent();
    }

    /**
     * Converts an HTTP response entity containing a JSON array into a stream of {@link JsonNode} objects.
     * Each element in the JSON array becomes a {@link JsonNode} in the stream.
     *
     * @param responseEntity The {@link HttpEntity} from the HTTP response, expected to contain a JSON array.
     * @return A stream of {@link JsonNode} objects.
     * @throws IOException If an I/O error occurs while reading the response entity.
     * @throws JsonMappingException If an error occurs during JSON parsing.
     */
    protected static Stream<JsonNode> toJsonStream(HttpEntity responseEntity) throws IOException, JsonMappingException {
        JsonNode rootNode = new ObjectMapper().readTree(new InputStreamReader(responseEntity.getContent(), getCharset(responseEntity)));
        return StreamSupport.stream(rootNode.spliterator(), false);
    }

    /**
     * Try pulling a charset from the given response. Default to UTF-8.
     * 
     * @param responseEntity
     * @return
     */
    protected static Charset getCharset(HttpEntity responseEntity) {
        Charset charset = StandardCharsets.UTF_8;
        try {
            ContentType ct = ContentType.get(responseEntity); 
            Charset fromHeader = ct.getCharset(); 
            if (fromHeader != null) {
                charset = fromHeader;
            }
        } catch (Exception ignore) {}
        return charset;
    }

    /**
     * Given a JSON node with HTML content from a GitHub release feed, convert it to a notification.
     * 
     * @param node
     * @return a notification
     */
    protected static Notification toNotification(JsonNode node) {
        Notification notification = new Notification();
        notification.setId(node.get("id").asInt());
        notification.setName(node.get("name").asText());
        notification.setDate(node.get("published_at").asText());
        notification.setContent(node.get("body_html").asText());
        return notification;
    }

    public static int getNotificationCount(String serverId, String mirthVersion, Map<String, String> extensionVersions, Set<Integer> archivedNotifications, String[] protocols, String[] cipherSuites) {
        if (!BrandingConstants.CHECK_FOR_NOTIFICATIONS) {
            throw new UnsupportedOperationException("Checking for Notifications is disabled.");
        }

        Long notificationCount = 0L;
        try {
            notificationCount = getNotifications(serverId, mirthVersion, extensionVersions, protocols, cipherSuites)
                .stream()
                .map(Notification::getId)
                .filter(id -> !archivedNotifications.contains(id))
                .count();
        } catch (Exception ignore) {
           System.err.println("Failed to get notification count, defaulting to zero: " + ignore);
        }
        return notificationCount.intValue();
    }

    public static boolean sendStatistics(String serverId, String mirthVersion, boolean server, String data, String[] protocols, String[] cipherSuites) {
        if (!BrandingConstants.SEND_USAGE_STATISTICS) {
            throw new UnsupportedOperationException("Sending Usage Statistics is disabled.");
        }
        
        if (data == null) {
            return false;
        }

        boolean isSent = false;

        CloseableHttpClient client = null;
        HttpPost post = new HttpPost();
        CloseableHttpResponse response = null;
        NameValuePair[] params = { new BasicNameValuePair("serverId", serverId),
                new BasicNameValuePair("version", mirthVersion),
                new BasicNameValuePair("server", Boolean.toString(server)),
                new BasicNameValuePair("data", data) };
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(TIMEOUT).setConnectionRequestTimeout(TIMEOUT).setSocketTimeout(TIMEOUT).build();

        post.setURI(URI.create(URL_CONNECT_SERVER + URL_USAGE_SERVLET));
        post.setEntity(new UrlEncodedFormEntity(Arrays.asList(params), StandardCharsets.UTF_8));

        try {
            HttpClientContext postContext = HttpClientContext.create();
            postContext.setRequestConfig(requestConfig);
            client = getClient(protocols, cipherSuites);
            response = client.execute(post, postContext);
            StatusLine statusLine = response.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if ((statusCode == HttpStatus.SC_OK)) {
                isSent = true;
            }
        } catch (Exception e) {
        } finally {
            HttpClientUtils.closeQuietly(response);
            HttpClientUtils.closeQuietly(client);
        }
        return isSent;
    }

    private static CloseableHttpClient getClient(String[] protocols, String[] cipherSuites) {
        RegistryBuilder<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create();
        String[] enabledProtocols = MirthSSLUtil.getEnabledHttpsProtocols(protocols);
        String[] enabledCipherSuites = MirthSSLUtil.getEnabledHttpsCipherSuites(cipherSuites);
        SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(SSLContexts.createSystemDefault(), enabledProtocols, enabledCipherSuites, SSLConnectionSocketFactory.STRICT_HOSTNAME_VERIFIER);
        socketFactoryRegistry.register("https", sslConnectionSocketFactory);

        BasicHttpClientConnectionManager httpClientConnectionManager = new BasicHttpClientConnectionManager(socketFactoryRegistry.build());
        httpClientConnectionManager.setSocketConfig(SocketConfig.custom().setSoTimeout(TIMEOUT).build());
        return HttpClients.custom().setConnectionManager(httpClientConnectionManager).build();
    }
}
