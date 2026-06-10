// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom;

/**
 * Version-neutral interface for DICOM C-STORE SCP (receiver). Abstracts dcm4che2's
 * MirthDcmRcv/DcmRcv and dcm4che5's StoreSCP.
 */
public interface OieDicomReceiver {

    void setPort(int port);

    void setHostname(String hostname);

    void setDestination(String destination);

    void setTransferSyntax(String[] transferSyntax);

    void setAEtitle(String aeTitle);

    void setAssociationReaperPeriod(int period);

    void setIdleTimeout(int timeout);

    void setRequestTimeout(int timeout);

    void setReleaseTimeout(int timeout);

    void setSocketCloseDelay(int delay);

    void setDimseRspDelay(int delay);

    void setMaxPDULengthReceive(int length);

    void setMaxPDULengthSend(int length);

    void setSendBufferSize(int size);

    void setReceiveBufferSize(int size);

    void setFileBufferSize(int size);

    void setPackPDV(boolean packPDV);

    void setTcpNoDelay(boolean tcpNoDelay);

    void setMaxOpsPerformed(int maxOps);

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

    void initTransferCapability();

    void start() throws Exception;

    void stop();

    /**
     * Returns the underlying library-specific receiver object. Use with caution.
     */
    Object unwrap();
}
