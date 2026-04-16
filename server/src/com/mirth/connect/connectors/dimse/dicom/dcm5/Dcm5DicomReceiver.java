/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom.dcm5;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.UserIdentityRQ;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.connectors.dimse.dicom.DicomConstants;
import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.SourceConnector;

/**
 * dcm4che5 implementation of OieDicomReceiver. Composes from Device + Connection +
 * ApplicationEntity + BasicCStoreSCP instead of delegating to a monolithic DcmRcv tool class.
 *
 * <p>The {@code onCStoreRQ} handler populates the sourceMap with the exact same keys
 * as MirthDcmRcv.onCStoreRQ to ensure behavioral parity.
 */
public class Dcm5DicomReceiver implements OieDicomReceiver {

    private static final Logger logger = LogManager.getLogger(Dcm5DicomReceiver.class);

    private final Device device;
    private final Connection conn;
    private final ApplicationEntity ae;
    private final SourceConnector sourceConnector;
    private final DICOMConfiguration dicomConfiguration;

    /**
     * Default transfer syntaxes matching dcm4che2's NON_RETIRED_LE_TS.
     * Registered for all SOP classes when no explicit restriction is configured.
     */
    private static final String[] NON_RETIRED_LE_TS = {
        DicomConstants.JPEG_LS_LOSSLESS,
        DicomConstants.JPEG_LOSSLESS_SV1,
        DicomConstants.JPEG_LOSSLESS_NH14,
        DicomConstants.JPEG_2000_LOSSLESS,
        DicomConstants.DEFLATED_EXPLICIT_VR_LITTLE_ENDIAN,
        DicomConstants.RLE_LOSSLESS,
        DicomConstants.EXPLICIT_VR_LITTLE_ENDIAN,
        DicomConstants.IMPLICIT_VR_LITTLE_ENDIAN,
        DicomConstants.JPEG_BASELINE,
        DicomConstants.JPEG_EXTENDED,
        DicomConstants.JPEG_LS_NEAR_LOSSLESS,
        DicomConstants.JPEG_2000,
        DicomConstants.MPEG2,
    };

    private String[] transferSyntax = NON_RETIRED_LE_TS;
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

    public Dcm5DicomReceiver(SourceConnector sourceConnector, DICOMConfiguration configuration) {
        this.sourceConnector = sourceConnector;
        this.dicomConfiguration = configuration;
        this.device = new Device("DCMRCV");

        if (configuration instanceof Dcm5DICOMConfiguration) {
            this.conn = ((Dcm5DICOMConfiguration) configuration).createDcm5Connection();
        } else {
            Object custom = configuration.createNetworkConnection();
            this.conn = (custom instanceof Connection) ? (Connection) custom : new Connection();
        }

        device.addConnection(conn);
        this.ae = new ApplicationEntity("*");
        ae.setAssociationAcceptor(true);
        ae.addConnection(conn);
        device.addApplicationEntity(ae);

        // Register DICOM service handlers
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        serviceRegistry.addDicomService(new BasicCStoreSCP("*") {
            @Override
            protected void store(Association as, PresentationContext pc, Attributes rq,
                                  PDVInputStream data, Attributes rsp) throws IOException {
                onCStoreRQ(as, pc, rq, data, rsp);
            }
        });
        serviceRegistry.addDicomService(new BasicCEchoSCP());
        device.setDimseRQHandler(serviceRegistry);
    }

    /**
     * Handles incoming C-STORE requests. Populates sourceMap with the same keys as
     * MirthDcmRcv.onCStoreRQ for behavioral parity.
     */
    private void onCStoreRQ(Association as, PresentationContext pc, Attributes rq,
                             PDVInputStream data, Attributes rsp) throws IOException {
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        String tsuid = pc.getTransferSyntax();

        Attributes fmi = Attributes.createFileMetaInformation(iuid, cuid, tsuid);

        String originalThreadName = Thread.currentThread().getName();
        ByteArrayOutputStream baos = null;
        DicomOutputStream dos = null;

        try {
            Thread.currentThread().setName("DICOM Receiver Thread on " + sourceConnector.getChannel().getName()
                    + " (" + sourceConnector.getChannelId() + ") < " + originalThreadName);

            Map<String, Object> sourceMap = buildSourceMap(as);
            sourceMap.putAll(dicomConfiguration.getCStoreRequestInformation(as));

            // Write DICOM file bytes (FMI + data stream)
            baos = new ByteArrayOutputStream();
            BufferedOutputStream bos = new BufferedOutputStream(baos);
            dos = new DicomOutputStream(bos, UID.ExplicitVRLittleEndian);
            dos.writeFileMetaInformation(fmi);
            data.copyTo(dos);
            dos.close();

            byte[] dicomMessage = baos.toByteArray();
            baos = null;

            DispatchResult dispatchResult = null;
            try {
                dispatchResult = sourceConnector.dispatchRawMessage(new RawMessage(dicomMessage, null, sourceMap));

                if (dispatchResult != null && dispatchResult.getSelectedResponse() != null
                        && dispatchResult.getSelectedResponse().getStatus() == com.mirth.connect.donkey.model.message.Status.ERROR) {
                    throw new DicomServiceException(Status.ProcessingFailure,
                            dispatchResult.getSelectedResponse().getStatusMessage());
                }
            } finally {
                sourceConnector.finishDispatch(dispatchResult);
            }
        } catch (Throwable t) {
            logger.error("Error receiving DICOM message on channel " + sourceConnector.getChannelId(), t);
            if (t instanceof DicomServiceException) {
                throw (DicomServiceException) t;
            } else {
                throw new DicomServiceException(Status.ProcessingFailure,
                        "Error processing DICOM message: " + t.getMessage());
            }
        } finally {
            Thread.currentThread().setName(originalThreadName);
            IOUtils.closeQuietly(baos);
        }
    }

