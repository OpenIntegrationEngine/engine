/* Published under the Mozilla Public License 2.0. */
package com.mirth.connect.donkey.server.channel;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import java.util.*;
import com.mirth.connect.donkey.server.channel.*;
import com.mirth.connect.donkey.server.channel.MessageTelemetry.*;
import com.mirth.connect.donkey.model.message.*;
import com.mirth.connect.donkey.model.DonkeyException;
import com.mirth.connect.donkey.test.util.*;

public class MessageTelemetryAdversarialTest {
    interface Action { void run() throws Throwable; }
    static Throwable thrown(Action action) {
        try { action.run(); return null; } catch (Throwable t) { return t; }
    }
    static ConnectorMessage message() {
        ConnectorMessage message = new ConnectorMessage();
        message.setStatus(Status.RECEIVED);
        message.setRaw(new MessageContent("review", 1L, 0, ContentType.RAW, "content", "RAW", false));
        return message;
    }
    @Test public void allOrdinaryCallbackSurfacesPreserveOriginalFailure() throws Exception {
        for (int surface = 0; surface < 6; surface++) {
            final int selected = surface;
            for (Error callback : new Error[] {new AssertionError("sentinel"), new LinkageError("sentinel")}) {
                AtomicInteger called = new AtomicInteger(), closed = new AtomicInteger();
                Provider provider = new Provider() {
                    Observation observation() {
                        return new Observation() {
                            public void status(Status s) { if (selected == 3) throw callback; }
                            public void failed(Throwable t) { if (selected == 4) throw callback; }
                            public void close() { closed.incrementAndGet(); if (selected == 5) throw callback; }
                        };
                    }
                    public Observation start(Stage s, ConnectorMessage m) { if (selected == 0) throw callback; return observation(); }
                    public Supplier<Observation> capture() { if (selected == 1) throw callback; return () -> { if (selected == 2) throw callback; return () -> { closed.incrementAndGet(); }; }; }
                };
                try (AutoCloseable token = MessageTelemetry.install(provider)) {
                    for (Throwable original : new Throwable[] {new RuntimeException(), new AssertionError(), new OutOfMemoryError(), new ThreadDeath()}) {
                        Throwable actual = thrown(() -> MessageTelemetry.wrap(() -> {
                            called.incrementAndGet();
                            try (Observation observation = MessageTelemetry.start(Stage.SOURCE, null)) {
                                observation.status(Status.ERROR);
                                observation.failed(original);
                                if (original instanceof Error) throw (Error) original;
                                throw (RuntimeException) original;
                            }
                        }).call());
                        assertSame("surface " + selected, original, actual);
                        assertEquals(0, actual.getSuppressed().length);
                    }
                    assertEquals(4, called.get());
                    assertTrue(closed.get() >= 4);
                }
            }
        }
    }
    @Test public void fatalSurfacesPropagateAndCloseUsesJavaSuppression() throws Exception {
        for (int surface = 0; surface < 6; surface++) {
            final int selected = surface;
            for (Error callback : new Error[] {new OutOfMemoryError("sentinel"), new ThreadDeath()}) {
                AtomicInteger called = new AtomicInteger(), closed = new AtomicInteger();
                Provider provider = new Provider() {
                    Observation observation() { return new Observation() {
                        public void status(Status s) { if (selected == 3) throw callback; }
                        public void failed(Throwable cause) { if (selected == 4) throw callback; }
                        public void close() { closed.incrementAndGet(); if (selected == 5) throw callback; }
                    }; }
                    public Observation start(Stage s, ConnectorMessage m) { if (selected == 0) throw callback; return observation(); }
                    public Supplier<Observation> capture() { if (selected == 1) throw callback; return () -> { if (selected == 2) throw callback; return () -> { closed.incrementAndGet(); }; }; }
                };
                try (AutoCloseable token = MessageTelemetry.install(provider)) {
                    Throwable actual = thrown(() -> MessageTelemetry.wrap(() -> {
                        called.incrementAndGet();
                        try (Observation observation = MessageTelemetry.start(Stage.SOURCE, null)) {
                            observation.status(Status.ERROR);
                            observation.failed(new RuntimeException("business"));
                        }
                        return "done";
                    }).call());
                    assertSame("surface " + selected, callback, actual);
                    assertEquals(selected == 1 || selected == 2 ? 0 : 1, called.get());
                }
            }
        }
        Error business = new OutOfMemoryError("business"), cleanup = new ThreadDeath();
        try (AutoCloseable token = MessageTelemetry.install((s, m) -> () -> { throw cleanup; })) {
            Throwable actual = thrown(() -> { try (Observation o = MessageTelemetry.start(Stage.SOURCE, null)) { throw business; } });
            assertSame(business, actual);
            assertArrayEquals(new Throwable[] {cleanup}, actual.getSuppressed());
        }
    }
    @Test public void capturedActivationUsesOldProviderButNewStartsUseReplacement() throws Exception {
        AtomicInteger oldStarts = new AtomicInteger(), newStarts = new AtomicInteger(), oldActivations = new AtomicInteger();
        AutoCloseable old = MessageTelemetry.install(new Provider() {
            public Observation start(Stage stage, ConnectorMessage message) { oldStarts.incrementAndGet(); return null; }
            public Supplier<Observation> capture() { return () -> { oldActivations.incrementAndGet(); return () -> {}; }; }
        });
        Callable<Void> captured = MessageTelemetry.wrap(() -> { MessageTelemetry.start(Stage.TRANSFORM, null).close(); return null; });
        old.close();
        try (AutoCloseable replacement = MessageTelemetry.install((s, m) -> { newStarts.incrementAndGet(); return null; })) {
            captured.call(); old.close(); captured.call();
            assertEquals(0, oldStarts.get()); assertEquals(2, newStarts.get()); assertEquals(2, oldActivations.get());
        }
    }
    @Test public void concurrentInstallHasSingleWinnerAndStaleCloseIsHarmless() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<AutoCloseable> winners = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) futures.add(workers.submit(() -> { start.await(); try { winners.add(MessageTelemetry.install((s, m) -> null)); } catch (IllegalStateException expected) {} return null; }));
            start.countDown();
            for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
            assertEquals(1, winners.size());
            winners.get(0).close();
            AtomicInteger starts = new AtomicInteger();
            try (AutoCloseable replacement = MessageTelemetry.install((s, m) -> { starts.incrementAndGet(); return null; })) {
                for (int i = 0; i < 1000; i++) winners.get(0).close();
                MessageTelemetry.start(Stage.SOURCE, null).close(); assertEquals(1, starts.get());
            }
        } finally { for (AutoCloseable winner : winners) winner.close(); workers.shutdownNow(); assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS)); }
    }
    @Test public void originalFatalEngineFailureMustSurviveFatalFailedCallback() throws Exception {
        for (Error business : new Error[] {new OutOfMemoryError("engine-fatal"), new ThreadDeath()}) {
            Error callback = new OutOfMemoryError("callback-fatal");
            AtomicInteger closed = new AtomicInteger();
            FilterTransformerExecutor transform = TestUtils.createDefaultFilterTransformerExecutor();
            transform.setFilterTransformer(new TestFilterTransformer() {
                public FilterTransformerResult doFilterTransform(ConnectorMessage m) { throw business; }
            });
            try (AutoCloseable token = MessageTelemetry.install((s, m) -> new Observation() {
                public void failed(Throwable failure) { assertSame(business, failure); throw callback; }
                public void close() { closed.incrementAndGet(); }
            })) {
                Throwable actual = thrown(() -> transform.processConnectorMessage(message()));
                assertEquals(1, closed.get());
                assertSame("fatal business error was replaced; actual=" + actual + "; suppressed=" + Arrays.toString(actual.getSuppressed()), business, actual);
            }
        }
    }
}
