// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.dcm4che3.data.Tag;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory;
import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory.DicomLibrary;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DicomConverter;
import com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DicomSender;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomConverter;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.server.channel.SourceConnector;

/**
 * Cross-library integration tests proving that dcm4che2 and dcm4che5 can
 * interoperate at the DICOM protocol level. Both libraries use different
 * Java packages (org.dcm4che2 vs org.dcm4che3), so they coexist on the classpath.
 */
public class CrossLibraryIntegrationTest extends DicomIntegrationTestBase {

    private Dcm2DicomReceiver dcm2Receiver;
    private Dcm2DicomSender dcm2Sender;

    @Override
    public void tearDown() {
        if (dcm2Sender != null) {
            try { dcm2Sender.close(); } catch (Exception e) { /* ignore */ }
            try { dcm2Sender.stop(); } catch (Exception e) { /* ignore */ }
            dcm2Sender = null;
        }
        if (dcm2Receiver != null) {
            try { dcm2Receiver.stop(); } catch (Exception e) { /* ignore */ }
            dcm2Receiver = null;
        }
        super.tearDown();
    }

    @Test
    public void testDcm2SenderToDcm5Receiver() throws Exception {
        int port = allocatePort();
        ArgumentCaptor<RawMessage> captor = ArgumentCaptor.forClass(RawMessage.class);
        SourceConnector mockConnector = createMockSourceConnector(captor);

        // Start dcm5 receiver
        receiver = startDcm5Receiver(port, mockConnector);

        // Create DICOM file using dcm2 converter
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        File dcm2File = createDcm2TempFile("Dcm2^Sender", "DCM2TO5");

        // Configure dcm2 sender
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE5);
        dcm2Sender = new Dcm2DicomSender(new TestConfig());
        dcm2Sender.setRemoteHost("127.0.0.1");
        dcm2Sender.setRemotePort(port);
        dcm2Sender.setCalledAET("TEST_SCP");
        dcm2Sender.setCalling("DCM2_SCU");
        dcm2Sender.addFile(dcm2File);
        dcm2Sender.configureTransferCapability();
        dcm2Sender.start();

        dcm2Sender.open();
        dcm2Sender.send((cmd, data) -> {});
        // dcm2 sender's send() is synchronous
        dcm2Sender.close();

        // Give the receiver time to finish processing
        Thread.sleep(500);

        RawMessage received = captor.getValue();
        assertNotNull("Receiver should have dispatched a message", received);
        assertNotNull(received.getRawBytes());

        // Parse with dcm5 converter
        Dcm5DicomConverter dcm5Converter = new Dcm5DicomConverter();
        OieDicomObject parsed = dcm5Converter.byteArrayToDicomObject(received.getRawBytes(), false);
        assertEquals("Dcm2^Sender", parsed.getString(Tag.PatientName));
        assertEquals("DCM2TO5", parsed.getString(Tag.PatientID));
    }

    @Test
    public void testDcm5SenderToDcm2Receiver() throws Exception {
        int port = allocatePort();
        ArgumentCaptor<RawMessage> captor = ArgumentCaptor.forClass(RawMessage.class);
        SourceConnector mockConnector = createMockSourceConnector(captor);

        // Start dcm2 receiver
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        dcm2Receiver = new Dcm2DicomReceiver(mockConnector, new TestConfig());
        dcm2Receiver.setHostname("127.0.0.1");
        dcm2Receiver.setPort(port);
        dcm2Receiver.setAEtitle("DCM2_SCP");
        dcm2Receiver.setDestination(tempFolder.getRoot().getAbsolutePath());
        dcm2Receiver.setTransferSyntax(new String[] { "1.2.840.10008.1.2", "1.2.840.10008.1.2.1" });
        dcm2Receiver.initTransferCapability();
        dcm2Receiver.start();
        waitForPort(port, 2000);

        // Create dcm5 DICOM file and send with dcm5 sender
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE5);
        File dcm5File = createDicomTempFile("Dcm5^Sender", "DCM5TO2");
        sender = configureDcm5Sender(port, dcm5File);
        sender.setCalledAET("DCM2_SCP");

        CapturingDimseRspHandler handler = new CapturingDimseRspHandler(1);
        sender.open();
        sender.send(handler);
        assertTrue("DIMSE response not received", handler.awaitResponses(5000));
        sender.close();

        // Give dcm2 receiver time to process
        Thread.sleep(500);

        RawMessage received = captor.getValue();
        assertNotNull("dcm2 receiver should have dispatched a message", received);
        assertNotNull(received.getRawBytes());

        // Parse with dcm2 converter
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        Dcm2DicomConverter dcm2Converter = new Dcm2DicomConverter();
        OieDicomObject parsed = dcm2Converter.byteArrayToDicomObject(received.getRawBytes(), false);
        assertEquals("Dcm5^Sender", parsed.getString(Tag.PatientName));
        assertEquals("DCM5TO2", parsed.getString(Tag.PatientID));
    }

    /**
     * Creates a temp DICOM file using the dcm2 converter.
     */
    private File createDcm2TempFile(String patientName, String patientId) throws Exception {
        Dcm2DicomConverter converter = new Dcm2DicomConverter();
        OieDicomObject obj = converter.createDicomObject();
        obj.putString(Tag.PatientName, "PN", patientName);
        obj.putString(Tag.PatientID, "LO", patientId);
        // Use dcm4che2's UID generation for a pure dcm2 test artifact
        obj.putString(Tag.StudyInstanceUID, "UI", org.dcm4che2.util.UIDUtils.createUID());
        obj.putString(Tag.SeriesInstanceUID, "UI", org.dcm4che2.util.UIDUtils.createUID());
        obj.putString(Tag.SOPInstanceUID, "UI", org.dcm4che2.util.UIDUtils.createUID());
        obj.putString(Tag.Modality, "CS", "CT");
        obj.initFileMetaInformation(
                "1.2.840.10008.5.1.4.1.1.2",
                org.dcm4che2.util.UIDUtils.createUID(),
                "1.2.840.10008.1.2");
        byte[] bytes = converter.dicomObjectToByteArray(obj);
        File tempFile = tempFolder.newFile(patientId + ".dcm");
        Files.write(tempFile.toPath(), bytes);
        return tempFile;
    }
}
