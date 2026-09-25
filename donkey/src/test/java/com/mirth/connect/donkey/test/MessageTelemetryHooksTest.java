/* Published under the Mozilla Public License 2.0. */
package com.mirth.connect.donkey.test;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.*;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorProperties;
import com.mirth.connect.donkey.model.DonkeyException;
import com.mirth.connect.donkey.server.channel.DestinationConnector;
import com.mirth.connect.donkey.server.channel.components.ResponseTransformerException;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.DonkeyConfiguration;
import com.mirth.connect.donkey.server.DonkeyConnectionPools;
import com.mirth.connect.donkey.server.channel.FilterTransformerResult;
import com.mirth.connect.donkey.server.channel.DestinationChainProvider;
import com.mirth.connect.donkey.server.channel.DefaultChannelProcessLock;
import com.mirth.connect.donkey.server.channel.ResponseSelector;
import com.mirth.connect.donkey.server.queue.SourceQueue;
import com.mirth.connect.donkey.server.channel.MessageTelemetry;
import com.mirth.connect.donkey.server.channel.MessageTelemetry.Observation;
import com.mirth.connect.donkey.server.channel.MessageTelemetry.Stage;
import com.mirth.connect.donkey.server.channel.components.FilterTransformerException;
import com.mirth.connect.donkey.server.controllers.ChannelController;
import com.mirth.connect.donkey.test.util.*;

