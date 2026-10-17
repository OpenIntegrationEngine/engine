/* MPL-2.0. Isolated additive native dispatch-context protocol controls. */
package com.mirth.connect.donkey.server.channel;

import static org.junit.Assert.*;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.util.xstream.XStreamSerializer;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;

public class MessageTelemetryDispatchTest {
    static final MessageTelemetry.Observation NONE = () -> {};
    static ConnectorMessage message(long id) { return new ConnectorMessage("channel", "name", id, 0, "server", Calendar.getInstance(), Status.RECEIVED); }
    static class Provider implements MessageTelemetry.Provider {
        final List<Object> seen = new CopyOnWriteArrayList<>();
        final AtomicInteger legacyStarts = new AtomicInteger(), legacyPrepares = new AtomicInteger(), prepares = new AtomicInteger();
        volatile Object prepared = new Object();
        public boolean supportsDispatchContext() { return true; }
        public MessageTelemetry.Observation start(MessageTelemetry.Stage stage, ConnectorMessage message) { legacyStarts.incrementAndGet(); return NONE; }
        public void beforeStore(ConnectorMessage message, Map<String,Object> map) { legacyPrepares.incrementAndGet(); }
        public Object prepareDispatch(ConnectorMessage message, Map<String,Object> map) { prepares.incrementAndGet(); return prepared; }
        public MessageTelemetry.Observation start(MessageTelemetry.Stage stage, ConnectorMessage message, Object proof) { seen.add(proof == null ? "absent" : proof); return NONE; }
    }

    @Test public void ownerSlotIsOpaqueTransientAndCompareCompleted() {
        var message=message(1); var owner=new Object(); var other=new Object(); var value=new Object();
        Object first=message.reserveTelemetryContext(owner); assertNull(message.getTelemetryContext(owner));
        Object second=message.reserveTelemetryContext(owner); assertFalse(message.completeTelemetryContext(first,value));
        assertFalse(message.completeTelemetryContext(new Object(),value)); assertTrue(message.completeTelemetryContext(second,value));
        assertSame(value,message.getTelemetryContext(owner)); assertNull(message.getTelemetryContext(other)); assertNull(message.getTelemetryContext(null));
        assertFalse(message.completeTelemetryContext(second,null)); assertSame(value,message.getTelemetryContext(owner));
        var replacement=message.reserveTelemetryContext(other); assertNull(message.getTelemetryContext(owner));
        assertTrue(message.completeTelemetryContext(replacement,null)); assertNull(message.getTelemetryContext(other));
    }

    @Test public void unchangedLegacyProviderStillUsesOnlyItsOriginalCallbacks() throws Exception {
        var provider=new Provider() { public boolean supportsDispatchContext(){return false;} };
        try(var registration=MessageTelemetry.install(provider)) {
            var message=message(1);MessageTelemetry.beforeStore(message,message.getSourceMap());MessageTelemetry.start(MessageTelemetry.Stage.SOURCE,message).close();
            assertEquals(1,provider.legacyPrepares.get()); assertEquals(1,provider.legacyStarts.get());assertEquals(0,provider.prepares.get()); assertTrue(provider.seen.isEmpty());
        }
    }

    @Test public void preparedContextReachesOnlyTheCurrentRegistrationAndCopyTarget() throws Exception {
        var provider=new Provider();var source=message(1);var target=message(1);
        try(var registration=MessageTelemetry.install(provider)) {
            MessageTelemetry.beforeStore(source,source.getSourceMap());MessageTelemetry.copyDispatchContext(source,target);
            MessageTelemetry.start(MessageTelemetry.Stage.SOURCE,source).close();MessageTelemetry.start(MessageTelemetry.Stage.DESTINATION,target).close();
            assertEquals(List.of(provider.prepared,provider.prepared),provider.seen);assertEquals(0,provider.legacyStarts.get());
        }
        provider.seen.clear();
        try(var next=MessageTelemetry.install(provider)) {
            MessageTelemetry.start(MessageTelemetry.Stage.SOURCE,source).close();MessageTelemetry.start(MessageTelemetry.Stage.DESTINATION,target).close();
            assertEquals(List.of("absent","absent"),provider.seen);
        }
    }

