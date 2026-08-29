package com.mirth.connect.plugins.datatypes.hl7v2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXParseException;

import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.model.datatype.SerializerProperties;

public class ER7SerializerTest {
	private static ER7Serializer serializer;
	// Strict parser with strict validation: XML input is parsed by HAPI (the XXE sink).
	private static ER7Serializer strictValidatingSerializer;

	@BeforeClass
	public static void setupClass() throws Exception {
		SerializerProperties serializerProperties = new SerializerProperties(new HL7v2SerializationProperties(), new HL7v2DeserializationProperties(), null);
		serializer = new ER7Serializer(serializerProperties);

		HL7v2SerializationProperties strictValidatingProperties = new HL7v2SerializationProperties();
		strictValidatingProperties.setUseStrictParser(true);
		strictValidatingProperties.setUseStrictValidation(true);
		strictValidatingSerializer = new ER7Serializer(new SerializerProperties(strictValidatingProperties, new HL7v2DeserializationProperties(), null));
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
	public void testToXmlStrictValidatingRejectsExternalDTD() throws Exception {
		// A DOCTYPE-bearing message on the strict-parser toXML path is the unauthenticated MLLP XXE
		// vector (HAPI 2.3 resolved external entities). It must be rejected rather than have its
		// external entity resolved. Note: this asserts the intended behavior; the discriminating
		// before/after proof that the override (not incidental parser behavior) closes the XXE is the
		// live MLLP reproduction documented in the PR.
		String xml = FileUtils.readFileToString(new File("tests/test-xxe-hl7-strict-mllp.xml"), "UTF-8");

		boolean exceptionThrown = false;
		try {
			strictValidatingSerializer.toXML(xml);
		} catch (MessageSerializerException e) {
			exceptionThrown = true;

			// The rejection must be the DOCTYPE being disallowed, not some incidental parse failure.
			Throwable rootCause = ExceptionUtils.getRootCause(e);
			assertTrue(rootCause instanceof SAXParseException);
			assertTrue(rootCause.getMessage().contains("DOCTYPE"));
		}

		assertTrue(exceptionThrown);
	}

	@Test
	public void testToXmlStrictValidatingAllowsValidXml() throws Exception {
		// The same message without a DOCTYPE is legitimate HL7 v2.x XML and must still round-trip, so
		// the hardening does not break the strict parser's XML support.
		String xml = FileUtils.readFileToString(new File("tests/test-xxe-hl7-strict-mllp-valid.xml"), "UTF-8");

		boolean exceptionThrown = false;
		try {
			strictValidatingSerializer.toXML(xml);
		} catch (MessageSerializerException e) {
			exceptionThrown = true;
		}

		assertFalse(exceptionThrown);
	}
}
