package com.mirth.connect.plugins.datatypes.hl7v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.ContentType;
import com.mirth.connect.donkey.model.message.MessageContent;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseValidationProperties.OriginalMessageControlId;

public class HL7v2ResponseValidatorTest {

    private static final String ORIGINAL_ER7 = "MSH|^~\\&|SEND|FAC|RECV|FAC|20261031120000||ADT^A01|CTRL123|P|2.5\rPID|1||12345";

    private HL7v2SerializationProperties serializationProperties;
    private HL7v2ResponseValidationProperties validationProperties;
    private ConnectorMessage connectorMessage;

    @Before
    public void setup() {
        serializationProperties = new HL7v2SerializationProperties();
        validationProperties = new HL7v2ResponseValidationProperties();
        connectorMessage = new ConnectorMessage();
        connectorMessage.setConnectorMap(new HashMap<>());
        connectorMessage.setChannelMap(new HashMap<>());
        setEncoded(ORIGINAL_ER7);
    }

    private void setEncoded(String content) {
        connectorMessage.setEncoded(new MessageContent("channel", 1, 1, ContentType.ENCODED, content, "HL7V2", false));
    }

    private Response validate(String responseData) {
        HL7v2ResponseValidator validator = new HL7v2ResponseValidator(serializationProperties, validationProperties);
        return validator.validate(new Response(Status.SENT, responseData), connectorMessage);
    }

    private static String ack(String code, String controlId) {
        return "MSH|^~\\&|RECV|FAC|SEND|FAC|20261031120001||ACK|ACK1|P|2.5\rMSA|" + code + "|" + controlId;
    }

    @Test
    public void testBlankResponseIsQueued() {
        Response response = validate("   ");

        assertEquals(Status.QUEUED, response.getStatus());
        assertEquals("Empty or blank response received.", response.getStatusMessage());
    }

    @Test
    public void testAcceptedAckWithMatchingControlId() {
        assertEquals(Status.SENT, validate(ack("AA", "CTRL123")).getStatus());
    }

    @Test
    public void testCommitAcceptIsSuccessful() {
        assertEquals(Status.SENT, validate(ack("CA", "CTRL123") + "|done").getStatus());
    }

    @Test
    public void testAcceptedAckWithMismatchedControlId() {
        Response response = validate(ack("AA", "OTHER"));

        assertEquals(Status.ERROR, response.getStatus());
        assertEquals("Message control Ids do not match.", response.getStatusMessage());
        assertTrue(response.getError().contains("Expected: CTRL123"));
        assertTrue(response.getError().contains("Actual: OTHER"));
    }

    @Test
    public void testAcceptedAckWithoutMsa2() {
        Response response = validate("MSH|^~\\&|R|F\rMSA|AA");

        assertEquals(Status.ERROR, response.getStatus());
        assertTrue(response.getError().endsWith("Actual: "));
    }

    @Test
    public void testControlIdValidationDisabled() {
        validationProperties.setValidateMessageControlId(false);

        assertEquals(Status.SENT, validate(ack("AA", "OTHER")).getStatus());
    }

    @Test
    public void testErrorAckWithMsa3AndErr1() {
        String responseData = ack("AE", "CTRL123") + "|Something failed|x\rERR|PID^1^3|other";
        Response response = validate(responseData);

        assertEquals(Status.ERROR, response.getStatus());
        assertEquals("NACK sent from receiver. (Error)\nSomething failed\nPID^1^3", response.getStatusMessage());
        assertTrue(response.getError().endsWith("\n\n" + responseData));
    }

    @Test
    public void testErrorAckWithTrailingMsa3AndErr1() {
        Response response = validate(ack("AE", "CTRL123") + "|Last field\rERR|Only");

        assertEquals("NACK sent from receiver. (Error)\nLast field\nOnly", response.getStatusMessage());
    }

    @Test
    public void testRejectedAckWithoutDetails() {
        Response response = validate(ack("AR", "CTRL123"));

        assertEquals(Status.ERROR, response.getStatus());
        assertEquals("NACK sent from receiver. (Rejected)", response.getStatusMessage());
    }

    @Test
    public void testCustomAckCodes() {
        validationProperties.setSuccessfulACKCode("OK");
        validationProperties.setErrorACKCode("BAD");
        validationProperties.setRejectedACKCode("NO");

        assertEquals(Status.SENT, validate(ack("OK", "CTRL123")).getStatus());
        assertEquals("NACK sent from receiver. (Error)", validate(ack("BAD", "CTRL123")).getStatusMessage());
        assertEquals("NACK sent from receiver. (Rejected)", validate(ack("NO", "CTRL123")).getStatusMessage());
        assertEquals(Status.QUEUED, validate(ack("AA", "CTRL123")).getStatus());
    }

    @Test
    public void testUnknownAckCodeIsInvalid() {
        Response response = validate(ack("XX", "CTRL123"));

        assertEquals(Status.QUEUED, response.getStatus());
        assertEquals("Invalid HL7 v2.x acknowledgement received.", response.getStatusMessage());
    }

    @Test
    public void testMissingMsaIsInvalid() {
        assertEquals("Invalid HL7 v2.x acknowledgement received.", validate("MSH|^~\\&|R|F\rPID|1").getStatusMessage());
    }

