// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.dcm5;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.junit.Test;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.connectors.dimse.DICOMDispatcher;
import com.mirth.connect.connectors.dimse.DICOMDispatcherProperties;
import com.mirth.connect.connectors.dimse.DICOMReceiver;
import com.mirth.connect.connectors.dimse.DICOMReceiverProperties;
import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;
import com.mirth.connect.donkey.server.channel.Connector;

public class Dcm5DICOMConfigurationTest {

    @Test
    public void testGetCStoreRequestInfoBridgesToAssociationOverload() {
        final boolean[] called = { false };
        Dcm5DICOMConfiguration config = new TestDcm5Config() {
            @Override
            public Map<String, Object> getCStoreRequestInformation(Association association) {
                called[0] = true;
                return new HashMap<>();
            }
        };

        config.getCStoreRequestInformation((Object) null);
        assertTrue("bridge did not delegate to Association overload", called[0]);
    }

    @Test
    public void testCreateNetworkConnectionBridgesToDcm5Connection() {
        Dcm5DICOMConfiguration config = new TestDcm5Config();
        Object conn = config.createNetworkConnection();
        assertNotNull(conn);
        assertTrue(conn instanceof Connection);
    }

    @Test
    public void testImplementsDICOMConfiguration() {
        Dcm5DICOMConfiguration config = new TestDcm5Config();
        assertTrue(config instanceof DICOMConfiguration);
    }

    /** Minimal test implementation of Dcm5DICOMConfiguration. */
    private static class TestDcm5Config implements Dcm5DICOMConfiguration {
        @Override
        public void configureConnectorDeploy(Connector connector) {}

        @Override
        public void configureDcm5Receiver(Dcm5DicomReceiver receiver, DICOMReceiver connector,
                                           DICOMReceiverProperties connectorProperties) {}

        @Override
        public void configureDcm5Sender(Dcm5DicomSender sender, DICOMDispatcher connector,
                                         DICOMDispatcherProperties connectorProperties) {}

        @Override
        public Map<String, Object> getCStoreRequestInformation(Association association) {
            return new HashMap<>();
        }

        @Override
        public Connection createDcm5Connection() {
            return new Connection();
        }
    }
}
