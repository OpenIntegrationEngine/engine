package com.mirth.connect.plugins.datatypes.hl7v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.mirth.connect.model.converters.XMLPrettyPrinter;

public class ER7ReaderTest {

    private static Document parse(String message, boolean handleRepetitions, boolean handleSubcomponents) throws Exception {
        ER7Reader reader = new ER7Reader(handleRepetitions, handleSubcomponents, "\r");
        StringWriter writer = new StringWriter();
        XMLPrettyPrinter printer = new XMLPrettyPrinter(writer);
        printer.setEncodeEntities(true);
        reader.setContentHandler(printer);
        reader.parse(new InputSource(new StringReader(message)));
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(writer.toString())));
    }

    private static Document parse(String message) throws Exception {
        return parse(message, false, false);
    }

    private static String text(Document doc, String xpath) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(xpath, doc);
    }

    private static int count(Document doc, String xpath) throws Exception {
        return ((NodeList) XPathFactory.newInstance().newXPath().evaluate(xpath, doc, XPathConstants.NODESET)).getLength();
    }

    @Test
    public void testHeaderAndSegments() throws Exception {
        Document doc = parse("MSH|^~\\&|APP|FAC\rPID|1||12345^^^MRN||DOE^JOHN\r");

        assertEquals("HL7Message", doc.getDocumentElement().getNodeName());
        assertEquals("|", text(doc, "/HL7Message/MSH/MSH.1"));
        assertEquals("^~\\&", text(doc, "/HL7Message/MSH/MSH.2"));
        assertEquals("APP", text(doc, "/HL7Message/MSH/MSH.3/MSH.3.1"));
        assertEquals("FAC", text(doc, "/HL7Message/MSH/MSH.4/MSH.4.1"));
        assertEquals("1", text(doc, "/HL7Message/PID/PID.1/PID.1.1"));
        assertEquals("12345", text(doc, "/HL7Message/PID/PID.3/PID.3.1"));
        assertEquals("MRN", text(doc, "/HL7Message/PID/PID.3/PID.3.4"));
        assertEquals("DOE", text(doc, "/HL7Message/PID/PID.5/PID.5.1"));
        assertEquals("JOHN", text(doc, "/HL7Message/PID/PID.5/PID.5.2"));
    }

    @Test
    public void testEmptyFields() throws Exception {
        Document doc = parse("MSH|^~\\&\rPID|||X|");

        assertEquals(1, count(doc, "/HL7Message/PID/PID.1"));
        assertEquals(0, count(doc, "/HL7Message/PID/PID.1/*"));
        assertEquals(1, count(doc, "/HL7Message/PID/PID.2"));
        assertEquals("X", text(doc, "/HL7Message/PID/PID.3/PID.3.1"));
        // A trailing field separator still produces the final, empty field
        assertEquals(1, count(doc, "/HL7Message/PID/PID.4"));
        assertEquals(0, count(doc, "/HL7Message/PID/PID.4/*"));
    }

    @Test
    public void testEmptyComponents() throws Exception {
        Document doc = parse("MSH|^~\\&\rPID|^A^");

        assertEquals("", text(doc, "/HL7Message/PID/PID.1/PID.1.1"));
        assertEquals("A", text(doc, "/HL7Message/PID/PID.1/PID.1.2"));
        assertEquals(1, count(doc, "/HL7Message/PID/PID.1/PID.1.3"));
        assertEquals("", text(doc, "/HL7Message/PID/PID.1/PID.1.3"));
    }

    @Test
    public void testRepetitionsIgnoredByDefault() throws Exception {
        Document doc = parse("MSH|^~\\&\rPID|A~B");

        assertEquals(1, count(doc, "/HL7Message/PID/PID.1"));
        assertEquals("A~B", text(doc, "/HL7Message/PID/PID.1/PID.1.1"));
    }

    @Test
    public void testHandleRepetitions() throws Exception {
        Document doc = parse("MSH|^~\\&\rPID|A~~B^C~", true, false);

        assertEquals(4, count(doc, "/HL7Message/PID/PID.1"));
        assertEquals("A", text(doc, "/HL7Message/PID/PID.1[1]/PID.1.1"));
        assertEquals(0, count(doc, "/HL7Message/PID/PID.1[2]/*"));
        assertEquals("B", text(doc, "/HL7Message/PID/PID.1[3]/PID.1.1"));
        assertEquals("C", text(doc, "/HL7Message/PID/PID.1[3]/PID.1.2"));
        assertEquals(0, count(doc, "/HL7Message/PID/PID.1[4]/*"));
    }

    @Test
    public void testSubcomponentsIgnoredByDefault() throws Exception {
        Document doc = parse("MSH|^~\\&\rPID|A&B^C");

        assertEquals("A&B", text(doc, "/HL7Message/PID/PID.1/PID.1.1"));
        assertEquals("C", text(doc, "/HL7Message/PID/PID.1/PID.1.2"));
    }

    @Test
    public void testHandleSubcomponents() throws Exception {
        Document doc = parse("MSH|^~\\&\rPID|A&B^C|X&&Y&", false, true);

        assertEquals("A", text(doc, "/HL7Message/PID/PID.1/PID.1.1/PID.1.1.1"));
        assertEquals("B", text(doc, "/HL7Message/PID/PID.1/PID.1.1/PID.1.1.2"));
        assertEquals("C", text(doc, "/HL7Message/PID/PID.1/PID.1.2"));

        // A field with subcomponents but no component separator is still split
        assertEquals("X", text(doc, "/HL7Message/PID/PID.2/PID.2.1/PID.2.1.1"));
        assertEquals(1, count(doc, "/HL7Message/PID/PID.2/PID.2.1/PID.2.1.2"));
        assertEquals("", text(doc, "/HL7Message/PID/PID.2/PID.2.1/PID.2.1.2"));
        assertEquals("Y", text(doc, "/HL7Message/PID/PID.2/PID.2.1/PID.2.1.3"));
        assertEquals(1, count(doc, "/HL7Message/PID/PID.2/PID.2.1/PID.2.1.4"));
    }

    @Test
    public void testCustomEncodingCharacters() throws Exception {
        Document doc = parse("MSH#*@!$#APP\rPID#A*B@C$D", true, true);

        assertEquals("#", text(doc, "/HL7Message/MSH/MSH.1"));
        assertEquals("*@!$", text(doc, "/HL7Message/MSH/MSH.2"));
        assertEquals("APP", text(doc, "/HL7Message/MSH/MSH.3/MSH.3.1"));
        assertEquals(2, count(doc, "/HL7Message/PID/PID.1"));
        assertEquals("A", text(doc, "/HL7Message/PID/PID.1[1]/PID.1.1"));
        assertEquals("B", text(doc, "/HL7Message/PID/PID.1[1]/PID.1.2"));
        assertEquals("C", text(doc, "/HL7Message/PID/PID.1[2]/PID.1.1/PID.1.1.1"));
        assertEquals("D", text(doc, "/HL7Message/PID/PID.1[2]/PID.1.1/PID.1.1.2"));
    }

    @Test
    public void testMissingEscapeCharacterIsRestored() throws Exception {
        // MIRTH-1544: ^~& is treated as ^~\&
        Document doc = parse("MSH|^~&|APP\rPID|A&B", false, true);

        assertEquals("^~\\&", text(doc, "/HL7Message/MSH/MSH.2"));
        assertEquals("A", text(doc, "/HL7Message/PID/PID.1/PID.1.1/PID.1.1.1"));
        assertEquals("B", text(doc, "/HL7Message/PID/PID.1/PID.1.1/PID.1.1.2"));
    }

    @Test
    public void testHeaderWithOnlyEncodingCharacters() throws Exception {
        assertEquals("^~", text(parse("MSH|^~"), "/HL7Message/MSH/MSH.2"));
        assertEquals("^~\\", text(parse("MSH|^~\\"), "/HL7Message/MSH/MSH.2"));
        assertEquals("^~\\&", text(parse("MSH|^~\\&"), "/HL7Message/MSH/MSH.2"));
    }

    @Test
    public void testBatchHeaders() throws Exception {
        Document doc = parse("FHS|^~\\&|F\rBHS|^~\\&|B\rMSH|^~\\&|M\rBTS|1\rFTS|1");

        assertEquals("^~\\&", text(doc, "/HL7Message/FHS/FHS.2"));
        assertEquals("F", text(doc, "/HL7Message/FHS/FHS.3/FHS.3.1"));
        assertEquals("^~\\&", text(doc, "/HL7Message/BHS/BHS.2"));
        assertEquals("B", text(doc, "/HL7Message/BHS/BHS.3/BHS.3.1"));
        assertEquals("M", text(doc, "/HL7Message/MSH/MSH.3/MSH.3.1"));
        assertEquals("1", text(doc, "/HL7Message/FTS/FTS.1/FTS.1.1"));
    }

    @Test
    public void testMessageWithoutHeaderUsesDefaultSeparators() throws Exception {
        Document doc = parse("PID|1|A^B~C", true, false);

        assertEquals("1", text(doc, "/HL7Message/PID/PID.1/PID.1.1"));
        assertEquals("A", text(doc, "/HL7Message/PID/PID.2[1]/PID.2.1"));
        assertEquals("B", text(doc, "/HL7Message/PID/PID.2[1]/PID.2.2"));
        assertEquals("C", text(doc, "/HL7Message/PID/PID.2[2]/PID.2.1"));
    }

    @Test
    public void testSurroundingWhitespaceIsTrimmed() throws Exception {
        Document doc = parse("\n  MSH|^~\\&|APP\r  \n");

        assertEquals("APP", text(doc, "/HL7Message/MSH/MSH.3/MSH.3.1"));
        assertEquals(1, count(doc, "/HL7Message/*"));
    }

    @Test
    public void testMessageTooShort() throws Exception {
        ER7Reader reader = new ER7Reader(false, false, "\r");
        reader.setContentHandler(new org.xml.sax.helpers.DefaultHandler());

        try {
            reader.parse(new InputSource(new StringReader(" MSH| ")));
            fail("Expected SAXException");
        } catch (SAXException e) {
            assertTrue(e.getMessage().contains("too short"));
        }
    }

    @Test
    public void testContentHandlerRequired() throws Exception {
        try {
            new ER7Reader(false, false, "\r").parse(new InputSource(new StringReader("MSH|^~\\&")));
            fail("Expected SAXException");
        } catch (SAXException e) {
            assertTrue(e.getMessage().contains("ContentHandler"));
        }
    }
}
