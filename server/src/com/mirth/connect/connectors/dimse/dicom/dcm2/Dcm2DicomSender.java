// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.dcm2;

import java.io.File;
import java.io.IOException;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.UserIdentity;
import org.dcm4che2.tool.dcmsnd.CustomDimseRSPHandler;
import org.dcm4che2.tool.dcmsnd.MirthDcmSnd;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;
import com.mirth.connect.connectors.dimse.dicom.OieDimseRspHandler;

/**
 * dcm4che2 implementation of OieDicomSender, wrapping MirthDcmSnd.
 */
public class Dcm2DicomSender implements OieDicomSender {

    private final MirthDcmSnd delegate;

    public Dcm2DicomSender(DICOMConfiguration configuration) {
        this.delegate = new MirthDcmSnd(configuration);
    }

    /**
     * Wrapping constructor for an existing MirthDcmSnd instance.
     */
    public Dcm2DicomSender(MirthDcmSnd delegate) {
        this.delegate = delegate;
    }

    @Override
    public void setCalledAET(String aet) {
        delegate.setCalledAET(aet);
    }

    @Override
    public void setRemoteHost(String host) {
        delegate.setRemoteHost(host);
    }

    @Override
    public void setRemotePort(int port) {
        delegate.setRemotePort(port);
    }

    @Override
    public void setCalling(String aet) {
        delegate.setCalling(aet);
    }

    @Override
    public void setLocalHost(String host) {
        delegate.setLocalHost(host);
    }

    @Override
    public void setLocalPort(int port) {
        delegate.setLocalPort(port);
    }

    @Override
    public void addFile(File file) {
        delegate.addFile(file);
    }

    @Override
    public void setAcceptTimeout(int timeout) {
        delegate.setAcceptTimeout(timeout);
    }

    @Override
    public void setMaxOpsInvoked(int maxOps) {
        delegate.setMaxOpsInvoked(maxOps);
    }

    @Override
    public void setTranscoderBufferSize(int size) {
        delegate.setTranscoderBufferSize(size);
    }

    @Override
    public void setConnectTimeout(int timeout) {
        delegate.setConnectTimeout(timeout);
    }

    @Override
    public void setPriority(int priority) {
        delegate.setPriority(priority);
    }

    @Override
    public void setPackPDV(boolean packPDV) {
        delegate.setPackPDV(packPDV);
    }

    @Override
    public void setMaxPDULengthReceive(int length) {
        delegate.setMaxPDULengthReceive(length);
    }

    @Override
    public void setMaxPDULengthSend(int length) {
        delegate.setMaxPDULengthSend(length);
    }

    @Override
    public void setReceiveBufferSize(int size) {
        delegate.setReceiveBufferSize(size);
    }

    @Override
    public void setSendBufferSize(int size) {
        delegate.setSendBufferSize(size);
    }

    @Override
    public void setAssociationReaperPeriod(int period) {
        delegate.setAssociationReaperPeriod(period);
    }

    @Override
    public void setReleaseTimeout(int timeout) {
        delegate.setReleaseTimeout(timeout);
    }

    @Override
    public void setDimseRspTimeout(int timeout) {
        delegate.setDimseRspTimeout(timeout);
    }

    @Override
    public void setShutdownDelay(int delay) {
        delegate.setShutdownDelay(delay);
    }

    @Override
    public void setSocketCloseDelay(int delay) {
        delegate.setSocketCloseDelay(delay);
    }

    @Override
    public void setTcpNoDelay(boolean tcpNoDelay) {
        delegate.setTcpNoDelay(tcpNoDelay);
    }

    @Override
    public void setOfferDefaultTransferSyntaxInSeparatePresentationContext(boolean ts1) {
        delegate.setOfferDefaultTransferSyntaxInSeparatePresentationContext(ts1);
    }

    @Override
    public void setStorageCommitment(boolean stgcmt) {
        delegate.setStorageCommitment(stgcmt);
    }

    @Override
    public void setUserIdentity(String username, String passcode, boolean positiveResponseRequested) {
        UserIdentity userId;
        if (passcode != null && !passcode.isEmpty()) {
            userId = new UserIdentity.UsernamePasscode(username, passcode.toCharArray());
        } else {
            userId = new UserIdentity.Username(username);
        }
        userId.setPositiveResponseRequested(positiveResponseRequested);
        delegate.setUserIdentity(userId);
    }

    @Override
    public void setTlsWithoutEncryption() {
        delegate.setTlsWithoutEncyrption();
    }

    @Override
    public void setTls3DES_EDE_CBC() {
        delegate.setTls3DES_EDE_CBC();
    }

    @Override
    public void setTlsAES_128_CBC() {
        delegate.setTlsAES_128_CBC();
    }

    @Override
    public void setTrustStoreURL(String url) {
        delegate.setTrustStoreURL(url);
    }

    @Override
    public void setTrustStorePassword(String password) {
        delegate.setTrustStorePassword(password);
    }

    @Override
    public void setKeyPassword(String password) {
        delegate.setKeyPassword(password);
    }

    @Override
    public void setKeyStoreURL(String url) {
        delegate.setKeyStoreURL(url);
    }

    @Override
    public void setKeyStorePassword(String password) {
        delegate.setKeyStorePassword(password);
    }

    @Override
    public void setTlsNeedClientAuth(boolean needClientAuth) {
        delegate.setTlsNeedClientAuth(needClientAuth);
    }

    @Override
    public void setTlsProtocol(String[] protocols) {
        delegate.setTlsProtocol(protocols);
    }

    @Override
    public void initTLS() throws Exception {
        delegate.initTLS();
    }

    @Override
    public void configureTransferCapability() {
        delegate.configureTransferCapability();
    }

    @Override
    public void start() throws IOException {
        delegate.start();
    }

    @Override
    public void open() throws Exception {
        delegate.open();
    }

    @Override
    public void send(OieDimseRspHandler handler) throws Exception {
        // Bridge from OieDimseRspHandler to dcm4che2's CustomDimseRSPHandler
        delegate.send(new CustomDimseRSPHandler() {
            @Override
            public void onDimseRSP(Association as, DicomObject cmd, DicomObject data) {
                OieDicomObject wrappedCmd = cmd != null ? new Dcm2DicomObject(cmd) : null;
                OieDicomObject wrappedData = data != null ? new Dcm2DicomObject(data) : null;
                handler.onDimseRSP(wrappedCmd, wrappedData);
            }
        });
    }

    @Override
    public boolean isStorageCommitment() {
        return delegate.isStorageCommitment();
    }

    @Override
    public boolean commit() throws Exception {
        return delegate.commit();
    }

    @Override
    public OieDicomObject waitForStgCmtResult() throws InterruptedException {
        DicomObject result = delegate.waitForStgCmtResult();
        return result != null ? new Dcm2DicomObject(result) : null;
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public Object unwrap() {
        return delegate;
    }
}
