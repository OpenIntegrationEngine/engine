package com.mirth.connect.connectors.dimse.dicom.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import org.dcm4che3.data.Tag;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.SourceConnector;

/**
 * Integration tests for error handling scenarios: connection refused,
 * receiver stop/restart, and dispatch errors.
 */
public class Dcm5ErrorHandlingIntegrationTest extends DicomIntegrationTestBase {

    @Test
    public void testConnectionRefused() throws Exception {
        int port = allocatePort();
        // No receiver started — port is unbound
        File dicomFile = createDicomTempFile("NoReceiver^Test", "ERR001");
        sender = configureDcm5Sender(port, dicomFile);

        try {
            sender.open();
            fail("open() should throw when no receiver is listening");
        } catch (Exception e) {
            // Expected: connection refused or similar
            assertNotNull(e);
        }

        // Verify clean shutdown after connection failure
        sender.close();
        sender.stop();
        sender = null;
    }

    @Test
    public void testReceiverStopAndRestart() throws Exception {
        int port = allocatePort();
        ArgumentCaptor<RawMessage> captor = ArgumentCaptor.forClass(RawMessage.class);
        SourceConnector mockConnector = createMockSourceConnector(captor);

        // Start receiver, send successfully
        receiver = startDcm5Receiver(port, mockConnector);
        File dicomFile = createDicomTempFile("StopRestart^Test", "SR001");
        sender = configureDcm5Sender(port, dicomFile);

        CapturingDimseRspHandler handler1 = new CapturingDimseRspHandler(1);
        sender.open();
        sender.send(handler1);
        assertTrue("First send should succeed", handler1.awaitResponses(5000));
        sender.close();
        sender.stop();
        sender = null;

        // Stop receiver
        receiver.stop();
        receiver = null;

        // Verify send fails when receiver is stopped
        File dicomFile2 = createDicomTempFile("StopRestart^Fail", "SR002");
        sender = configureDcm5Sender(port, dicomFile2);
        try {
            sender.open();
            fail("open() should throw when receiver is stopped");
        } catch (Exception e) {
            // Expected
        }
        sender.close();
        sender.stop();
        sender = null;

        // Restart receiver on same port
        receiver = startDcm5Receiver(port, mockConnector);
        File dicomFile3 = createDicomTempFile("StopRestart^Restart", "SR003");
        sender = configureDcm5Sender(port, dicomFile3);

        CapturingDimseRspHandler handler3 = new CapturingDimseRspHandler(1);
        sender.open();
        sender.send(handler3);
        assertTrue("Send after restart should succeed", handler3.awaitResponses(5000));
        assertEquals(Integer.valueOf(0), handler3.getStatuses().get(0));
    }

    @Test
    public void testReceiverRejectsOnDispatchError() throws Exception {
        int port = allocatePort();

        // Create a mock SourceConnector where dispatchRawMessage returns an error response
        Channel mockChannel = mock(Channel.class);
        when(mockChannel.getName()).thenReturn("errorChannel");

        SourceConnector mockConnector = mock(SourceConnector.class);
        when(mockConnector.getChannel()).thenReturn(mockChannel);
        when(mockConnector.getChannelId()).thenReturn("errorChannelId");

        Response errorResponse = new Response(
                com.mirth.connect.donkey.model.message.Status.ERROR,
                "Simulated dispatch failure");
        DispatchResult errorResult = mock(DispatchResult.class);
        when(errorResult.getSelectedResponse()).thenReturn(errorResponse);
        when(mockConnector.dispatchRawMessage(any(RawMessage.class))).thenReturn(errorResult);

        receiver = startDcm5Receiver(port, mockConnector);
        File dicomFile = createDicomTempFile("Error^Test", "DISP001");
        sender = configureDcm5Sender(port, dicomFile);

        CapturingDimseRspHandler handler = new CapturingDimseRspHandler(1);
        sender.open();
        sender.send(handler);
        assertTrue("DIMSE response should be received", handler.awaitResponses(5000));
        sender.close();

        // Receiver should have sent a non-success status back to the sender
        assertEquals(1, handler.getStatuses().size());
        int status = handler.getStatuses().get(0);
        assertTrue("Status should indicate failure (non-zero), got: 0x" + Integer.toHexString(status),
                status != 0);
    }
}
