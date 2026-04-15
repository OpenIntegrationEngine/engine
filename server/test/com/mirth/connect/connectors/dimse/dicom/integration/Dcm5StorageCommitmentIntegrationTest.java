package com.mirth.connect.connectors.dimse.dicom.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Commands;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.AbstractDicomService;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.junit.Test;

import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;

/**
 * Integration test for the dcm5 sender's storage commitment protocol flow.
 *
 * <p>Uses a custom test SCP (not Dcm5DicomReceiver) that handles both C-STORE
 * and N-ACTION (Storage Commitment), then responds with N-EVENT-REPORT. This
 * tests the sender's commit() and waitForStgCmtResult() methods end-to-end.
 */
public class Dcm5StorageCommitmentIntegrationTest extends DicomIntegrationTestBase {

    private Device scpDevice;
    private ExecutorService scpExecutor;
    private ScheduledExecutorService scpScheduled;

    @Override
    public void tearDown() {
        if (scpDevice != null) {
            scpDevice.unbindConnections();
        }
        if (scpExecutor != null) {
            scpExecutor.shutdownNow();
        }
        if (scpScheduled != null) {
            scpScheduled.shutdownNow();
        }
        super.tearDown();
    }

    @Test
    public void testStorageCommitmentEndToEnd() throws Exception {
        int port = allocatePort();

        // Build a test SCP that accepts C-STORE and handles storage commitment N-ACTION
        startStgCmtScp(port);

        File dicomFile = createDicomTempFile("StgCmt^Test", "STGCMT001");

        // Build sender manually (not via helper) so we can enable storage commitment before start()
        sender = new com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomSender(new TestConfig());
        sender.setRemoteHost("127.0.0.1");
        sender.setRemotePort(port);
        sender.setCalledAET("STGCMT_SCP");
        sender.setCalling("TEST_SCU");
        sender.setStorageCommitment(true);
        sender.addFile(dicomFile);
        sender.configureTransferCapability();
        sender.start();

        CapturingDimseRspHandler handler = new CapturingDimseRspHandler(1);
        sender.open();
        sender.send(handler);
        assertTrue("C-STORE response not received", handler.awaitResponses(5000));

        // Request storage commitment
        boolean commitResult = sender.commit();
        assertTrue("commit() should return true", commitResult);

        // Wait for the N-EVENT-REPORT from the SCP
        OieDicomObject stgCmtResult = sender.waitForStgCmtResult();
        assertNotNull("Storage commitment result should not be null", stgCmtResult);

        sender.close();
    }

    /**
     * Starts a raw dcm4che5 SCP that accepts C-STORE and responds to
     * Storage Commitment N-ACTION by sending back an N-EVENT-REPORT with success.
     */
    private void startStgCmtScp(int port) throws Exception {
        scpDevice = new Device("STGCMT_SCP");
        Connection conn = new Connection();
        conn.setHostname("127.0.0.1");
        conn.setPort(port);
        scpDevice.addConnection(conn);

        ApplicationEntity ae = new ApplicationEntity("STGCMT_SCP");
        ae.setAssociationAcceptor(true);
        ae.addConnection(conn);
        scpDevice.addApplicationEntity(ae);

        // Accept any SOP class for C-STORE, plus Verification and Storage Commitment
        ae.addTransferCapability(new TransferCapability(null, "*",
                TransferCapability.Role.SCP, "1.2.840.10008.1.2", "1.2.840.10008.1.2.1"));
        ae.addTransferCapability(new TransferCapability(null, UID.Verification,
                TransferCapability.Role.SCP, UID.ImplicitVRLittleEndian));
        ae.addTransferCapability(new TransferCapability(null, UID.StorageCommitmentPushModel,
                TransferCapability.Role.SCP, UID.ImplicitVRLittleEndian));

        DicomServiceRegistry services = new DicomServiceRegistry();
        services.addDicomService(new BasicCEchoSCP());

        // C-STORE handler — just accept everything
        services.addDicomService(new BasicCStoreSCP("*") {
            @Override
            protected void store(Association as, PresentationContext pc, Attributes rq,
                                  PDVInputStream data, Attributes rsp) throws IOException {
                // Read and discard the data stream to complete the transfer
                data.skipAll();
            }
        });

        // Storage Commitment N-ACTION handler — respond with success N-EVENT-REPORT
        services.addDicomService(new AbstractDicomService(UID.StorageCommitmentPushModel) {
            @Override
            public void onDimseRQ(Association as, PresentationContext pc,
                                   Dimse dimse, Attributes rq, Attributes data) throws IOException {
                if (dimse == Dimse.N_ACTION_RQ) {
                    // Send N-ACTION response (success)
                    Attributes actionRsp = Commands.mkNActionRSP(rq, Status.Success);
                    as.tryWriteDimseRSP(pc, actionRsp);

                    // Now send N-EVENT-REPORT back to the sender with the committed references
                    try {
                        Attributes eventInfo = new Attributes();
                        eventInfo.setString(Tag.TransactionUID, VR.UI,
                                data.getString(Tag.TransactionUID));

                        // Copy the ReferencedSOPSequence from the action data
                        org.dcm4che3.data.Sequence srcSeq = data.getSequence(Tag.ReferencedSOPSequence);
                        if (srcSeq != null) {
                            org.dcm4che3.data.Sequence destSeq =
                                    eventInfo.newSequence(Tag.ReferencedSOPSequence, srcSeq.size());
                            for (Attributes item : srcSeq) {
                                destSeq.add(new Attributes(item));
                            }
                        }

                        // Open a reverse association to deliver N-EVENT-REPORT
                        // In dcm4che5, the SCP typically sends N-EVENT-REPORT on a new association
                        // to the SCU. But some implementations send it on the same association.
                        // For simplicity, send on the existing association using the same PC.
                        as.neventReport(
                                UID.StorageCommitmentPushModel,
                                UID.StorageCommitmentPushModelInstance,
                                1, // eventTypeID = 1 (Storage Commitment Request Successful)
                                eventInfo, UID.ImplicitVRLittleEndian,
                                new org.dcm4che3.net.DimseRSPHandler(as.nextMessageID()));
                    } catch (Exception e) {
                        throw new IOException("Failed to send N-EVENT-REPORT", e);
                    }
                }
            }
        });

        scpDevice.setDimseRQHandler(services);

        scpExecutor = Executors.newCachedThreadPool();
        scpScheduled = Executors.newSingleThreadScheduledExecutor();
        scpDevice.setExecutor(scpExecutor);
        scpDevice.setScheduledExecutor(scpScheduled);
        scpDevice.bindConnections();
        waitForPort(port, 2000);
    }
}
