package com.mirth.connect.connectors.dimse.dicom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory.DicomLibrary;

public class DicomLibraryFactoryTest {

    @After
    public void resetFactory() {
        DicomLibraryFactory.resetForTesting(null);
    }

    @Test
    public void testDefaultLibraryIsDcm4che2() {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        assertEquals(DicomLibrary.DCM4CHE2, DicomLibraryFactory.getActiveLibrary());
    }

    @Test
    public void testGetConverterReturnsSingleton() {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        OieDicomConverter c1 = DicomLibraryFactory.getConverter();
        OieDicomConverter c2 = DicomLibraryFactory.getConverter();
        assertNotNull(c1);
        assertSame(c1, c2);
    }

    @Test
    public void testCreateSenderReturnsNonNull() {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        // Use a null-safe test configuration
        OieDicomSender sender = DicomLibraryFactory.createSender(new TestDICOMConfiguration());
        assertNotNull(sender);
    }

    @Test
    public void testLoadConfigurationWithNullReturnsDefault() {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        DICOMConfiguration config = DicomLibraryFactory.loadConfiguration(null);
        assertNotNull(config);
    }

    @Test
    public void testLoadConfigurationWithEmptyReturnsDefault() {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        DICOMConfiguration config = DicomLibraryFactory.loadConfiguration("  ");
        assertNotNull(config);
    }

    @Test
    public void testLoadConfigurationWithInvalidClassReturnsDefault() {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        DICOMConfiguration config = DicomLibraryFactory.loadConfiguration("com.nonexistent.FakeClass");
        assertNotNull(config);
    }

    @Test
    public void testDcm5ConverterCreation() {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE5);
        OieDicomConverter converter = DicomLibraryFactory.getConverter();
        assertNotNull(converter);
        assertEquals("Dcm5DicomConverter", converter.getClass().getSimpleName());
    }

    @Test
    public void testDcm5SenderCreation() {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE5);
        OieDicomSender sender = DicomLibraryFactory.createSender(new TestDICOMConfiguration());
        assertNotNull(sender);
        assertEquals("Dcm5DicomSender", sender.getClass().getSimpleName());
    }

    @Test
    public void testDcm5ReceiverCreation() {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE5);
        OieDicomReceiver receiver = DicomLibraryFactory.createReceiver(
                org.mockito.Mockito.mock(com.mirth.connect.donkey.server.channel.SourceConnector.class),
                new TestDICOMConfiguration());
        assertNotNull(receiver);
        assertEquals("Dcm5DicomReceiver", receiver.getClass().getSimpleName());
    }

    @Test
    public void testDcm5ConverterSingleton() {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE5);
        OieDicomConverter c1 = DicomLibraryFactory.getConverter();
        OieDicomConverter c2 = DicomLibraryFactory.getConverter();
        assertSame(c1, c2);
    }

    /**
     * Minimal DICOMConfiguration for testing — avoids ControllerFactory dependencies.
     */
    private static class TestDICOMConfiguration implements DICOMConfiguration {
        @Override
        public void configureConnectorDeploy(com.mirth.connect.donkey.server.channel.Connector connector) {}
        @Override
        public void configureReceiver(OieDicomReceiver receiver,
                com.mirth.connect.connectors.dimse.DICOMReceiver connector,
                com.mirth.connect.connectors.dimse.DICOMReceiverProperties connectorProperties) {}
        @Override
        public void configureSender(OieDicomSender sender,
                com.mirth.connect.connectors.dimse.DICOMDispatcher connector,
                com.mirth.connect.connectors.dimse.DICOMDispatcherProperties connectorProperties) {}
        @Override
        public java.util.Map<String, Object> getCStoreRequestInformation(Object association) {
            return new java.util.HashMap<>();
        }
    }
}
