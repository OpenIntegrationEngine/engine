// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.donkey.model.event.Event;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.model.ServerEvent;
import com.mirth.connect.model.filters.EventFilter;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.event.EventListener;

public class DICOMReceiverTest {

    @Test
    public void testOnStartSetsPort() throws Exception {
        TestDicomReceiver stub = new TestDicomReceiver();
        TestDICOMReceiver receiver = createTestReceiver(stub);
        receiver.connectorProperties.getListenerConnectorProperties().setPort("1234");

        receiver.onStart();

        assertEquals(1234, stub.port);
    }

    @Test
    public void testOnStartSetsHostname() throws Exception {
        TestDicomReceiver stub = new TestDicomReceiver();
        TestDICOMReceiver receiver = createTestReceiver(stub);
        receiver.connectorProperties.getListenerConnectorProperties().setHost("192.168.1.100");

        receiver.onStart();

        assertEquals("192.168.1.100", stub.hostname);
    }

    @Test
    public void testOnStartSetsAETitle() throws Exception {
        TestDicomReceiver stub = new TestDicomReceiver();
        TestDICOMReceiver receiver = createTestReceiver(stub);
        receiver.connectorProperties.setDest("MY_AE_TITLE");

        receiver.onStart();

        assertEquals("MY_AE_TITLE", stub.destination);
    }

    @Test
    public void testOnStartTransferSyntax() throws Exception {
        TestDicomReceiver stub = new TestDicomReceiver();
        TestDICOMReceiver receiver = createTestReceiver(stub);
        receiver.connectorProperties.setDefts(true);

        receiver.onStart();

        assertArrayEquals(new String[] { "1.2.840.10008.1.2" }, stub.transferSyntax);
    }

    @Test
    public void testOnStartCallsStart() throws Exception {
        TestDicomReceiver stub = new TestDicomReceiver();
        TestDICOMReceiver receiver = createTestReceiver(stub);

        receiver.onStart();

        assertTrue("start() should have been called", stub.started);
    }

    @Test
    public void testOnStopCallsStop() throws Exception {
        TestDicomReceiver stub = new TestDicomReceiver();
        TestDICOMReceiver receiver = createTestReceiver(stub);

        receiver.onStop();

        assertTrue("stop() should have been called", stub.stopped);
    }

    private TestDICOMReceiver createTestReceiver(TestDicomReceiver stub) {
        TestDICOMReceiver receiver = new TestDICOMReceiver(stub);
        receiver.connectorProperties = new DICOMReceiverProperties();
        receiver.dicomReceiver = stub;
        return receiver;
    }

    /**
     * Stub OieDicomReceiver that captures setter calls for assertions.
     */
    private static class TestDicomReceiver implements OieDicomReceiver {
        int port;
        String hostname;
        String destination;
        String[] transferSyntax;
        String aeTitle;
        boolean started;
        boolean stopped;

        @Override public void setPort(int port) { this.port = port; }
        @Override public void setHostname(String hostname) { this.hostname = hostname; }
        @Override public void setDestination(String destination) { this.destination = destination; }
        @Override public void setTransferSyntax(String[] transferSyntax) { this.transferSyntax = transferSyntax; }
        @Override public void setAEtitle(String aeTitle) { this.aeTitle = aeTitle; }
        @Override public void setAssociationReaperPeriod(int period) {}
        @Override public void setIdleTimeout(int timeout) {}
        @Override public void setRequestTimeout(int timeout) {}
        @Override public void setReleaseTimeout(int timeout) {}
        @Override public void setSocketCloseDelay(int delay) {}
        @Override public void setDimseRspDelay(int delay) {}
        @Override public void setMaxPDULengthReceive(int length) {}
        @Override public void setMaxPDULengthSend(int length) {}
        @Override public void setSendBufferSize(int size) {}
        @Override public void setReceiveBufferSize(int size) {}
        @Override public void setFileBufferSize(int size) {}
        @Override public void setPackPDV(boolean packPDV) {}
        @Override public void setTcpNoDelay(boolean tcpNoDelay) {}
        @Override public void setMaxOpsPerformed(int maxOps) {}
        @Override public void setTlsWithoutEncryption() {}
        @Override public void setTls3DES_EDE_CBC() {}
        @Override public void setTlsAES_128_CBC() {}
        @Override public void setTrustStoreURL(String url) {}
        @Override public void setTrustStorePassword(String password) {}
        @Override public void setKeyPassword(String password) {}
        @Override public void setKeyStoreURL(String url) {}
        @Override public void setKeyStorePassword(String password) {}
        @Override public void setTlsNeedClientAuth(boolean needClientAuth) {}
        @Override public void setTlsProtocol(String[] protocols) {}
        @Override public void initTLS() {}
        @Override public void initTransferCapability() {}
        @Override public void start() { this.started = true; }
        @Override public void stop() { this.stopped = true; }
        @Override public Object unwrap() { return null; }
    }

    /**
     * Test subclass of DICOMReceiver that bypasses deployment and infrastructure
     * dependencies. Sets up a no-op EventController and DICOMConfiguration, and
     * provides a fake Channel for template value replacement.
     */
    private static class TestDICOMReceiver extends DICOMReceiver {
        private final OieDicomReceiver stubReceiver;

        TestDICOMReceiver(OieDicomReceiver stubReceiver) {
            this.stubReceiver = stubReceiver;
            this.configuration = new DefaultDICOMConfiguration() {
                @Override
                public void configureConnectorDeploy(com.mirth.connect.donkey.server.channel.Connector connector) {}

                @Override
                public void configureReceiver(OieDicomReceiver receiver, DICOMReceiver connector, DICOMReceiverProperties connectorProperties) {}
            };
            this.eventController = new NoOpEventController();

            // Set up a minimal Channel so getChannel().getName() works in onStart
            Channel ch = new Channel();
            ch.setName("testChannel");
            this.channel = ch;
            setChannelId("testChannelId");
        }

        @Override
        protected OieDicomReceiver createDicomReceiver(DICOMConfiguration configuration) {
            return stubReceiver;
        }
    }

    private static class NoOpEventController extends EventController {
        @Override public void addListener(EventListener listener) {}
        @Override public void removeListener(EventListener listener) {}
        @Override public void dispatchEvent(Event event) {}
        @Override public void insertEvent(ServerEvent serverEvent) {}
        @Override public Integer getMaxEventId() throws ControllerException { return 0; }
        @Override public List<ServerEvent> getEvents(EventFilter filter, Integer offset, Integer limit) throws ControllerException { return null; }
        @Override public Long getEventCount(EventFilter filter) throws ControllerException { return 0L; }
        @Override public void removeAllEvents() {}
        @Override public String exportAllEvents() { return null; }
        @Override public String exportAndRemoveAllEvents() { return null; }
        @Override public List<ServerEvent> getEventsByAsc(EventFilter filter, Integer offset, Integer limit) throws ControllerException { return null; }
    }
}
