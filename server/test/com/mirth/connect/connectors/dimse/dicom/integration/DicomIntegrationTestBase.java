package com.mirth.connect.connectors.dimse.dicom.integration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.connectors.dimse.DICOMDispatcher;
import com.mirth.connect.connectors.dimse.DICOMDispatcherProperties;
import com.mirth.connect.connectors.dimse.DICOMReceiver;
import com.mirth.connect.connectors.dimse.DICOMReceiverProperties;
import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory;
import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory.DicomLibrary;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;
import com.mirth.connect.connectors.dimse.dicom.OieDimseRspHandler;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomConverter;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomObject;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomSender;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.channel.Connector;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.SourceConnector;

/**
 * Shared base class for DICOM integration tests. Provides port allocation,
 * temp DICOM file creation, mock setup, and receiver/sender lifecycle helpers.
 */
public abstract class DicomIntegrationTestBase {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    protected Dcm5DicomReceiver receiver;
    protected Dcm5DicomSender sender;

    @Before
    public void setUpFactory() {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE5);
    }

    @After
    public void tearDown() {
        if (sender != null) {
            try { sender.close(); } catch (Exception e) { /* ignore */ }
            try { sender.stop(); } catch (Exception e) { /* ignore */ }
            sender = null;
        }
        if (receiver != null) {
            try { receiver.stop(); } catch (Exception e) { /* ignore */ }
            receiver = null;
        }
        DicomLibraryFactory.resetForTesting(null);
    }

    /**
     * Allocate a free port using ServerSocket(0). The tiny TOCTOU race
     * between close and re-bind is acceptable for localhost integration tests.
     */
    protected int allocatePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setReuseAddress(true);
            return ss.getLocalPort();
        }
    }

    /**
     * Creates a temp DICOM file with the given patient data, FMI (CT Image Storage,
     * Implicit VR Little Endian), and a generated SOP Instance UID.
     */
    protected File createDicomTempFile(String patientName, String patientId) throws IOException {
        Dcm5DicomConverter converter = new Dcm5DicomConverter();
        Dcm5DicomObject obj = (Dcm5DicomObject) converter.createDicomObject();
        obj.putString(Tag.PatientName, "PN", patientName);
        obj.putString(Tag.PatientID, "LO", patientId);
        obj.putString(Tag.StudyInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SeriesInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.SOPInstanceUID, "UI", UIDUtils.createUID());
        obj.putString(Tag.Modality, "CS", "CT");
        // CT Image Storage SOP Class, Implicit VR Little Endian
        obj.initFileMetaInformation(
                "1.2.840.10008.5.1.4.1.1.2",
                UIDUtils.createUID(),
                "1.2.840.10008.1.2");
        byte[] bytes = converter.dicomObjectToByteArray(obj);
        File tempFile = tempFolder.newFile(patientId + ".dcm");
        Files.write(tempFile.toPath(), bytes);
        return tempFile;
    }

    /**
     * Creates a Mockito mock of SourceConnector wired for DICOM receiver integration tests.
     * The captor captures RawMessage objects passed to dispatchRawMessage().
     */
    protected SourceConnector createMockSourceConnector(ArgumentCaptor<RawMessage> captor) throws Exception {
        Channel mockChannel = mock(Channel.class);
        when(mockChannel.getName()).thenReturn("testChannel");

        SourceConnector mockConnector = mock(SourceConnector.class);
        when(mockConnector.getChannel()).thenReturn(mockChannel);
        when(mockConnector.getChannelId()).thenReturn("testChannelId");

        DispatchResult mockResult = mock(DispatchResult.class);
        when(mockResult.getSelectedResponse()).thenReturn(null);
        when(mockConnector.dispatchRawMessage(captor.capture())).thenReturn(mockResult);

        return mockConnector;
    }

    /**
     * Creates and starts a Dcm5DicomReceiver on the given port with standard config.
     */
    protected Dcm5DicomReceiver startDcm5Receiver(int port, SourceConnector mockConnector) throws Exception {
        Dcm5DicomReceiver rcv = new Dcm5DicomReceiver(mockConnector, new TestConfig());
        rcv.setHostname("127.0.0.1");
        rcv.setPort(port);
        rcv.setAEtitle("TEST_SCP");
        rcv.setTransferSyntax(new String[] { "1.2.840.10008.1.2", "1.2.840.10008.1.2.1" });
        rcv.initTransferCapability();
        rcv.start();
        waitForPort(port, 2000);
        return rcv;
    }

    /**
     * Creates and starts a Dcm5DicomSender configured to connect to localhost:port.
     */
    protected Dcm5DicomSender configureDcm5Sender(int port, File... dicomFiles) throws Exception {
        Dcm5DicomSender snd = new Dcm5DicomSender(new TestConfig());
        snd.setRemoteHost("127.0.0.1");
        snd.setRemotePort(port);
        snd.setCalledAET("TEST_SCP");
        snd.setCalling("TEST_SCU");
        for (File f : dicomFiles) {
            snd.addFile(f);
        }
        snd.configureTransferCapability();
        snd.start();
        return snd;
    }

    /**
     * Polls until a TCP connection to localhost:port succeeds, or timeout expires.
     */
    protected void waitForPort(int port, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                new java.net.Socket("127.0.0.1", port).close();
                return;
            } catch (IOException e) {
                Thread.sleep(50);
            }
        }
        throw new IOException("Port " + port + " not ready after " + timeoutMs + "ms");
    }

    /**
     * DIMSE response handler that captures responses and provides a latch for synchronization.
     * dcm4che5's cstore() is async — the response handler fires on a worker thread after
     * send() returns. Use awaitResponses() to block until all expected responses arrive.
     */
    protected static class CapturingDimseRspHandler implements OieDimseRspHandler {
        private final CountDownLatch latch;
        private final List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        public CapturingDimseRspHandler(int expectedResponses) {
            this.latch = new CountDownLatch(expectedResponses);
        }

        @Override
        public void onDimseRSP(OieDicomObject cmd, OieDicomObject data) {
            if (cmd != null) {
                statuses.add(cmd.getInt(Tag.Status));
            }
            latch.countDown();
        }

        public boolean awaitResponses(long timeoutMs) throws InterruptedException {
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        public List<Integer> getStatuses() {
            return statuses;
        }
    }

    /**
     * Minimal DICOMConfiguration for testing — no ControllerFactory dependencies.
     */
    protected static class TestConfig implements DICOMConfiguration {
        @Override
        public void configureConnectorDeploy(Connector connector) {}

        @Override
        public void configureReceiver(OieDicomReceiver receiver, DICOMReceiver connector,
                DICOMReceiverProperties connectorProperties) {}

        @Override
        public void configureSender(OieDicomSender sender, DICOMDispatcher connector,
                DICOMDispatcherProperties connectorProperties) {}

        @Override
        public Map<String, Object> getCStoreRequestInformation(Object association) {
            return new HashMap<>();
        }
    }
}
