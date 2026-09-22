package com.mirth.connect.plugins.datatypes.edi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXParseException;

import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.model.converters.TestUtil;
import com.mirth.connect.model.datatype.SerializerProperties;

public class EDISerializerTest {
	
	private EDISerializer serializer;
	
	@Before
	public void setup() {
		serializer = new EDISerializer(new SerializerProperties(new EDISerializationProperties(), null, null));
	}

	@Test
	public void testFromXML() {
		try {
			serializer.fromXML(validXml);
		} catch (MessageSerializerException e) {
			fail("Failed to parse valid XML. Exception: " + e.getMessage());
		}
	}
	
	@Test
	public void testFromXMLWithExternalDtd() {
		boolean exceptionCaught = false;
		try {
			serializer.fromXML(xmlWithExternalDtd);
		} catch (MessageSerializerException e) {
			assertEquals(SAXParseException.class, e.getCause().getClass());
			exceptionCaught = true;
		}
		assertTrue(exceptionCaught);
	}
	
	@Test
	public void testToXml() throws Exception {
		assertXmlEquals(read("test-edi-output.xml"), serializer.toXML(read("test-edi-input.txt")));
	}

	@Test
	public void testFromXml() throws Exception {
		assertEquals(read("test-edi-input.txt"), serializer.fromXML(read("test-edi-output.xml")));
	}

	/** Issue 1597: the serializer fills in elements the XML leaves out. */
	@Test
	public void testIssue1597fromXML() throws Exception {
		assertEquals(read("test-1597-output.txt"), serializer.fromXML(read("test-1597-input-missing-elements.xml")));
	}

	@Test
	public void testIssue1597toXML() throws Exception {
		assertXmlEquals(read("test-1597-input.xml"), serializer.toXML(read("test-1597-output.txt")));
	}

	private static String read(String name) throws Exception {
		return FileUtils.readFileToString(new File("tests/" + name), "UTF-8");
	}

	/** toXML returns one unindented line with an XML declaration; the fixtures are readable. */
	private static void assertXmlEquals(String expected, String actual) throws Exception {
		assertEquals(TestUtil.normalizeLineEndings(expected), TestUtil.normalizeLineEndings(TestUtil.prettyPrintXml(actual)));
	}

	private static String validXml =  "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\r\n"
			+ "<foo><bar>bar</bar></foo>";
	
	private static String xmlWithExternalDtd =  "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\r\n"
			+ "<!DOCTYPE foo [\r\n"
			+ "<!ELEMENT foo ANY >\r\n"
			+ "<!ENTITY xxe SYSTEM \"file:///dev/random\" >]><foo>&xxe;</foo>";	
}
