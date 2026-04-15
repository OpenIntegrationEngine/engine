package com.mirth.connect.connectors.dimse.dicom.dcm5;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.Test;

import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;

public class Dcm5DicomConverterTest {

    private final Dcm5DicomConverter converter = new Dcm5DicomConverter();

    @Test
    public void testByteArrayRoundTripWithoutFmi() throws Exception {
        Dcm5DicomObject original = new Dcm5DicomObject();
        original.putString(Tag.PatientName, "PN", "Test^Patient");
        original.putString(Tag.PatientID, "LO", "12345");

        byte[] bytes = converter.dicomObjectToByteArray(original);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        OieDicomObject parsed = converter.byteArrayToDicomObject(bytes, false);
        assertNotNull(parsed);
        assertEquals("Test^Patient", parsed.getString(Tag.PatientName));
        assertEquals("12345", parsed.getString(Tag.PatientID));
    }

    @Test
    public void testByteArrayRoundTripWithFmi() throws Exception {
        Dcm5DicomObject original = new Dcm5DicomObject();
        original.putString(Tag.PatientName, "PN", "FmiTest");
        original.initFileMetaInformation("1.2.840.10008.5.1.4.1.1.2", "1.2.3.4.5", "1.2.840.10008.1.2");

        byte[] bytes = converter.dicomObjectToByteArray(original);
        assertNotNull(bytes);

        OieDicomObject parsed = converter.byteArrayToDicomObject(bytes, false);
        assertNotNull(parsed);
        assertTrue(parsed.hasFileMetaInfo());
    }

    @Test
    public void testByteArrayBase64RoundTrip() throws Exception {
        // Create DICOM bytes
        Dcm5DicomObject original = new Dcm5DicomObject();
        original.putString(Tag.PatientName, "PN", "Base64Test");
        original.initFileMetaInformation("1.2.840.10008.5.1.4.1.1.2", "1.2.3.4.5", "1.2.840.10008.1.2");
        byte[] dicomBytes = converter.dicomObjectToByteArray(original);

        // Base64 encode
        byte[] base64Bytes = Base64.getEncoder().encode(dicomBytes);

        // Parse with decodeBase64=true
        OieDicomObject parsed = converter.byteArrayToDicomObject(base64Bytes, true);
        assertNotNull(parsed);
    }

    @Test
    public void testCreateDicomObject() {
        OieDicomObject obj = converter.createDicomObject();
        assertNotNull(obj);
        assertTrue(obj instanceof Dcm5DicomObject);
        assertFalse(obj.hasFileMetaInfo());
    }

    @Test
    public void testGetElementName() {
        String name = converter.getElementName(Tag.PatientName);
        assertNotNull(name);
        assertFalse(name.isEmpty());
        assertEquals("PatientName", name);
    }

    @Test
    public void testGetElementNameUnknown() {
        String name = converter.getElementName(0x99999999);
        assertNotNull(name);
        // Unknown tags return empty string
    }

    @Test
    public void testDicomBytesToXml() throws Exception {
        // Build a DICOM file with FMI
        Attributes fmi = Attributes.createFileMetaInformation("1.2.3.4.5", "1.2.840.10008.5.1.4.1.1.2", "1.2.840.10008.1.2.1");
        Attributes dataset = new Attributes();
        dataset.setString(Tag.PatientName, VR.PN, "XmlTest^Patient");
        dataset.setString(Tag.PatientID, VR.LO, "XML123");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DicomOutputStream dos = new DicomOutputStream(baos, UID.ExplicitVRLittleEndian);
        dos.writeDataset(fmi, dataset);
        dos.close();

        byte[] base64Bytes = Base64.getEncoder().encode(baos.toByteArray());

        String xml = converter.dicomBytesToXml(base64Bytes);
        assertNotNull(xml);
        assertTrue(xml.contains("PatientName") || xml.contains("00100010"));
    }

    @Test
    public void testXmlToDicomObject() throws Exception {
        // dcm4che5 XML format uses <NativeDicomModel> and <DicomAttribute>
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<NativeDicomModel>" +
                "<DicomAttribute keyword=\"PatientName\" tag=\"00100010\" vr=\"PN\">" +
                "<PersonName number=\"1\"><Alphabetic><FamilyName>Test</FamilyName></Alphabetic></PersonName>" +
                "</DicomAttribute>" +
                "<DicomAttribute keyword=\"PatientID\" tag=\"00100020\" vr=\"LO\"><Value number=\"1\">ID123</Value></DicomAttribute>" +
                "</NativeDicomModel>";

        OieDicomObject obj = converter.xmlToDicomObject(xml, "UTF-8");
        assertNotNull(obj);
        assertEquals("ID123", obj.getString(Tag.PatientID));
    }

    @Test
    public void testXxePrevention() {
        String maliciousXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>" +
                "<NativeDicomModel>&xxe;</NativeDicomModel>";

        try {
            converter.xmlToDicomObject(maliciousXml, "UTF-8");
            fail("Expected exception for XXE attack");
        } catch (Exception e) {
            // Expected — DOCTYPE is disallowed
        }
    }

    @Test
    public void testDicomObjectToByteArrayClearsObject() throws Exception {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        obj.putString(Tag.PatientName, "PN", "ClearTest");

        converter.dicomObjectToByteArray(obj);

        // After serialization, the object should be cleared
        assertNull(obj.getString(Tag.PatientName));
    }
}
