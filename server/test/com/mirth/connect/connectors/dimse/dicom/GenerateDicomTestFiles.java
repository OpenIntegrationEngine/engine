// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.junit.Test;

import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory.DicomLibrary;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomConverter;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomObject;

/**
 * Generates proper DICOM Part 10 test files for manual testing with storescu.
 * Run with: ./gradlew :server:test --tests "com.mirth.connect.connectors.dimse.dicom.GenerateDicomTestFiles"
 */
public class GenerateDicomTestFiles {

    private static final String OUTPUT_DIR = "tests";

    // SOP Class UIDs
    private static final String CT_IMAGE_STORAGE   = "1.2.840.10008.5.1.4.1.1.2";
    private static final String MR_IMAGE_STORAGE   = "1.2.840.10008.5.1.4.1.1.4";
    private static final String US_IMAGE_STORAGE   = "1.2.840.10008.5.1.4.1.1.6.1";

    // Transfer Syntax
    private static final String IMPLICIT_VR_LE     = "1.2.840.10008.1.2";

    @Test
    public void generateTestFiles() throws Exception {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE5);
        try {
            Path outputDir = Paths.get(OUTPUT_DIR);
            Files.createDirectories(outputDir);

            createCtFile(outputDir);
            createMrFile(outputDir);
            createUsFile(outputDir);

            System.out.println("DICOM Part 10 test files generated in: " + outputDir.toAbsolutePath());
        } finally {
            DicomLibraryFactory.resetForTesting(null);
        }
    }

    private void createCtFile(Path outputDir) throws IOException {
        Dcm5DicomConverter converter = new Dcm5DicomConverter();
        Dcm5DicomObject obj = (Dcm5DicomObject) converter.createDicomObject();

        obj.putString(Tag.PatientName, "PN", "Doe^John");
        obj.putString(Tag.PatientID, "LO", "PAT001");
        obj.putString(Tag.PatientBirthDate, "DA", "19800101");
        obj.putString(Tag.PatientSex, "CS", "M");
        obj.putString(Tag.Modality, "CS", "CT");
        obj.putString(Tag.StudyDate, "DA", "20230101");
        obj.putString(Tag.StudyTime, "TM", "120000");
        obj.putString(Tag.SpecificCharacterSet, "CS", "ISO_IR 100");
        obj.putString(Tag.StudyInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SeriesInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SOPInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SOPClassUID, "UI", CT_IMAGE_STORAGE);

        obj.initFileMetaInformation(CT_IMAGE_STORAGE, UIDUtils.createUID(), IMPLICIT_VR_LE);

        byte[] bytes = converter.dicomObjectToByteArray(obj);
        Path file = outputDir.resolve("test-dicom-input-1.dcm");
        Files.write(file, bytes);
        System.out.println("Created: " + file + " (" + bytes.length + " bytes) - CT/Doe^John/PAT001");
    }

    private void createMrFile(Path outputDir) throws IOException {
        Dcm5DicomConverter converter = new Dcm5DicomConverter();
        Dcm5DicomObject obj = (Dcm5DicomObject) converter.createDicomObject();

        obj.putString(Tag.PatientName, "PN", "Smith^Jane");
        obj.putString(Tag.PatientID, "LO", "PAT002");
        obj.putString(Tag.PatientSex, "CS", "F");
        obj.putString(Tag.Modality, "CS", "MR");
        obj.putString(Tag.StudyDate, "DA", "20230215");
        obj.putString(Tag.SpecificCharacterSet, "CS", "ISO_IR 100");
        obj.putString(Tag.StudyInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SeriesInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SOPInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SOPClassUID, "UI", MR_IMAGE_STORAGE);

        obj.initFileMetaInformation(MR_IMAGE_STORAGE, UIDUtils.createUID(), IMPLICIT_VR_LE);

        byte[] bytes = converter.dicomObjectToByteArray(obj);
        Path file = outputDir.resolve("test-dicom-input-2.dcm");
        Files.write(file, bytes);
        System.out.println("Created: " + file + " (" + bytes.length + " bytes) - MR/Smith^Jane/PAT002");
    }

    private void createUsFile(Path outputDir) throws IOException {
        Dcm5DicomConverter converter = new Dcm5DicomConverter();
        Dcm5DicomObject obj = (Dcm5DicomObject) converter.createDicomObject();

        obj.putString(Tag.PatientName, "PN", "Brown^Bob");
        obj.putString(Tag.PatientID, "LO", "PAT003");
        obj.putString(Tag.Modality, "CS", "US");
        obj.putString(Tag.StudyInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SeriesInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SOPInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SOPClassUID, "UI", US_IMAGE_STORAGE);

        obj.initFileMetaInformation(US_IMAGE_STORAGE, UIDUtils.createUID(), IMPLICIT_VR_LE);

        byte[] bytes = converter.dicomObjectToByteArray(obj);
        Path file = outputDir.resolve("test-dicom-input-3.dcm");
        Files.write(file, bytes);
        System.out.println("Created: " + file + " (" + bytes.length + " bytes) - US/Brown^Bob/PAT003");
    }
}