    /**
     * Builds the sourceMap from a DICOM association. Package-visible for testing.
     * The keys MUST match MirthDcmRcv.onCStoreRQ exactly for behavioral parity.
     */
    Map<String, Object> buildSourceMap(Association as) {
        Map<String, Object> sourceMap = new HashMap<String, Object>();

        sourceMap.put("localApplicationEntityTitle", as.getLocalAET());
        sourceMap.put("remoteApplicationEntityTitle", as.getRemoteAET());

        if (as.getSocket() != null) {
            sourceMap.put("localAddress", as.getSocket().getLocalAddress().getHostAddress());
            sourceMap.put("localPort", as.getSocket().getLocalPort());
            if (as.getSocket().getRemoteSocketAddress() instanceof InetSocketAddress) {
                sourceMap.put("remoteAddress", ((InetSocketAddress) as.getSocket().getRemoteSocketAddress()).getAddress().getHostAddress());
                sourceMap.put("remotePort", ((InetSocketAddress) as.getSocket().getRemoteSocketAddress()).getPort());
            }
        }

        if (as.getAAssociateAC() != null) {
            sourceMap.put("associateACProtocolVersion", as.getAAssociateAC().getProtocolVersion());
            sourceMap.put("associateACImplClassUID", as.getAAssociateAC().getImplClassUID());
            sourceMap.put("associateACImplVersionName", as.getAAssociateAC().getImplVersionName());
            sourceMap.put("associateACApplicationContext", as.getAAssociateAC().getApplicationContext());

            if (as.getAAssociateAC().getNumberOfPresentationContexts() > 0) {
                Map<Integer, String> pcMap = new LinkedHashMap<Integer, String>();
                for (PresentationContext pctx : as.getAAssociateAC().getPresentationContexts()) {
                    pcMap.put(pctx.getPCID(), pctx.toString());
                }
                sourceMap.put("associateACPresentationContexts", MapUtils.unmodifiableMap(pcMap));
            }
        }

        if (as.getAAssociateRQ() != null) {
            sourceMap.put("associateRQProtocolVersion", as.getAAssociateRQ().getProtocolVersion());
            sourceMap.put("associateRQImplClassUID", as.getAAssociateRQ().getImplClassUID());
            sourceMap.put("associateRQImplVersionName", as.getAAssociateRQ().getImplVersionName());
            sourceMap.put("associateRQApplicationContext", as.getAAssociateRQ().getApplicationContext());

            if (as.getAAssociateRQ().getNumberOfPresentationContexts() > 0) {
                Map<Integer, String> pcMap = new LinkedHashMap<Integer, String>();
                for (PresentationContext pctx : as.getAAssociateRQ().getPresentationContexts()) {
                    pcMap.put(pctx.getPCID(), pctx.toString());
                }
                sourceMap.put("associateRQPresentationContexts", MapUtils.unmodifiableMap(pcMap));
            }

            if (as.getAAssociateRQ().getUserIdentityRQ() != null) {
                UserIdentityRQ uid = as.getAAssociateRQ().getUserIdentityRQ();
                sourceMap.put("username", uid.getUsername());
                sourceMap.put("passcode", String.valueOf(uid.getPasscode()));

                int type = uid.getType();
                String typeString;
                switch (type) {
                    case 1: typeString = "USERNAME"; break;
                    case 2: typeString = "USERNAME_PASSCODE"; break;
                    case 3: typeString = "KERBEROS"; break;
                    case 4: typeString = "SAML"; break;
                    default: typeString = String.valueOf(type);
                }
                sourceMap.put("userIdentityType", typeString);
            }
        }

        return sourceMap;
    }

    @Override
    public void setPort(int port) {
        conn.setPort(port);
    }

    @Override
    public void setHostname(String hostname) {
        conn.setHostname(hostname);
    }

