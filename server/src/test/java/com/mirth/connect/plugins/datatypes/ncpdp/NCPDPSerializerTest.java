// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2017 Mirth Corporation
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan <mitch@gaffigan.net>
package com.mirth.connect.plugins.datatypes.ncpdp;

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

public class NCPDPSerializerTest {

	private NCPDPSerializer serializer;
	
	@Before
	public void setup() {
		serializer = new NCPDPSerializer(new SerializerProperties(new NCPDPSerializationProperties(), new NCPDPDeserializationProperties(), null));
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
	public void test51RequestToXml() throws Exception {
		String input = read("test-ncpdp-51-request-input.txt");
		String output = read("test-ncpdp-51-request-output.xml");
		assertEquals(output, TestUtil.prettyPrintXml(serializer.toXML(input)));
	}

	@Test
	public void test51RequestFromXml() throws Exception {
		String input = read("test-ncpdp-51-request-output.xml");
		String output = read("test-ncpdp-51-request-input.txt");
		assertEquals(output, serializer.fromXML(input));
	}

	@Test
	public void test51ResponseToXml() throws Exception {
		String input = read("test-ncpdp-51-response-input.txt");
		String output = read("test-ncpdp-51-response-output.xml");
		assertEquals(output, TestUtil.prettyPrintXml(serializer.toXML(input)));
	}

	@Test
	public void test51ResponseFromXml() throws Exception {
		String input = read("test-ncpdp-51-response-output.xml");
		String output = read("test-ncpdp-51-response-input.txt");
		assertEquals(output, serializer.fromXML(input));
	}

	@Test
	public void testD0RequestToXml() throws Exception {
		String input = read("test-ncpdp-d0-request-input.txt");
		String output = read("test-ncpdp-d0-request-output.xml");
		assertEquals(output, TestUtil.prettyPrintXml(serializer.toXML(input)));
	}

	@Test
	public void testD0RequestFromXml() throws Exception {
		String input = read("test-ncpdp-d0-request-output.xml");
		String output = read("test-ncpdp-d0-request-input.txt");
		assertEquals(output, serializer.fromXML(input));
	}

	@Test
	public void testD0ResponseToXml() throws Exception {
		String input = read("test-ncpdp-d0-response-input.txt");
		String output = read("test-ncpdp-d0-response-output.xml");
		assertEquals(output, TestUtil.prettyPrintXml(serializer.toXML(input)));
	}

	@Test
	public void testD0ResponseFromXml() throws Exception {
		String input = read("test-ncpdp-d0-response-output.xml");
		String output = read("test-ncpdp-d0-response-input.txt");
		assertEquals(output, serializer.fromXML(input));
	}
	
	@Test
	public void testvalidateTransformHeaderWithfieldValue() throws Exception {
		String input = "<BinNumber>6100</BinNumber>";
		String expectedOutput = "<BinNumber>6100  </BinNumber>";
		String actualOutput = serializer.validateTransformHeader(input);
		assertEquals(actualOutput, expectedOutput);
	}
	
	@Test
	public void testvalidateTransformHeaderWithoutFieldValue() throws Exception {
		String input = "<BinNumber></BinNumber>";
		String expectedOutput = "<BinNumber>      </BinNumber>";
		String actualOutput = serializer.validateTransformHeader(input);
		assertEquals(actualOutput, expectedOutput);  
	}
	
	@Test
	public void testvalidateTransformHeaderWithOneEndTag() throws Exception {
		String input = "<BinNumber/>";
		String expectedOutput = "<BinNumber>      </BinNumber>";
		String actualOutput = serializer.validateTransformHeader(input);
		assertEquals(actualOutput, expectedOutput);
	}
	private static String read(String name) throws Exception {
		return FileUtils.readFileToString(new File("tests/" + name));
	}

	private static String validXml =  "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\r\n"
			+ "<foo><bar>bar</bar></foo>";
	
	private static String xmlWithExternalDtd =  "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\r\n"
			+ "<!DOCTYPE foo [\r\n"
			+ "<!ELEMENT foo ANY >\r\n"
			+ "<!ENTITY xxe SYSTEM \"file:///dev/random\" >]><foo>&xxe;</foo>";	
}
