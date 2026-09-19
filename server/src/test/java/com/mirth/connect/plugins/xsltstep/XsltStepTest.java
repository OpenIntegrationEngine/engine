// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>
package com.mirth.connect.plugins.xsltstep;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.util.JavaScriptTestUtil;

/**
 * Covers the script {@link XsltStep} generates for a plain transformer step. The iterator form is
 * covered by FilterTransformerIterableTest#testIteratorXsltStep.
 */
public class XsltStepTest {

    /** Copies the whole source document to the result, so anything leaked shows up in the output. */
    private static final String TEXT_XSLT = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
            + "<xsl:output method=\"text\"/><xsl:template match=\"/\"><xsl:value-of select=\".\"/></xsl:template></xsl:stylesheet>";

    private static File secret;

    @BeforeClass
    public static void setup() throws Exception {
        JavaScriptTestUtil.setup();

        secret = File.createTempFile("xxe", ".txt");
        secret.deleteOnExit();
        FileUtils.write(secret, "canary", UTF_8);
    }

    /** Dropping the entity and rejecting the message are both fine; leaking the file is not. */
    @Test
    public void externalEntityInSourceIsNotResolved() throws Exception {
        String xml = "<!DOCTYPE r [ <!ENTITY xxe SYSTEM \"" + secret.toURI() + "\"> ]><r>&xxe;</r>";
        String outcome;

        try {
            outcome = transform(xml, TEXT_XSLT);
        } catch (Exception e) {
            outcome = String.valueOf(e);
        }

        assertFalse(outcome, outcome.contains("canary"));
    }

    /**
     * A DOCTYPE is refused outright, as everywhere else in the codebase, so the payload above is
     * rejected before any entity is looked at.
     */
    @Test
    public void doctypeIsRejected() throws Exception {
        try {
            transform("<!DOCTYPE r [ <!ENTITY ok \"canary\"> ]><r>&ok;</r>", TEXT_XSLT);
            fail("expected the DOCTYPE to be refused");
        } catch (Exception e) {
            assertTrue(String.valueOf(e), String.valueOf(e).contains("DOCTYPE is disallowed"));
        }
    }

    @Test
    public void benignDocumentIsTransformed() throws Exception {
        assertEquals("hello", transform("<?xml version=\"1.0\"?><r>hello</r>", TEXT_XSLT));
    }

    /** The step restricts the message, not the stylesheet. */
    @Test
    public void stylesheetMayImportFromAFileUri() throws Exception {
        File imported = File.createTempFile("imported", ".xsl");
        imported.deleteOnExit();
        FileUtils.write(imported, "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                + "<xsl:template match=\"/\">imported</xsl:template></xsl:stylesheet>", UTF_8);

        String xslt = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                + "<xsl:import href=\"" + imported.toURI() + "\"/><xsl:output method=\"text\"/></xsl:stylesheet>";
        assertEquals("imported", transform("<r>hello</r>", xslt));
    }

    @Test
    public void customFactoryIsUsed() throws Exception {
        XsltStep step = step("<r>hello</r>", TEXT_XSLT);
        step.setUseCustomFactory(true);
        step.setCustomFactory("com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
        assertEquals("hello", transform(step));
    }

    private static String transform(String sourceXml, String xslt) throws Exception {
        return transform(step(sourceXml, xslt));
    }

    private static XsltStep step(String sourceXml, String xslt) {
        XsltStep step = new XsltStep();
        step.setSourceXml("'" + StringEscapeUtils.escapeEcmaScript(sourceXml) + "'");
        step.setTemplate("'" + StringEscapeUtils.escapeEcmaScript(xslt) + "'");
        step.setResultVariable("xsltResult");
        return step;
    }

    private static String transform(XsltStep step) throws Exception {
        ConnectorMessage connectorMessage = new ConnectorMessage();
        connectorMessage.setMetaDataId(0);
        connectorMessage.setChannelMap(new HashMap<String, Object>());

        JavaScriptTestUtil.testTransformerStep(step, connectorMessage);

        return String.valueOf(connectorMessage.getChannelMap().get("xsltResult"));
    }

    /**
     * A channel's resource library is on the context classloader while the step runs, so a plain
     * JAXP lookup resolves against it. Xerces 2.12.2 is the reported case: it parses normally but
     * does not recognize accessExternalDTD, which failed every transformation in such a channel.
     */
    @Test
    public void parserFromChannelResourceLibraryIsNotUsed() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader contextClassLoader = currentThread.getContextClassLoader();

        try (URLClassLoader resourceLibrary = resourceLibraryProviding(RejectsSecurityProperties.class)) {
            currentThread.setContextClassLoader(resourceLibrary);

            // Guards the fixture: a plain lookup really would pick the channel's parser up.
            assertEquals(RejectsSecurityProperties.class, SAXParserFactory.newInstance().getClass());

            assertEquals("hello", transform("<r>hello</r>", TEXT_XSLT));
        } finally {
            currentThread.setContextClassLoader(contextClassLoader);
        }
    }

    /** Builds a classloader that advertises the factory the way a jar in a library would. */
    private static URLClassLoader resourceLibraryProviding(Class<? extends SAXParserFactory> factory) throws Exception {
        Path root = Files.createTempDirectory("resource-library");
        root.toFile().deleteOnExit();

        Path services = Files.createDirectories(root.resolve("META-INF").resolve("services"));
        FileUtils.write(services.resolve(SAXParserFactory.class.getName()).toFile(), factory.getName(), UTF_8);

        return new URLClassLoader(new URL[] { root.toUri().toURL() }, XsltStepTest.class.getClassLoader());
    }

    /** Stands in for Xerces 2.12.2: parses normally, but rejects the security property. */
    public static class RejectsSecurityProperties extends SAXParserFactory {

        private final SAXParserFactory delegate = SAXParserFactory.newInstance("com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl", null);

        @Override
        public SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
            delegate.setNamespaceAware(isNamespaceAware());
            SAXParser parser = delegate.newSAXParser();

            return new SAXParser() {
                @Override
                public void setProperty(String name, Object value) throws SAXNotRecognizedException {
                    throw new SAXNotRecognizedException("Property '" + name + "' is not recognized.");
                }

                @Override
                public Object getProperty(String name) throws SAXNotRecognizedException {
                    throw new SAXNotRecognizedException("Property '" + name + "' is not recognized.");
                }

                @Override
                @SuppressWarnings("deprecation")
                public org.xml.sax.Parser getParser() throws SAXException {
                    return parser.getParser();
                }

                @Override
                public XMLReader getXMLReader() throws SAXException {
                    return parser.getXMLReader();
                }

                @Override
                public boolean isNamespaceAware() {
                    return parser.isNamespaceAware();
                }

                @Override
                public boolean isValidating() {
                    return parser.isValidating();
                }
            };
        }

        @Override
        public void setFeature(String name, boolean value) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
            delegate.setFeature(name, value);
        }

        @Override
        public boolean getFeature(String name) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
            return delegate.getFeature(name);
        }
    }
}
