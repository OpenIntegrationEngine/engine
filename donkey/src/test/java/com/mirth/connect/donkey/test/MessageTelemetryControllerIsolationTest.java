/*
 * The software in this package is published under the terms of the MPL license a copy of which
 * has been included with this distribution in the LICENSE.txt file.
 */
package com.mirth.connect.donkey.test;

import static org.junit.Assert.*;
import org.junit.*;
import java.lang.reflect.Field;
import java.util.Collections;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.DonkeyConnectionPools;
import com.mirth.connect.donkey.server.controllers.ChannelController;
import com.mirth.connect.donkey.server.controllers.MessageController;
import com.mirth.connect.donkey.test.MessageTelemetryHooksTest;

public class MessageTelemetryControllerIsolationTest {
    private final Class<?>[] types={Donkey.class,DonkeyConnectionPools.class,ChannelController.class,MessageController.class};
    private Object[] prior;
    private static Field singleton(Class<?> type)throws Exception{Field field=type.getDeclaredField("instance");field.setAccessible(true);return field;}
    @Before public void isolateProbe()throws Exception{
        prior=new Object[types.length];
        for(int i=0;i<types.length;i++){Field field=singleton(types[i]);prior[i]=field.get(null);field.set(null,null);}
    }
    @After public void restoreProbe()throws Exception{
        for(int i=0;i<types.length;i++)singleton(types[i]).set(null,prior[i]);
    }
    private void runRealChannel()throws Exception{
        MessageTelemetryHooksTest fixture=new MessageTelemetryHooksTest();
        try{fixture.synchronousScopesCoverActualTransformsSendResponseAndRestoreCaller();}
        finally{fixture.cleanup();}
    }
    @Test public void completedFixtureRestoresOriginallyAbsentMessageController()throws Exception{
        assertNull(singleton(MessageController.class).get(null));
        try{MessageTelemetryHooksTest.startEngine();runRealChannel();}
        finally{MessageTelemetryHooksTest.stopEngine();}
        assertNull("fixture retained a controller pointing to its closed engine",singleton(MessageController.class).get(null));
    }
    @Test public void laterFixtureMessageOperationsMustUseItsOpenPool()throws Exception{
        try{MessageTelemetryHooksTest.startEngine();runRealChannel();}
        finally{MessageTelemetryHooksTest.stopEngine();}
        try{
            MessageTelemetryHooksTest.startEngine();
            MessageController.getInstance().deleteMessages("unused",Collections.emptyMap());
        }finally{MessageTelemetryHooksTest.stopEngine();}
    }
}
