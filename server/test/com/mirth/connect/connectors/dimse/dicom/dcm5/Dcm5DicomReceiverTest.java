// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.dcm5;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

import org.dcm4che3.net.Association;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.pdu.AAssociateAC;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.UserIdentityRQ;
import org.junit.Test;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.connectors.dimse.DICOMDispatcher;
import com.mirth.connect.connectors.dimse.DICOMDispatcherProperties;
import com.mirth.connect.connectors.dimse.DICOMReceiver;
import com.mirth.connect.connectors.dimse.DICOMReceiverProperties;
import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;
import com.mirth.connect.donkey.server.channel.Connector;
import com.mirth.connect.donkey.server.channel.SourceConnector;

public class Dcm5DicomReceiverTest {

    @Test
    public void testConstructionCreatesDevice() {
        Dcm5DicomReceiver receiver = createReceiver();
        assertNotNull(receiver);
        assertNotNull(receiver.unwrap());
        assertTrue(receiver.unwrap() instanceof Device);
    }

    @Test
    public void testImplementsOieDicomReceiver() {
        Dcm5DicomReceiver receiver = createReceiver();
        assertTrue(receiver instanceof OieDicomReceiver);
    }

    /**
     * Verifies that buildSourceMap produces the exact same keys as MirthDcmRcv.onCStoreRQ.
     * This is the most critical parity test for the dcm5 receiver.
     */
    @Test
    public void testSourceMapKeysMatchMirthDcmRcv() throws Exception {
        Dcm5DicomReceiver receiver = createReceiver();

        // Mock Association with full metadata
        Association as = mock(Association.class);
        when(as.getLocalAET()).thenReturn("LOCAL_AE");
        when(as.getRemoteAET()).thenReturn("REMOTE_AE");

        // Mock Socket
        Socket socket = mock(Socket.class);
        InetAddress localAddr = InetAddress.getByName("127.0.0.1");
        InetAddress remoteAddr = InetAddress.getByName("192.168.1.100");
        when(socket.getLocalAddress()).thenReturn(localAddr);
        when(socket.getLocalPort()).thenReturn(11112);
        when(socket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress(remoteAddr, 50000));
        when(as.getSocket()).thenReturn(socket);

        // Mock AAssociateAC
        AAssociateAC ac = mock(AAssociateAC.class);
        when(ac.getProtocolVersion()).thenReturn(1);
        when(ac.getImplClassUID()).thenReturn("1.2.3.4");
        when(ac.getImplVersionName()).thenReturn("DCM5TEST");
        when(ac.getApplicationContext()).thenReturn("1.2.840.10008.3.1.1.1");
        when(ac.getNumberOfPresentationContexts()).thenReturn(0);
        when(as.getAAssociateAC()).thenReturn(ac);

        // Mock AAssociateRQ with UserIdentity
        AAssociateRQ rq = mock(AAssociateRQ.class);
        when(rq.getProtocolVersion()).thenReturn(1);
        when(rq.getImplClassUID()).thenReturn("1.2.3.5");
        when(rq.getImplVersionName()).thenReturn("DCM5REQ");
        when(rq.getApplicationContext()).thenReturn("1.2.840.10008.3.1.1.1");
        when(rq.getNumberOfPresentationContexts()).thenReturn(0);

        UserIdentityRQ uid = mock(UserIdentityRQ.class);
        when(uid.getUsername()).thenReturn("testuser");
        when(uid.getPasscode()).thenReturn("testpass".toCharArray());
        when(uid.getType()).thenReturn(2); // USERNAME_PASSCODE
        when(rq.getUserIdentityRQ()).thenReturn(uid);
        when(as.getAAssociateRQ()).thenReturn(rq);

        Map<String, Object> sourceMap = receiver.buildSourceMap(as);

        // Verify ALL keys that MirthDcmRcv.onCStoreRQ populates
        assertEquals("LOCAL_AE", sourceMap.get("localApplicationEntityTitle"));
        assertEquals("REMOTE_AE", sourceMap.get("remoteApplicationEntityTitle"));
        assertEquals("127.0.0.1", sourceMap.get("localAddress"));
        assertEquals(11112, sourceMap.get("localPort"));
        assertEquals("192.168.1.100", sourceMap.get("remoteAddress"));
        assertEquals(50000, sourceMap.get("remotePort"));
        assertEquals(1, sourceMap.get("associateACProtocolVersion"));
        assertEquals("1.2.3.4", sourceMap.get("associateACImplClassUID"));
        assertEquals("DCM5TEST", sourceMap.get("associateACImplVersionName"));
        assertEquals("1.2.840.10008.3.1.1.1", sourceMap.get("associateACApplicationContext"));
        assertEquals(1, sourceMap.get("associateRQProtocolVersion"));
        assertEquals("1.2.3.5", sourceMap.get("associateRQImplClassUID"));
        assertEquals("DCM5REQ", sourceMap.get("associateRQImplVersionName"));
        assertEquals("1.2.840.10008.3.1.1.1", sourceMap.get("associateRQApplicationContext"));
        assertEquals("testuser", sourceMap.get("username"));
        assertEquals("testpass", sourceMap.get("passcode"));
        assertEquals("USERNAME_PASSCODE", sourceMap.get("userIdentityType"));
    }

    @Test
    public void testSourceMapWithNullSocket() {
        Dcm5DicomReceiver receiver = createReceiver();
        Association as = mock(Association.class);
        when(as.getLocalAET()).thenReturn("AE");
        when(as.getRemoteAET()).thenReturn("REMOTE");
        when(as.getSocket()).thenReturn(null);
        when(as.getAAssociateAC()).thenReturn(null);
        when(as.getAAssociateRQ()).thenReturn(null);

        Map<String, Object> sourceMap = receiver.buildSourceMap(as);
        assertEquals("AE", sourceMap.get("localApplicationEntityTitle"));
        assertEquals("REMOTE", sourceMap.get("remoteApplicationEntityTitle"));
        // No socket keys should be present
        assertTrue(!sourceMap.containsKey("localAddress"));
    }

    private Dcm5DicomReceiver createReceiver() {
        return new Dcm5DicomReceiver(mock(SourceConnector.class), new TestConfig());
    }

    private static class TestConfig implements DICOMConfiguration {
        @Override public void configureConnectorDeploy(Connector connector) {}
        @Override public void configureReceiver(OieDicomReceiver r, DICOMReceiver c, DICOMReceiverProperties p) {}
        @Override public void configureSender(OieDicomSender s, DICOMDispatcher c, DICOMDispatcherProperties p) {}
        @Override public Map<String, Object> getCStoreRequestInformation(Object association) { return new java.util.HashMap<>(); }
    }
}
