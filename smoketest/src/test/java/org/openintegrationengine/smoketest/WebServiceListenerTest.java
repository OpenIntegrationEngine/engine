// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sends a SOAP request to a deployed Web Service Listener. Messages submitted through the client
 * API never reach the listener's HTTP worker threads, which is where SAAJ is resolved, so the
 * fixture tests cannot cover this.
 */
@DisplayName("Web Service Listener")
class WebServiceListenerTest {

    /** The listener port and service name in web-service-listener/channel.xml. */
    private static final int PORT = 8081;
    private static final String SERVICE = "Mirth";

    private static final String ENVELOPE = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ws="http://ws.connectors.connect.mirth.com/">
              <soapenv:Body>
                <ws:acceptMessage>
                  <arg0>smoke test</arg0>
                </ws:acceptMessage>
              </soapenv:Body>
            </soapenv:Envelope>
            """;

    private static String channelId;

    @BeforeAll
    static void deploy() throws Exception {
        channelId = Harness.deploy("web-service-listener/channel.xml");
    }

    @AfterAll
    static void undeploy() {
        Harness.undeploy(channelId);
    }

    /**
     * On JDK 19 and later the listener's worker threads lost the server classloader, so every
     * request failed with "Unable to create SAAJ meta-factory" while the WSDL still served fine.
     */
    @Test
    @DisplayName("accepts a SOAP request")
    void acceptsSoapRequest() throws Exception {
        URI endpoint = new URI("http", null, URI.create(HarnessConfig.BASE_URL).getHost(), PORT,
                "/services/" + SERVICE, null, null);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMillis(HarnessConfig.REQUEST_TIMEOUT_MILLIS))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(ENVELOPE))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), () -> "status of POST " + endpoint + ": " + response.body());
        assertTrue(response.body().contains("acceptMessageResponse"), () -> "response body: " + response.body());
    }
}
