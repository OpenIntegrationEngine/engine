/*
 * The software in this package is published under the terms of the MPL license a copy of which
 * has been included with this distribution in the LICENSE.txt file.
 */
package com.mirth.connect.donkey.test;

import static org.junit.Assert.*;
import org.junit.*;
import java.lang.reflect.Field;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.DonkeyConnectionPools;
import com.mirth.connect.donkey.server.controllers.ChannelController;
import com.mirth.connect.donkey.test.MessageTelemetryHooksTest;

public class MessageTelemetryFixtureIsolationTest {
    private Object[] originals;
    private final Class<?>[] singletons={Donkey.class,DonkeyConnectionPools.class,ChannelController.class};
    private static Field singleton(Class<?> type)throws Exception{Field field=type.getDeclaredField("instance");field.setAccessible(true);return field;}
    @Before public void isolateProbe()throws Exception{
        originals=new Object[singletons.length];
        for(int i=0;i<singletons.length;i++){Field field=singleton(singletons[i]);originals[i]=field.get(null);field.set(null,null);}
    }
    @After public void restoreProbe()throws Exception{
        for(int i=0;i<singletons.length;i++)singleton(singletons[i]).set(null,originals[i]);
    }
    @Test public void fixtureCanStartAfterControllerWasInitializedByAnotherTest()throws Exception{
        Donkey originalEngine=Donkey.getInstance();
        ChannelController originalController=ChannelController.getInstance();
        try{MessageTelemetryHooksTest.startEngine();}
        finally{MessageTelemetryHooksTest.stopEngine();}
        assertSame(originalEngine,Donkey.getInstance());
        assertSame(originalController,ChannelController.getInstance());
    }
    @Test public void fixtureCanRestartAfterClosingItsFirstPool()throws Exception{
        for(int attempt=0;attempt<2;attempt++){
            try{MessageTelemetryHooksTest.startEngine();}
            finally{MessageTelemetryHooksTest.stopEngine();}
        }
    }
}
