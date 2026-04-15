/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom.dcm2;

import org.dcm4che2.tool.dcmrcv.MirthDcmRcv;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.donkey.server.channel.SourceConnector;

/**
 * dcm4che2 implementation of OieDicomReceiver, wrapping MirthDcmRcv.
 */
public class Dcm2DicomReceiver implements OieDicomReceiver {

    private final MirthDcmRcv delegate;

    public Dcm2DicomReceiver(SourceConnector sourceConnector, DICOMConfiguration configuration) {
        this.delegate = new MirthDcmRcv(sourceConnector, configuration);
    }

    /**
     * Wrapping constructor for an existing MirthDcmRcv instance.
     */
    public Dcm2DicomReceiver(MirthDcmRcv delegate) {
        this.delegate = delegate;
    }

    @Override
    public void setPort(int port) {
        delegate.setPort(port);
    }

    @Override
    public void setHostname(String hostname) {
        delegate.setHostname(hostname);
    }

    @Override
    public void setDestination(String destination) {
        delegate.setDestination(destination);
    }

    @Override
    public void setTransferSyntax(String[] transferSyntax) {
        delegate.setTransferSyntax(transferSyntax);
    }

    @Override
    public void setAEtitle(String aeTitle) {
        delegate.setAEtitle(aeTitle);
    }

    @Override
    public void setAssociationReaperPeriod(int period) {
        delegate.setAssociationReaperPeriod(period);
    }

    @Override
    public void setIdleTimeout(int timeout) {
        delegate.setIdleTimeout(timeout);
    }

    @Override
    public void setRequestTimeout(int timeout) {
        delegate.setRequestTimeout(timeout);
    }

    @Override
    public void setReleaseTimeout(int timeout) {
        delegate.setReleaseTimeout(timeout);
    }

    @Override
    public void setSocketCloseDelay(int delay) {
        delegate.setSocketCloseDelay(delay);
    }

    @Override
    public void setDimseRspDelay(int delay) {
        delegate.setDimseRspDelay(delay);
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
    public void setSendBufferSize(int size) {
        delegate.setSendBufferSize(size);
    }

    @Override
    public void setReceiveBufferSize(int size) {
        delegate.setReceiveBufferSize(size);
    }

    @Override
    public void setFileBufferSize(int size) {
        delegate.setFileBufferSize(size);
    }

    @Override
    public void setPackPDV(boolean packPDV) {
        delegate.setPackPDV(packPDV);
    }

    @Override
    public void setTcpNoDelay(boolean tcpNoDelay) {
        delegate.setTcpNoDelay(tcpNoDelay);
    }

    @Override
    public void setMaxOpsPerformed(int maxOps) {
        delegate.setMaxOpsPerformed(maxOps);
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
    public void initTransferCapability() {
        delegate.initTransferCapability();
    }

    @Override
    public void start() throws Exception {
        delegate.start();
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
