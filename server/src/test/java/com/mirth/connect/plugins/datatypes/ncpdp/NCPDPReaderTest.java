package com.mirth.connect.plugins.datatypes.ncpdp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.mirth.connect.model.converters.XMLPrettyPrinter;

public class NCPDPReaderTest {

    private static final String SEGMENT = "\u001E";
    private static final String GROUP = "\u001D";
    private static final String FIELD = "\u001C";

    // BIN, version, transaction code, PCN, count, provider id qualifier, provider id, date of service, vendor id
    private static final String REQUEST_HEADER_D0 = "610066" + "D0" + "B1" + "PCN1234567" + "1" + "01" + "123456789012345" + "20261031" + "VENDOR1234";
    // Version, transaction code, count, status, provider id qualifier, provider id, date of service
    private static final String RESPONSE_HEADER_D0 = "D0" + "B1" + "1" + "A" + "01" + "123456789012345" + "20261031";

    private static Document parse(String message) throws Exception {
        NCPDPReader reader = new NCPDPReader(SEGMENT, GROUP, FIELD);
        StringWriter writer = new StringWriter();
        XMLPrettyPrinter printer = new XMLPrettyPrinter(writer);
        printer.setEncodeEntities(true);
        reader.setContentHandler(printer);
        reader.parse(new InputSource(new StringReader(message)));
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(writer.toString())));
    }

    private static String segment(String id, String... fields) {
        StringBuilder builder = new StringBuilder(SEGMENT).append(FIELD).append(id);
        for (String field : fields) {
            builder.append(FIELD).append(field);
        }
        return builder.toString();
    }

    private static String text(Document doc, String xpath) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(xpath, doc);
    }

    private static int count(Document doc, String xpath) throws Exception {
        return ((NodeList) XPathFactory.newInstance().newXPath().evaluate(xpath, doc, XPathConstants.NODESET)).getLength();
    }

    @Test
    public void testRequestHeader() throws Exception {
        Document doc = parse(REQUEST_HEADER_D0 + segment("AM01", "C419800101", "C51"));

        assertEquals("NCPDP_D0_Billing_Request", doc.getDocumentElement().getNodeName());
        String header = "/NCPDP_D0_Billing_Request/TransactionHeaderRequest/";
        assertEquals("610066", text(doc, header + "BinNumber"));
        assertEquals("D0", text(doc, header + "VersionReleaseNumber"));
        assertEquals("B1", text(doc, header + "TransactionCode"));
        assertEquals("PCN1234567", text(doc, header + "ProcessorControlNumber"));
        assertEquals("1", text(doc, header + "TransactionCount"));
        assertEquals("01", text(doc, header + "ServiceProviderIdQualifier"));
        assertEquals("123456789012345", text(doc, header + "ServiceProviderId"));
        assertEquals("20261031", text(doc, header + "DateOfService"));
        assertEquals("VENDOR1234", text(doc, header + "SoftwareVendorCertificationId"));

        assertEquals("19800101", text(doc, "/NCPDP_D0_Billing_Request/Patient/DateOfBirth"));
        assertEquals("1", text(doc, "/NCPDP_D0_Billing_Request/Patient/PatientGenderCode"));
    }

    @Test
    public void testResponseHeader() throws Exception {
        Document doc = parse(RESPONSE_HEADER_D0 + segment("AM21", "ANA", "F3AUTH1"));

        assertEquals("NCPDP_D0_Billing_Response", doc.getDocumentElement().getNodeName());
        String header = "/NCPDP_D0_Billing_Response/TransactionHeaderResponse/";
        assertEquals("D0", text(doc, header + "VersionReleaseNumber"));
        assertEquals("B1", text(doc, header + "TransactionCode"));
        assertEquals("1", text(doc, header + "TransactionCount"));
        assertEquals("A", text(doc, header + "HeaderResponseStatus"));
        assertEquals("01", text(doc, header + "ServiceProviderIdQualifier"));
        assertEquals("123456789012345", text(doc, header + "ServiceProviderId"));
        assertEquals("20261031", text(doc, header + "DateOfService"));

        assertEquals("A", text(doc, "/NCPDP_D0_Billing_Response/ResponseStatus/TransactionResponseStatus"));
        assertEquals("AUTH1", text(doc, "/NCPDP_D0_Billing_Response/ResponseStatus/AuthorizationNumber"));
    }

    @Test
    public void testUnknownTransactionCodeAndField() throws Exception {
        String header = REQUEST_HEADER_D0.substring(0, 8) + "ZZ" + REQUEST_HEADER_D0.substring(10);
        Document doc = parse(header + segment("AM01", "Q9value"));

        assertEquals("NCPDP_D0_ZZ_Request", doc.getDocumentElement().getNodeName());
        assertEquals("value", text(doc, "/NCPDP_D0_ZZ_Request/Patient/Q9_field"));
    }

    @Test
    public void testTransactionGroups() throws Exception {
        String message = REQUEST_HEADER_D0
                + segment("AM04", "C2CARD1")
                + GROUP + segment("AM07", "D2RX1", "E703")
                + GROUP + segment("AM07", "D2RX2", "E704");
        Document doc = parse(message);

        String root = "/NCPDP_D0_Billing_Request";
        assertEquals("CARD1", text(doc, root + "/Insurance/CardholderId"));
        assertEquals(1, count(doc, root + "/TRANSACTIONS"));
        assertEquals(2, count(doc, root + "/TRANSACTIONS/TRANSACTION"));
        assertEquals("1", text(doc, root + "/TRANSACTIONS/TRANSACTION[1]/@counter"));
        assertEquals("RX1", text(doc, root + "/TRANSACTIONS/TRANSACTION[1]/Claim/PrescriptionServiceReferenceNumber"));
        assertEquals("03", text(doc, root + "/TRANSACTIONS/TRANSACTION[1]/Claim/QuantityDispensed"));
        assertEquals("2", text(doc, root + "/TRANSACTIONS/TRANSACTION[2]/@counter"));
        assertEquals("RX2", text(doc, root + "/TRANSACTIONS/TRANSACTION[2]/Claim/PrescriptionServiceReferenceNumber"));
    }

    @Test
    public void testCountFieldWrapsRepeatingFields() throws Exception {
        Document doc = parse(RESPONSE_HEADER_D0 + segment("AM21", "ANR", "FA2", "FB70", "FB75", "F3AUTH1"));

        String status = "/NCPDP_D0_Billing_Response/ResponseStatus";
        assertEquals("R", text(doc, status + "/TransactionResponseStatus"));
        assertEquals("2", text(doc, status + "/RejectCount/@RejectCount"));
        assertEquals(2, count(doc, status + "/RejectCount/RejectCode"));
        assertEquals("70", text(doc, status + "/RejectCount/RejectCode[1]"));
        assertEquals("75", text(doc, status + "/RejectCount/RejectCode[2]"));
        // The first non-repeating field closes the count element
        assertEquals("AUTH1", text(doc, status + "/AuthorizationNumber"));
        assertEquals(0, count(doc, status + "/RejectCount/AuthorizationNumber"));
    }

    @Test
    public void testCountFieldClosedAtEndOfSegment() throws Exception {
        Document doc = parse(RESPONSE_HEADER_D0 + segment("AM21", "FA1", "FB70"));

        assertEquals("70", text(doc, "/NCPDP_D0_Billing_Response/ResponseStatus/RejectCount/RejectCode"));
    }

    @Test
    public void testCounterFieldsGroupFollowingFields() throws Exception {
        Document doc = parse(REQUEST_HEADER_D0 + segment("AM08", "7E1", "E5M0", "7E2", "E5P0"));

        String dur = "/NCPDP_D0_Billing_Request/DURPPS";
        assertEquals(2, count(doc, dur + "/DurPpsCodeCounter"));
        assertEquals("1", text(doc, dur + "/DurPpsCodeCounter[1]/@counter"));
        assertEquals("M0", text(doc, dur + "/DurPpsCodeCounter[1]/ProfessionalServiceCode"));
        assertEquals("2", text(doc, dur + "/DurPpsCodeCounter[2]/@counter"));
        assertEquals("P0", text(doc, dur + "/DurPpsCodeCounter[2]/ProfessionalServiceCode"));
    }

    @Test
    public void testFieldNamesDependOnVersion() throws Exception {
        String segment = segment("AM07", "NX2", "DK1", "DK2");

        // NX is a count field only in D0
        Document d0 = parse(REQUEST_HEADER_D0 + segment);
        assertEquals("2", text(d0, "/NCPDP_D0_Billing_Request/Claim/SubmissionClarificationCodeCount/@SubmissionClarificationCodeCount"));
        assertEquals(2, count(d0, "/NCPDP_D0_Billing_Request/Claim/SubmissionClarificationCodeCount/SubmissionClarificationCode"));

        String header51 = REQUEST_HEADER_D0.substring(0, 6) + "51" + REQUEST_HEADER_D0.substring(8);
        Document v51 = parse(header51 + segment);
        assertEquals("NCPDP_51_Billing_Request", v51.getDocumentElement().getNodeName());
        assertEquals("2", text(v51, "/NCPDP_51_Billing_Request/Claim/NX_field"));
        assertEquals(2, count(v51, "/NCPDP_51_Billing_Request/Claim/SubmissionClarificationCode"));
    }

    @Test
    public void testSegmentWithoutFields() throws Exception {
        Document doc = parse(REQUEST_HEADER_D0 + segment("AM01", "C51") + SEGMENT + "AM99");

        assertEquals(1, count(doc, "/NCPDP_D0_Billing_Request/AM99"));
        assertEquals(0, count(doc, "/NCPDP_D0_Billing_Request/AM99/*"));
    }

    @Test
    public void testMessageTooShort() throws Exception {
        NCPDPReader reader = new NCPDPReader(SEGMENT, GROUP, FIELD);
        reader.setContentHandler(new DefaultHandler());

        try {
            reader.parse(new InputSource(new StringReader("  AB  ")));
            fail("Expected SAXException");
        } catch (SAXException e) {
            assertTrue(e.getMessage().contains("too short"));
        }
    }
}