    @Test public void arbitraryApplicationSlotCannotForgeRegistrationOwnership() throws Exception {
        var provider=new Provider();var message=message(1);
        try(var registration=MessageTelemetry.install(provider)) {
            MessageTelemetry.beforeStore(message,message.getSourceMap());
            Object forged=message.reserveTelemetryContext(new Object());message.completeTelemetryContext(forged,provider.prepared);
            MessageTelemetry.start(MessageTelemetry.Stage.SOURCE,message).close();assertEquals(List.of("absent"),provider.seen);
        }
    }

    @Test public void copyingAnUnpreparedSourceCannotLeaveAStaleDestinationProof() throws Exception {
        var provider=new Provider();var source=message(1);var target=message(1);
        try(var registration=MessageTelemetry.install(provider)) {
            MessageTelemetry.beforeStore(target,target.getSourceMap());
            MessageTelemetry.copyDispatchContext(source,target);
            MessageTelemetry.start(MessageTelemetry.Stage.DESTINATION,target).close();assertEquals(List.of("absent"),provider.seen);
        }
    }

    @Test public void capabilityDeclarationFailureDoesNotLeaveARegistrationInstalled() throws Exception {
        var sentinel=new IllegalStateException("capability declaration");
        var broken=new Provider(){public boolean supportsDispatchContext(){throw sentinel;}};
        try{MessageTelemetry.install(broken);fail("declaration failure required");}catch(IllegalStateException actual){assertSame(sentinel,actual);}
        var next=new Provider();try(var registration=MessageTelemetry.install(next)){MessageTelemetry.start(MessageTelemetry.Stage.SOURCE,message(1)).close();assertEquals(List.of("absent"),next.seen);}
    }

    @Test public void oldObservationKeepsItsOwnerAfterReplacementEvenWithDispatchState() throws Exception {
        var closes=new AtomicInteger();var first=new Provider(){public MessageTelemetry.Observation start(MessageTelemetry.Stage stage,ConnectorMessage m,Object proof){assertSame(prepared,proof);return closes::incrementAndGet;}};
        var next=new Provider();var message=message(1);AutoCloseable a=MessageTelemetry.install(first),b=null;
        try {
            MessageTelemetry.beforeStore(message,message.getSourceMap());var observation=MessageTelemetry.start(MessageTelemetry.Stage.SOURCE,message);
            a.close();b=MessageTelemetry.install(next);MessageTelemetry.beforeStore(message,message.getSourceMap());observation.close();
            assertEquals(1,closes.get());MessageTelemetry.start(MessageTelemetry.Stage.SOURCE,message).close();assertEquals(List.of(next.prepared),next.seen);
        }finally{a.close();if(b!=null)b.close();}
    }

    @Test public void nativeSlotKeyIsNotAProviderOrRegistrationReference() throws Exception {
        var provider=new Provider();var message=message(1);
        try(var registration=MessageTelemetry.install(provider)) {
            MessageTelemetry.beforeStore(message,message.getSourceMap());
            var field=ConnectorMessage.class.getDeclaredField("telemetrySlot");field.setAccessible(true);Object slot=field.get(message);
            var key=slot.getClass().getDeclaredField("owner");key.setAccessible(true);assertEquals(Object.class,key.get(slot).getClass());
            assertTrue(java.lang.reflect.Modifier.isTransient(field.getModifiers()));
        }
    }

    @Test public void failedPreparationCannotReuseAnEarlierProof() throws Exception {
        var fail=new AtomicBoolean();var provider=new Provider(){public Object prepareDispatch(ConnectorMessage m,Map<String,Object> map){if(fail.get())throw new IllegalStateException("private failure");return super.prepareDispatch(m,map);}};
        var message=message(1);
        try(var registration=MessageTelemetry.install(provider)) {
            MessageTelemetry.beforeStore(message,message.getSourceMap());fail.set(true);MessageTelemetry.beforeStore(message,message.getSourceMap());
            MessageTelemetry.start(MessageTelemetry.Stage.SOURCE,message).close();assertEquals(List.of("absent"),provider.seen);
        }
    }

