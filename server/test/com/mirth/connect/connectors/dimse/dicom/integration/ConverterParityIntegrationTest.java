// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import org.dcm4che3.data.Tag;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory;
import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory.DicomLibrary;
import com.mirth.connect.connectors.dimse.dicom.OieDicomConverter;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DicomConverter;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomConverter;

/**
 * Verifies semantic equivalence between dcm2 and dcm5 converters. Both converters
 * must produce DICOM data that is correctly parseable by the other.
 *
 * <p>Note: Byte-level equivalence is NOT expected because:
 * <ul>
 *   <li>FMI implementation version names differ</li>
 *   <li>dcm2 uses two-pass write (ByteCounterOutputStream), dcm5 uses single-pass</li>
 *   <li>Internal padding/alignment may differ</li>
 * </ul>
 */
public class ConverterParityIntegrationTest {

    private Dcm2DicomConverter dcm2Converter;
    private Dcm5DicomConverter dcm5Converter;

    @Before
    public void setUp() {
        dcm2Converter = new Dcm2DicomConverter();
        dcm5Converter = new Dcm5DicomConverter();
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE5);
    }

    @After
    public void tearDown() {
        DicomLibraryFactory.resetForTesting(null);
    }

    @Test
    public void testDcm5BytesParsedByDcm2() throws Exception {
        // Create DICOM data using dcm5
        OieDicomObject dcm5Obj = dcm5Converter.createDicomObject();
        dcm5Obj.putString(Tag.PatientName, "PN", "Cross^Library");
        dcm5Obj.putString(Tag.PatientID, "LO", "XLIB001");
        dcm5Obj.putString(Tag.Modality, "CS", "MR");
        dcm5Obj.initFileMetaInformation(
                "1.2.840.10008.5.1.4.1.1.4",  // MR Image Storage
                "1.2.3.4.5.6.7.8.9",
                "1.2.840.10008.1.2");           // Implicit VR LE
        byte[] dcm5Bytes = dcm5Converter.dicomObjectToByteArray(dcm5Obj);

        // Parse dcm5 bytes using dcm2
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        OieDicomObject parsedByDcm2 = dcm2Converter.byteArrayToDicomObject(dcm5Bytes, false);
        assertEquals("Cross^Library", parsedByDcm2.getString(Tag.PatientName));
        assertEquals("XLIB001", parsedByDcm2.getString(Tag.PatientID));
        assertEquals("MR", parsedByDcm2.getString(Tag.Modality));
    }

    @Test
    public void testDcm2BytesParsedByDcm5() throws Exception {
        // Create DICOM data using dcm2
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        OieDicomObject dcm2Obj = dcm2Converter.createDicomObject();
        dcm2Obj.putString(Tag.PatientName, "PN", "Reverse^Test");
        dcm2Obj.putString(Tag.PatientID, "LO", "REV001");
        dcm2Obj.putString(Tag.Modality, "CS", "CT");
        dcm2Obj.initFileMetaInformation(
                "1.2.840.10008.5.1.4.1.1.2",  // CT Image Storage
                "1.2.3.4.5.6.7.8.10",
                "1.2.840.10008.1.2");
        byte[] dcm2Bytes = dcm2Converter.dicomObjectToByteArray(dcm2Obj);

        // Parse dcm2 bytes using dcm5
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE5);
        OieDicomObject parsedByDcm5 = dcm5Converter.byteArrayToDicomObject(dcm2Bytes, false);
        assertEquals("Reverse^Test", parsedByDcm5.getString(Tag.PatientName));
        assertEquals("REV001", parsedByDcm5.getString(Tag.PatientID));
        assertEquals("CT", parsedByDcm5.getString(Tag.Modality));
    }

    @Test
    public void testBidirectionalRoundTrip() throws Exception {
        // dcm5 -> bytes -> dcm2 parse -> dcm2 bytes -> dcm5 parse
        OieDicomObject original = dcm5Converter.createDicomObject();
        original.putString(Tag.PatientName, "PN", "RoundTrip^Full");
        original.putString(Tag.PatientID, "LO", "RT001");
        original.putString(Tag.StudyDescription, "LO", "Integration Test");
        original.initFileMetaInformation(
                "1.2.840.10008.5.1.4.1.1.2",
                "1.2.3.4.5.6.7.8.11",
                "1.2.840.10008.1.2");
        byte[] dcm5Bytes = dcm5Converter.dicomObjectToByteArray(original);

        // dcm2 parses dcm5 bytes
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        OieDicomObject intermediate = dcm2Converter.byteArrayToDicomObject(dcm5Bytes, false);
        assertEquals("RoundTrip^Full", intermediate.getString(Tag.PatientName));

        // dcm2 re-serializes
        byte[] dcm2Bytes = dcm2Converter.dicomObjectToByteArray(intermediate);

        // dcm5 parses dcm2 bytes
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE5);
        OieDicomObject final5 = dcm5Converter.byteArrayToDicomObject(dcm2Bytes, false);
        assertEquals("RoundTrip^Full", final5.getString(Tag.PatientName));
        assertEquals("RT001", final5.getString(Tag.PatientID));
        assertEquals("Integration Test", final5.getString(Tag.StudyDescription));
    }

    @Test
    public void testElementNameParity() {
        // Both converters should return equivalent element names for standard tags.
        // Note: dcm2 returns "Patient's Name", dcm5 returns "PatientName" — both are valid.
        String dcm2Name = dcm2Converter.getElementName(Tag.PatientName);
        String dcm5Name = dcm5Converter.getElementName(Tag.PatientName);
        assertNotNull(dcm2Name);
        assertNotNull(dcm5Name);
        // Both should be non-empty for a well-known tag
        assertNotEquals("dcm2 element name should not be empty", "", dcm2Name);
        assertNotEquals("dcm5 element name should not be empty", "", dcm5Name);

        // PatientID should also be recognized by both
        assertNotEquals("", dcm2Converter.getElementName(Tag.PatientID));
        assertNotEquals("", dcm5Converter.getElementName(Tag.PatientID));

        // Modality
        assertNotEquals("", dcm2Converter.getElementName(Tag.Modality));
        assertNotEquals("", dcm5Converter.getElementName(Tag.Modality));
    }
}
