package com.mirth.connect.plugins.datatypes.hl7v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXParseException;

import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.model.datatype.SerializerProperties;

public class ER7SerializerTest {
	private static ER7Serializer serializer;
	private static ER7Serializer strictSerializer;
	private static ER7Serializer strictSerializerAllowXml;

	@BeforeClass
	public static void setupClass() throws Exception {
		SerializerProperties serializerProperties = new SerializerProperties(new HL7v2SerializationProperties(), new HL7v2DeserializationProperties(), null);
		serializer = new ER7Serializer(serializerProperties);

		HL7v2SerializationProperties strictProperties = new HL7v2SerializationProperties();
		strictProperties.setUseStrictParser(true);
		strictSerializer = new ER7Serializer(new SerializerProperties(strictProperties, new HL7v2DeserializationProperties(), null));

		HL7v2SerializationProperties strictPropertiesAllowXml = new HL7v2SerializationProperties();
		strictPropertiesAllowXml.setUseStrictParser(true);
		strictPropertiesAllowXml.setAllowXml(true);
		strictSerializerAllowXml = new ER7Serializer(new SerializerProperties(strictPropertiesAllowXml, new HL7v2DeserializationProperties(), null));
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
	public void testToXmlWithStrictParserRejectsXmlInputByDefault() throws Exception {
		String xmlDisguisedAsHl7 = "<foo><bar>notreallyhl7</bar></foo>";

		boolean exceptionThrown = false;
		try {
			strictSerializer.toXML(xmlDisguisedAsHl7);
		} catch (MessageSerializerException e) {
			exceptionThrown = true;
		}

		assertTrue(exceptionThrown);
	}

	@Test
	public void testToXmlWithStrictParserAllowsXmlInputWhenOptedIn() throws Exception {
		String xmlDisguisedAsHl7 = "<foo><bar>notreallyhl7</bar></foo>";

		String result = strictSerializerAllowXml.toXML(xmlDisguisedAsHl7);

		assertEquals(xmlDisguisedAsHl7, result);
	}
}
