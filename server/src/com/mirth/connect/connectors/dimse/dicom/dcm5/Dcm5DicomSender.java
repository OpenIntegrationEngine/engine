/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom.dcm5;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.UIDUtils;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Commands;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.DataWriterAdapter;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.UserIdentityRQ;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;
import com.mirth.connect.connectors.dimse.dicom.OieDimseRspHandler;

/**
 * dcm4che5 implementation of OieDicomSender. Composes from Device + Connection +
 * ApplicationEntity + Association instead of delegating to a monolithic DcmSnd tool class.
 */
public class Dcm5DicomSender implements OieDicomSender {

    private static final Logger logger = LogManager.getLogger(Dcm5DicomSender.class);

    private final DICOMConfiguration configuration;
    private final Device device;
    private final Connection localConn;
    private final Connection remoteConn;
    private final ApplicationEntity localAE;
    private final ApplicationEntity remoteAE;
    private Association association;

    private final List<FileInfo> files = new ArrayList<>();
    private final Map<String, Set<String>> sopClassToTsMap = new HashMap<>();
    private boolean storageCommitment;
    private int priority = 0;
    private boolean offerDefaultTsInSeparatePC = false;
    private UserIdentityRQ userIdentityRQ;
    private int shutdownDelay = 1000;

    private ExecutorService executor;
    private ScheduledExecutorService scheduledExecutor;

    // TLS config stored for deferred initTLS()
    private String keyStoreURL;
    private String keyStorePassword;
    private String keyPassword;
    private String keyStoreType;
    private String trustStoreURL;
    private String trustStorePassword;
    private String trustStoreType;

    // Storage commitment result (set by N-EVENT-REPORT handler)
    private volatile Attributes stgCmtResult;

    /** Tracks sent file metadata for storage commitment. */
    private static final class FileInfo {
        final File file;
        String cuid;
        String iuid;
        String tsuid;
        boolean transferred;

        FileInfo(File file) {
            this.file = file;
        }
    }

    public Dcm5DicomSender(DICOMConfiguration configuration) {
        this.configuration = configuration;
        this.device = new Device("DCMSND");

        if (configuration instanceof Dcm5DICOMConfiguration) {
            this.localConn = ((Dcm5DICOMConfiguration) configuration).createDcm5Connection();
        } else {
            Object custom = configuration.createNetworkConnection();
            this.localConn = (custom instanceof Connection) ? (Connection) custom : new Connection();
        }

        this.remoteConn = new Connection();
        this.localAE = new ApplicationEntity("DCMSND");
        this.remoteAE = new ApplicationEntity();

        device.addConnection(localConn);
        localAE.addConnection(localConn);
        device.addApplicationEntity(localAE);
    }

    @Override
    public void setCalledAET(String aet) {
        remoteAE.setAETitle(aet);
    }

    @Override
    public void setRemoteHost(String host) {
        remoteConn.setHostname(host);
    }

    @Override
    public void setRemotePort(int port) {
        remoteConn.setPort(port);
    }

    @Override
    public void setCalling(String aet) {
        localAE.setAETitle(aet);
    }

    @Override
    public void setLocalHost(String host) {
        localConn.setHostname(host);
    }

    @Override
    public void setLocalPort(int port) {
        localConn.setPort(port);
    }

