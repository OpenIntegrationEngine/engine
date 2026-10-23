package com.mirth.connect.connectors.ws;

import static org.junit.Assert.assertSame;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

public class WebServiceReceiverTest {

    @Test
    public void testWorkerThreadsUseGivenContextClassLoader() throws Exception {
        ClassLoader expected = new URLClassLoader(new URL[0], getClass().getClassLoader());
        CompletableFuture<ClassLoader> actual = new CompletableFuture<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = WebServiceReceiver.createExecutor(1, expected);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            actual.complete(Thread.currentThread().getContextClassLoader());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/").toURL().openConnection();
            connection.getResponseCode();
            connection.disconnect();

            assertSame(expected, actual.get(10, TimeUnit.SECONDS));
        } finally {
            server.stop(0);
            executor.shutdown();
        }
    }
}
