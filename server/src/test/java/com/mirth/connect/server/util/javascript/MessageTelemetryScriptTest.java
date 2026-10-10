/* Published under the Mozilla Public License 2.0. */
package com.mirth.connect.server.util.javascript;

import static org.junit.Assert.*;
import org.junit.*;
import org.mozilla.javascript.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
import java.net.URL;
import java.lang.reflect.Field;
import java.util.function.Supplier;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.server.channel.MessageTelemetry;
import com.mirth.connect.donkey.server.channel.MessageTelemetry.*;

public class MessageTelemetryScriptTest {
    private Field field;
    private ExecutorService prior, worker;
    private AutoCloseable registration;
    private final ThreadLocal<String> context = new ThreadLocal<>();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicReference<String> scopeError = new AtomicReference<>();
    public static class Bridge {
        final ThreadLocal<String> context; final CountDownLatch started;
        Bridge(ThreadLocal<String> context, CountDownLatch started) { this.context = context; this.started = started; }
        public String read() { return context.get(); }
        public void started() { started.countDown(); }
    }
    @BeforeClass public static void setupControllers() { JavaScriptUtilTest.setUpBeforeClass(); }
    @Before public void start() throws Exception {
        field = JavaScriptUtil.class.getDeclaredField("executor"); field.setAccessible(true);
        prior = (ExecutorService) field.get(null);
        worker = Executors.newSingleThreadExecutor(new MirthJavaScriptThreadFactory());
        field.set(null, worker);
        worker.submit(() -> context.set("worker prior")).get(5, TimeUnit.SECONDS);
        registration = MessageTelemetry.install(new Provider() {
            public Observation start(Stage s, ConnectorMessage m) { return null; }
            public Supplier<Observation> capture() {
                String captured = context.get();
                return () -> {
                    String previous = context.get(); Thread thread = Thread.currentThread();
                    context.set(captured); active.incrementAndGet();
                    return () -> { if (thread != Thread.currentThread()) scopeError.set("wrong owner"); context.set(previous); active.decrementAndGet(); };
                };
            }
        });
    }
    @After public void cleanup() throws Exception {
        try {
            assertEquals("worker prior", worker.submit(context::get).get(5, TimeUnit.SECONDS));
            assertEquals(0, active.get()); assertNull(scopeError.get());
        } finally {
            registration.close(); field.set(null, prior); worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
    private JavaScriptTask<Object> script(String script, CountDownLatch started) {
        return new JavaScriptTask<Object>(new MirthContextFactory(new URL[0], new HashSet<>(), false), "review script") {
            public Object doCall() throws Exception {
                Context cx = getContextFactory().enterContext();
                try {
                    Scriptable scope = cx.initStandardObjects();
                    ScriptableObject.putProperty(scope, "bridge", Context.javaToJS(new Bridge(context, started), scope));
                    return executeScript(cx.compileString(script, "telemetry-review", 1, null), scope);
                } finally { Context.exit(); }
            }
        };
    }
    @Test public void actualRhinoScriptReadsTransferredContextAndRestoresAfterRhinoFailure() throws Exception {
        context.set("source context");
        assertEquals("source context", JavaScriptUtil.execute(script("String(bridge.read());", new CountDownLatch(1))));
        try {
            JavaScriptUtil.execute(script("if (String(bridge.read()) !== 'source context') throw new Error('wrong context'); throw 'original script failure';", new CountDownLatch(1)));
            fail("script must throw");
        } catch (JavaScriptExecutorException failure) {
            assertTrue(failure.getCause() instanceof JavaScriptException);
            assertEquals("original script failure", ((JavaScriptException) failure.getCause()).getValue());
        }
        assertEquals("source context", context.get());
    }
    @Test public void realRhinoInfiniteLoopCancellationStopsViaOriginalTaskMonitor() throws Exception {
        CountDownLatch started = new CountDownLatch(1), done = new CountDownLatch(1);
        AtomicReference<Throwable> problem = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            context.set("interrupted context");
            try {
                JavaScriptUtil.execute(script("if (String(bridge.read()) !== 'interrupted context') throw new Error('wrong context'); bridge.started(); while (true) {}", started));
                problem.set(new AssertionError("script unexpectedly returned"));
            } catch (InterruptedException expected) { if (!Thread.currentThread().isInterrupted()) problem.set(new AssertionError("interrupt cleared")); }
            catch (Throwable t) { problem.set(t); }
            finally { done.countDown(); }
        });
        try {
            caller.start(); assertTrue(started.await(5, TimeUnit.SECONDS)); caller.interrupt();
            assertTrue(done.await(5, TimeUnit.SECONDS)); assertNull(problem.get());
            assertEquals("worker prior", worker.submit(context::get).get(5, TimeUnit.SECONDS));
        } finally { caller.interrupt(); caller.join(5000); assertFalse(caller.isAlive()); }
    }
}