    @Override
    public void addFile(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addFile(child);
                }
            }
        } else {
            FileInfo info = new FileInfo(file);
            files.add(info);
            // Scan file for SOP Class UID and Transfer Syntax UID
            DicomInputStream dis = null;
            try {
                dis = new DicomInputStream(file);
                Attributes fmi = dis.readFileMetaInformation();
                if (fmi != null) {
                    info.cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
                    info.iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
                    info.tsuid = fmi.getString(Tag.TransferSyntaxUID);
                    if (info.cuid != null && info.tsuid != null) {
                        sopClassToTsMap.computeIfAbsent(info.cuid, k -> new HashSet<>()).add(info.tsuid);
                    }
                }
            } catch (Exception e) {
                logger.trace("Could not read DICOM file meta info: " + file, e);
            } finally {
                IOUtils.closeQuietly(dis);
            }
        }
    }

    @Override
    public void setAcceptTimeout(int timeout) {
        localConn.setAcceptTimeout(timeout);
    }

    @Override
    public void setMaxOpsInvoked(int maxOps) {
        localConn.setMaxOpsInvoked(maxOps);
    }

    @Override
    public void setTranscoderBufferSize(int size) {
        // dcm4che5 has no transcoder buffer — transcoding is handled internally via DataWriterAdapter.
        // Only reached when the user explicitly changed bufSize from default, so warn instead of
        // trace to surface the ignored setting.
        logger.warn("bufSize={} has no effect on dcm4che5 sender (dcm4che3 manages transcoder buffers internally). "
                + "Revert dicom.library=dcm4che2 if this tuning is load-bearing.", size);
    }

    @Override
    public void setConnectTimeout(int timeout) {
        localConn.setConnectTimeout(timeout);
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public void setPackPDV(boolean packPDV) {
        localConn.setPackPDV(packPDV);
    }

    @Override
    public void setMaxPDULengthReceive(int length) {
        localConn.setReceivePDULength(length);
    }

    @Override
    public void setMaxPDULengthSend(int length) {
        localConn.setSendPDULength(length);
    }

    @Override
    public void setReceiveBufferSize(int size) {
        localConn.setReceiveBufferSize(size);
    }

    @Override
    public void setSendBufferSize(int size) {
        localConn.setSendBufferSize(size);
    }

    @Override
    public void setAssociationReaperPeriod(int period) {
        // dcm4che5 manages association lifecycle via idle timeouts, not a reaper period.
        localConn.setIdleTimeout(period);
    }

    @Override
    public void setReleaseTimeout(int timeout) {
        localConn.setReleaseTimeout(timeout);
    }

    @Override
    public void setDimseRspTimeout(int timeout) {
        localConn.setResponseTimeout(timeout);
    }

    @Override
    public void setShutdownDelay(int delay) {
        this.shutdownDelay = delay;
    }

    @Override
    public void setSocketCloseDelay(int delay) {
        localConn.setSocketCloseDelay(delay);
    }

    @Override
    public void setTcpNoDelay(boolean tcpNoDelay) {
        localConn.setTcpNoDelay(tcpNoDelay);
    }

    @Override
    public void setOfferDefaultTransferSyntaxInSeparatePresentationContext(boolean ts1) {
        this.offerDefaultTsInSeparatePC = ts1;
    }

    @Override
    public void setStorageCommitment(boolean stgcmt) {
        this.storageCommitment = stgcmt;
    }

    @Override
    public void setUserIdentity(String username, String passcode, boolean positiveResponseRequested) {
        if (passcode != null && !passcode.isEmpty()) {
            userIdentityRQ = UserIdentityRQ.usernamePasscode(username, passcode.toCharArray(), positiveResponseRequested);
        } else {
            userIdentityRQ = UserIdentityRQ.username(username, positiveResponseRequested);
        }
    }

    @Override
    public void setTlsWithoutEncryption() {
        localConn.setTlsCipherSuites("SSL_RSA_WITH_NULL_SHA");
        remoteConn.setTlsCipherSuites("SSL_RSA_WITH_NULL_SHA");
    }

    @Override
    public void setTls3DES_EDE_CBC() {
        localConn.setTlsCipherSuites("SSL_RSA_WITH_3DES_EDE_CBC_SHA");
        remoteConn.setTlsCipherSuites("SSL_RSA_WITH_3DES_EDE_CBC_SHA");
    }

    @Override
    public void setTlsAES_128_CBC() {
        localConn.setTlsCipherSuites("TLS_RSA_WITH_AES_128_CBC_SHA");
        remoteConn.setTlsCipherSuites("TLS_RSA_WITH_AES_128_CBC_SHA");
    }

    @Override
    public void setTlsCipherSuites(String[] cipherSuites) {
        localConn.setTlsCipherSuites(cipherSuites);
        remoteConn.setTlsCipherSuites(cipherSuites);
    }

    @Override
    public void setTrustStoreURL(String url) {
        this.trustStoreURL = url;
    }

    @Override
    public void setTrustStorePassword(String password) {
        this.trustStorePassword = password;
    }

    @Override
    public void setKeyPassword(String password) {
        this.keyPassword = password;
    }

    @Override
    public void setKeyStoreURL(String url) {
        this.keyStoreURL = url;
    }

    @Override
    public void setKeyStorePassword(String password) {
        this.keyStorePassword = password;
    }

    @Override
    public void setKeyStoreType(String type) {
        this.keyStoreType = type;
    }

    @Override
    public void setTrustStoreType(String type) {
        this.trustStoreType = type;
    }

    @Override
    public void setTlsNeedClientAuth(boolean needClientAuth) {
        localConn.setTlsNeedClientAuth(needClientAuth);
    }

    @Override
    public void setTlsProtocol(String[] protocols) {
        localConn.setTlsProtocols(protocols);
        remoteConn.setTlsProtocols(protocols);
    }

    @Override
    public void initTLS() throws Exception {
        if (keyStoreURL != null) {
            device.setKeyStoreURL(keyStoreURL);
            device.setKeyStoreType(keyStoreType != null ? keyStoreType : Dcm5TlsUtil.inferStoreType(keyStoreURL));
            if (keyStorePassword != null) {
                device.setKeyStorePin(keyStorePassword);
            }
            if (keyPassword != null) {
                device.setKeyStoreKeyPin(keyPassword);
            }
        }
        if (trustStoreURL != null) {
            device.setTrustStoreURL(trustStoreURL);
            device.setTrustStoreType(trustStoreType != null ? trustStoreType : Dcm5TlsUtil.inferStoreType(trustStoreURL));
            if (trustStorePassword != null) {
                device.setTrustStorePin(trustStorePassword);
            }
        }
    }

    @Override
    public void configureTransferCapability() {
        for (Map.Entry<String, Set<String>> entry : sopClassToTsMap.entrySet()) {
            String[] tsArray = entry.getValue().toArray(new String[0]);
            localAE.addTransferCapability(
                    new TransferCapability(null, entry.getKey(), TransferCapability.Role.SCU, tsArray));

            if (offerDefaultTsInSeparatePC && !entry.getValue().contains(UID.ImplicitVRLittleEndian)) {
                localAE.addTransferCapability(
                        new TransferCapability(null, entry.getKey(), TransferCapability.Role.SCU, UID.ImplicitVRLittleEndian));
            }
        }
    }

    @Override
    public void start() throws IOException {
        executor = Executors.newCachedThreadPool();
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        device.setExecutor(executor);
        device.setScheduledExecutor(scheduledExecutor);

        // Register N-EVENT-REPORT handler for storage commitment responses
        if (storageCommitment) {
            org.dcm4che3.net.service.DicomServiceRegistry serviceRegistry = new org.dcm4che3.net.service.DicomServiceRegistry();
            serviceRegistry.addDicomService(new org.dcm4che3.net.service.AbstractDicomService(UID.StorageCommitmentPushModel) {
                @Override
                public void onDimseRQ(Association as, PresentationContext pc,
                                       org.dcm4che3.net.Dimse dimse, Attributes rq, Attributes data)
                        throws IOException {
                    if (dimse == org.dcm4che3.net.Dimse.N_EVENT_REPORT_RQ) {
                        // Send success response
                        Attributes rsp = org.dcm4che3.net.Commands.mkNEventReportRSP(rq, Status.Success);
                        as.tryWriteDimseRSP(pc, rsp);
                        // Notify waitForStgCmtResult
                        onNEventReportRSP(data);
                    }
                }
            });
            device.setDimseRQHandler(serviceRegistry);
            localAE.setAssociationAcceptor(true);
        }
    }

    @Override
    public void open() throws Exception {
        AAssociateRQ aarq = new AAssociateRQ();
        aarq.setCalledAET(remoteAE.getAETitle());
        aarq.setCallingAET(localAE.getAETitle());

        if (userIdentityRQ != null) {
            aarq.setUserIdentityRQ(userIdentityRQ);
        }

        // Add presentation contexts from transfer capabilities
        int pcid = 1;
        for (Map.Entry<String, Set<String>> entry : sopClassToTsMap.entrySet()) {
            String[] tsArray = entry.getValue().toArray(new String[0]);
            aarq.addPresentationContext(new PresentationContext(pcid, entry.getKey(), tsArray));
            pcid += 2;

            if (offerDefaultTsInSeparatePC && !entry.getValue().contains(UID.ImplicitVRLittleEndian)) {
                aarq.addPresentationContext(new PresentationContext(pcid, entry.getKey(), UID.ImplicitVRLittleEndian));
                pcid += 2;
            }
        }

        // Add storage commitment if enabled
        if (storageCommitment) {
            aarq.addPresentationContext(new PresentationContext(pcid, UID.StorageCommitmentPushModel, UID.ImplicitVRLittleEndian));
        }

        try {
            association = localAE.connect(localConn, remoteConn, aarq);
        } catch (IncompatibleConnectionException e) {
            throw new IOException("Failed to open DICOM association", e);
        }
    }

    @Override
    public void send(OieDimseRspHandler handler) throws Exception {
        for (FileInfo info : files) {
            DicomInputStream dis = null;
            try {
                dis = new DicomInputStream(info.file);
                Attributes fmi = dis.readFileMetaInformation();
                Attributes dataset = dis.readDataset(-1, -1);

                String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
                String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
                String tsuid = fmi.getString(Tag.TransferSyntaxUID);

                DimseRSPHandler rspHandler = new DimseRSPHandler(association.nextMessageID()) {
                    @Override
                    public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                        super.onDimseRSP(as, cmd, data);
                        OieDicomObject wrappedCmd = cmd != null ? new Dcm5DicomObject(cmd) : null;
                        OieDicomObject wrappedData = data != null ? new Dcm5DicomObject(data) : null;
                        handler.onDimseRSP(wrappedCmd, wrappedData);
                    }
                };

                association.cstore(cuid, iuid, priority, new DataWriterAdapter(dataset), tsuid, rspHandler);
                info.transferred = true;
            } finally {
                IOUtils.closeQuietly(dis);
            }
        }
    }

    @Override
    public boolean isStorageCommitment() {
        return storageCommitment;
    }

    @Override
    public boolean commit() throws Exception {
        if (!storageCommitment || association == null) {
            return false;
        }

        Attributes actionInfo = new Attributes();
        actionInfo.setString(Tag.TransactionUID, VR.UI, UIDUtils.createUID());

        // Build reference SOP sequence from successfully transferred files
        org.dcm4che3.data.Sequence refSOPSeq = actionInfo.newSequence(Tag.ReferencedSOPSequence, files.size());
        for (FileInfo info : files) {
            if (info.transferred && info.cuid != null && info.iuid != null) {
                Attributes refSOP = new Attributes(2);
                refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, info.cuid);
                refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, info.iuid);
                refSOPSeq.add(refSOP);
            }
        }

        try {
            stgCmtResult = null;
            org.dcm4che3.net.DimseRSP rsp = association.naction(
                    UID.StorageCommitmentPushModel,
                    UID.StorageCommitmentPushModelInstance,
                    1, actionInfo, UID.ImplicitVRLittleEndian);
            rsp.next();
            Attributes cmd = rsp.getCommand();
            int status = cmd.getInt(Tag.Status, -1);
            return status == Status.Success;
        } catch (Exception e) {
            logger.error("Failed to send Storage Commitment request", e);
            return false;
        }
    }

    @Override
    public synchronized OieDicomObject waitForStgCmtResult() throws InterruptedException {
        while (stgCmtResult == null) {
            wait();
        }
        return new Dcm5DicomObject(stgCmtResult);
    }

    /**
     * Called when the remote SCP sends an N-EVENT-REPORT with the storage commitment result.
     * Sets the result and wakes up any thread waiting in waitForStgCmtResult().
     */
    synchronized void onNEventReportRSP(Attributes info) {
        stgCmtResult = info;
        notifyAll();
    }

    @Override
    public void close() {
        if (association != null) {
            try {
                association.release();
            } catch (IOException e) {
                logger.trace("Error releasing association", e);
            }
            association = null;
        }
    }

    @Override
    public void stop() {
        device.unbindConnections();
        if (executor != null) {
            executor.shutdown();
        }
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
        try {
            if (executor != null && !executor.awaitTermination(shutdownDelay, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
            if (scheduledExecutor != null && !scheduledExecutor.awaitTermination(shutdownDelay, TimeUnit.MILLISECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            if (executor != null) executor.shutdownNow();
            if (scheduledExecutor != null) scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Object unwrap() {
        return device;
    }
}
