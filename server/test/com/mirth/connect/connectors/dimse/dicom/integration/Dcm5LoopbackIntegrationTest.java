// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.mirth.connect.connectors.dimse.dicom.OieDicomElement;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomConverter;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomObject;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.server.channel.SourceConnector;

/**
 * Integration tests verifying dcm5 sender -> dcm5 receiver over real TCP sockets.
 * These are the highest-value tests for the dcm4che5 backend — they prove the
 * full DICOM C-STORE lifecycle works end-to-end.
 */
public class Dcm5LoopbackIntegrationTest extends DicomIntegrationTestBase {

    @Test
    public void testSendSingleFile() throws Exception {
        int port = allocatePort();
        ArgumentCaptor<RawMessage> captor = ArgumentCaptor.forClass(RawMessage.class);
        SourceConnector mockConnector = createMockSourceConnector(captor);

        receiver = startDcm5Receiver(port, mockConnector);
        File dicomFile = createDicomTempFile("Doe^John", "PAT001");
        sender = configureDcm5Sender(port, dicomFile);

        CapturingDimseRspHandler handler = new CapturingDimseRspHandler(1);
        sender.open();
        sender.send(handler);
        assertTrue("DIMSE response not received within timeout", handler.awaitResponses(5000));
        sender.close();

        // Verify receiver got the message
        RawMessage received = captor.getValue();
        assertNotNull(received);
        assertNotNull(received.getRawBytes());
        assertTrue(received.getRawBytes().length > 0);

        // Parse received bytes and verify DICOM data integrity
        Dcm5DicomConverter converter = new Dcm5DicomConverter();
        OieDicomObject parsed = converter.byteArrayToDicomObject(received.getRawBytes(), false);
        assertEquals("Doe^John", parsed.getString(Tag.PatientName));
        assertEquals("PAT001", parsed.getString(Tag.PatientID));

        // Verify sender got a success response
        assertEquals(1, handler.getStatuses().size());
        assertEquals(Integer.valueOf(0), handler.getStatuses().get(0));
    }

    @Test
    public void testSourceMapFromRealAssociation() throws Exception {
        int port = allocatePort();
        ArgumentCaptor<RawMessage> captor = ArgumentCaptor.forClass(RawMessage.class);
        SourceConnector mockConnector = createMockSourceConnector(captor);

        receiver = startDcm5Receiver(port, mockConnector);
        File dicomFile = createDicomTempFile("Smith^Jane", "PAT002");
        sender = configureDcm5Sender(port, dicomFile);

        CapturingDimseRspHandler handler = new CapturingDimseRspHandler(1);
        sender.open();
        sender.send(handler);
        assertTrue("DIMSE response not received within timeout", handler.awaitResponses(5000));
        sender.close();

        RawMessage received = captor.getValue();
        Map<String, Object> sourceMap = received.getSourceMap();
        assertNotNull(sourceMap);

        // Verify key sourceMap entries from a real association (not mocked)
        assertNotNull("localApplicationEntityTitle", sourceMap.get("localApplicationEntityTitle"));
        assertEquals("TEST_SCU", sourceMap.get("remoteApplicationEntityTitle"));
        assertNotNull("localAddress should be set", sourceMap.get("localAddress"));
        assertEquals(port, sourceMap.get("localPort"));
        assertNotNull("remoteAddress should be set", sourceMap.get("remoteAddress"));
        assertNotNull("remotePort should be set", sourceMap.get("remotePort"));

        // Association metadata from a real DICOM handshake
        assertNotNull("associateRQImplClassUID should be set", sourceMap.get("associateRQImplClassUID"));
    }

    @Test
    public void testSendMultipleFiles() throws Exception {
        int port = allocatePort();
        ArgumentCaptor<RawMessage> captor = ArgumentCaptor.forClass(RawMessage.class);
        SourceConnector mockConnector = createMockSourceConnector(captor);

        receiver = startDcm5Receiver(port, mockConnector);

        File file1 = createDicomTempFile("Alpha^Patient", "MULTI001");
        File file2 = createDicomTempFile("Beta^Patient", "MULTI002");
        File file3 = createDicomTempFile("Gamma^Patient", "MULTI003");

        sender = configureDcm5Sender(port, file1, file2, file3);

        CapturingDimseRspHandler handler = new CapturingDimseRspHandler(3);
        sender.open();
        sender.send(handler);
        assertTrue("All DIMSE responses not received within timeout", handler.awaitResponses(10000));
        sender.close();

        // Verify all 3 messages were dispatched
        List<RawMessage> allMessages = captor.getAllValues();
        assertEquals(3, allMessages.size());

        // Verify each message has valid DICOM data
        Dcm5DicomConverter converter = new Dcm5DicomConverter();
        List<String> receivedNames = new ArrayList<>();
        for (RawMessage msg : allMessages) {
            assertNotNull(msg.getRawBytes());
            OieDicomObject parsed = converter.byteArrayToDicomObject(msg.getRawBytes(), false);
            receivedNames.add(parsed.getString(Tag.PatientName));
        }

        assertTrue("Should contain Alpha^Patient", receivedNames.contains("Alpha^Patient"));
        assertTrue("Should contain Beta^Patient", receivedNames.contains("Beta^Patient"));
        assertTrue("Should contain Gamma^Patient", receivedNames.contains("Gamma^Patient"));

        // All 3 should have success status
        assertEquals(3, handler.getStatuses().size());
        for (int status : handler.getStatuses()) {
            assertEquals(0, status);
        }
    }