    @Test public void fatalPreparationRetiresOnlyItsReservationAndPropagatesIdentity() throws Exception {
        var sentinel=new OutOfMemoryError("synthetic preparation fatal");var provider=new Provider(){public Object prepareDispatch(ConnectorMessage m,Map<String,Object> map){throw sentinel;}};
        var message=message(1);
        try(var registration=MessageTelemetry.install(provider)) {
            try{MessageTelemetry.beforeStore(message,message.getSourceMap());fail("fatal required");}catch(OutOfMemoryError actual){assertSame(sentinel,actual);}
            MessageTelemetry.start(MessageTelemetry.Stage.SOURCE,message).close();assertEquals(List.of("absent"),provider.seen);
        }
    }

    @Test public void reentrantNewerPreparationWinsEvenIfOuterFails() throws Exception {
        for(boolean failOuter:new boolean[]{false,true}) {
            var inner=new Object();var outer=new Object();var depth=new AtomicInteger();
            var provider=new Provider(){public Object prepareDispatch(ConnectorMessage m,Map<String,Object> map){
                if(depth.getAndIncrement()==0){MessageTelemetry.beforeStore(m,map);if(failOuter)throw new IllegalStateException("outer failed");return outer;}return inner;
            }};
            var message=message(1);
            try(var registration=MessageTelemetry.install(provider)) {
                MessageTelemetry.beforeStore(message,message.getSourceMap());MessageTelemetry.start(MessageTelemetry.Stage.SOURCE,message).close();assertEquals(List.of(inner),provider.seen);
            }
        }
    }

    @Test public void inFlightOldPreparationCannotGrantTheReplacementProviderItsProof() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var old=new Provider(){public Object prepareDispatch(ConnectorMessage m,Map<String,Object> map){entered.countDown();try{assertTrue(release.await(2,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return prepared;}};
        var next=new Provider();var message=message(1);var worker=Executors.newSingleThreadExecutor();
        AutoCloseable first=MessageTelemetry.install(old),second=null;
        try {
            var future=worker.submit(()->MessageTelemetry.beforeStore(message,message.getSourceMap()));assertTrue(entered.await(2,TimeUnit.SECONDS));first.close();second=MessageTelemetry.install(next);
            MessageTelemetry.beforeStore(message,message.getSourceMap());release.countDown();future.get(2,TimeUnit.SECONDS);
            MessageTelemetry.start(MessageTelemetry.Stage.SOURCE,message).close();assertEquals(List.of(next.prepared),next.seen);
        }finally{release.countDown();first.close();if(second!=null)second.close();worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
    }

    @Test public void slotOwnerAndPayloadCannotMixAcrossConcurrentReplacements() throws Exception {
        var message=message(1);var a=new Object();var b=new Object();var worker=Executors.newFixedThreadPool(2);
        try {
            var first=worker.submit(()->{for(int i=0;i<10000;i++){Object slot=message.reserveTelemetryContext(a);message.completeTelemetryContext(slot,a);Object read=message.getTelemetryContext(a);assertTrue(read==null||read==a);}});
            var second=worker.submit(()->{for(int i=0;i<10000;i++){Object slot=message.reserveTelemetryContext(b);message.completeTelemetryContext(slot,b);Object read=message.getTelemetryContext(b);assertTrue(read==null||read==b);}});
            first.get(3,TimeUnit.SECONDS);second.get(3,TimeUnit.SECONDS);
        }finally{worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
    }

    @Test public void dispatchContextDoesNotEnterJavaOrXmlSerialization() throws Exception {
        var message=message(1);var serializer=new XStreamSerializer();String original=serializer.serialize(message);
        var key=new Object();message.completeTelemetryContext(message.reserveTelemetryContext(key),new Object());
        assertEquals(original,serializer.serialize(message));assertFalse(original.contains("telemetrySlot"));
        assertEquals(8556587410415698618L,ObjectStreamClass.lookup(ConnectorMessage.class).getSerialVersionUID());
        var bytes=new ByteArrayOutputStream();try(var output=new ObjectOutputStream(bytes)){output.writeObject(message);}
        ConnectorMessage restored;try(var input=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){restored=(ConnectorMessage)input.readObject();}
        assertNull(restored.getTelemetryContext(key));assertEquals(message.getMessageId(),restored.getMessageId());assertEquals(message.getSourceMap(),restored.getSourceMap());
    }
}
