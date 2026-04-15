package com.mirth.connect.connectors.dimse.dicom.integration;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.server.channel.SourceConnector;

/**
 * Integration test verifying that the dcm5 receiver responds to C-ECHO requests.
 * Uses raw dcm4che5 API to send C-ECHO because OieDicomSender only supports C-STORE.
 */
public class Dcm5CEchoIntegrationTest extends DicomIntegrationTestBase {

    @Test
    public void testCEchoSuccess() throws Exception {
        int port = allocatePort();
        ArgumentCaptor<RawMessage> captor = ArgumentCaptor.forClass(RawMessage.class);
        SourceConnector mockConnector = createMockSourceConnector(captor);

        receiver = startDcm5Receiver(port, mockConnector);

        // Build a raw dcm4che5 SCU to send C-ECHO
        Device echoDevice = new Device("ECHO_SCU");
        Connection echoConn = new Connection();
        echoDevice.addConnection(echoConn);
        ApplicationEntity echoAE = new ApplicationEntity("ECHO_SCU");
        echoAE.addConnection(echoConn);
        echoDevice.addApplicationEntity(echoAE);
        ExecutorService executor = Executors.newCachedThreadPool();
        ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor();
        echoDevice.setExecutor(executor);
        echoDevice.setScheduledExecutor(scheduled);

        Connection remoteConn = new Connection();
        remoteConn.setHostname("127.0.0.1");
        remoteConn.setPort(port);

        AAssociateRQ aarq = new AAssociateRQ();
        aarq.setCalledAET("TEST_SCP");
        aarq.setCallingAET("ECHO_SCU");
        aarq.addPresentationContext(
                new PresentationContext(1, UID.Verification, UID.ImplicitVRLittleEndian));

        Association as = null;
        try {
            as = echoAE.connect(echoConn, remoteConn, aarq);
            DimseRSP rsp = as.cecho();
            rsp.next();
            int status = rsp.getCommand().getInt(Tag.Status, -1);
            assertEquals("C-ECHO should return success status", 0, status);
        } finally {
            if (as != null) {
                try { as.release(); } catch (Exception e) { /* ignore */ }
            }
            echoDevice.unbindConnections();
            executor.shutdownNow();
            scheduled.shutdownNow();
        }
    }
}
