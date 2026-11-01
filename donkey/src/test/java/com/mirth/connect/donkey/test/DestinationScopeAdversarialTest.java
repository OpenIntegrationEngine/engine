/* Published under the Mozilla Public License 2.0. */
package com.mirth.connect.donkey.test;

import static org.junit.Assert.*;
import org.junit.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;
import com.mirth.connect.donkey.model.message.*;
import com.mirth.connect.donkey.server.channel.*;
import com.mirth.connect.donkey.server.channel.MessageTelemetry.Stage;
import com.mirth.connect.donkey.server.data.*;
import com.mirth.connect.donkey.test.MessageTelemetryHooksTest;
import com.mirth.connect.donkey.test.util.*;

public class DestinationScopeAdversarialTest {
    MessageTelemetryHooksTest fixture;TestChannel channel;Object recorder;
    @BeforeClass public static void start()throws Exception{MessageTelemetryHooksTest.startEngine();}
    @AfterClass public static void stop()throws Exception{MessageTelemetryHooksTest.stopEngine();}
    @Before public void setup()throws Exception{fixture=new MessageTelemetryHooksTest();}
    @After public void cleanup()throws Exception{if(fixture!=null)fixture.cleanup();}
    static Object get(Object target,String name)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    static void set(Object target,String name,Object value)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    static Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{
        Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);
        try{return method.invoke(target,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Error error)throw error;throw(Exception)e.getCause();}
    }
    void create(int destinations)throws Exception{call(fixture,"create",new Class<?>[]{boolean.class,int.class},true,destinations);channel=(TestChannel)get(fixture,"channel");recorder=get(fixture,"recorder");}
    void run()throws Exception{call(fixture,"runMessage",new Class<?>[0]);}
    TestDestinationConnector sender(Supplier<Response> send)throws Exception{return(TestDestinationConnector)call(fixture,"sender",new Class<?>[]{Supplier.class},send);}
    void queued(TestDestinationConnector destination,int retry)throws Exception{
        call(fixture,"queueProperties",new Class<?>[]{TestDestinationConnector.class},destination);
        ((TestConnectorProperties)destination.getConnectorProperties()).getDestinationConnectorProperties().setRetryIntervalMillis(retry);
    }
    ReentrantReadWriteLock queueLock(TestDestinationConnector destination)throws Exception{return(ReentrantReadWriteLock)get(destination.getQueue(),"statusUpdateLock");}
    boolean queueThread(){return Thread.currentThread() instanceof DestinationConnector.DestinationQueueThread;}
    Object current()throws Exception{return((ThreadLocal<?>)get(recorder,"current")).get();}
    boolean coarse()throws Exception{Object current=current();return current!=null&&get(current,"stage")==Stage.DESTINATION;}
    List<Object> records(Stage stage,boolean queue)throws Exception{
        var selected=new ArrayList<Object>();for(Object r:(List<?>)get(recorder,"records"))if(get(r,"stage")==stage&&(!queue||get(r,"thread") instanceof DestinationConnector.DestinationQueueThread))selected.add(r);return selected;
    }
    void after(Consumer<Object> action)throws Exception{set(recorder,"afterClose",action);}
    static Object invoke(Object target,Method method,Object[] args)throws Throwable{try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
    void closeFailure(TestDestinationConnector destination,RuntimeException failure,boolean requireLock)throws Exception{
        DonkeyDaoFactory original=channel.getDaoFactory();AtomicBoolean armed=new AtomicBoolean(true);
        channel.setDaoFactory((DonkeyDaoFactory)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DonkeyDaoFactory.class},(proxy,method,args)->{
            Object value=invoke(original,method,args);if(!(value instanceof DonkeyDao dao))return value;
            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DonkeyDao.class},(p,m,a)->{
                Object result=invoke(dao,m,a);
                if(m.getName().equals("close")&&queueThread()&&coarse()&&(!requireLock||queueLock(destination).getReadHoldCount()>0)&&armed.compareAndSet(true,false))throw failure;
                return result;
            });
        }));
    }

    @Test(timeout=15000) public void queueObservationClosesAfterFallbackStatusLockCleanupEvenWhenDaoCloseThrows()throws Exception{
        create(1);var destination=sender(()->new Response(Status.SENT,"reply"));queued(destination,1000);
        RuntimeException original=new IllegalStateException("review DAO close");closeFailure(destination,original,true);
        AtomicInteger heldAtClose=new AtomicInteger(-1);CountDownLatch closed=new CountDownLatch(1);
        after(record->{try{if(get(record,"stage")==Stage.DESTINATION&&queueThread()){heldAtClose.set(queueLock(destination).getReadHoldCount());closed.countDown();}}catch(Exception e){throw new RuntimeException(e);}});
        run();assertTrue(closed.await(5,TimeUnit.SECONDS));destination.stopQueue();
        List<Object> attempts=records(Stage.DESTINATION,true);assertEquals(1,attempts.size());assertSame(original,get(attempts.get(0),"failure"));
        assertEquals(0,queueLock(destination).getReadLockCount());
        System.out.println("STATUS_LOCK_AT_DESTINATION_CLOSE="+heldAtClose.get());
        assertEquals("coarse finish must follow the original fallback unlock",0,heldAtClose.get());
    }

    @Test(timeout=15000) public void caughtEngineFatalSurvivesLaterOrdinaryDaoCleanupAndCallbackFatal()throws Exception{
        create(1);ThreadDeath original=new ThreadDeath(),callback=new ThreadDeath();AtomicInteger sends=new AtomicInteger();
        var destination=sender(()->{if(sends.incrementAndGet()==1)throw original;return new Response(Status.SENT,"reply");});queued(destination,1000);
        RuntimeException cleanup=new IllegalStateException("review later DAO close");closeFailure(destination,cleanup,false);CountDownLatch closed=new CountDownLatch(1);
        after(record->{try{if(get(record,"stage")==Stage.DESTINATION&&queueThread()){closed.countDown();throw callback;}}catch(RuntimeException|Error e){throw e;}catch(Exception e){throw new RuntimeException(e);}});
        run();assertTrue(closed.await(5,TimeUnit.SECONDS));destination.stopQueue();
        assertSame(original,get(records(Stage.SEND,true).get(0),"failure"));
        Object coarseFailure=get(records(Stage.DESTINATION,true).get(0),"failure");
        System.out.println("COARSE_FAILURE_AFTER_CAUGHT_FATAL="+coarseFailure.getClass().getName());
        assertSame("the caught engine fatal must remain the observation's primary failure",original,coarseFailure);
        assertTrue(Arrays.asList(original.getSuppressed()).contains(callback));
    }

    @Test(timeout=15000) public void chainBodyFatalRemainsObservedWhenOrdinaryDaoCloseMasksIt() throws Exception {
        chainFatalCleanup(new ThreadDeath(), new IllegalStateException("later chain close"), new ThreadDeath());
    }

    @Test(timeout=15000) public void chainFirstFatalRemainsObservedWhenDaoCloseAlsoThrowsFatal() throws Exception {
        chainFatalCleanup(new OutOfMemoryError("original chain send"), new ThreadDeath(), new ThreadDeath());
    }

    private void chainFatalCleanup(Error original, Throwable cleanup, Error callback) throws Exception {
        create(1);
        AtomicBoolean failSend = new AtomicBoolean();
        sender(() -> { if (failSend.get()) throw original; return new Response(Status.SENT, "reply"); });
        run();
        ConnectorMessage message = (ConnectorMessage) get(records(Stage.DESTINATION, false).get(0), "message");
        var provider = channel.getDestinationChainProviders().get(0);
        DonkeyDaoFactory originalFactory = channel.getDaoFactory();
        Method setDao = DestinationChainProvider.class.getDeclaredMethod("setDaoFactory", DonkeyDaoFactory.class);
        setDao.setAccessible(true);
        setDao.invoke(provider, Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DonkeyDaoFactory.class}, (p, m, a) -> {
            Object value = invoke(originalFactory, m, a);
            if (!(value instanceof DonkeyDao dao)) return value;
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DonkeyDao.class}, (proxy, method, args) -> {
                Object result = invoke(dao, method, args);
                if (method.getName().equals("close")) throw cleanup;
                return result;
            });
        }));
        after(record -> {
            try { if (get(record, "stage") == Stage.DESTINATION) throw callback; }
            catch (RuntimeException | Error failure) { throw failure; }
            catch (Exception failure) { throw new RuntimeException(failure); }
        });
        failSend.set(true);
        message.setStatus(Status.RECEIVED);
        try {
            var chain = new DestinationChain(provider); chain.setMessage(message);
            try { chain.call(); fail("DAO close failure was lost"); }
            catch (RuntimeException | Error actual) { assertSame("original engine finally behavior", cleanup, actual); }
            assertSame(original, get(records(Stage.SEND, false).get(1), "failure"));
            Object destination = records(Stage.DESTINATION, false).get(1);
            assertSame(original, get(destination, "failure"));
            assertEquals(1, get(destination, "closed"));
            assertTrue(Arrays.asList(original.getSuppressed()).contains(callback));
            assertNull(current());
        } finally {
            setDao.invoke(provider, originalFactory);
            message.setStatus(Status.SENT);
        }
    }

    @Test(timeout=15000) public void unavailableQueueDaoIsObservedAndActualRetryUsesFreshScope()throws Exception{
        create(1);AtomicInteger sends=new AtomicInteger();var destination=sender(()->{sends.incrementAndGet();return new Response(Status.SENT,"reply");});queued(destination,1);
        DonkeyDaoFactory original=channel.getDaoFactory();AtomicBoolean armed=new AtomicBoolean(true);RuntimeException unavailable=new IllegalStateException("review unavailable queue DAO");
        channel.setDaoFactory((DonkeyDaoFactory)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DonkeyDaoFactory.class},(proxy,method,args)->{
            if(method.getName().equals("getDao")&&queueThread()&&coarse()&&armed.compareAndSet(true,false))throw unavailable;
            return invoke(original,method,args);
        }));
        run();call(fixture,"awaitQueuedCompletion",new Class<?>[0]);
        List<Object> attempts=records(Stage.DESTINATION,true);assertEquals(2,attempts.size());assertSame(unavailable,get(attempts.get(0),"failure"));
        assertEquals(Status.QUEUED,get(attempts.get(0),"status"));assertEquals(Status.SENT,get(attempts.get(1),"status"));assertEquals(1,sends.get());assertNotSame(attempts.get(0),attempts.get(1));
        for(Object r:attempts)assertEquals(1,get(r,"closed"));
    }

    @Test(timeout=15000) public void sequentialAndParallelDestinationsOwnIndependentCoarseScopes()throws Exception{
        create(3);var providers=channel.getDestinationChainProviders();var first=providers.get(0);var second=providers.get(1);
        first.addDestination(2,second.getDestinationConnectors().get(2));providers.remove(1);
        AtomicBoolean previousClosed=new AtomicBoolean();
        set(recorder,"beforeStage",(BiConsumer<Stage,ConnectorMessage>)(stage,message)->{if(stage==Stage.DESTINATION&&message.getMetaDataId()==2)try{
            previousClosed.set(records(Stage.DESTINATION,false).stream().filter(r->{try{return((ConnectorMessage)get(r,"message")).getMetaDataId()==1;}catch(Exception e){throw new RuntimeException(e);}}).allMatch(r->{try{return(Integer)get(r,"closed")==1;}catch(Exception e){throw new RuntimeException(e);}}));
        }catch(Exception e){throw new RuntimeException(e);}});
        run();assertTrue(previousClosed.get());assertEquals(3,records(Stage.DESTINATION,false).size());assertEquals(3,records(Stage.SEND,false).size());
        Object source=records(Stage.SOURCE,false).get(0);long id=((ConnectorMessage)get(source,"message")).getMessageId();
        for(Object coarse:records(Stage.DESTINATION,false)){assertSame(source,get(coarse,"parent"));assertEquals(1,get(coarse,"closed"));ConnectorMessage msg=(ConnectorMessage)get(coarse,"message");TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(),id,msg.getMetaDataId(),Status.SENT);}
        for(Object send:records(Stage.SEND,false)){Object parent=get(send,"parent");assertEquals(Stage.DESTINATION,get(parent,"stage"));assertSame(get(send,"message"),get(parent,"message"));}
    }
}
