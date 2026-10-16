package com.mirth.connect.plugins.datatypes.edi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXParseException;

import com.mirth.connect.donkey.model.message.MessageSerializerException;
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
	public void testFromXMLKeepsIsaFixedWidth() throws Exception {
		String xml = serializer.toXML(validX12);
		assertEquals(validX12, serializer.fromXML(xml));
	}

	@Test
	public void testFromXMLPadsBlankIsaFields() throws Exception {
		// A field of spaces can come back empty, e.g. after XmlUtil.prettyPrint
		String xml = serializer.toXML(validX12).replace("<ISA.02.1>          </ISA.02.1>", "<ISA.02.1/>");
		assertEquals(validX12, serializer.fromXML(xml));
	}

	@Test
	public void testFromXMLPadsMissingIsaElement() throws Exception {
		// The whole <ISA.02> element is gone, not just empty
		String xml = serializer.toXML(validX12).replace("<ISA.02><ISA.02.1>          </ISA.02.1></ISA.02>", "");
		assertEquals(validX12, serializer.fromXML(xml));
	}

	@Test
	public void testFromXMLPadsShortIsaValues() throws Exception {
		String xml = serializer.toXML(validX12)
				.replace("<ISA.06.1>SUBMITTERID    </ISA.06.1>", "<ISA.06.1>SUBMITTERID</ISA.06.1>")
				.replace("<ISA.13.1>000000001</ISA.13.1>", "<ISA.13.1>1</ISA.13.1>");
		assertEquals(validX12, serializer.fromXML(xml));
	}

	@Test
	public void testFromXMLPadsIsaValueSplitAcrossCharacters() throws Exception {
		// SAX hands "S&amp;P" over in several characters() calls
		String xml = serializer.toXML(validX12).replace("<ISA.06.1>SUBMITTERID    </ISA.06.1>", "<ISA.06.1>S&amp;P</ISA.06.1>");
		assertEquals(validX12.replace("SUBMITTERID    ", "S&P            "), serializer.fromXML(xml));
	}

	@Test
	public void testFromXMLPadsEveryIsa() throws Exception {
		String twoInterchanges = validX12 + validX12;
		String xml = serializer.toXML(twoInterchanges).replace("<ISA.02.1>          </ISA.02.1>", "<ISA.02.1/>");
		assertEquals(twoInterchanges, serializer.fromXML(xml));
	}

	private static String validX12 = "ISA*00*          *00*          *ZZ*SUBMITTERID    *ZZ*PAYERID        *260330*1430*^*00501*000000001*0*T*:~"
			+ "GS*HC*SUBMITTERID*PAYERID*20260330*1430*1*X*005010X222A1~";

	private static String validXml =  "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\r\n"
			+ "<foo><bar>bar</bar></foo>";
	
	private static String xmlWithExternalDtd =  "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\r\n"
			+ "<!DOCTYPE foo [\r\n"
			+ "<!ELEMENT foo ANY >\r\n"
			+ "<!ENTITY xxe SYSTEM \"file:///dev/random\" >]><foo>&xxe;</foo>";	
}
