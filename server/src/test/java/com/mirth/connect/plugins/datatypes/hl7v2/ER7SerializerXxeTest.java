package com.mirth.connect.plugins.datatypes.hl7v2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.model.datatype.SerializerProperties;

/**
 * XXE coverage for {@link ER7Serializer#fromXML(String)}. The lenient handler and the strict (HAPI)
 * parser are independent code paths; ci/tests/205-hl7-xxe covers the strict one end to end.
 *
 * Every payload declares a dummy entity holding " xmlns=". fromXML injects a namespace into the
 * first '>'-delimited chunk of the message when that chunk does not already contain one, and with a
 * DOCTYPE present the chunk is an entity declaration rather than the root element. The injection
 * then corrupts the declaration into malformed XML and the parser rejects the message for being
 * malformed instead of for its external entity, which makes an XXE test pass for the wrong reason.
 * The dummy entity satisfies the check so the payload reaches the parser intact.
 */
public class ER7SerializerXxeTest {

    private static File secret;

    @BeforeClass
    public static void writeSecret() throws Exception {
        secret = File.createTempFile("xxe", ".txt");
        secret.deleteOnExit();
        FileUtils.write(secret, "canary", UTF_8);
    }

    @Test
    public void lenientParserDoesNotResolveExternalEntity() {
        assertExternalEntityNotResolved(false, false);
    }

    @Test
    public void strictParserDoesNotResolveExternalEntity() {
        assertExternalEntityNotResolved(true, false);
    }

    @Test
    public void strictValidatingParserDoesNotResolveExternalEntity() {
        assertExternalEntityNotResolved(true, true);
    }

    /** Guards the payload shape, so the tests above exercise a message the parser really reads. */
    @Test
    public void strictParserAcceptsDoctypeWithoutExternalEntity() throws Exception {
        assertTrue(serializer(true, false).fromXML(message("", "asdf")).contains("asdf"));
    }

    /** Dropping the entity and rejecting the message are both fine; leaking the file is not. */
    private static void assertExternalEntityNotResolved(boolean strictParser, boolean strictValidation) {
        String xml = message("<!ENTITY xxe SYSTEM \"" + secret.toURI() + "\">", "&xxe;");
        String outcome;

        try {
            outcome = serializer(strictParser, strictValidation).fromXML(xml);
        } catch (MessageSerializerException e) {
            outcome = String.valueOf(e);
        }

        assertFalse(outcome, outcome.contains("canary"));
    }

    private static ER7Serializer serializer(boolean strictParser, boolean strictValidation) {
        HL7v2DeserializationProperties properties = new HL7v2DeserializationProperties();
        properties.setUseStrictParser(strictParser);
        properties.setUseStrictValidation(strictValidation);
        return new ER7Serializer(new SerializerProperties(new HL7v2SerializationProperties(), properties, null));
    }

    private static String message(String entityDeclaration, String sendingApplication) {
        return "<?xml version=\"1.0\"?>\n<!DOCTYPE ACK [ <!ENTITY ns \" xmlns=\" > " + entityDeclaration + " ]>\n"
                + "<ACK xmlns=\"urn:hl7-org:v2xml\"><MSH><MSH.1>|</MSH.1><MSH.2>^~\\&amp;</MSH.2>"
                + "<MSH.3><HD.1>" + sendingApplication + "</HD.1></MSH.3><MSH.9><MSG.1>ACK</MSG.1></MSH.9>"
                + "<MSH.10>1</MSH.10><MSH.12><VID.1>2.4</VID.1></MSH.12></MSH>"
                + "<MSA><MSA.1>AA</MSA.1><MSA.2>1</MSA.2></MSA></ACK>";
    }
}
