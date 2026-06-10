// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.dcm2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.dcm4che2.net.Association;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.tool.dcmrcv.MirthDcmRcv;
import org.dcm4che2.tool.dcmsnd.MirthDcmSnd;
import org.junit.Test;

import com.mirth.connect.connectors.dimse.DICOMDispatcher;
import com.mirth.connect.connectors.dimse.DICOMDispatcherProperties;
import com.mirth.connect.connectors.dimse.DICOMReceiver;
import com.mirth.connect.connectors.dimse.DICOMReceiverProperties;
import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;
import com.mirth.connect.donkey.server.channel.Connector;

/**
 * Tests that the Dcm2DICOMConfiguration bridge defaults correctly delegate
 * version-neutral calls to the legacy dcm4che2-typed methods.
 */
public class Dcm2DICOMConfigurationTest {

    @Test
    public void testConfigureReceiverBridgesToConfigureDcmRcv() throws Exception {
        BridgeTrackingConfig config = new BridgeTrackingConfig();

        // Use a real Dcm2DicomReceiver wrapping a real MirthDcmRcv.
        // The Dcm2DICOMConfiguration default configureReceiver calls unwrap() and casts.
        MirthDcmRcv realRcv = new MirthDcmRcv(null, config);
        Dcm2DicomReceiver receiver = new Dcm2DicomReceiver(realRcv);

        // Call configureReceiver — this uses the Dcm2DICOMConfiguration default bridge:
        //   configureReceiver → (MirthDcmRcv) receiver.unwrap() → configureDcmRcv
        config.configureReceiver(receiver, null, null);
        assertTrue("configureDcmRcv should have been called via bridge default", config.configureDcmRcvCalled);
    }

    @Test
    public void testConfigureSenderBridgesToConfigureDcmSnd() throws Exception {
        BridgeTrackingConfig config = new BridgeTrackingConfig();

        MirthDcmSnd realSnd = new MirthDcmSnd(config);
        Dcm2DicomSender sender = new Dcm2DicomSender(realSnd);

        // Call configureSender — uses the Dcm2DICOMConfiguration default bridge:
        //   configureSender → (MirthDcmSnd) sender.unwrap() → configureDcmSnd
        config.configureSender(sender, null, null);
        assertTrue("configureDcmSnd should have been called via bridge default", config.configureDcmSndCalled);
    }

    @Test
    public void testGetCStoreRequestInfoBridgesToAssociationOverload() {
        BridgeTrackingConfig config = new BridgeTrackingConfig();
        Map<String, Object> result = config.getCStoreRequestInformation((Object) null);
        assertNotNull(result);
        assertTrue("getCStoreRequestInformation(Association) should have been called",
                config.getCStoreRequestInfoCalled);
    }

    @Test
    public void testCreateNetworkConnectionBridgesToLegacy() {
        BridgeTrackingConfig config = new BridgeTrackingConfig();
        Object nc = config.createNetworkConnection();
        assertNotNull(nc);
        assertTrue(nc instanceof NetworkConnection);
        assertTrue("createLegacyNetworkConnection should have been called",
                config.createLegacyNetworkConnectionCalled);
    }

    /**
     * Tracks all bridge paths. Does NOT override configureReceiver/configureSender,
     * so the Dcm2DICOMConfiguration interface defaults (the real bridge logic) are exercised.
     */
    private static class BridgeTrackingConfig implements Dcm2DICOMConfiguration {
        boolean configureDcmRcvCalled = false;
        boolean configureDcmSndCalled = false;
        boolean getCStoreRequestInfoCalled = false;
        boolean createLegacyNetworkConnectionCalled = false;

        @Override
        public void configureConnectorDeploy(Connector connector) {}
        @Override
        public void configureDcmRcv(MirthDcmRcv dcmrcv, DICOMReceiver connector, DICOMReceiverProperties props) {
            configureDcmRcvCalled = true;
        }
        @Override
        public void configureDcmSnd(MirthDcmSnd dcmsnd, DICOMDispatcher connector, DICOMDispatcherProperties props) {
            configureDcmSndCalled = true;
        }
        @Override
        public Map<String, Object> getCStoreRequestInformation(Association association) {
            getCStoreRequestInfoCalled = true;
            return new HashMap<>();
        }
        @Override
        public NetworkConnection createLegacyNetworkConnection() {
            createLegacyNetworkConnectionCalled = true;
            return new NetworkConnection();
        }
    }

}
