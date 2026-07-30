package com.mirth.connect.plugins.datatypes.hl7v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.model.datatype.SerializerProperties;

public class ER7SerializerTest {
	private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
	private static final String MSH_ER7 = "MSH|^~\\&|A\r";
	private static final String MSH_XML = "<MSH><MSH.1>|</MSH.1><MSH.2>^~\\&amp;</MSH.2><MSH.3><MSH.3.1>A</MSH.3.1></MSH.3></MSH>";

	private static ER7Serializer serializer;

	@BeforeClass
	public static void setupClass() throws Exception {
		SerializerProperties serializerProperties = new SerializerProperties(new HL7v2SerializationProperties(), new HL7v2DeserializationProperties(), null);
		serializer = new ER7Serializer(serializerProperties);
	}
	
	@Test
	public void testFromXMLWithExternalDTD() throws Exception {
		String xml = FileUtils.readFileToString(new File("tests/test-xxe-hl7-example.xml"), "UTF-8");
		
		boolean exceptionThrown = false;
		try {
			serializer.fromXML(xml);
		} catch (MessageSerializerException e) {
			exceptionThrown = true;
			
			// See https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j
			assertTrue(e.getCause() instanceof SAXParseException);
		}
		
		assertTrue(exceptionThrown);
	}

	@Test
	public void testValidFromXMLWithExternalDTD() throws Exception {
		String xml = FileUtils.readFileToString(new File("tests/test-xxe-hl7-example-valid.xml"), "UTF-8");
		
		boolean exceptionThrown = false;
		try {
			serializer.fromXML(xml);
		} catch (MessageSerializerException e) {
			exceptionThrown = true;
			
		}
		
		assertFalse(exceptionThrown);
	}

	@Test
	public void testToXMLWithCustomEncodingCharacters() throws Exception {
		String er7 = "MSH#@%\\$#App1#Fac1\rPID#1#Smith@John";

		assertEquals(XML_DECL + "<HL7Message><MSH><MSH.1>#</MSH.1><MSH.2>@%\\$</MSH.2>"
				+ "<MSH.3><MSH.3.1>App1</MSH.3.1></MSH.3>"
				+ "<MSH.4><MSH.4.1>Fac1</MSH.4.1></MSH.4></MSH>"
				+ "<PID><PID.1><PID.1.1>1</PID.1.1></PID.1>"
				+ "<PID.2><PID.2.1>Smith</PID.2.1><PID.2.2>John</PID.2.2></PID.2></PID></HL7Message>",
				serializer.toXML(er7));
	}

	@Test
	public void testToXMLWithHeaderOnlyAndNoTrailingFieldSeparator() throws Exception {
		// Covers ER7Reader's nextDelimiter == -1 branch: MSH-2 runs to end of message.
		assertEquals(XML_DECL + "<HL7Message><MSH><MSH.1>|</MSH.1><MSH.2>^~\\&amp;</MSH.2></MSH></HL7Message>",
				serializer.toXML("MSH|^~\\&"));
	}

	@Test
	public void testToXMLAppliesMirth1544FixupToNonHeaderFirstSegment() throws Exception {
		/*
		 * Characterization of ER7Reader's MIRTH-1544 fixup. The "^~&|" check is a positional
		 * substring test that does not require a header segment, so it also fires on a Z-segment,
		 * installing '&' as the subcomponent separator. Recorded as current behavior, not endorsed.
		 */
		assertEquals(XML_DECL + "<HL7Message><ZZZ>"
				+ "<ZZZ.1><ZZZ.1.1></ZZZ.1.1><ZZZ.1.2></ZZZ.1.2></ZZZ.1>"
				+ "<ZZZ.1><ZZZ.1.1><ZZZ.1.1.1></ZZZ.1.1.1><ZZZ.1.1.2></ZZZ.1.1.2></ZZZ.1.1></ZZZ.1>"
				+ "<ZZZ.2><ZZZ.2.1><ZZZ.2.1.1>a</ZZZ.2.1.1><ZZZ.2.1.2>b</ZZZ.2.1.2></ZZZ.2.1></ZZZ.2>"
				+ "</ZZZ></HL7Message>",
				serializer.toXML("ZZZ|^~&|a&b"));
	}

	@Test
	public void testToXMLRejectsMessageShorterThanSixCharacters() throws Exception {
		try {
			serializer.toXML("MSH");
			fail("expected MessageSerializerException");
		} catch (MessageSerializerException e) {
			assertTrue(e.getCause() instanceof SAXException);
			assertEquals("Unable to parse message. It is NULL or too short. MSH", e.getCause().getMessage());
		}
	}

	@Test
	public void testToXMLWithConsecutiveRepetitionSeparators() throws Exception {
		assertEquals(XML_DECL + "<HL7Message>" + MSH_XML + "<PID>"
				+ "<PID.1><PID.1.1>a</PID.1.1></PID.1>"
				+ "<PID.1></PID.1>"
				+ "<PID.1><PID.1.1>b</PID.1.1></PID.1>"
				+ "</PID></HL7Message>",
				serializer.toXML(MSH_ER7 + "PID|a~~b"));
	}

	@Test
	public void testToXMLWithTrailingRepetitionSeparator() throws Exception {
		assertEquals(XML_DECL + "<HL7Message>" + MSH_XML + "<PID>"
				+ "<PID.1><PID.1.1>a</PID.1.1></PID.1>"
				+ "<PID.1></PID.1>"
				+ "</PID></HL7Message>",
				serializer.toXML(MSH_ER7 + "PID|a~"));
	}

	@Test
	public void testToXMLWithTrailingEmptyFields() throws Exception {
		assertEquals(XML_DECL + "<HL7Message>" + MSH_XML + "<PID>"
				+ "<PID.1><PID.1.1>a</PID.1.1></PID.1>"
				+ "<PID.2></PID.2>"
				+ "<PID.3></PID.3>"
				+ "</PID></HL7Message>",
				serializer.toXML(MSH_ER7 + "PID|a||"));
	}

	@Test
	public void testToXMLSkipsEmptySegments() throws Exception {
		// StringUtils.split drops empty tokens, so a blank line between segments simply vanishes.
		assertEquals(XML_DECL + "<HL7Message>" + MSH_XML
				+ "<PID><PID.1><PID.1.1>1</PID.1.1></PID.1></PID></HL7Message>",
				serializer.toXML("MSH|^~\\&|A\r\rPID|1"));
	}
}
