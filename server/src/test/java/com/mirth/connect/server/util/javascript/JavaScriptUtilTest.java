/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util.javascript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.MessageContent;
import com.mirth.connect.donkey.server.channel.MessageTelemetry;
import com.mirth.connect.model.codetemplates.ContextType;
import com.mirth.connect.server.builders.JavaScriptBuilder;
import com.mirth.connect.server.controllers.CodeTemplateController;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.controllers.ExtensionController;
import com.mirth.connect.server.controllers.ScriptController;
import com.mirth.connect.server.util.CompiledScriptCache;

public class JavaScriptUtilTest {

    private static final String SCRIPT_ID = "JavaScriptUtilTest-script";

    @BeforeClass
    public static void setUpBeforeClass() {
        // Same mocked ControllerFactory pattern as FileReceiverTest, so this class is
        // self-sufficient regardless of which test classes ran (and injected) before it.
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        EventController eventController = mock(EventController.class);
        when(controllerFactory.createEventController()).thenReturn(eventController);

        ConfigurationController configurationController = mock(ConfigurationController.class);
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        ExtensionController extensionController = mock(ExtensionController.class);
        when(controllerFactory.createExtensionController()).thenReturn(extensionController);

        CodeTemplateController codeTemplateController = mock(CodeTemplateController.class);
        when(controllerFactory.createCodeTemplateController()).thenReturn(codeTemplateController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);

        /*
         * JavaScriptBuilder captures its controllers in static fields at class-load time. If an
         * earlier test class loaded it with a mocked factory that left them null, repair them so
         * generateGlobalSealedScript/appendCodeTemplates don't NPE.
         */
        JavaScriptBuilder.setControllersForTesting(extensionController, codeTemplateController);
    }

    private MirthContextFactory contextFactory() {
        return new MirthContextFactory(new URL[0], new HashSet<>(), false);
    }

    @After
    public void cleanup() {
        CompiledScriptCache.getInstance().removeCompiledScript(SCRIPT_ID);
    }

    @Test
    public void compileAndAddScriptWithNullScriptDoesNotCompile() throws Exception {
        boolean inserted = JavaScriptUtil.compileAndAddScript("channelId", contextFactory(), SCRIPT_ID, null, ContextType.CHANNEL_PREPROCESSOR);
        assertFalse(inserted);
        assertNull(CompiledScriptCache.getInstance().getCompiledScript(SCRIPT_ID));
    }

    @Test
    public void compileAndAddScriptWithBlankScriptDoesNotCompile() throws Exception {
        boolean inserted = JavaScriptUtil.compileAndAddScript("channelId", contextFactory(), SCRIPT_ID, "   \n", ContextType.CHANNEL_PREPROCESSOR);
        assertFalse(inserted);
        assertNull(CompiledScriptCache.getInstance().getCompiledScript(SCRIPT_ID));
    }

    @Test
    public void compileAndAddScriptWithRealScriptCompiles() throws Exception {
        boolean inserted = JavaScriptUtil.compileAndAddScript("channelId", contextFactory(), SCRIPT_ID, "var x = 1; return 'x';", ContextType.CHANNEL_PREPROCESSOR);
        assertTrue(inserted);
        assertNotNull(CompiledScriptCache.getInstance().getCompiledScript(SCRIPT_ID));
    }

    private static final String CHANNEL_ID = "JavaScriptUtilTest-channel";

    private ConnectorMessage messageWithRaw() {
        ConnectorMessage message = mock(ConnectorMessage.class);
        MessageContent rawContent = mock(MessageContent.class);
        when(message.getRaw()).thenReturn(rawContent);
        when(rawContent.getContent()).thenReturn("MSH|^~\\&|X");
        when(message.getChannelId()).thenReturn(CHANNEL_ID);
        return message;
    }

    private JavaScriptTask<Object> task(MirthContextFactory contextFactory) {
        return new JavaScriptTask<>(contextFactory, "JavaScriptUtilTest") {
            @Override
            public Object doCall() {
                return null;
            }
        };
    }

    @Test
    public void preprocessorReturningNothingYieldsNullNotUndefined() throws Exception {
        String scriptId = ScriptController.getScriptId(ScriptController.PREPROCESSOR_SCRIPT_KEY, CHANNEL_ID);
        MirthContextFactory contextFactory = contextFactory();
        try {
            JavaScriptUtil.compileAndAddScript(CHANNEL_ID, contextFactory, scriptId, "var unused = 1;", ContextType.CHANNEL_PREPROCESSOR);
            String result = JavaScriptUtil.executePreprocessorScripts(task(contextFactory), messageWithRaw(), new HashMap<>(), null);
            assertNull(result);
        } finally {
            CompiledScriptCache.getInstance().removeCompiledScript(scriptId);
        }
    }

