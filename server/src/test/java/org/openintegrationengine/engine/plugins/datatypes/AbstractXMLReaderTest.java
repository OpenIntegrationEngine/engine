// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Tony Germano

package org.openintegrationengine.engine.plugins.datatypes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

public class AbstractXMLReaderTest {

    private AbstractXMLReader reader;

    @Before
    public void setUp() {
        reader = new TestXMLReader();
    }

    // ---------------------------------------------------------
    // Parsing Logic Tests
    // ---------------------------------------------------------

    @Test
    public void testParseEmitsSaxEvents() throws Exception {
        // Mock the ContentHandler to verify events
        ContentHandler handler = mock(ContentHandler.class);
        reader.setContentHandler(handler);

        // Execute parse
        reader.parse(new InputSource());

        // Verify the exact sequence of SAX events was fired
        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).startDocument();
        inOrder.verify(handler).startElement(eq(""), eq("test"), eq(""), any(Attributes.class));
        inOrder.verify(handler).characters(eq("value".toCharArray()), eq(0), eq(5));
        inOrder.verify(handler).endElement(eq(""), eq("test"), eq(""));
        inOrder.verify(handler).endDocument();
    }

    @Test
    public void testParseStringDelegatesToInputSource() throws IOException, SAXException {
        // Spy on the reader to verify method calls
        AbstractXMLReader spyReader = spy(new TestXMLReader());
        String testUri = "file:///test.xml";

        // Set a dummy handler so ensuringHandlerSet doesn't fail
        spyReader.setContentHandler(mock(ContentHandler.class));

        spyReader.parse(testUri);

        // Verify that parse(String) called parse(InputSource)
        verify(spyReader).parse(any(InputSource.class));
    }

    // ---------------------------------------------------------
    // Handler Getter/Setter Tests
    // ---------------------------------------------------------

    @Test
    public void testContentHandlerAccessors() {
        assertNull("Should be null initially", reader.getContentHandler());

        ContentHandler mockHandler = mock(ContentHandler.class);
        reader.setContentHandler(mockHandler);

        assertSame("Should return the set handler", mockHandler, reader.getContentHandler());
    }

    @Test
    public void testErrorHandlerAccessors() {
        assertNull("Should be null initially", reader.getErrorHandler());

        ErrorHandler mockHandler = mock(ErrorHandler.class);
        reader.setErrorHandler(mockHandler);

        assertSame("Should return the set handler", mockHandler, reader.getErrorHandler());
    }

    @Test
    public void testDTDHandlerAccessors() {
        assertNull("Should be null initially", reader.getDTDHandler());

        DTDHandler mockHandler = mock(DTDHandler.class);
        reader.setDTDHandler(mockHandler);

        assertSame("Should return the set handler", mockHandler, reader.getDTDHandler());
    }

    @Test
    public void testEntityResolverAccessors() {
        assertNull("Should be null initially", reader.getEntityResolver());

        EntityResolver mockResolver = mock(EntityResolver.class);
        reader.setEntityResolver(mockResolver);

        assertSame("Should return the set resolver", mockResolver, reader.getEntityResolver());
    }

    // ---------------------------------------------------------
    // Helper Method Tests
    // ---------------------------------------------------------

    @Test
    public void testEnsureHandlerSetSuccess() throws SAXException {
        // Setup: Set a handler
        reader.setContentHandler(mock(ContentHandler.class));
        
        // Execute: Should not throw exception
        reader.ensureHandlerSet();
    }

    @Test(expected = SAXException.class)
    public void testEnsureHandlerSetFailure() throws SAXException {
        // Setup: Ensure handler is null
        reader.setContentHandler(null);
        
        // Execute: Should throw SAXException
        reader.ensureHandlerSet();
    }

    // ---------------------------------------------------------
    // Feature Flag Tests
    // ---------------------------------------------------------

    @Test
    public void testGetFeatureNamespaces() throws Exception {
        assertTrue("Namespaces should always be true", 
            reader.getFeature("http://xml.org/sax/features/namespaces"));
    }

    @Test
    public void testGetFeatureNamespacePrefixes() throws Exception {
        assertFalse("Namespace prefixes should always be false", 
            reader.getFeature("http://xml.org/sax/features/namespace-prefixes"));
    }

    @Test(expected = SAXNotRecognizedException.class)
    public void testGetFeatureUnknown() throws Exception {
        reader.getFeature("http://xml.org/sax/features/unknown-feature");
    }

    @Test
    public void testSetFeatureNamespacesTrue() throws Exception {
        // Should succeed (no-op)
        reader.setFeature("http://xml.org/sax/features/namespaces", true);
    }

    @Test(expected = SAXNotSupportedException.class)
    public void testSetFeatureNamespacesFalse() throws Exception {
        // Should fail - cannot disable namespaces
        reader.setFeature("http://xml.org/sax/features/namespaces", false);
    }

    @Test
    public void testSetFeatureNamespacePrefixesFalse() throws Exception {
        // Should succeed (no-op)
        reader.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
    }

    @Test(expected = SAXNotSupportedException.class)
    public void testSetFeatureNamespacePrefixesTrue() throws Exception {
        // Should fail - cannot enable namespace prefixes
        reader.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
    }

    @Test(expected = SAXNotRecognizedException.class)
    public void testSetFeatureUnknown() throws Exception {
        reader.setFeature("http://xml.org/sax/features/unknown-feature", true);
    }

    // ---------------------------------------------------------
    // Property Tests
    // ---------------------------------------------------------

    @Test(expected = SAXNotRecognizedException.class)
    public void testGetPropertyUnknown() throws Exception {
        reader.getProperty("http://xml.org/sax/properties/lexical-handler");
    }

    @Test(expected = SAXNotRecognizedException.class)
    public void testSetPropertyUnknown() throws Exception {
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", new Object());
    }

    // ---------------------------------------------------------
    // Concrete Implementation for Testing
    // ---------------------------------------------------------

    /**
     * A concrete implementation that simulates parsing a simple XML document:
     * <test>value</test>
     */
    private static class TestXMLReader extends AbstractXMLReader {
        @Override
        public void parse(InputSource input) throws IOException, SAXException {
            // Verify handler is present
            ensureHandlerSet();

            // Simulate parsing <test>value</test>
            contentHandler.startDocument();

            contentHandler.startElement("", "test", "", getEmptyAttributes());

            String text = "value";
            contentHandler.characters(text.toCharArray(), 0, text.length());

            contentHandler.endElement("", "test", "");
            contentHandler.endDocument();
        }
    }
}
