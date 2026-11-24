// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.connectors.file;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.mirth.connect.connectors.file.filesystems.S3Connection;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.model.message.attachment.AttachmentHandlerProvider;
import com.mirth.connect.donkey.testing.StaticOverride;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class FileDispatcherTest {

    private static StaticOverride factoryOverride;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);
        EventController eventController = mock(EventController.class);
        when(controllerFactory.createEventController()).thenReturn(eventController);
        ConfigurationController configurationController = mock(ConfigurationController.class);
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        factoryOverride = StaticOverride.install(ControllerFactory.class, "factory", controllerFactory);
    }

    @AfterClass
    public static void restoreControllerFactory() {
        if (factoryOverride != null) {
            factoryOverride.close();
        }
    }

    /*
     * The pool hands the same S3Connection to every message, so each write must carry the custom
     * headers of the message being written, not those of the message that created the connection.
     */
    @Test
    public void pooledS3ConnectionSendsEachMessagesHeaders() throws Exception {
        S3Client client = mockClient();
        TestFileDispatcher dispatcher = dispatcher(connection(client));

        assertEquals(Status.SENT, dispatcher.send(dispatcherProperties("1", false), new ConnectorMessage()).getStatus());
        assertEquals(Status.SENT, dispatcher.send(dispatcherProperties("2", false), new ConnectorMessage()).getStatus());

        assertEquals(List.of("dir/message.txt=1", "dir/message.txt=2"), uploads(client, 2));
    }

    /*
     * With Write to Temporary File on, the File Writer uploads a .tmp file and then moves it. On an
     * anonymous connection the move downloads the file and uploads it again with the custom headers,
     * so both uploads must carry the headers of the message being written.
     */
    @Test
    public void pooledS3ConnectionSendsEachMessagesHeadersThroughTemporaryFile() throws Exception {
        S3Client client = mockClient();
        when(client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class))).thenAnswer(invocation -> {
            ResponseTransformer<GetObjectResponse, ?> transformer = invocation.getArgument(1);
            return transformer.transform(GetObjectResponse.builder().contentLength(7L).build(), AbortableInputStream.create(new ByteArrayInputStream("message".getBytes())));
        });
        TestFileDispatcher dispatcher = dispatcher(connection(client));

        assertEquals(Status.SENT, dispatcher.send(dispatcherProperties("1", true), new ConnectorMessage()).getStatus());
        assertEquals(Status.SENT, dispatcher.send(dispatcherProperties("2", true), new ConnectorMessage()).getStatus());

        assertEquals(List.of("dir/message.txt.tmp=1", "dir/message.txt=1", "dir/message.txt.tmp=2", "dir/message.txt=2"), uploads(client, 4));
    }

    private static S3Client mockClient() {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());
        return client;
    }

    /*
     * An anonymous S3Connection that uses the given client. The client field is package-private to
     * the filesystems package, and FileConnector's getConnection is protected to this one, so the
     * client is set by reflection, after closing the real one the constructor built.
     */
    private static S3Connection connection(S3Client client) throws Exception {
        S3Connection connection = new S3Connection(new FileSystemConnectionOptions(true, null, null, schemeProperties("1")), 10000);
        Field clientField = S3Connection.class.getDeclaredField("client");
        clientField.setAccessible(true);
        ((S3Client) clientField.get(connection)).close();
        clientField.set(connection, client);
        return connection;
    }

    private static TestFileDispatcher dispatcher(S3Connection connection) throws Exception {
        FileConnector fileConnector = mock(FileConnector.class);
        URI uri = new URI("s3://bucket/dir");
        when(fileConnector.getEndpointURI(any(), any(), any(), anyBoolean())).thenReturn(uri);
        when(fileConnector.getPathPart(any())).thenReturn("bucket/dir");
        when(fileConnector.getConnection(any())).thenReturn(connection);

        TestFileDispatcher dispatcher = new TestFileDispatcher();
        dispatcher.setFileConnector(fileConnector);
        return dispatcher;
    }

    // The key and x-test-id header of each upload, in the order they were made
    private static List<String> uploads(S3Client client, int count) {
        ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client, times(count)).putObject(requests.capture(), any(RequestBody.class));
        List<String> uploads = new ArrayList<String>();
        for (PutObjectRequest request : requests.getAllValues()) {
            uploads.add(request.key() + "=" + request.metadata().get("x-test-id"));
        }
        return uploads;
    }

    private static S3SchemeProperties schemeProperties(String headerValue) {
        S3SchemeProperties schemeProperties = new S3SchemeProperties();
        schemeProperties.setRegion(Region.US_WEST_2.id());
        schemeProperties.getCustomHeaders().put("x-test-id", Collections.singletonList(headerValue));
        return schemeProperties;
    }

    private static FileDispatcherProperties dispatcherProperties(String headerValue, boolean temporary) {
        FileDispatcherProperties properties = new FileDispatcherProperties();
        properties.setScheme(FileScheme.S3);
        properties.setSchemeProperties(schemeProperties(headerValue));
        properties.setHost("bucket/dir");
        properties.setAnonymous(true);
        properties.setOutputPattern("message.txt");
        properties.setTemplate("message");
        properties.setKeepConnectionOpen(true);
        properties.setTemporary(temporary);
        return properties;
    }

    private static class TestFileDispatcher extends FileDispatcher {
        @Override
        protected AttachmentHandlerProvider getAttachmentHandlerProvider() {
            AttachmentHandlerProvider provider = mock(AttachmentHandlerProvider.class);
            when(provider.reAttachMessage(any(), any(), any(), anyBoolean(), anyBoolean())).thenReturn("message".getBytes());
            return provider;
        }
    }
}