    @Test
    public void preprocessorReturningStringYieldsThatString() throws Exception {
        String scriptId = ScriptController.getScriptId(ScriptController.PREPROCESSOR_SCRIPT_KEY, CHANNEL_ID);
        MirthContextFactory contextFactory = contextFactory();
        try {
            JavaScriptUtil.compileAndAddScript(CHANNEL_ID, contextFactory, scriptId, "return 'processed';", ContextType.CHANNEL_PREPROCESSOR);
            String result = JavaScriptUtil.executePreprocessorScripts(task(contextFactory), messageWithRaw(), new HashMap<>(), null);
            assertEquals("processed", result);
        } finally {
            CompiledScriptCache.getInstance().removeCompiledScript(scriptId);
        }
    }

    @Test
    public void telemetryContextTransfersThroughActualJavaScriptExecutorAndRestoresAfterFailure() throws Exception {
        withTelemetryWorker((context, worker) -> {
            worker.submit(() -> context.set("worker prior")).get(5, TimeUnit.SECONDS);
            context.set("source context");
            assertEquals("source context", JavaScriptUtil.execute(telemetryTask(() -> {
                assertTrue(Thread.currentThread() instanceof MirthJavaScriptThread);
                return context.get();
            })));
            RuntimeException original = new RuntimeException("script failure");
            try {
                JavaScriptUtil.execute(telemetryTask(() -> {
                    assertEquals("source context", context.get());
                    throw original;
                }));
                org.junit.Assert.fail("script must fail");
            } catch (JavaScriptExecutorException failure) { assertSame(original, failure.getCause()); }
            assertEquals("worker prior", worker.submit(context::get).get(5, TimeUnit.SECONDS));
            assertEquals("source context", context.get());
        });
    }

    @Test
    public void interruptedScriptCallerLeavesRestorationOnScriptWorker() throws Exception {
        withTelemetryWorker((context, worker) -> {
            worker.submit(() -> context.set("worker prior")).get(5, TimeUnit.SECONDS);
            CountDownLatch started = new CountDownLatch(1), finished = new CountDownLatch(1);
            AtomicReference<Throwable> unexpected = new AtomicReference<>();
            Thread caller = new Thread(() -> {
                context.set("interrupted request");
                try {
                    JavaScriptUtil.execute(telemetryTask(() -> {
                        assertEquals("interrupted request", context.get());
                        started.countDown();
                        new CountDownLatch(1).await();
                        return null;
                    }));
                    unexpected.set(new AssertionError("expected caller interruption"));
                } catch (InterruptedException expected) {
                    if (!Thread.currentThread().isInterrupted()) unexpected.set(new AssertionError("interrupt flag cleared"));
                } catch (Throwable failure) { unexpected.set(failure); }
                finally { finished.countDown(); }
            }, "telemetry-script-caller");
            try {
                caller.start();
                assertTrue(started.await(5, TimeUnit.SECONDS));
                caller.interrupt();
                assertTrue(finished.await(5, TimeUnit.SECONDS));
                assertNull(unexpected.get());
                assertEquals("worker prior", worker.submit(context::get).get(5, TimeUnit.SECONDS));
            } finally {
                caller.interrupt();
                caller.join(5000);
                assertFalse(caller.isAlive());
            }
        });
    }

    private <T> JavaScriptTask<T> telemetryTask(Callable<T> body) {
        return new JavaScriptTask<>(contextFactory(), "Telemetry test") {
            @Override public T doCall() throws Exception { return body.call(); }
        };
    }

    @FunctionalInterface
    private interface TelemetryScenario { void run(ThreadLocal<String> context, ExecutorService worker) throws Exception; }

    private void withTelemetryWorker(TelemetryScenario scenario) throws Exception {
        Field field = JavaScriptUtil.class.getDeclaredField("executor");
        field.setAccessible(true);
        ExecutorService priorExecutor = (ExecutorService) field.get(null);
        ExecutorService worker = Executors.newSingleThreadExecutor(new MirthJavaScriptThreadFactory());
        ThreadLocal<String> context = new ThreadLocal<>();
        AtomicReference<String> scopeError = new AtomicReference<>();
        field.set(null, worker);
        try (AutoCloseable registration = MessageTelemetry.install(new MessageTelemetry.Provider() {
            public MessageTelemetry.Observation start(MessageTelemetry.Stage stage, ConnectorMessage message) { return null; }
            public Supplier<MessageTelemetry.Observation> capture() {
                String captured = context.get();
                return () -> {
                    String previous = context.get();
                    Thread owner = Thread.currentThread();
                    context.set(captured);
                    return () -> {
                        if (Thread.currentThread() != owner) scopeError.set("scope closed on another thread");
                        context.set(previous);
                    };
                };
            }
        })) {
            scenario.run(context, worker);
            assertNull(scopeError.get());
        } finally {
            field.set(null, priorExecutor);
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