/** Real channel processing and private embedded Derby transactions, with a recording provider. */
public class MessageTelemetryHooksTest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static Donkey priorEngine;
    private static Object priorPools;
    private static Object priorChannelController;
    private static Object priorMessageController;
    private static AutoCloseable pool;
    private TestChannel channel;
    private AutoCloseable registration;
    private final Recorder recorder = new Recorder();

    @BeforeClass
    public static void startEngine() throws Exception {
        Field field = Donkey.class.getDeclaredField("instance");
        field.setAccessible(true);
        priorEngine = (Donkey) field.get(null);
        field.set(null, new Donkey());
        Field controller = ChannelController.class.getDeclaredField("instance");
        controller.setAccessible(true);
        priorChannelController = controller.get(null);
        controller.set(null, null);
        Field messages = com.mirth.connect.donkey.server.controllers.MessageController.class.getDeclaredField("instance");
        messages.setAccessible(true);
        priorMessageController = messages.get(null);
        messages.set(null, null);
        Field pools = DonkeyConnectionPools.class.getDeclaredField("instance");
        pools.setAccessible(true);
        priorPools = pools.get(null);
        var constructor = DonkeyConnectionPools.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        pools.set(null, constructor.newInstance());
        DonkeyConfiguration configuration = TestUtils.getDonkeyTestConfiguration();
        Properties properties = configuration.getDonkeyProperties();
        properties.setProperty("database", "derby");
        properties.setProperty("database.url", "jdbc:derby:memory:message-telemetry-hooks;create=true");
        properties.setProperty("database.driver", "org.apache.derby.jdbc.EmbeddedDriver");
        properties.remove("database.username");
        properties.remove("database.password");
        properties.setProperty("database.pool", "Hikari");
        properties.setProperty("database.jdbc4", "true");
        DonkeyConnectionPools.getInstance().init(properties);
        pool = (AutoCloseable) DonkeyConnectionPools.getInstance().getConnectionPool().getDataSource();
        Donkey.getInstance().startEngine(configuration);
    }

    @AfterClass
    public static void stopEngine() throws Exception {
        try { Donkey.getInstance().stopEngine(); }
        finally {
            try { if (pool != null) pool.close(); }
            finally {
                Field field = Donkey.class.getDeclaredField("instance");
                field.setAccessible(true);
                field.set(null, priorEngine);
                Field pools = DonkeyConnectionPools.class.getDeclaredField("instance");
                pools.setAccessible(true);
                pools.set(null, priorPools);
                Field controller = ChannelController.class.getDeclaredField("instance");
                controller.setAccessible(true);
                controller.set(null, priorChannelController);
                Field messages = com.mirth.connect.donkey.server.controllers.MessageController.class.getDeclaredField("instance");
                messages.setAccessible(true);
                messages.set(null, priorMessageController);
            }
        }
    }

    @After
    public void cleanup() throws Exception {
        Thread.interrupted();
        try {
            if (channel != null) {
                try { channel.stop(); }
                finally {
                    try { channel.undeploy(); }
                    finally { ChannelController.getInstance().removeChannel(channel.getChannelId()); }
                }
            }
        } finally {
            if (registration != null) registration.close();
        }
        assertNull(recorder.current.get());
        assertTrue("observation nesting/thread failures: " + recorder.errors, recorder.errors.isEmpty());
        for (Recorded record : recorder.records) assertEquals("each start closes once", 1, record.closed);
    }

    private void create(boolean synchronous, int chains) throws Exception {
        channel = TestUtils.createDefaultChannel("telemetry" + SEQUENCE.incrementAndGet(), "telemetry-test", synchronous, 0, 1);
        channel.setName(channel.getChannelId());
        channel.setSourceQueue(new SourceQueue());
        channel.setProcessLock(new DefaultChannelProcessLock(1));
        channel.setResponseSelector(new ResponseSelector(channel.getSourceConnector().getInboundDataType()));
        channel.getSourceConnector().setConnectorProperties(new TestListenerConnectorProperties());
        for (int id = 1; id <= chains; id++) {
            TestDestinationConnector destination = new TestDestinationConnector();
            destination.setChannel(channel);
            TestUtils.initDestinationConnector(destination, channel.getChannelId(), channel.getServerId(),
                    new TestConnectorProperties(), "destination" + id, new TestDataType(), new TestDataType(), new TestResponseTransformer(), id);
            destination.setMetaDataReplacer(channel.getSourceConnector().getMetaDataReplacer());
            destination.setMetaDataColumns(channel.getMetaDataColumns());
            destination.setFilterTransformerExecutor(TestUtils.createDefaultFilterTransformerExecutor());
            DestinationChainProvider chain = new DestinationChainProvider();
            chain.setChannelId(channel.getChannelId());
            chain.addDestination(id, destination);
            channel.addDestinationChainProvider(chain);
        }
        registration = MessageTelemetry.install(recorder);
    }

    private void runMessage() throws Exception {
        channel.deploy();
        channel.start(null);
        ((TestSourceConnector) channel.getSourceConnector()).readTestMessage("test content");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (recorder.records.stream().noneMatch(r -> r.stage == Stage.SOURCE && r.closed == 1)) {
            if (System.nanoTime() >= deadline) throw new AssertionError("source did not finish");
            Thread.sleep(5);
        }
    }

    @Test
    public void synchronousScopesCoverActualTransformsSendResponseAndRestoreCaller() throws Exception {
        create(true, 1);
        runMessage();
        assertHierarchy(1);
        Recorded source = recorder.only(Stage.SOURCE, 0);
        assertSame(Thread.currentThread(), source.thread);
        TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(), source.message.getMessageId(), 0, Status.TRANSFORMED);
        TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(), source.message.getMessageId(), 1, Status.SENT);
        assertEquals(Status.SENT, recorder.only(Stage.SEND, 1).status);
        assertEquals(Status.SENT, recorder.only(Stage.RESPONSE, 1).status);
    }

    @Test
    public void parallelDestinationWorkerSeesSourceAndHasIndependentMessageMap() throws Exception {
        create(true, 2);
        runMessage();
        assertHierarchy(2);
        Recorded source = recorder.only(Stage.SOURCE, 0);
        assertNotSame(source.thread, recorder.only(Stage.SEND, 1).thread);
        assertSame(source.thread, recorder.only(Stage.SEND, 2).thread);
        assertNotSame(recorder.only(Stage.SEND, 1).message.getChannelMap(), recorder.only(Stage.SEND, 2).message.getChannelMap());
    }

    @Test
    public void queuedSourceStartsScopeOnWorkerAndCompletesStoredMessage() throws Exception {
        create(false, 2);
        runMessage();
        assertHierarchy(2);
        Recorded source = recorder.only(Stage.SOURCE, 0);
        assertNotSame(Thread.currentThread(), source.thread);
        for (int id = 1; id <= 2; id++)
            TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(), source.message.getMessageId(), id, Status.SENT);
    }

    @Test
    public void preparationPersistsOnIngressBeforeQueuedSourceProcessingAndFreezesSourceKeys() throws Exception {
        preparationPersistsBeforeProcessing(true);
    }

    @Test
    public void preparationPersistsWithOnlyInitialRawDurabilityAndNoLaterMapStorage() throws Exception {
        preparationPersistsBeforeProcessing(false);
    }

    private void preparationPersistsBeforeProcessing(boolean storeMaps) throws Exception {
        create(false,1);
        channel.getStorageSettings().setStoreMaps(storeMaps);
        channel.getStorageSettings().setRawDurable(true);
        java.util.concurrent.atomic.AtomicReference<ConnectorMessage> prepared = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Thread> ingress = new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch processing = new CountDownLatch(1), release = new CountDownLatch(1);
        Map<String,String> carrier = new HashMap<>(); carrier.put("traceparent","content-free-fixture");
        recorder.preparation = (message,map) -> { assertNull(prepared.getAndSet(message)); ingress.set(Thread.currentThread()); map.put("oie.test.context",carrier); };
        recorder.sourceStarted = () -> {
            processing.countDown();
            try { assertTrue(release.await(5,TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        };
        channel.deploy(); channel.start(null);
        try {
            ((TestSourceConnector)channel.getSourceConnector()).readTestMessage("application content");
            assertTrue(processing.await(5,TimeUnit.SECONDS));
            ConnectorMessage message = prepared.get(); assertNotNull(message); assertSame(Thread.currentThread(),ingress.get());
            assertTrue(recorder.records.isEmpty());
            TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(),message.getMessageId(),0,Status.RECEIVED);
            var content = TestUtils.getMessageContent(channel.getChannelId(),message.getMessageId(),0,
                    com.mirth.connect.donkey.model.message.ContentType.SOURCE_MAP);
            Map<?,?> stored = Donkey.getInstance().getSerializer().deserialize(content.getContent(),Map.class);
            assertEquals(carrier,stored.get("oie.test.context"));
            try { message.getSourceMap().put("application-overwrite","forbidden"); fail("source keys must be read-only"); }
            catch (UnsupportedOperationException expected) { }
        } finally { release.countDown(); }
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while (recorder.records.stream().noneMatch(r->r.stage==Stage.SOURCE && r.closed==1)) {
            assertTrue(System.nanoTime()-until<0);Thread.sleep(2);
        }
        assertHierarchy(1);
        assertEquals(carrier,recorder.only(Stage.SEND,1).message.getSourceMap().get("oie.test.context"));
        TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(),prepared.get().getMessageId(),1,Status.SENT);
    }

    @Test
    public void failedPreparationDoesNotChangeActualMessageCompletion() throws Exception {
        create(true,1); recorder.preparation=(message,map)->{throw new LinkageError("private callback detail");};
        runMessage(); assertHierarchy(1);
        Recorded source=recorder.only(Stage.SOURCE,0);
        TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(),source.message.getMessageId(),1,Status.SENT);
    }

    @Test
    public void fatalPreparationUsesExistingDispatchErrorAndReleasesTransactionAndProcessLock() throws Exception {
        create(true,1);
        Error original = new ThreadDeath();
        java.util.concurrent.atomic.AtomicReference<ConnectorMessage> prepared = new java.util.concurrent.atomic.AtomicReference<>();
        recorder.preparation=(message,map)->{ prepared.set(message); throw original; };
        channel.deploy(); channel.start(null); String threadName=Thread.currentThread().getName();
        try { ((TestSourceConnector)channel.getSourceConnector()).readTestMessage("before failure"); fail("dispatch must fail"); }
        catch (com.mirth.connect.donkey.server.channel.ChannelException expected) { assertSame(original,expected.getCause()); }
        assertEquals(threadName,Thread.currentThread().getName()); assertTrue(recorder.records.isEmpty()); assertNotNull(prepared.get());
        com.mirth.connect.donkey.model.message.Message absent = new com.mirth.connect.donkey.model.message.Message();
        absent.setChannelId(channel.getChannelId()); absent.setMessageId(prepared.get().getMessageId());
        TestUtils.assertMessageDoesNotExist(absent);
        recorder.preparation=(message,map)->{};
        ((TestSourceConnector)channel.getSourceConnector()).readTestMessage("after failure");
        assertHierarchy(1);
        TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(),recorder.only(Stage.SOURCE,0).message.getMessageId(),1,Status.SENT);
    }

    @Test
    public void filteredSourceClosesEarlyWithoutStartingDestinations() throws Exception {
        create(true, 1);
        ((TestFilterTransformer) channel.getSourceConnector().getFilterTransformerExecutor().getFilterTransformer()).setFiltered(true);
        runMessage();
        assertEquals(2, recorder.records.size());
        Recorded source = recorder.only(Stage.SOURCE, 0);
        assertEquals(Status.FILTERED, source.status);
        TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(), source.message.getMessageId(), 0, Status.FILTERED);
    }

    @Test
    public void originalTransformFailureIsObservedAndEngineStillStoresError() throws Exception {
        create(true, 1);
        FilterTransformerException original = new FilterTransformerException("deliberate transform failure", null, "test");
        channel.getSourceConnector().getFilterTransformerExecutor().setFilterTransformer(new TestFilterTransformer() {
            @Override public FilterTransformerResult doFilterTransform(ConnectorMessage message) throws FilterTransformerException { throw original; }
        });
        runMessage();
        assertEquals(2, recorder.records.size());
        assertSame(original, recorder.only(Stage.TRANSFORM, 0).failure);
        Recorded source = recorder.only(Stage.SOURCE, 0);
        assertEquals(Status.ERROR, source.status);
        TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(), source.message.getMessageId(), 0, Status.ERROR);
    }

    @Test
    public void brokenCallbacksLeaveRealChannelAndTransactionOutcomeIntact() throws Exception {
        create(true, 2);
        recorder.failCallbacks = true;
        runMessage();
        assertHierarchy(2);
        Recorded source = recorder.only(Stage.SOURCE, 0);
        for (int id = 1; id <= 2; id++)
            TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(), source.message.getMessageId(), id, Status.SENT);
    }

    private List<Recorded> records(Stage stage, int connector) {
        List<Recorded> result = new ArrayList<>();
        for (Recorded record : recorder.records)
            if (record.stage == stage && record.message.getMetaDataId() == connector) result.add(record);
        return result;
    }
    private Recorded only(Stage stage, int connector) { return recorder.only(stage, connector); }
    private void status(int connector, Status status) throws Exception {
        TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(), only(Stage.SOURCE, 0).message.getMessageId(), connector, status);
    }
    private TestDestinationConnector sender(Supplier<Response> body) throws Exception {
        return sender(body, message -> { });
    }
    private TestDestinationConnector sender(Supplier<Response> body, java.util.function.Consumer<ConnectorMessage> replacement) throws Exception {
        TestDestinationConnector destination = new TestDestinationConnector() {
            @Override public Response send(ConnectorProperties properties, ConnectorMessage message) { return body.get(); }
            @Override public void replaceConnectorProperties(ConnectorProperties properties, ConnectorMessage message) { replacement.accept(message); }
        };
        destination.setChannel(channel);
        TestUtils.initDestinationConnector(destination, channel.getChannelId(), channel.getServerId(), new TestConnectorProperties(), "review destination", new TestDataType(), new TestDataType(), new TestResponseTransformer(), 1);
        destination.setMetaDataReplacer(channel.getSourceConnector().getMetaDataReplacer());
        destination.setMetaDataColumns(channel.getMetaDataColumns());
        destination.setFilterTransformerExecutor(TestUtils.createDefaultFilterTransformerExecutor());
        channel.getDestinationChainProviders().get(0).addDestination(1, destination);
        return destination;
    }

    private void awaitQueuedCompletion() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            try {
                status(1, Status.SENT);
                assertFalse(channel.getDestinationConnector(1).getQueue().isCheckedOut(only(Stage.SOURCE, 0).message.getMessageId()));
                assertTrue(recorder.records.stream().filter(r -> r.stage == Stage.DESTINATION).allMatch(r -> r.closed == 1));
                return;
            } catch (AssertionError waiting) {
                if (System.nanoTime() - deadline >= 0) throw waiting;
                Thread.sleep(5);
            }
        }
    }

    private DestinationConnectorProperties queueProperties(TestDestinationConnector destination) {
        var props = ((TestConnectorProperties) destination.getConnectorProperties()).getDestinationConnectorProperties();
        props.setQueueEnabled(true); props.setSendFirst(false); props.setRegenerateTemplate(true); props.setRetryIntervalMillis(1);
        return props;
    }

    @Test public void sentTraversalSkipsDestinationObservation() throws Exception {
        create(true, 1); runMessage();
        ConnectorMessage sent = only(Stage.SEND, 1).message;
        assertEquals(Status.SENT, sent.getStatus());
        int before = recorder.records.size();
        var chain = new com.mirth.connect.donkey.server.channel.DestinationChain(channel.getDestinationChainProviders().get(0));
        chain.setMessage(sent); assertEquals(1, chain.call().size());
        assertEquals(before, recorder.records.size());
        status(1, Status.SENT);
    }

    @Test public void actualPersistedPendingDestinationCreatesOnlyCoarseAndResponseScopes() throws Exception {
        create(true, 1); runMessage();
        ConnectorMessage sent = only(Stage.SEND, 1).message;
        var dao = channel.getDaoFactory().getDao();
        try { sent.setStatus(Status.PENDING); dao.updateStatus(sent, Status.SENT); dao.commit(true); }
        finally { dao.close(); }
        ConnectorMessage restored;
        dao = channel.getDaoFactory().getDao();
        try { restored = dao.getConnectorMessages(channel.getChannelId(), sent.getMessageId(), java.util.Set.of(1), true).get(0); }
        finally { dao.close(); }
        assertNotSame(sent, restored); assertEquals(Status.PENDING, restored.getStatus());
        int before = recorder.records.size();
        var chain = new com.mirth.connect.donkey.server.channel.DestinationChain(channel.getDestinationChainProviders().get(0));
        chain.setMessage(restored); chain.call();
        assertEquals(before + 2, recorder.records.size());
        Recorded coarse = records(Stage.DESTINATION, 1).get(1), response = records(Stage.RESPONSE, 1).get(1);
        assertNull(coarse.parent); assertSame(coarse, response.parent);
        assertEquals(1, records(Stage.SEND, 1).size()); assertEquals(1, records(Stage.SOURCE, 0).size());
        assertEquals(Status.SENT, coarse.status); status(1, Status.SENT);
    }

    @Test public void fatalQueueStartReleasesAcquiredMessageForRealRetry() throws Exception {
        create(true, 1);
        AtomicInteger starts = new AtomicInteger(), sends = new AtomicInteger();
        var destination = sender(() -> { sends.incrementAndGet(); return new Response(Status.SENT, "reply"); });
        queueProperties(destination);
        recorder.beforeStage = (stage, message) -> {
            if (stage == Stage.DESTINATION && Thread.currentThread() instanceof DestinationConnector.DestinationQueueThread
                    && starts.incrementAndGet() == 1) throw new ThreadDeath();
        };
        runMessage(); awaitQueuedCompletion();
        assertEquals(2, starts.get()); assertEquals(1, sends.get());
        assertEquals(2, records(Stage.DESTINATION, 1).size());
    }

    @Test public void queuePreSendFailureIsObservedEvenWhileStatusRemainsQueued() throws Exception {
        create(true, 1);
        AtomicInteger replacements = new AtomicInteger(), sends = new AtomicInteger();
        RuntimeException original = new IllegalStateException("private pre-send failure");
        var destination = sender(() -> { sends.incrementAndGet(); return new Response(Status.SENT, "reply"); }, message -> {
            if (Thread.currentThread() instanceof DestinationConnector.DestinationQueueThread
                    && replacements.incrementAndGet() == 1) throw original;
        });
        queueProperties(destination);
        runMessage(); awaitQueuedCompletion();
        List<Recorded> attempts = records(Stage.DESTINATION, 1).stream()
                .filter(r -> r.thread instanceof DestinationConnector.DestinationQueueThread).toList();
        assertEquals(2, attempts.size()); assertSame(original, attempts.get(0).failure);
        assertEquals(Status.QUEUED, attempts.get(0).status); assertEquals(Status.SENT, attempts.get(1).status);
        assertEquals(1, sends.get()); assertSame(attempts.get(1), only(Stage.SEND, 1).parent);
    }

    @Test public void fatalQueueCloseRunsAfterEntryAndStatusLockRelease() throws Exception {
        create(true, 1);
        var destination = sender(() -> new Response(Status.SENT, "reply"));
        queueProperties(destination);
        AtomicInteger closes = new AtomicInteger();
        recorder.afterClose = record -> {
            if (record.stage == Stage.DESTINATION && record.thread instanceof DestinationConnector.DestinationQueueThread) {
                try {
                    assertFalse(destination.getQueue().isCheckedOut(record.message.getMessageId()));
                    Field field = destination.getQueue().getClass().getDeclaredField("statusUpdateLock"); field.setAccessible(true);
                    assertEquals(0, ((java.util.concurrent.locks.ReentrantReadWriteLock) field.get(destination.getQueue())).getReadLockCount());
                    closes.incrementAndGet();
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                throw new ThreadDeath();
            }
        };
        runMessage(); awaitQueuedCompletion(); assertEquals(1, closes.get());
        assertEquals(Status.SENT, only(Stage.SEND, 1).status);
    }
    @Test public void checkedPreprocessorFailureStoresSourceErrorWithoutDestinationScopes() throws Exception {
        create(true, 1);
        DonkeyException original = new DonkeyException("preprocessor", null, "formatted");
        channel.setPreProcessor(message -> { throw original; }); runMessage();
        status(0, Status.ERROR); assertEquals(Status.ERROR, only(Stage.SOURCE,0).status);
        assertTrue(records(Stage.TRANSFORM,0).isEmpty()); assertTrue(records(Stage.SEND,1).isEmpty());
    }
    @Test public void filteredDestinationKeepsSourceTransformedAndSkipsSend() throws Exception {
        create(true, 1);
        ((TestFilterTransformer) channel.getDestinationConnector(1).getFilterTransformerExecutor().getFilterTransformer()).setFiltered(true);
        runMessage(); status(0,Status.TRANSFORMED); status(1,Status.FILTERED);
        assertEquals(Status.FILTERED, only(Stage.TRANSFORM,1).status);
        assertTrue(records(Stage.SEND,1).isEmpty()); assertTrue(records(Stage.RESPONSE,1).isEmpty());
    }
    @Test public void checkedDestinationTransformFailurePreservesCauseAndStoresError() throws Exception {
        create(true, 1);
        FilterTransformerException original = new FilterTransformerException("transform", null, "formatted");
        channel.getDestinationConnector(1).getFilterTransformerExecutor().setFilterTransformer(new TestFilterTransformer() {
            public FilterTransformerResult doFilterTransform(ConnectorMessage message) throws FilterTransformerException { throw original; }
        });
        runMessage(); status(0, Status.TRANSFORMED); status(1, Status.ERROR);
        assertSame(original, only(Stage.TRANSFORM,1).failure); assertTrue(records(Stage.SEND,1).isEmpty());
    }
    @Test public void rawSendStatusIsVisibleBeforeQueueRulesAlterIt() throws Exception {
        create(true, 1);
        sender(() -> new Response(Status.QUEUED,"reply")); runMessage();
        assertEquals(Status.QUEUED,only(Stage.SEND,1).status);
        assertEquals(Status.ERROR,only(Stage.RESPONSE,1).status);
        status(0,Status.TRANSFORMED); status(1,Status.ERROR);
    }
    @Test public void synchronousRetryProducesTwoSendsAndOneFinalResponse() throws Exception {
        create(true, 1);
        AtomicInteger calls = new AtomicInteger();
        TestDestinationConnector d = sender(() -> new Response(calls.incrementAndGet() == 1 ? Status.ERROR : Status.SENT,"reply"));
        DestinationConnectorProperties props = ((TestConnectorProperties)d.getConnectorProperties()).getDestinationConnectorProperties();
        props.setRetryCount(1); props.setRetryIntervalMillis(1); runMessage();
        assertEquals(2,calls.get()); List<Recorded> sends=records(Stage.SEND,1); assertEquals(2,sends.size());
        assertEquals(Status.ERROR,sends.get(0).status); assertEquals(Status.SENT,sends.get(1).status);
        assertEquals(Status.SENT,only(Stage.RESPONSE,1).status); status(1,Status.SENT);
        assertEquals(2,((ConnectorMessage)sends.get(1).message).getSendAttempts());
    }
    @Test public void checkedResponseTransformFailurePreservesCauseAndStoresError() throws Exception {
        create(true, 1);
        ResponseTransformerException original = new ResponseTransformerException("response",null,"formatted");
        channel.getDestinationConnector(1).getResponseTransformerExecutor().setResponseTransformer(new TestResponseTransformer() {
            public String doTransform(Response response, ConnectorMessage message) throws DonkeyException { throw original; }
        });
        runMessage(); status(0,Status.TRANSFORMED); status(1,Status.ERROR);
        assertSame(original,only(Stage.RESPONSE,1).failure);
        assertEquals(Status.SENT,only(Stage.SEND,1).status);
    }
    @Test public void validationExceptionIsSendFailureAndDoesNotRunResponseTransformer() throws Exception {
        create(true, 1);
        RuntimeException original = new IllegalStateException("validator failed");
        TestDestinationConnector d=sender(() -> new Response(Status.SENT,"reply","","",true));
        d.setResponseValidator((response,message) -> {throw original;}); runMessage();
        assertSame(original,only(Stage.SEND,1).failure);
        assertSame(original,only(Stage.DESTINATION,1).failure);
        assertTrue(records(Stage.RESPONSE,1).isEmpty()); status(1,Status.ERROR);
    }
    @Test public void queuedRetryRunsPerAttemptSendAndResponseOnQueueWorker() throws Exception {
        create(true, 1);
        AtomicInteger calls = new AtomicInteger();
        TestDestinationConnector d=sender(() -> new Response(calls.incrementAndGet()==1?Status.QUEUED:Status.SENT,"reply"));
        DestinationConnectorProperties props=((TestConnectorProperties)d.getConnectorProperties()).getDestinationConnectorProperties();
        props.setQueueEnabled(true); props.setSendFirst(false); props.setRegenerateTemplate(true); props.setRetryIntervalMillis(1); runMessage();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while (true) {
            if (System.nanoTime()>deadline) fail("queued retry not completed");
            try { status(1,Status.SENT); break; } catch (AssertionError waiting) { Thread.sleep(5); }
        }
        assertEquals(2,calls.get()); assertEquals(2,records(Stage.SEND,1).size()); assertEquals(2,records(Stage.RESPONSE,1).size());
        for(Recorded record:records(Stage.SEND,1)) assertTrue(record.thread instanceof DestinationConnector.DestinationQueueThread);
        assertEquals(Status.QUEUED,records(Stage.SEND,1).get(0).status);
        assertEquals(Status.SENT,records(Stage.SEND,1).get(1).status);
        List<Recorded> attempts = records(Stage.DESTINATION, 1).stream()
                .filter(r -> r.thread instanceof DestinationConnector.DestinationQueueThread).toList();
        assertEquals(2, attempts.size());
        for (int i = 0; i < 2; i++) assertSame(attempts.get(i), records(Stage.SEND, 1).get(i).parent);
    }

    @Test public void queuedTransformationBelongsToAcquiredDestinationScope() throws Exception {
        create(true, 1);
        var destination = sender(() -> new Response(Status.SENT, "reply"));
        queueProperties(destination).setIncludeFilterTransformer(true);
        runMessage(); awaitQueuedCompletion();
        List<Recorded> coarse = records(Stage.DESTINATION, 1);
        assertEquals(2, coarse.size());
        Recorded actual = coarse.stream().filter(r -> r.thread instanceof DestinationConnector.DestinationQueueThread).findFirst().orElseThrow();
        assertSame(actual, only(Stage.TRANSFORM, 1).parent);
        assertSame(actual, only(Stage.SEND, 1).parent);
        assertSame(actual, only(Stage.RESPONSE, 1).parent);
        assertEquals(Status.QUEUED, coarse.get(0).status);
    }

    @Test public void interruptedHeldRetryIsObservedBeforeAnySecondSend() throws Exception {
        create(true, 1);
        AtomicInteger starts = new AtomicInteger(), sends = new AtomicInteger();
        CountDownLatch retry = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Thread> retryThread = new java.util.concurrent.atomic.AtomicReference<>();
        var destination = sender(() -> { sends.incrementAndGet(); return new Response(Status.QUEUED, "reply"); });
        queueProperties(destination).setRetryIntervalMillis(30000);
        recorder.beforeStage = (stage, message) -> {
            if (stage == Stage.DESTINATION && Thread.currentThread() instanceof DestinationConnector.DestinationQueueThread
                    && starts.incrementAndGet() == 2) {
                retryThread.set(Thread.currentThread()); retry.countDown();
            }
        };
        runMessage(); assertTrue(retry.await(5, TimeUnit.SECONDS)); retryThread.get().interrupt();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (destination.isQueueThreadRunning()) {
            if (System.nanoTime() - deadline >= 0) fail("interrupted queue worker did not exit");
            Thread.sleep(5);
        }
        List<Recorded> attempts = records(Stage.DESTINATION, 1).stream()
                .filter(r -> r.thread instanceof DestinationConnector.DestinationQueueThread).toList();
        assertEquals(2, attempts.size()); assertEquals(1, sends.get());
        assertTrue(attempts.get(1).failure instanceof InterruptedException);
        assertEquals(Status.QUEUED, attempts.get(1).status); assertEquals(1, attempts.get(1).closed);
        status(1, Status.QUEUED);
    }

    private void assertHierarchy(int destinations) {
        assertEquals(2 + destinations * 4, recorder.records.size());
        Recorded source = recorder.only(Stage.SOURCE, 0);
        assertNull(source.parent);
        for (Recorded record : recorder.records) {
            if (record != source) {
                Recorded expected = record.message.getMetaDataId() == 0 || record.stage == Stage.DESTINATION
                        ? source : recorder.only(Stage.DESTINATION, record.message.getMetaDataId());
                assertSame("details belong to the actual destination scope", expected, record.parent);
            }
            assertEquals(1, record.closed);
        }
    }

    private static final class Recorded {
        final Stage stage;
        final ConnectorMessage message;
        final Recorded parent;
        final Thread thread = Thread.currentThread();
        volatile int closed;
        Status status;
        Throwable failure;
        Recorded(Stage stage, ConnectorMessage message, Recorded parent) { this.stage = stage; this.message = message; this.parent = parent; }
    }

    private static final class Recorder implements MessageTelemetry.Provider {
        final ThreadLocal<Recorded> current = new ThreadLocal<>();
        final List<Recorded> records = new CopyOnWriteArrayList<>();
        final List<String> errors = new CopyOnWriteArrayList<>();
        boolean failCallbacks;
        java.util.function.BiConsumer<ConnectorMessage,Map<String,Object>> preparation = (message,map) -> {};
        Runnable sourceStarted = () -> {};
        java.util.function.BiConsumer<Stage, ConnectorMessage> beforeStage = (stage, message) -> {};
        java.util.function.Consumer<Recorded> afterClose = record -> {};
        public void beforeStore(ConnectorMessage message, Map<String,Object> sourceMap) { preparation.accept(message,sourceMap); }
        public Observation start(Stage stage, ConnectorMessage message) {
            beforeStage.accept(stage, message);
            if (stage == Stage.SOURCE) sourceStarted.run();
            Recorded record = new Recorded(stage, message, current.get());
            records.add(record);
            current.set(record);
            return new Observation() {
                public void status(Status status) { record.status = status; if (failCallbacks) throw new IllegalStateException(); }
                public void failed(Throwable failure) { record.failure = failure; if (failCallbacks) throw new IllegalStateException(); }
                public void close() {
                    if (record.thread != Thread.currentThread() || current.get() != record) errors.add("scope restored on wrong thread or in wrong order");
                    current.set(record.parent);
                    if (record.status == null) record.status = message.getStatus();
                    try { afterClose.accept(record); }
                    finally { record.closed++; }
                    if (failCallbacks) throw new IllegalStateException();
                }
            };
        }
        public Supplier<Observation> capture() {
            Recorded captured = current.get();
            return () -> {
                Recorded previous = current.get();
                current.set(captured);
                return () -> {
                    if (current.get() != captured) errors.add("worker leaked a nested observation");
                    current.set(previous);
                };
            };
        }
        Recorded only(Stage stage, int connector) {
            List<Recorded> matches = new ArrayList<>();
            for (Recorded record : records) if (record.stage == stage && record.message.getMetaDataId() == connector) matches.add(record);
            assertEquals("one " + stage + " observation for connector " + connector, 1, matches.size());
            return matches.get(0);
        }
    }
}
