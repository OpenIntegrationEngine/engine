// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.connectors.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.client.core.Version;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

public class HttpReceiverPropertiesTest {

    @BeforeClass
    public static void setup() throws Exception {
        try {
            ObjectXMLSerializer.getInstance().init(Version.getLatest().toString());
        } catch (Exception e) {
            // Ignore if it has already been initialized
        }
    }

    /**
     * The whole point of the constant is that leaving the field alone changes nothing, so it has to
     * track Jetty rather than merely agree with itself. This fails if a Jetty upgrade moves the
     * default out from under us.
     */
    @Test
    public void testDefaultRequestHeaderSizeMatchesJetty() {
        assertEquals(new org.eclipse.jetty.server.HttpConfiguration().getRequestHeaderSize(), HttpReceiverProperties.DEFAULT_REQUEST_HEADER_SIZE);
    }

    @Test
    public void testNewPropertiesUseTheDefaultRequestHeaderSize() {
        assertEquals(String.valueOf(HttpReceiverProperties.DEFAULT_REQUEST_HEADER_SIZE), new HttpReceiverProperties().getRequestHeaderSize());
    }

    @Test
    public void testRequestHeaderSizeRoundTrip() {
        HttpReceiverProperties properties = new HttpReceiverProperties();
        properties.setRequestHeaderSize("32768");
        assertEquals("32768", properties.getRequestHeaderSize());
    }

    /**
     * Channels saved before this property existed have no requestHeaderSize element. XStream
     * instantiates without calling the constructor, so the field stays null and the getter is the
     * only thing standing between an old channel and a listener configured with a header size of
     * zero.
     */
    @Test
    public void testPropertiesWithoutRequestHeaderSizeElementFallBackToJettyDefault() {
        HttpReceiverProperties properties = new HttpReceiverProperties();
        properties.setRequestHeaderSize("32768");

        String xml = ObjectXMLSerializer.getInstance().serialize(properties);
        String legacyXml = xml.replaceAll("<requestHeaderSize>[^<]*</requestHeaderSize>", "");
        assertFalse(legacyXml.contains("requestHeaderSize"));

        HttpReceiverProperties deserialized = ObjectXMLSerializer.getInstance().deserialize(legacyXml, HttpReceiverProperties.class);
        assertEquals(String.valueOf(HttpReceiverProperties.DEFAULT_REQUEST_HEADER_SIZE), deserialized.getRequestHeaderSize());
    }
}
