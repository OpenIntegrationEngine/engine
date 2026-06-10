// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.junit.Test;

import com.mirth.connect.connectors.dimse.DICOMDispatcher.CommandDataDimseRSPHandler;
import com.mirth.connect.connectors.dimse.dicom.DicomConstants;
import com.mirth.connect.connectors.dimse.dicom.OieDicomElement;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;
import com.mirth.connect.connectors.dimse.dicom.OieDimseRspHandler;
import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory;
import com.mirth.connect.connectors.dimse.dicom.OieDicomConverter;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.model.message.attachment.AttachmentHandlerProvider;
import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.donkey.util.DonkeyElement.DonkeyElementException;
import com.mirth.connect.server.attachments.dicom.DICOMAttachmentHandlerProvider;
import com.mirth.connect.server.controllers.MessageController;

public class DICOMDispatcherTest {

    @Test
    public void testSendWithStatusCodes() {
        // send message using our custom sender
        TestDICOMDispatcher dispatcher = new TestDICOMDispatcher();
        dispatcher.configuration = new DefaultDICOMConfiguration();
        DICOMDispatcherProperties props = new DICOMDispatcherProperties();
        props.setHost("host");
        props.setPort("9000");
        ConnectorMessage message = new ConnectorMessage();

        Response response = null;
        Status status = null;
        String statusMessage = null;

        TestDicomSender.setCommitSucceeded(true);
        TestDicomSender.setCmdStatus(0);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();

        // check with 0 status
        assertEquals(Status.SENT, status);
        assertEquals("DICOM message successfully sent", statusMessage);

        // check with 0xB000 || 0xB006 || 0xB007 status
        TestDicomSender.setCmdStatus(0xB000);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();
        assertEquals(Status.SENT, status);
        assertEquals("DICOM message successfully sent with warning status code: 0x" + DicomConstants.shortToHex(0xB000), statusMessage);

        TestDicomSender.setCmdStatus(0xB006);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();
        assertEquals(Status.SENT, status);
        assertEquals("DICOM message successfully sent with warning status code: 0x" + DicomConstants.shortToHex(0xB006), statusMessage);

        TestDicomSender.setCmdStatus(0xB007);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();
        assertEquals(Status.SENT, status);
        assertEquals("DICOM message successfully sent with warning status code: 0x" + DicomConstants.shortToHex(0xB007), statusMessage);

        // check other status == QUEUED
        TestDicomSender.setCmdStatus(0xB008);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();
        assertEquals(Status.QUEUED, status);
        assertEquals("Error status code received from DICOM server: 0x" + DicomConstants.shortToHex(0xB008), statusMessage);
    }

    @Test
    public void testResponseData() throws DonkeyElementException {
        // send message using our custom sender
        TestDICOMDispatcher dispatcher = new TestDICOMDispatcher();
        dispatcher.configuration = new DefaultDICOMConfiguration();
        DICOMDispatcherProperties props = new DICOMDispatcherProperties();
        props.setHost("host");
        props.setPort("9000");
        ConnectorMessage message = new ConnectorMessage();

        TestDicomSender.setCmdStatus(0);
        TestDicomSender.setCommitSucceeded(true);
        Response response = dispatcher.send(props, message);
        String responseData = response.getMessage();

        String expectedResponseString = "<dicom><tag00000900 len=\"2\" tag=\"00000900\" vr=\"IS\">0</tag00000900></dicom>";
        DonkeyElement dicom = new DonkeyElement(expectedResponseString);
        assertEquals(dicom.toXml(), responseData);
    }

    @Test
    public void testStorageCommitment() throws Exception {
        TestDICOMDispatcher dispatcher = new TestDICOMDispatcher();
        dispatcher.configuration = new DefaultDICOMConfiguration();
        DICOMDispatcherProperties props = new DICOMDispatcherProperties();
        props.setHost("host");
        props.setPort("9000");
        props.setStgcmt(true);
        ConnectorMessage message = new ConnectorMessage();

        TestDicomSender.setCmdStatus(0);
        TestDicomSender.setCommitSucceeded(false);

        Response response = null;
        Status status = null;
        String statusMessage = null;

        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();

        assertEquals(Status.QUEUED, status);
        assertEquals("DICOM message successfully sent but Storage Commitment failed with reason: Unknown", statusMessage);

        // Test the case where the stgcmt request succeeds but contains failed SOP items
        TestDicomSender.setCommitSucceeded(true);
        TestDicomSender.setFailedSOP(true);
        TestDicomSender.setFailureReason(1);

        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();

        assertEquals(Status.QUEUED, status);
        assertEquals("DICOM message successfully sent but Storage Commitment failed with reason: 1", statusMessage);

        TestDicomSender.setCommitSucceeded(false);
        TestDicomSender.setFailedSOP(false);
        TestDicomSender.setFailureReason(0);

        // test that a failed storage commitment doesn't cause the message to fail
        // if the dispatcher isn't configured to care
        props.setStgcmt(false);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();

        assertEquals(Status.SENT, status);
        assertEquals("DICOM message successfully sent", statusMessage);

        // check with 0xB000 and requesting storage commitment
        props.setStgcmt(true);
        TestDicomSender.setCmdStatus(0xB000);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();
        assertEquals(Status.QUEUED, status);
        String expectedMessage = "DICOM message successfully sent with warning status code: 0x" + DicomConstants.shortToHex(0xB000) + " but Storage Commitment failed with reason: Unknown";
        assertEquals(expectedMessage, statusMessage);

        // check other status and requesting storage commitment
        TestDicomSender.setCmdStatus(0xB008);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();
        assertEquals(Status.QUEUED, status);
        assertEquals("Error status code received from DICOM server: 0x" + DicomConstants.shortToHex(0xB008), statusMessage);
    }

