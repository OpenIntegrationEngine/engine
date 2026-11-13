// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.server.alert;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.mirth.connect.donkey.model.event.Event;
import com.mirth.connect.donkey.testing.StaticOverride;
import com.mirth.connect.model.ServerEvent;
import com.mirth.connect.model.alert.AlertActionGroup;
import com.mirth.connect.model.alert.AlertModel;
import com.mirth.connect.server.controllers.AlertController;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.controllers.ExtensionController;

public class AlertWorkerTest {

    private static final String SERVER_ID = "0000-0000-0000";

    private StaticOverride factoryOverride;
    private ConfigurationController configurationController;
    private EventController eventController;
    private DefaultAlertWorker worker;
    private AlertActionGroup actionGroup;
    private Alert alert;

    @Before
    public void setup() {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);
        configurationController = mock(ConfigurationController.class);
        eventController = mock(EventController.class);
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);
        when(controllerFactory.createEventController()).thenReturn(eventController);
        when(controllerFactory.createExtensionController()).thenReturn(mock(ExtensionController.class));
        when(controllerFactory.createAlertController()).thenReturn(mock(AlertController.class));
        when(configurationController.getServerId()).thenReturn(SERVER_ID);
        factoryOverride = StaticOverride.install(ControllerFactory.class, "factory", controllerFactory);

        worker = new DefaultAlertWorker();
        actionGroup = new AlertActionGroup();
        AlertModel model = new AlertModel(null, actionGroup);
        model.setName("Test Alert");
        worker.enableAlert(model);
        alert = worker.getEnabledAlert(model.getId());
    }

    @After
    public void teardown() {
        if (worker != null) {
            worker.shutdown();
        }
        if (factoryOverride != null) {
            factoryOverride.close();
        }
    }

    @Test
    public void writesEventWhenServerNameIsNotSet() throws Exception {
        // getServerName() is not stubbed, so it returns null, as it does on a server with no name set
        Map<String, Object> context = alert.createContext();
        // Filter, transformer and alerts.sendAlert errors carry no connector type
        context.put("connectorType", null);
        context.put("channelName", "Throws Error");

        runTask(context);

        ServerEvent event = captureEvent();
        assertEquals("Alert Dispatched", event.getName());
        assertEquals(SERVER_ID, event.getServerId());
        Map<String, String> attributes = event.getAttributes();
        assertEquals("", attributes.get("serverName"));
        assertEquals("", attributes.get("connectorType"));
        assertEquals("Test Alert", attributes.get("alertName"));
        assertEquals("Throws Error", attributes.get("channelName"));
    }

    @Test
    public void writesServerNameWhenSet() throws Exception {
        when(configurationController.getServerName()).thenReturn("Server A");

        runTask(alert.createContext());

        assertEquals("Server A", captureEvent().getAttributes().get("serverName"));
    }

    @Test
    public void logsWhenTheEventCannotBeDispatched() throws Exception {
        when(configurationController.getServerName()).thenReturn("Server A");
        RuntimeException failure = new RuntimeException("event queue unavailable");
        doThrow(failure).when(eventController).dispatchEvent(any(Event.class));
        Logger logger = mock(Logger.class);
        worker.logger = logger;

        runTask(alert.createContext());

        verify(logger).error(eq("Error dispatching the Alert Dispatched event for alert \"Test Alert\" (" + alert.getModel().getId() + ")."),
                same(failure));
    }

    private void runTask(Map<String, Object> context) throws Exception {
        worker.new ActionTask(alert.getModel().getId(), actionGroup, context).call();
    }

    private ServerEvent captureEvent() {
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventController).dispatchEvent(captor.capture());
        return (ServerEvent) captor.getValue();
    }
}
