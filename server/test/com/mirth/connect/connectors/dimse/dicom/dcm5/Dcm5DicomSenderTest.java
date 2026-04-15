package com.mirth.connect.connectors.dimse.dicom.dcm5;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.dcm4che3.net.Device;
import org.junit.Test;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.connectors.dimse.DICOMDispatcher;
import com.mirth.connect.connectors.dimse.DICOMDispatcherProperties;
import com.mirth.connect.connectors.dimse.DICOMReceiver;
import com.mirth.connect.connectors.dimse.DICOMReceiverProperties;
import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;
import com.mirth.connect.donkey.server.channel.Connector;

public class Dcm5DicomSenderTest {

    @Test
    public void testConstructionCreatesDevice() {
        Dcm5DicomSender sender = new Dcm5DicomSender(new TestConfig());
        assertNotNull(sender);
        assertNotNull(sender.unwrap());
        assertTrue(sender.unwrap() instanceof Device);
    }

    @Test
    public void testImplementsOieDicomSender() {
        Dcm5DicomSender sender = new Dcm5DicomSender(new TestConfig());
        assertTrue(sender instanceof OieDicomSender);
    }

    @Test
    public void testStorageCommitmentDefault() {
        Dcm5DicomSender sender = new Dcm5DicomSender(new TestConfig());
        assertTrue(!sender.isStorageCommitment());
    }

    @Test
    public void testSetStorageCommitment() {
        Dcm5DicomSender sender = new Dcm5DicomSender(new TestConfig());
        sender.setStorageCommitment(true);
        assertTrue(sender.isStorageCommitment());
    }

    private static class TestConfig implements DICOMConfiguration {
        @Override public void configureConnectorDeploy(Connector connector) {}
        @Override public void configureReceiver(OieDicomReceiver r, DICOMReceiver c, DICOMReceiverProperties p) {}
        @Override public void configureSender(OieDicomSender s, DICOMDispatcher c, DICOMDispatcherProperties p) {}
        @Override public Map<String, Object> getCStoreRequestInformation(Object association) { return new java.util.HashMap<>(); }
    }
}
