/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.ssl.SSLContexts;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.util.MessageMaps;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

public class HttpUtilTest {

    private static final char[] KEY_PASSWORD = "changeit".toCharArray();

    @Test
    public void testExecuteGetRequest() throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(null, null);
        SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(trustStore, new TrustAllStrategy()).build();

        // Test GET request to a local HTTPS server, so the test makes no outside call
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(SSLContexts.custom().loadKeyMaterial(selfSignedKeyStore(), KEY_PASSWORD).build()));
        server.createContext("/", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            assertEquals("ok", HttpUtil.doExecuteGetRequest("https://127.0.0.1:" + server.getAddress().getPort() + "/", 30000, true, MirthSSLUtil.DEFAULT_HTTPS_CLIENT_PROTOCOLS, MirthSSLUtil.DEFAULT_HTTPS_CIPHER_SUITES, sslContext));
        } finally {
            server.stop(0);
        }

        // Test GET request to a local port nothing listens on
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            closedPort = socket.getLocalPort();
        }
        try {
            HttpUtil.doExecuteGetRequest("https://127.0.0.1:" + closedPort + "/", 30000, true, MirthSSLUtil.DEFAULT_HTTPS_CLIENT_PROTOCOLS, MirthSSLUtil.DEFAULT_HTTPS_CIPHER_SUITES, sslContext);
            fail("Exception should have been thrown");
        } catch (Exception e) {
        }
    }

    /** A keystore holding a one-day self-signed certificate for 127.0.0.1, so hostname verification passes. */
    private static KeyStore selfSignedKeyStore() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Name name = new X500Name("CN=127.0.0.1");
        Date now = new Date();
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(name, BigInteger.ONE, now, new Date(now.getTime() + 86400000L), name, keyPair.getPublic());
        certBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName(GeneralName.iPAddress, "127.0.0.1")));
        Certificate certificate = new JcaX509CertificateConverter().getCertificate(certBuilder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", keyPair.getPrivate(), KEY_PASSWORD, new Certificate[] { certificate });
        return keyStore;
    }

    @Test
    public void testMapOrderPreserved() throws Exception {
        final String MAP_KEY = "MyMap";

        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("First", 1);
        initial.put("Second", 2);
        initial.put("Third", 3);

        ConnectorMessage cm = new ConnectorMessage();
        cm.getSourceMap().put(MAP_KEY, initial);

        Map<String, ?> copied = HttpUtil.getTableMap(MAP_KEY, new MessageMaps(), cm);
        assertArrayEquals("Failed to preserve key order", initial.keySet().toArray(), copied.keySet().toArray());
    }
}
