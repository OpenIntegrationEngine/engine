package com.mirth.connect.plugins.datatypes.hl7v2;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.mirth.connect.model.datatype.SerializerProperties;

/**
 * A field written as CDATA must keep its contents.
 *
 * <p>
 * HAPI 2.6.0's {@code XMLUtils.parseDocument} builds its factory without coalescing, so the parser
 * hands back CDATA_SECTION nodes, and {@code XMLParser.parsePrimitive} has only ever read text
 * nodes. The content is then dropped with no error. HAPI 2.3 did not produce those nodes because it
 * parsed through an {@code LSParser}, which is why this only appears after the upgrade.
 *
 * <p>
 * Both shapes are covered, a field that is entirely CDATA and one that mixes CDATA with ordinary
 * text, on each strict path that reaches the parser: {@code fromXML} with validation off and on,
 * and {@code toXML}, which only parses XML input when validation is also on.
 */
public class Hl7v2CdataTest {

    private static final String PURE_CDATA = message("<![CDATA[clinical]]>");
    private static final String MIXED_CDATA = message("before<![CDATA[clinical]]>after");

    @Test
    public void fromXmlKeepsPureCdata() throws Exception {
        assertKeeps("clinical", serializer(false).fromXML(PURE_CDATA));
    }

    @Test
    public void fromXmlKeepsPureCdataWhenValidating() throws Exception {
        assertKeeps("clinical", serializer(true).fromXML(PURE_CDATA));
    }

    @Test
    public void fromXmlKeepsMixedCdata() throws Exception {
        assertKeeps("beforeclinicalafter", serializer(false).fromXML(MIXED_CDATA));
    }

    @Test
    public void fromXmlKeepsMixedCdataWhenValidating() throws Exception {
        assertKeeps("beforeclinicalafter", serializer(true).fromXML(MIXED_CDATA));
    }

    /** toXML only parses XML input when strict validation is on; otherwise it passes it through. */
    @Test
    public void toXmlKeepsPureCdataWhenValidating() throws Exception {
        assertKeeps("clinical", serializer(true).toXML(PURE_CDATA));
    }

    @Test
    public void toXmlKeepsMixedCdataWhenValidating() throws Exception {
        assertKeeps("beforeclinicalafter", serializer(true).toXML(MIXED_CDATA));
    }

    private static void assertKeeps(String expected, String actual) {
        assertTrue("expected MSH-3 to still hold '" + expected + "', got: " + actual,
                actual.contains(expected));
    }

    /** MSH-3 carries the payload, so a dropped field is visible in the sending application. */
    private static String message(String sendingApplication) {
        return "<?xml version=\"1.0\"?>\n"
                + "<ADT_A01 xmlns=\"urn:hl7-org:v2xml\"><MSH>"
                + "<MSH.1>|</MSH.1><MSH.2>^~\\&amp;</MSH.2>"
                + "<MSH.3><HD.1>" + sendingApplication + "</HD.1></MSH.3>"
                + "<MSH.9><MSG.1>ADT</MSG.1><MSG.2>A01</MSG.2><MSG.3>ADT_A01</MSG.3></MSH.9>"
                + "<MSH.10>MSGX</MSH.10><MSH.12><VID.1>2.4</VID.1></MSH.12>"
                + "</MSH></ADT_A01>\n";
    }

    private static ER7Serializer serializer(boolean strictValidation) {
        HL7v2SerializationProperties serialization = new HL7v2SerializationProperties();
        serialization.setUseStrictParser(true);
        serialization.setUseStrictValidation(strictValidation);

        HL7v2DeserializationProperties deserialization = new HL7v2DeserializationProperties();
        deserialization.setUseStrictParser(true);
        deserialization.setUseStrictValidation(strictValidation);

        return new ER7Serializer(new SerializerProperties(serialization, deserialization, null));
    }
}
