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

public class DestinationScopeParityTest {
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


    @Test(timeout=15000) public void rotatedQueueKeepsMessageOrderAndStartsFreshCoarseScope()throws Exception{
        create(1);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();List<Long> order=new CopyOnWriteArrayList<>();
        var destination=sender(()->{try{order.add(((ConnectorMessage)get(current(),"message")).getMessageId());if(calls.incrementAndGet()==1){entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("first send not released");return new Response(Status.QUEUED,"retry");}return new Response(Status.SENT,"reply");}catch(Exception e){throw new RuntimeException(e);}});
        queued(destination,1);((TestConnectorProperties)destination.getConnectorProperties()).getDestinationConnectorProperties().setRotate(true);
        try{run();assertTrue(entered.await(5,TimeUnit.SECONDS));((TestSourceConnector)channel.getSourceConnector()).readTestMessage("second");}finally{release.countDown();}
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(true){try{assertEquals(3,calls.get());for(Object source:records(Stage.SOURCE,false)){long id=((ConnectorMessage)get(source,"message")).getMessageId();TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(),id,1,Status.SENT);assertFalse(destination.getQueue().isCheckedOut(id));}for(Object coarse:records(Stage.DESTINATION,true))assertEquals(1,get(coarse,"closed"));break;}catch(AssertionError waiting){if(System.nanoTime()>end)throw waiting;Thread.sleep(5);}}
        destination.stopQueue();List<Object> sources=records(Stage.SOURCE,false);assertEquals(2,sources.size());long first=((ConnectorMessage)get(sources.get(0),"message")).getMessageId(),second=((ConnectorMessage)get(sources.get(1),"message")).getMessageId();
        assertEquals(Arrays.asList(first,second,first),order);List<Object> attempts=records(Stage.DESTINATION,true);assertEquals(3,attempts.size());assertNotSame(attempts.get(0),attempts.get(2));
        List<Object> sends=records(Stage.SEND,true);for(int i=0;i<3;i++){assertSame(attempts.get(i),get(sends.get(i),"parent"));assertNull(get(attempts.get(i),"parent"));}
    }

    static final class FailingCloneProperties extends TestConnectorProperties {
        transient AtomicInteger clones; transient RuntimeException failure;
        FailingCloneProperties(AtomicInteger clones,RuntimeException failure){this.clones=clones;this.failure=failure;}
        @Override public com.mirth.connect.donkey.model.channel.ConnectorProperties clone(){if(Thread.currentThread() instanceof DestinationConnector.DestinationQueueThread&&clones.incrementAndGet()==1)throw failure;return this;}
    }
    @Test(timeout=15000) public void queuePropertyCloneFailureClosesAndReleasesForRetry()throws Exception{
        create(1);AtomicInteger sends=new AtomicInteger(),clones=new AtomicInteger();RuntimeException original=new IllegalStateException("review properties");
        var destination=sender(()->{sends.incrementAndGet();return new Response(Status.SENT,"reply");});
        destination.setConnectorProperties(new FailingCloneProperties(clones,original));queued(destination,1);
        run();call(fixture,"awaitQueuedCompletion",new Class<?>[0]);List<Object> attempts=records(Stage.DESTINATION,true);assertEquals(2,attempts.size());assertSame(original,get(attempts.get(0),"failure"));assertEquals(Status.QUEUED,get(attempts.get(0),"status"));assertEquals(Status.SENT,get(attempts.get(1),"status"));assertEquals(1,sends.get());
    }

    @Test(timeout=15000) public void nullResponsePreservesActualEngineErrorAndHasNoResponseStage()throws Exception{
        create(1);AtomicInteger sends=new AtomicInteger();sender(()->{sends.incrementAndGet();return null;});run();Object send=records(Stage.SEND,false).get(0),coarse=records(Stage.DESTINATION,false).get(0);
        assertEquals(1,sends.get());assertTrue(get(send,"failure") instanceof NullPointerException);assertSame(get(send,"failure"),get(coarse,"failure"));assertEquals(Status.ERROR,get(coarse,"status"));assertEquals(0,records(Stage.RESPONSE,false).size());ConnectorMessage msg=(ConnectorMessage)get(coarse,"message");TestUtils.assertConnectorMessageStatusEquals(channel.getChannelId(),msg.getMessageId(),1,Status.ERROR);
    }

    @Test(timeout=15000) public void missingChainDaoDoesNotCreatePartialCoarseScopeOrRetryBusinessTask()throws Exception{
        create(1);run();Object first=records(Stage.DESTINATION,false).get(0);ConnectorMessage msg=(ConnectorMessage)get(first,"message");msg.setStatus(Status.PENDING);int before=records(Stage.DESTINATION,false).size();var provider=channel.getDestinationChainProviders().get(0);DonkeyDaoFactory original=channel.getDaoFactory();RuntimeException unavailable=new IllegalStateException("review missing chain DAO");
        Method setDao=DestinationChainProvider.class.getDeclaredMethod("setDaoFactory",DonkeyDaoFactory.class);setDao.setAccessible(true);setDao.invoke(provider,Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DonkeyDaoFactory.class},(p,m,a)->{if(m.getName().equals("getDao"))throw unavailable;return invoke(original,m,a);}));
        try{var chain=new DestinationChain(provider);chain.setMessage(msg);try{chain.call();fail("missing DAO accepted");}catch(RuntimeException actual){assertSame(unavailable,actual);}assertEquals(before,records(Stage.DESTINATION,false).size());assertNull(current());}finally{setDao.invoke(provider,original);msg.setStatus(Status.SENT);}
    }
}
