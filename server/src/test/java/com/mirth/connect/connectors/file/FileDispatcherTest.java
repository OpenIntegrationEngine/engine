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

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.connectors.file.filesystems.S3Connection;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.model.message.attachment.AttachmentHandlerProvider;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class FileDispatcherTest {

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);
        EventController eventController = mock(EventController.class);
        when(controllerFactory.createEventController()).thenReturn(eventController);
        ConfigurationController configurationController = mock(ConfigurationController.class);
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);
    }

    /*
     * The pool hands the same S3Connection to every message, so each write must carry the custom
     * headers of the message being written, not those of the message that created the connection.
     */
    @Test
    public void pooledS3ConnectionSendsEachMessagesHeaders() throws Exception {
        S3Connection connection = new S3Connection(new FileSystemConnectionOptions(true, null, null, schemeProperties("1")), 10000);
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());
        // The client field is package-private to the filesystems package, and FileConnector's
        // getConnection is protected to this one, so the test sets the client by reflection.
        Field clientField = S3Connection.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connection, client);

        FileConnector fileConnector = mock(FileConnector.class);
        URI uri = new URI("s3://bucket/dir");
        when(fileConnector.getEndpointURI(any(), any(), any(), anyBoolean())).thenReturn(uri);
        when(fileConnector.getPathPart(any())).thenReturn("bucket/dir");
        when(fileConnector.getConnection(any())).thenReturn(connection);

        TestFileDispatcher dispatcher = new TestFileDispatcher();
        dispatcher.setFileConnector(fileConnector);

        assertEquals(Status.SENT, dispatcher.send(dispatcherProperties("1"), new ConnectorMessage()).getStatus());
        assertEquals(Status.SENT, dispatcher.send(dispatcherProperties("2"), new ConnectorMessage()).getStatus());

        ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client, times(2)).putObject(requests.capture(), any(RequestBody.class));
        List<String> sent = new ArrayList<String>();
        for (PutObjectRequest request : requests.getAllValues()) {
            sent.add(request.metadata().get("x-test-id"));
        }
        assertEquals(List.of("1", "2"), sent);
    }

    private static S3SchemeProperties schemeProperties(String headerValue) {
        S3SchemeProperties schemeProperties = new S3SchemeProperties();
        schemeProperties.setRegion(Region.US_WEST_2.id());
        schemeProperties.getCustomHeaders().put("x-test-id", Collections.singletonList(headerValue));
        return schemeProperties;
    }

    private static FileDispatcherProperties dispatcherProperties(String headerValue) {
        FileDispatcherProperties properties = new FileDispatcherProperties();
        properties.setScheme(FileScheme.S3);
        properties.setSchemeProperties(schemeProperties(headerValue));
        properties.setHost("bucket/dir");
        properties.setAnonymous(true);
        properties.setOutputPattern("message.txt");
        properties.setTemplate("message");
        properties.setKeepConnectionOpen(true);
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