    @Test
    public void testFullLifecycleNoExceptions() throws Exception {
        int port = allocatePort();
        ArgumentCaptor<RawMessage> captor = ArgumentCaptor.forClass(RawMessage.class);
        SourceConnector mockConnector = createMockSourceConnector(captor);

        receiver = startDcm5Receiver(port, mockConnector);
        File dicomFile = createDicomTempFile("Lifecycle^Test", "LIFE001");
        sender = configureDcm5Sender(port, dicomFile);

        CapturingDimseRspHandler handler = new CapturingDimseRspHandler(1);
        sender.open();
        sender.send(handler);
        assertTrue("DIMSE response not received", handler.awaitResponses(5000));
        sender.close();
        sender.stop();
        sender = null;

        receiver.stop();
        receiver = null;
    }

    @Test
    public void testSendWithSequenceAndFragmentData() throws Exception {
        int port = allocatePort();
        ArgumentCaptor<RawMessage> captor = ArgumentCaptor.forClass(RawMessage.class);
        SourceConnector mockConnector = createMockSourceConnector(captor);

        receiver = startDcm5Receiver(port, mockConnector);

        // Build a DICOM object with a nested sequence and encapsulated pixel data fragments
        Dcm5DicomConverter converter = new Dcm5DicomConverter();
        Dcm5DicomObject obj = (Dcm5DicomObject) converter.createDicomObject();
        obj.putString(Tag.PatientName, "PN", "Complex^Data");
        obj.putString(Tag.PatientID, "LO", "SEQ001");
        obj.putString(Tag.Modality, "CS", "CT");
        obj.putString(Tag.StudyInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SeriesInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SOPInstanceUID, "UI", UIDUtils.createUID());

        // Add a Referenced Study Sequence with a nested item
        OieDicomElement seq = obj.putSequence(Tag.ReferencedStudySequence);
        OieDicomObject seqItem = converter.createDicomObject();
        seqItem.putString(Tag.ReferencedSOPClassUID, "UI", "1.2.840.10008.3.1.2.3.1");
        seqItem.putString(Tag.ReferencedSOPInstanceUID, "UI", UIDUtils.createUID());
        seq.addDicomObject(seqItem);

        // Add encapsulated pixel data (OB fragments with small synthetic frames)
        OieDicomElement frags = obj.putFragments(Tag.PixelData, "OB", false, 3);
        frags.addFragment(new byte[0]); // offset table (empty)
        frags.addFragment(new byte[] { (byte) 0xFF, (byte) 0xD8, 0x01, 0x02 }); // frame 1
        frags.addFragment(new byte[] { (byte) 0xFF, (byte) 0xD8, 0x03, 0x04 }); // frame 2

        obj.initFileMetaInformation(
                "1.2.840.10008.5.1.4.1.1.2",
                UIDUtils.createUID(),
                "1.2.840.10008.1.2");
        byte[] bytes = converter.dicomObjectToByteArray(obj);
        File tempFile = tempFolder.newFile("complex.dcm");
        Files.write(tempFile.toPath(), bytes);

        sender = configureDcm5Sender(port, tempFile);

        CapturingDimseRspHandler handler = new CapturingDimseRspHandler(1);
        sender.open();
        sender.send(handler);
        assertTrue("DIMSE response not received", handler.awaitResponses(5000));
        sender.close();

        // Verify complex data survived the network round-trip
        RawMessage received = captor.getValue();
        assertNotNull(received);
        Dcm5DicomConverter parseConverter = new Dcm5DicomConverter();
        OieDicomObject parsed = parseConverter.byteArrayToDicomObject(received.getRawBytes(), false);
        assertEquals("Complex^Data", parsed.getString(Tag.PatientName));

        // Verify sequence survived
        OieDicomElement parsedSeq = parsed.get(Tag.ReferencedStudySequence);
        assertNotNull("Sequence should survive network transfer", parsedSeq);
        assertTrue("Sequence should have items", parsedSeq.hasItems());
        assertEquals(1, parsedSeq.countItems());
        assertEquals("1.2.840.10008.3.1.2.3.1",
                parsedSeq.getDicomObject().getString(Tag.ReferencedSOPClassUID));

        // Verify pixel data fragments survived
        OieDicomElement parsedFrags = parsed.get(Tag.PixelData);
        assertNotNull("Pixel data should survive network transfer", parsedFrags);

        assertEquals(Integer.valueOf(0), handler.getStatuses().get(0));
    }
}
