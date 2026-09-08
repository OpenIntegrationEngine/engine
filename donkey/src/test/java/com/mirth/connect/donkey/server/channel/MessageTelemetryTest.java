/* Published under the Mozilla Public License 2.0. */
package com.mirth.connect.donkey.server.channel;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Test;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.channel.MessageTelemetry.Observation;
import com.mirth.connect.donkey.server.channel.MessageTelemetry.Provider;
import com.mirth.connect.donkey.server.channel.MessageTelemetry.Stage;

public class MessageTelemetryTest {
    @FunctionalInterface
    private interface CheckedAction { void run() throws Throwable; }

    private static <T extends Throwable> T assertThrows(Class<T> type, CheckedAction action) {
        try { action.run(); }
        catch (Throwable failure) {
            if (type.isInstance(failure)) return type.cast(failure);
            throw new AssertionError("Expected " + type.getName(), failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }

    private final List<AutoCloseable> registrations = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private void install(Provider provider) { registrations.add(MessageTelemetry.install(provider)); }

    @After
    public void cleanup() throws Exception {
        Thread.interrupted();
        worker.shutdownNow();
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        for (AutoCloseable registration : registrations) registration.close();
    }

    @Test
    public void absentProviderReturnsSameNoopAndOriginalTask() throws Exception {
        Callable<Object> task = Object::new;
        Observation noop = MessageTelemetry.start(Stage.SOURCE, null);
        for (int i = 0; i < 1000; i++) {
            assertSame(noop, MessageTelemetry.start(Stage.SEND, null));
            assertSame(task, MessageTelemetry.wrap(task));
            noop.status(Status.ERROR);
            noop.failed(new IOException());
            noop.close();
        }
    }

    @Test
    public void registrationRejectsOverlapAndOldTokenCannotDetachReplacement() throws Exception {
        AtomicInteger first = new AtomicInteger(), second = new AtomicInteger();
        Provider firstProvider = (stage, message) -> { first.incrementAndGet(); return null; };
        install(firstProvider);
        assertThrows(IllegalStateException.class, () -> MessageTelemetry.install(firstProvider));
        MessageTelemetry.start(Stage.SOURCE, null).close();
        registrations.get(0).close();
        install((stage, message) -> { second.incrementAndGet(); return null; });
        registrations.get(0).close();
        MessageTelemetry.start(Stage.SOURCE, null).close();
        assertEquals(1, first.get());
        assertEquals(1, second.get());
        assertThrows(NullPointerException.class, () -> MessageTelemetry.install(null));
    }

    @Test
    public void callbacksCannotReplaceBusinessFailureOrSkipClose() throws Exception {
        AtomicInteger closed = new AtomicInteger();
        install((stage, message) -> new Observation() {
            public void status(Status status) { throw new AssertionError("ordinary callback failure"); }
            public void failed(Throwable cause) { throw new IllegalStateException("callback failure"); }
            public void close() { closed.incrementAndGet(); throw new IllegalStateException("close failure"); }
        });
        IOException original = new IOException("business failure");
        IOException thrown = assertThrows(IOException.class, () -> {
            try (Observation observation = MessageTelemetry.start(Stage.SOURCE, null)) {
                observation.status(Status.ERROR);
                observation.failed(original);
                throw original;
            }
        });
        assertSame(original, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        assertEquals(1, closed.get());
    }

    @Test
    public void startAndCaptureFailuresAreIsolated() throws Exception {
        install(new Provider() {
            public Observation start(Stage stage, ConnectorMessage message) { throw new LinkageError(); }
            public Supplier<Observation> capture() { throw new IllegalStateException(); }
        });
        MessageTelemetry.start(Stage.SOURCE, null).close();
        Callable<String> original = () -> "business result";
        assertSame(original, MessageTelemetry.wrap(original));
        assertEquals("business result", MessageTelemetry.wrap(original).call());
    }

    @Test
    public void activationAndCleanupFailuresStillRunTaskExactlyOnce() throws Exception {
        for (boolean failActivation : new boolean[] { true, false }) {
            AtomicInteger executions = new AtomicInteger(), activations = new AtomicInteger();
            install(new Provider() {
                public Observation start(Stage stage, ConnectorMessage message) { return null; }
                public Supplier<Observation> capture() {
                    return () -> {
                        activations.incrementAndGet();
                        if (failActivation) throw new IllegalArgumentException();
                        return () -> { throw new IllegalStateException(); };
                    };
                }
            });
            IOException original = new IOException("task failed");
            Callable<Void> wrapped = MessageTelemetry.wrap(() -> { executions.incrementAndGet(); throw original; });
            ExecutionException failure = assertThrows(ExecutionException.class, () -> worker.submit(wrapped).get(5, TimeUnit.SECONDS));
            assertSame(original, failure.getCause());
            assertEquals(1, activations.get());
            assertEquals(1, executions.get());
            registrations.get(registrations.size() - 1).close();
        }
    }

    @Test
    public void capturedContextRestoresWorkerOnSuccessFailureAndNestedExecutionAfterDetach() throws Exception {
        ThreadLocal<String> context = new ThreadLocal<>();
        AtomicInteger activeScopes = new AtomicInteger();
        install(contextProvider(context, activeScopes));
        worker.submit(() -> context.set("worker prior")).get(5, TimeUnit.SECONDS);
        context.set("request");
        Callable<String> success = MessageTelemetry.wrap(context::get);
        IOException original = new IOException();
        Callable<String> failure = MessageTelemetry.wrap(() -> {
            assertEquals("request", context.get());
            context.set("nested");
            assertEquals("nested", MessageTelemetry.wrap(context::get).call());
            throw original;
        });
        context.set("caller later");
        assertEquals("request", worker.submit(success).get(5, TimeUnit.SECONDS));
        assertSame(original, assertThrows(ExecutionException.class, () -> worker.submit(failure).get(5, TimeUnit.SECONDS)).getCause());
        assertEquals("worker prior", worker.submit(context::get).get(5, TimeUnit.SECONDS));
        registrations.get(0).close();
        assertEquals("request", worker.submit(success).get(5, TimeUnit.SECONDS));
        assertEquals("worker prior", worker.submit(context::get).get(5, TimeUnit.SECONDS));
        assertEquals("caller later", context.get());
        assertEquals(0, activeScopes.get());
    }

    @Test
    public void rejectedAndCancelledBeforeStartTasksNeverActivate() throws Exception {
        AtomicInteger scopes = new AtomicInteger();
        install(contextProvider(new ThreadLocal<>(), scopes));
        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<?> blocker = worker.submit(() -> { blocked.countDown(); release.await(); return null; });
        assertTrue(blocked.await(5, TimeUnit.SECONDS));
        AtomicInteger calls = new AtomicInteger();
        Future<?> cancelled = worker.submit(MessageTelemetry.wrap(calls::incrementAndGet));
        assertTrue(cancelled.cancel(false));
        release.countDown();
        blocker.get(5, TimeUnit.SECONDS);
        worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
        worker.shutdown();
        assertThrows(RejectedExecutionException.class, () -> worker.submit(MessageTelemetry.wrap(calls::incrementAndGet)));
        assertEquals(0, calls.get());
        assertEquals(0, scopes.get());
    }

    @Test
    public void cancellationAfterStartRestoresOnExecutingThread() throws Exception {
        ThreadLocal<String> context = new ThreadLocal<>();
        AtomicInteger scopes = new AtomicInteger();
        install(contextProvider(context, scopes));
        worker.submit(() -> context.set("prior")).get(5, TimeUnit.SECONDS);
        CountDownLatch started = new CountDownLatch(1);
        context.set("request");
        Future<?> task = worker.submit(MessageTelemetry.wrap(() -> {
            assertEquals("request", context.get());
            started.countDown();
            new CountDownLatch(1).await();
            return null;
        }));
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTrue(task.cancel(true));
        assertEquals("prior", worker.submit(context::get).get(5, TimeUnit.SECONDS));
        assertEquals(0, scopes.get());
    }

    @Test
    public void fatalCallbacksRetainThrowableIdentity() throws Exception {
        for (Error fatal : new Error[] { new OutOfMemoryError("synthetic"), new ThreadDeath() }) {
            install((stage, message) -> { throw fatal; });
            assertSame(fatal, assertThrows(fatal.getClass(), () -> MessageTelemetry.start(Stage.SOURCE, null)));
            registrations.get(registrations.size() - 1).close();
        }
    }

    private static Provider contextProvider(ThreadLocal<String> context, AtomicInteger scopes) {
        return new Provider() {
            public Observation start(Stage stage, ConnectorMessage message) { return null; }
            public Supplier<Observation> capture() {
                String captured = context.get();
                return () -> {
                    String prior = context.get();
                    Thread owner = Thread.currentThread();
                    context.set(captured);
                    scopes.incrementAndGet();
                    return () -> {
                        assertSame(owner, Thread.currentThread());
                        context.set(prior);
                        scopes.decrementAndGet();
                    };
                };
            }
        };
    }
}