    /**
     * Test OieDicomSender that stubs out all network operations.
     */
    private static class TestDicomSender implements OieDicomSender {
        private static int cmdStatus;
        private static boolean commitSucceeded = true;
        private static boolean failedSOP = false;
        private static int failureReason = 0;
        private boolean storageCommitment = false;

        public static void setCmdStatus(int status) {
            cmdStatus = status;
        }

        public static void setCommitSucceeded(boolean succeeded) {
            commitSucceeded = succeeded;
        }

        public static void setFailedSOP(boolean failedSOP) {
            TestDicomSender.failedSOP = failedSOP;
        }

        public static void setFailureReason(int failureReason) {
            TestDicomSender.failureReason = failureReason;
        }

        @Override
        public void send(OieDimseRspHandler handler) {
            OieDicomConverter converter = DicomLibraryFactory.getConverter();
            OieDicomObject cmd = converter.createDicomObject();
            cmd.putInt(DicomConstants.TAG_STATUS, DicomConstants.VR_IS, cmdStatus);
            handler.onDimseRSP(cmd, null);
        }

        @Override
        public OieDicomObject waitForStgCmtResult() throws InterruptedException {
            OieDicomConverter converter = DicomLibraryFactory.getConverter();
            OieDicomObject rsp = converter.createDicomObject();
            if (failedSOP) {
                OieDicomElement failedSOPSq = rsp.putSequence(DicomConstants.TAG_FAILED_SOP_SEQUENCE);
                OieDicomObject failedSOPItem = converter.createDicomObject();
                failedSOPItem.putInt(DicomConstants.TAG_FAILURE_REASON, DicomConstants.VR_IS, failureReason);
                failedSOPSq.addDicomObject(failedSOPItem);
            }
            return rsp;
        }

        @Override public boolean commit() { return commitSucceeded; }
        @Override public boolean isStorageCommitment() { return storageCommitment; }
        @Override public void setCalledAET(String aet) {}
        @Override public void setRemoteHost(String host) {}
        @Override public void setRemotePort(int port) {}
        @Override public void setCalling(String aet) {}
        @Override public void setLocalHost(String host) {}
        @Override public void setLocalPort(int port) {}
        @Override public void addFile(File file) {}
        @Override public void setAcceptTimeout(int timeout) {}
        @Override public void setMaxOpsInvoked(int maxOps) {}
        @Override public void setTranscoderBufferSize(int size) {}
        @Override public void setConnectTimeout(int timeout) {}
        @Override public void setPriority(int priority) {}
        @Override public void setPackPDV(boolean packPDV) {}
        @Override public void setMaxPDULengthReceive(int length) {}
        @Override public void setMaxPDULengthSend(int length) {}
        @Override public void setReceiveBufferSize(int size) {}
        @Override public void setSendBufferSize(int size) {}
        @Override public void setAssociationReaperPeriod(int period) {}
        @Override public void setReleaseTimeout(int timeout) {}
        @Override public void setDimseRspTimeout(int timeout) {}
        @Override public void setShutdownDelay(int delay) {}
        @Override public void setSocketCloseDelay(int delay) {}
        @Override public void setTcpNoDelay(boolean tcpNoDelay) {}
        @Override public void setOfferDefaultTransferSyntaxInSeparatePresentationContext(boolean ts1) {}
        @Override public void setStorageCommitment(boolean stgcmt) { this.storageCommitment = stgcmt; }
        @Override public void setUserIdentity(String username, String passcode, boolean positiveResponseRequested) {}
        @Override public void setTlsWithoutEncryption() {}
        @Override public void setTls3DES_EDE_CBC() {}
        @Override public void setTlsAES_128_CBC() {}
        @Override public void setTrustStoreURL(String url) {}
        @Override public void setTrustStorePassword(String password) {}
        @Override public void setKeyPassword(String password) {}
        @Override public void setKeyStoreURL(String url) {}
        @Override public void setKeyStorePassword(String password) {}
        @Override public void setTlsNeedClientAuth(boolean needClientAuth) {}
        @Override public void setTlsProtocol(String[] protocols) {}
        @Override public void initTLS() {}
        @Override public void configureTransferCapability() {}
        @Override public void start() {}
        @Override public void open() {}
        @Override public void close() {}
        @Override public void stop() {}
        @Override public Object unwrap() { return null; }
    }

    private class TestDICOMDispatcher extends DICOMDispatcher {
        @Override
        protected OieDicomSender createDicomSender(DICOMConfiguration configuration) {
            return new TestDicomSender();
        }

        @Override
        protected AttachmentHandlerProvider getAttachmentHandlerProvider() {
            return new TestAttachmentHandlerProvider(null);
        }
    }

    private class TestAttachmentHandlerProvider extends DICOMAttachmentHandlerProvider {
        public TestAttachmentHandlerProvider(MessageController messageController) {
            super(messageController);
        }

        @Override
        public byte[] reAttachMessage(String raw, ConnectorMessage connectorMessage, String charsetEncoding, boolean binary, boolean reattach) {
            return "".getBytes();
        }
    }
}