    @Override
    public void setDestination(String destination) {
        // Matches dcm4che2's de facto behavior: MirthDcmRcv streams DIMSE data directly
        // to the channel and never consults DcmRcv.setDestination, so the UI flag has
        // been a no-op on both backends. Only reached when the user explicitly set a
        // non-blank value in the Listener's "Store Received Objects in Directory" field.
        logger.warn("destination={} has no effect on either DICOM backend (the flag has been "
                + "silently ignored upstream for years). Remove this setting to clear the warning.",
                destination);
    }

    @Override
    public void setTransferSyntax(String[] transferSyntax) {
        this.transferSyntax = transferSyntax;
    }

    @Override
    public void setAEtitle(String aeTitle) {
        if (aeTitle != null && !aeTitle.isEmpty()) {
            ae.setAETitle(aeTitle);
        }
    }

    @Override
    public void setAssociationReaperPeriod(int period) {
        // dcm4che5 manages association lifecycle via idle timeouts, not a reaper period.
        // The closest equivalent is the connection idle timeout.
        conn.setIdleTimeout(period);
    }

    @Override
    public void setIdleTimeout(int timeout) {
        conn.setIdleTimeout(timeout);
    }

    @Override
    public void setRequestTimeout(int timeout) {
        conn.setRequestTimeout(timeout);
    }

    @Override
    public void setReleaseTimeout(int timeout) {
        conn.setReleaseTimeout(timeout);
    }

    @Override
    public void setSocketCloseDelay(int delay) {
        conn.setSocketCloseDelay(delay);
    }

    @Override
    public void setDimseRspDelay(int delay) {
        // dcm4che5 handles DIMSE response timing internally.
        // No direct equivalent — response delay is not configurable.
        logger.trace("setDimseRspDelay ignored in dcm5 receiver: " + delay);
    }

    @Override
    public void setMaxPDULengthReceive(int length) {
        conn.setReceivePDULength(length);
    }

    @Override
    public void setMaxPDULengthSend(int length) {
        conn.setSendPDULength(length);
    }

    @Override
    public void setSendBufferSize(int size) {
        conn.setSendBufferSize(size);
    }

    @Override
    public void setReceiveBufferSize(int size) {
        conn.setReceiveBufferSize(size);
    }

    @Override
    public void setFileBufferSize(int size) {
        // dcm5 receiver streams directly to memory, with no file buffer concept.
        // Only reached when the user explicitly changed bufSize from default,
        // so warn instead of trace to surface the ignored setting.
        logger.warn("bufSize={} has no effect on dcm4che5 receiver (dcm4che3 manages buffers internally). "
                + "Revert dicom.library=dcm4che2 if this tuning is load-bearing.", size);
    }

    @Override
    public void setPackPDV(boolean packPDV) {
        conn.setPackPDV(packPDV);
    }

    @Override
    public void setTcpNoDelay(boolean tcpNoDelay) {
        conn.setTcpNoDelay(tcpNoDelay);
    }

    @Override
    public void setMaxOpsPerformed(int maxOps) {
        conn.setMaxOpsPerformed(maxOps);
    }

    @Override
    public void setTlsWithoutEncryption() {
        conn.setTlsCipherSuites("SSL_RSA_WITH_NULL_SHA");
    }

    @Override
    public void setTls3DES_EDE_CBC() {
        conn.setTlsCipherSuites("SSL_RSA_WITH_3DES_EDE_CBC_SHA");
    }

    @Override
    public void setTlsAES_128_CBC() {
        conn.setTlsCipherSuites("TLS_RSA_WITH_AES_128_CBC_SHA");
    }

    @Override
    public void setTlsCipherSuites(String[] cipherSuites) {
        conn.setTlsCipherSuites(cipherSuites);
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
        conn.setTlsNeedClientAuth(needClientAuth);
    }

    @Override
    public void setTlsProtocol(String[] protocols) {
        conn.setTlsProtocols(protocols);
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
    public void initTransferCapability() {
        if (transferSyntax != null && transferSyntax.length > 0) {
            // All transfer syntaxes must be in a single TransferCapability for the wildcard
            // abstract syntax, because ApplicationEntity stores TCs in a map keyed by abstract
            // syntax — multiple adds with "*" would overwrite each other.
            ae.addTransferCapability(
                    new TransferCapability(null, "*", TransferCapability.Role.SCP, transferSyntax));
        }
        // Add verification SOP class
        ae.addTransferCapability(
                new TransferCapability(null, UID.Verification, TransferCapability.Role.SCP, UID.ImplicitVRLittleEndian));
    }

    @Override
    public void start() throws Exception {
        executor = Executors.newCachedThreadPool();
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        device.setExecutor(executor);
        device.setScheduledExecutor(scheduledExecutor);
        device.bindConnections();
    }

    private static final int SHUTDOWN_TIMEOUT_MS = 5000;

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
            if (executor != null && !executor.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
            if (scheduledExecutor != null && !scheduledExecutor.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
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
