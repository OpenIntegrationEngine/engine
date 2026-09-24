// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.plugins.datatypes.dicom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.codec.binary.Base64;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.io.DicomOutputStream;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.mirth.connect.model.converters.DICOMConverter;

/**
 * Round-trip tests for undefined-length VR=UN sequences (private implicit-VR tags, e.g. FujiFILM
 * (0029,E131) / Siemens MEDCOM (0029,1140)). dcm4che2's SAXWriter emits these with their wire VR
 * while ContentHandlerAdapter requires vr="SQ" for dataset items, so before the fromXML fix the
 * round trip threw IllegalStateException("state:EXPECT_FRAG").
 */
public class DICOMSerializerUnSequenceTest {

    private static final int PRIVATE_SEQUENCE_TAG = 0x0029E131;
    private static final int PRIVATE_BLOB_TAG = 0x00291001;
    private static final byte[] BLOB_BYTES = new byte[] { 1, 2, 3, 4 };

    @Test
    public void testUndefinedLengthUnSequenceRoundTrip() throws Exception {
        BasicDicomObject dcm = new BasicDicomObject();
        dcm.putString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        dcm.putString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9");
        dcm.putString(Tag.PatientName, VR.PN, "TEST^PATIENT");
        dcm.putBytes(PRIVATE_BLOB_TAG, VR.UN, BLOB_BYTES);

        BasicDicomObject item = new BasicDicomObject();
        item.putString(Tag.CodeValue, VR.SH, "VALUE1");
        dcm.putNestedDicomObject(PRIVATE_SEQUENCE_TAG, item);

        String base64 = writeDicomFile(dcm, UID.ImplicitVRLittleEndian);

        DICOMSerializer serializer = new DICOMSerializer();
        String xml = serializer.toXML(base64);

        // Guard against a vacuous pass: the XML must actually carry the broken wire shape
        // (vr="UN" with a structured <item> child) or this test isn't exercising the fix.
        Element sequenceElement = findElementByTag(xml, "0029E131");
        assertNotNull("private sequence element missing from XML", sequenceElement);
        assertEquals("UN", sequenceElement.getAttribute("vr"));
        NodeList sequenceItems = sequenceElement.getElementsByTagName("item");
        assertTrue("expected an <item> child", sequenceItems.getLength() > 0);
        assertTrue("expected the <item> to hold dataset elements", hasElementChild(sequenceItems.item(0)));

        // Pre-fix this threw MessageSerializerException caused by
        // IllegalStateException("state:EXPECT_FRAG").
        String roundTripped = serializer.fromXML(xml);

        DicomObject result = DICOMConverter.byteArrayToDicomObject(Base64.decodeBase64(roundTripped), false);
        assertEquals("TEST^PATIENT", result.getString(Tag.PatientName));

        DicomObject resultItem = result.getNestedDicomObject(PRIVATE_SEQUENCE_TAG);
        assertNotNull("private sequence lost in round trip", resultItem);
        assertEquals("VALUE1", resultItem.getString(Tag.CodeValue));

        // Defined-length UN blobs have no items and must pass through untouched.
        assertArrayEquals(BLOB_BYTES, result.getBytes(PRIVATE_BLOB_TAG));
    }

    @Test
    public void testEncapsulatedPixelDataFragmentsUntouched() throws Exception {
        BasicDicomObject dcm = new BasicDicomObject();
        dcm.putString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        dcm.putString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.10");

        byte[] fragment = new byte[] { 9, 8, 7, 6, 5, 4 };
        DicomElement pixelData = dcm.putFragments(Tag.PixelData, VR.OB, false);
        pixelData.addFragment(new byte[0]); // basic offset table
        pixelData.addFragment(fragment);

        String base64 = writeDicomFile(dcm, UID.JPEGBaseline1);

        DICOMSerializer serializer = new DICOMSerializer();
        String xml = serializer.toXML(base64);

        // The fragment container must keep its wire VR: its items hold encoded text
        // (backslash-hex for OB/OW), not elements, so the SQ rewrite must not touch it.
        Element pixelDataElement = findElementByTag(xml, "7FE00010");
        assertNotNull("pixel data element missing from XML", pixelDataElement);
        assertEquals("OB", pixelDataElement.getAttribute("vr"));

        String roundTripped = serializer.fromXML(xml);

        DicomObject result = DICOMConverter.byteArrayToDicomObject(Base64.decodeBase64(roundTripped), false);
        DicomElement resultPixelData = result.get(Tag.PixelData);
        assertNotNull("pixel data lost in round trip", resultPixelData);
        assertTrue("pixel data no longer encapsulated", resultPixelData.hasFragments());
        assertArrayEquals(fragment, resultPixelData.getFragment(1));
    }

    private String writeDicomFile(BasicDicomObject dcm, String transferSyntaxUid) throws Exception {
        dcm.initFileMetaInformation(transferSyntaxUid);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DicomOutputStream dos = new DicomOutputStream(baos);
        dos.writeDicomFile(dcm);
        dos.close();
        return Base64.encodeBase64String(baos.toByteArray());
    }

    private boolean hasElementChild(Node node) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }

        return false;
    }

    private Element findElementByTag(String xml, String tag) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList elements = document.getElementsByTagName("*");

        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);

            if (tag.equalsIgnoreCase(element.getAttribute("tag"))) {
                return element;
            }
        }

        return null;
    }
}
