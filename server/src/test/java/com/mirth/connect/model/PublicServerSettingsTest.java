package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.donkey.model.channel.MetaDataColumn;
import com.mirth.connect.donkey.model.channel.MetaDataColumnType;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ScriptController;

public class PublicServerSettingsTest {

    private static AutoCloseable factoryOverride;

    public static ServerSettings serverSettings = new ServerSettings();
    public static List<MetaDataColumn> COLUMNS;
    
    @BeforeClass
    public static void setup() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        ScriptController scriptController = mock(ScriptController.class);
        when(controllerFactory.createScriptController()).thenReturn(scriptController);

        factoryOverride = ControllerFactory.setFactoryForTesting(controllerFactory);
        serverSettings.setEnvironmentName("envName");
        serverSettings.setServerName("serverName");
        serverSettings.setDefaultMetaDataColumns(null);
        serverSettings.setQueueBufferSize(1000);
        COLUMNS = new ArrayList<MetaDataColumn>();
        COLUMNS.add(new MetaDataColumn("SOURCE", MetaDataColumnType.STRING, "mirth_source"));
        COLUMNS.add(new MetaDataColumn("TYPE", MetaDataColumnType.STRING, "mirth_type"));
        
    }

    @AfterClass
    public static void restoreControllerFactory() throws Exception {
        factoryOverride.close();
    }
    
    @Test
    public void defaultMetaDataColumnsTest() {
        PublicServerSettings publicServerSettings = new PublicServerSettings(serverSettings);      
        assertEquals(COLUMNS, publicServerSettings.getDefaultMetaDataColumns());
    }
    
    @Test
    public void queueBufferSizeTest() {
        PublicServerSettings publicServerSettings = new PublicServerSettings(serverSettings);
        assertTrue(1000 == publicServerSettings.getQueueBufferSize());
    }
    
}
