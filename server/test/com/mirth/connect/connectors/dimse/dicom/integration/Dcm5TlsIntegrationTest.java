package com.mirth.connect.connectors.dimse.dicom.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.dcm4che3.data.Tag;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomConverter;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomSender;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.server.channel.SourceConnector;

/**
 * Integration test verifying TLS-encrypted DICOM communication through the
 * production Dcm5DicomReceiver and Dcm5DicomSender code paths. Generates
 * ephemeral self-signed keystores at test time.
 *
 * <p>Uses the new setTlsCipherSuites() and setKeyStoreType() interface methods
 * to configure modern cipher suites (the legacy preset methods use suites
 * disabled in Java 21).
 */
public class Dcm5TlsIntegrationTest extends DicomIntegrationTestBase {

    // Cipher suite supported in Java 21 with RSA keys
    private static final String[] CIPHER_SUITES = { "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" };
    private static final String[] PROTOCOLS = { "TLSv1.2" };

    @Test
    public void testTlsEncryptedCStore() throws Exception {
        // Generate ephemeral keystore and truststore
        KeyStore ks = generateSelfSignedKeyStore("CN=DICOM-Test", "changeit");
        File keyStoreFile = tempFolder.newFile("test-keystore.jks");
        File trustStoreFile = tempFolder.newFile("test-truststore.jks");

        try (FileOutputStream fos = new FileOutputStream(keyStoreFile)) {
            ks.store(fos, "changeit".toCharArray());
        }
        KeyStore ts = KeyStore.getInstance("JKS");
        ts.load(null, "changeit".toCharArray());
        ts.setCertificateEntry("test", ks.getCertificate("key"));
        try (FileOutputStream fos = new FileOutputStream(trustStoreFile)) {
            ts.store(fos, "changeit".toCharArray());
        }

        String ksUrl = keyStoreFile.toURI().toString();
        String tsUrl = trustStoreFile.toURI().toString();
        int port = allocatePort();

        ArgumentCaptor<RawMessage> captor = ArgumentCaptor.forClass(RawMessage.class);
        SourceConnector mockConnector = createMockSourceConnector(captor);

        // Configure TLS receiver through the production code path
        Dcm5DicomReceiver rcv = new Dcm5DicomReceiver(mockConnector, new TestConfig());
        rcv.setHostname("127.0.0.1");
        rcv.setPort(port);
        rcv.setAEtitle("TLS_SCP");
        rcv.setTransferSyntax(new String[] { "1.2.840.10008.1.2", "1.2.840.10008.1.2.1" });
        rcv.setTlsCipherSuites(CIPHER_SUITES);
        rcv.setTlsProtocol(PROTOCOLS);
        rcv.setTlsNeedClientAuth(true);
        rcv.setKeyStoreURL(ksUrl);
        rcv.setKeyStorePassword("changeit");
        rcv.setKeyPassword("changeit");
        rcv.setKeyStoreType("JKS");
        rcv.setTrustStoreURL(tsUrl);
        rcv.setTrustStorePassword("changeit");
        rcv.setTrustStoreType("JKS");
        rcv.initTLS();
        rcv.initTransferCapability();
        rcv.start();
        receiver = rcv;
        waitForPort(port, 3000);

        // Configure TLS sender through the production code path
        File dicomFile = createDicomTempFile("Tls^Test", "TLS001");
        Dcm5DicomSender snd = new Dcm5DicomSender(new TestConfig());
        snd.setRemoteHost("127.0.0.1");
        snd.setRemotePort(port);
        snd.setCalledAET("TLS_SCP");
        snd.setCalling("TLS_SCU");
        snd.setTlsCipherSuites(CIPHER_SUITES);
        snd.setTlsProtocol(PROTOCOLS);
        snd.setKeyStoreURL(ksUrl);
        snd.setKeyStorePassword("changeit");
        snd.setKeyPassword("changeit");
        snd.setKeyStoreType("JKS");
        snd.setTrustStoreURL(tsUrl);
        snd.setTrustStorePassword("changeit");
        snd.setTrustStoreType("JKS");
        snd.initTLS();
        snd.addFile(dicomFile);
        snd.configureTransferCapability();
        snd.start();
        sender = snd;

        CapturingDimseRspHandler handler = new CapturingDimseRspHandler(1);
        sender.open();
        sender.send(handler);
        assertTrue("TLS DIMSE response not received", handler.awaitResponses(5000));
        sender.close();

        // Verify data integrity over TLS
        RawMessage received = captor.getValue();
        assertNotNull("Message should be received over TLS", received);
        Dcm5DicomConverter converter = new Dcm5DicomConverter();
        OieDicomObject parsed = converter.byteArrayToDicomObject(received.getRawBytes(), false);
        assertEquals("Tls^Test", parsed.getString(Tag.PatientName));
        assertEquals("TLS001", parsed.getString(Tag.PatientID));
        assertEquals(Integer.valueOf(0), handler.getStatuses().get(0));
    }

    private KeyStore generateSelfSignedKeyStore(String dn, String password) throws Exception {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        X500Principal subject = new X500Principal(dn);
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now);
        Date notAfter = new Date(now + 365L * 24 * 60 * 60 * 1000);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(now), notBefore, notAfter, subject, kp.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC").build(kp.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(builder.build(signer));

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, password.toCharArray());
        keyStore.setKeyEntry("key", kp.getPrivate(), password.toCharArray(), new Certificate[] { cert });
        return keyStore;
    }
}
