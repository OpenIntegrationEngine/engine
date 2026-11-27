// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package com.mirth.connect.connectors.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.connectors.file.filesystems.FileSystemConnection;
import com.mirth.connect.connectors.file.filesystems.FileSystemConnectionFactory;

/**
 * Checks how FileConnector uses its connection pool when a File Writer does not keep connections
 * open, so that a commons-pool2 upgrade cannot change it unnoticed.
 */
public class FileConnectorConnectionPoolTest {

    private CountingFactory factory;
    private FileConnector fileConnector;
    private FileSystemConnectionOptions options;

    @Before
    public void setUp() {
        options = new FileSystemConnectionOptions(false, "user", "password", null);
        factory = new CountingFactory(options);

        FileDispatcherProperties properties = new FileDispatcherProperties();
        properties.setKeepConnectionOpen(false);

        fileConnector = new FileConnector("channelId", properties, null) {
            @Override
            protected FileSystemConnectionFactory getFileSystemConnectionFactory(FileSystemConnectionOptions fileSystemOptions) {
                return factory;
            }
        };
    }

    @After
    public void tearDown() throws Exception {
        fileConnector.doStop();
    }

    @Test
    public void destroyConnectionDoesNotOpenAnotherConnection() throws Exception {
        FileSystemConnection connection = fileConnector.getConnection(options);
        fileConnector.destroyConnection(connection, options);

        assertEquals(1, factory.created.get());
        verify(connection).destroy();
    }

    @Test
    public void destroyConnectionForgetsConnectionWhenFactoryFails() throws Exception {
        FileSystemConnection connection = fileConnector.getConnection(options);
        factory.fail.set(true);

        // Creating a connection after destroying one would fail, as when the server is down
        try {
            fileConnector.destroyConnection(connection, options);
        } catch (Exception e) {
            // Only the state of the connector matters here
        }

        // A forgotten connection is not disconnected again
        fileConnector.disconnect();
        verify(connection, never()).disconnect();
    }

    @Test
    public void getConnectionReplacesDisconnectedConnection() throws Exception {
        factory.disconnectNext.set(true);

        FileSystemConnection connection = fileConnector.getConnection(options);

        assertEquals(2, factory.created.get());
        assertSame(factory.last, connection);
        verify(factory.first).destroy();

        // Only the replacement is tracked as an open connection
        fileConnector.disconnect();
        verify(factory.first, never()).disconnect();
        verify(connection).disconnect();
    }

    private static class CountingFactory extends FileSystemConnectionFactory {
        final AtomicInteger created = new AtomicInteger();
        final AtomicBoolean fail = new AtomicBoolean();
        final AtomicBoolean disconnectNext = new AtomicBoolean();
        volatile FileSystemConnection first;
        volatile FileSystemConnection last;

        CountingFactory(FileSystemConnectionOptions options) {
            super(FileScheme.FTP, options, "localhost", 21, false, false, 0);
        }

        @Override
        public String getPoolKey() {
            return "test";
        }

        @Override
        public PooledObject<FileSystemConnection> makeObject() throws Exception {
            if (fail.get()) {
                throw new IOException("Connection refused");
            }
            FileSystemConnection connection = mock(FileSystemConnection.class);
            when(connection.isConnected()).thenReturn(!disconnectNext.getAndSet(false));
            when(connection.isValid()).thenReturn(true);
            if (first == null) {
                first = connection;
            }
            last = connection;
            created.incrementAndGet();
            return new DefaultPooledObject<>(connection);
        }
    }
}
