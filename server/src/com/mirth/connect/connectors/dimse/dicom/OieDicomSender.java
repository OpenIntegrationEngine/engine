/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom;

import java.io.File;
import java.io.IOException;

/**
 * Version-neutral interface for DICOM C-STORE SCU (sender). Abstracts dcm4che2's
 * MirthDcmSnd/DcmSnd and dcm4che5's StoreSCU.
 */
public interface OieDicomSender {

    void setCalledAET(String aet);

    void setRemoteHost(String host);

    void setRemotePort(int port);

    void setCalling(String aet);

    void setLocalHost(String host);

    void setLocalPort(int port);

    void addFile(File file);

    void setAcceptTimeout(int timeout);

    void setMaxOpsInvoked(int maxOps);

    void setTranscoderBufferSize(int size);

    void setConnectTimeout(int timeout);

    void setPriority(int priority);

    void setPackPDV(boolean packPDV);

    void setMaxPDULengthReceive(int length);

    void setMaxPDULengthSend(int length);

    void setReceiveBufferSize(int size);

    void setSendBufferSize(int size);

    void setAssociationReaperPeriod(int period);

    void setReleaseTimeout(int timeout);

    void setDimseRspTimeout(int timeout);

    void setShutdownDelay(int delay);

    void setSocketCloseDelay(int delay);

    void setTcpNoDelay(boolean tcpNoDelay);

    void setOfferDefaultTransferSyntaxInSeparatePresentationContext(boolean ts1);

    void setStorageCommitment(boolean stgcmt);

    void setUserIdentity(String username, String passcode, boolean positiveResponseRequested);

    void setTlsWithoutEncryption();

    void setTls3DES_EDE_CBC();

    void setTlsAES_128_CBC();

    /**
     * Sets custom TLS cipher suites. Use this instead of the preset methods
     * (setTlsWithoutEncryption, setTls3DES_EDE_CBC, setTlsAES_128_CBC) when
     * those legacy suites are not suitable (e.g., disabled in modern JVMs).
     */
    default void setTlsCipherSuites(String[] cipherSuites) {}

    void setTrustStoreURL(String url);

    void setTrustStorePassword(String password);

    void setKeyPassword(String password);

    void setKeyStoreURL(String url);

    void setKeyStorePassword(String password);

    /**
     * Sets the keystore type (e.g., "JKS", "PKCS12", "JCEKS").
     * dcm4che2 infers this automatically; dcm4che5 requires it explicitly.
     * If not set, dcm4che5 will infer from the keystore URL file extension.
     */
    default void setKeyStoreType(String type) {}

    /**
     * Sets the truststore type (e.g., "JKS", "PKCS12", "JCEKS").
     * dcm4che2 infers this automatically; dcm4che5 requires it explicitly.
     * If not set, dcm4che5 will infer from the truststore URL file extension.
     */
    default void setTrustStoreType(String type) {}

    void setTlsNeedClientAuth(boolean needClientAuth);

    void setTlsProtocol(String[] protocols);

    void initTLS() throws Exception;

    void configureTransferCapability();

    void start() throws IOException;

    void open() throws Exception;

    void send(OieDimseRspHandler handler) throws Exception;

    boolean isStorageCommitment();

    boolean commit() throws Exception;

    OieDicomObject waitForStgCmtResult() throws InterruptedException;

    void close();

    void stop();

    /**
     * Returns the underlying library-specific sender object. Use with caution.
     */
    Object unwrap();
}
