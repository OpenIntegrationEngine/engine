package com.mirth.connect.plugins.datatypes.hl7v2;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.model.datatype.SerializerProperties;

/**
 * A message type with no generated structure class parses into a HAPI {@code GenericMessage}.
 * Through HAPI 2.3 the XML parser refused to encode one; 2.4 removed that refusal, so the same
 * message now converts successfully and reaches the transformer as {@code <GenericMessageV2x>},
 * which no existing filter or transformer step is written against.
 *
 * <p>
 * The engine keeps rejecting it by default so existing channels are unaffected, and exposes the
 * newer behaviour behind "Allow Unrecognized Message Types" for anyone who wants it.
 */
public class Hl7v2UnknownMessageTypeTest {

    /** MSH-9 names a type no structure jar provides, so HAPI falls back to GenericMessage. */
    private static final String UNKNOWN_TYPE =
            "MSH|^~\\&|SENDAPP|SENDFAC|RECVAPP|RECVFAC|20260101120000||XYZ^Q01^XYZ_Q01|MSG00001|P|2.4\r";

    /** The same message shape with a type HAPI does have a structure for. */
    private static final String KNOWN_TYPE =
            "MSH|^~\\&|SENDAPP|SENDFAC|RECVAPP|RECVFAC|20260101120000||ADT^A01^ADT_A01|MSG00002|P|2.4\r";

    @Test
    public void rejectsUnrecognizedMessageTypeByDefault() {
        try {
            serializer(false).toXML(UNKNOWN_TYPE);
            fail("An unrecognized message type should be rejected unless the channel opts in");
        } catch (MessageSerializerException e) {
            // expected
        }
    }

    @Test
    public void encodesUnrecognizedMessageTypeWhenAllowed() throws Exception {
        String xml = serializer(true).toXML(UNKNOWN_TYPE);

        assertTrue("expected a generic message document, got: " + xml, xml.contains("GenericMessage"));
        assertTrue("the original message type should survive the conversion: " + xml, xml.contains("XYZ"));
    }

    /** The option must not change anything for a type the parser does recognize. */
    @Test
    public void recognizedMessageTypeIsUnaffectedEitherWay() throws Exception {
        String rejecting = serializer(false).toXML(KNOWN_TYPE);
        String allowing = serializer(true).toXML(KNOWN_TYPE);

        assertTrue("expected an ADT_A01 document, got: " + rejecting, rejecting.contains("ADT_A01"));
        org.junit.Assert.assertEquals(rejecting, allowing);
    }

    private static ER7Serializer serializer(boolean allowUnknownMessageTypes) {
        HL7v2SerializationProperties serialization = new HL7v2SerializationProperties();
        serialization.setUseStrictParser(true);
        serialization.setAllowUnknownMessageTypes(allowUnknownMessageTypes);

        return new ER7Serializer(new SerializerProperties(serialization, new HL7v2DeserializationProperties(), null));
    }
}
