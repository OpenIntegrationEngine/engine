/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.serverlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;

import org.junit.Test;

public class ServerLogItemTest {

    @Test
    public void testDefaultConstructor() {
        ServerLogItem item = new ServerLogItem();
        assertNull(item.getServerId());
        assertNull(item.getId());
        assertNull(item.getLevel());
        assertNull(item.getDate());
        assertNull(item.getThreadName());
        assertNull(item.getCategory());
        assertNull(item.getLineNumber());
        assertNull(item.getMessage());
        assertNull(item.getThrowableInformation());
    }

    @Test
    public void testMessageConstructor() {
        String message = "Test Message";
        ServerLogItem item = new ServerLogItem(message);
        assertEquals(message, item.getMessage());
        assertNull(item.getId());
    }

    @Test
    public void testFullConstructor() {
        String serverId = "server-1";
        Long id = 100L;
        String level = "INFO";
        Date date = new Date();
        String threadName = "main";
        String category = "com.test";
        String lineNumber = "123";
        String message = "Test Message";
        String throwableInfo = "Exception stack trace";

        ServerLogItem item = new ServerLogItem(serverId, id, level, date, threadName, category, lineNumber, message,
                throwableInfo);

        assertEquals(serverId, item.getServerId());
        assertEquals(id, item.getId());
        assertEquals(level, item.getLevel());
        assertEquals(date, item.getDate());
        assertEquals(threadName, item.getThreadName());
        assertEquals(category, item.getCategory());
        assertEquals(lineNumber, item.getLineNumber());
        assertEquals(message, item.getMessage());
        assertEquals(throwableInfo, item.getThrowableInformation());
    }

    @Test
    public void testSetters() {
        ServerLogItem item = new ServerLogItem();

        item.setServerId("server-1");
        item.setId(1L);
        item.setLevel("ERROR");
        Date date = new Date();
        item.setDate(date);
        item.setThreadName("thread-1");
        item.setCategory("category");
        item.setLineNumber("50");
        item.setMessage("message");
        item.setThrowableInformation("stacktrace");

        assertEquals("server-1", item.getServerId());
        assertEquals(Long.valueOf(1), item.getId());
        assertEquals("ERROR", item.getLevel());
        assertEquals(date, item.getDate());
        assertEquals("thread-1", item.getThreadName());
        assertEquals("category", item.getCategory());
        assertEquals("50", item.getLineNumber());
        assertEquals("message", item.getMessage());
        assertEquals("stacktrace", item.getThrowableInformation());
    }

    @Test
    public void testSerialization() throws Exception {
        ServerLogItem original = new ServerLogItem("server-1", 1L, "INFO", new Date(), "main", "cat", "10", "msg",
                "err");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        ServerLogItem deserialized = (ServerLogItem) ois.readObject();

        assertEquals(original.getServerId(), deserialized.getServerId());
        assertEquals(original.getId(), deserialized.getId());
        assertEquals(original.getMessage(), deserialized.getMessage());
        assertEquals(original.getDate(), deserialized.getDate());
    }
}