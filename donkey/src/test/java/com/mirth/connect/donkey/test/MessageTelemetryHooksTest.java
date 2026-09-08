/* Published under the Mozilla Public License 2.0. */
package com.mirth.connect.donkey.test;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
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
        TestDestinationConnector destination = new TestDestinationConnector() {
            @Override public Response send(ConnectorProperties properties, ConnectorMessage message) { return body.get(); }
        };
        destination.setChannel(channel);
        TestUtils.initDestinationConnector(destination, channel.getChannelId(), channel.getServerId(), new TestConnectorProperties(), "review destination", new TestDataType(), new TestDataType(), new TestResponseTransformer(), 1);
        destination.setMetaDataReplacer(channel.getSourceConnector().getMetaDataReplacer());
        destination.setMetaDataColumns(channel.getMetaDataColumns());
        destination.setFilterTransformerExecutor(TestUtils.createDefaultFilterTransformerExecutor());
        channel.getDestinationChainProviders().get(0).addDestination(1, destination);
        return destination;
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
    }

    private void assertHierarchy(int destinations) {
        assertEquals(2 + destinations * 3, recorder.records.size());
        Recorded source = recorder.only(Stage.SOURCE, 0);
        assertNull(source.parent);
        for (Recorded record : recorder.records) {
            if (record != source) assertSame("all detailed scopes belong to source in this initial slice", source, record.parent);
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
        public Observation start(Stage stage, ConnectorMessage message) {
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
                    record.closed++;
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