    @Test
    public void testTruncatedMsaIsInvalid() {
        assertEquals("Invalid HL7 v2.x acknowledgement received.", validate("MSH|^~\\&|R|F\rMSA").getStatusMessage());
    }

    @Test
    public void testLineBreaksAreConverted() {
        String responseData = ack("AA", "CTRL123").replace("\r", "\r\n");
        assertEquals(Status.SENT, validate(responseData).getStatus());

        serializationProperties.setConvertLineBreaks(false);
        assertEquals(Status.SENT, validate(responseData.replace("\r\n", "\r")).getStatus());
        assertEquals(Status.QUEUED, validate(responseData.replace("\r\n", "\n")).getStatus());
    }

    @Test
    public void testOriginalControlIdFromXmlEncodedMessage() {
        setEncoded("<HL7Message><MSH><MSH.10><MSH.10.1>XMLCTRL</MSH.10.1></MSH.10></MSH></HL7Message>");

        assertEquals(Status.SENT, validate(ack("AA", "XMLCTRL")).getStatus());
    }

    @Test
    public void testOriginalMessageWithoutControlId() {
        setEncoded("MSH|^~\\&|SEND|FAC\rPID|1|2|3|4|5|6|7|8|9|10");

        assertEquals(Status.SENT, validate("MSH|^~\\&|R|F\rMSA|AA|").getStatus());
        assertEquals(Status.ERROR, validate(ack("AA", "1")).getStatus());
    }

    @Test
    public void testOriginalMessageEndingAtControlId() {
        setEncoded("MSH|^~\\&|SEND|FAC|RECV|FAC|20261031120000||ADT^A01|LASTCTRL");

        assertEquals(Status.SENT, validate(ack("AA", "LASTCTRL")).getStatus());
    }

    @Test
    public void testOriginalControlIdFromConnectorMap() {
        validationProperties.setOriginalMessageControlId(OriginalMessageControlId.Map_Variable);
        validationProperties.setOriginalIdMapVariable("controlId");
        connectorMessage.getConnectorMap().put("controlId", "FROMMAP");
        connectorMessage.getChannelMap().put("controlId", "IGNORED");

        assertEquals(Status.SENT, validate(ack("AA", "FROMMAP")).getStatus());
    }

    @Test
    public void testOriginalControlIdFromChannelMap() {
        validationProperties.setOriginalMessageControlId(OriginalMessageControlId.Map_Variable);
        validationProperties.setOriginalIdMapVariable("controlId");
        connectorMessage.getChannelMap().put("controlId", 42);

        assertEquals(Status.SENT, validate(ack("AA", "42")).getStatus());
    }

    @Test
    public void testOriginalControlIdMapVariableNotSet() {
        validationProperties.setOriginalMessageControlId(OriginalMessageControlId.Map_Variable);
        validationProperties.setOriginalIdMapVariable("");

        Response response = validate(ack("AA", "CTRL123"));
        assertEquals(Status.QUEUED, response.getStatus());
        assertEquals("Error validating response: Map variable for original control Id not set.", response.getStatusMessage());

        validationProperties.setOriginalIdMapVariable("missing");
        assertEquals("Error validating response: Map variable for original control Id not set.", validate(ack("AA", "CTRL123")).getStatusMessage());
    }

    @Test
    public void testXmlAcceptedAck() {
        String xml = "<HL7Message><MSA><MSA.1> AA </MSA.1><MSA.2>CTRL123</MSA.2></MSA></HL7Message>";
        assertEquals(Status.SENT, validate(xml).getStatus());

        validationProperties.setValidateMessageControlId(false);
        assertEquals(Status.SENT, validate(xml.replace("CTRL123", "OTHER")).getStatus());
    }

    @Test
    public void testXmlAcceptedAckWithMismatchedControlId() {
        Response response = validate("<HL7Message><MSA><MSA.1>AA</MSA.1><MSA.2>OTHER</MSA.2></MSA></HL7Message>");

        assertEquals(Status.ERROR, response.getStatus());
        assertEquals("Message control Ids do not match.", response.getStatusMessage());
    }

    @Test
    public void testXmlErrorAndRejectedAcks() {
        String xml = "<HL7Message><MSA><MSA.1>AE</MSA.1><MSA.3>Bad thing</MSA.3></MSA><ERR><ERR.1>PID</ERR.1></ERR></HL7Message>";
        assertEquals("NACK sent from receiver. (Error)\nBad thing\nPID", validate(xml).getStatusMessage());

        assertEquals("NACK sent from receiver. (Rejected)", validate("<HL7Message><MSA><MSA.1>CR</MSA.1></MSA></HL7Message>").getStatusMessage());
    }

    @Test
    public void testXmlUnknownAckCodeLeavesStatusUnchanged() {
        assertEquals(Status.SENT, validate("<HL7Message><MSA><MSA.1>XX</MSA.1></MSA></HL7Message>").getStatus());
    }

    @Test
    public void testXmlWithDoctypeIsRejected() {
        Response response = validate("<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><HL7Message><MSA><MSA.1>&xxe;</MSA.1></MSA></HL7Message>");

        assertEquals(Status.QUEUED, response.getStatus());
        assertTrue(response.getStatusMessage().startsWith("Error validating response: "));
        assertTrue(response.getStatusMessage().contains("DOCTYPE"));
    }
}
