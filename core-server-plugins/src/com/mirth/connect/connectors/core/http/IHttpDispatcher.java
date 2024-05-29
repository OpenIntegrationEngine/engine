package com.mirth.connect.connectors.core.http;

import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;

import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.IDestinationConnector;

public interface IHttpDispatcher extends IDestinationConnector {
    
    public RegistryBuilder<ConnectionSocketFactory> getSocketFactoryRegistry();
    
    public void doReplaceConnectorProperties(ConnectorProperties connectorProperties, ConnectorMessage message);

    public Response doSend(ConnectorProperties connectorProperties, ConnectorMessage message) throws InterruptedException;
    
    public void doOnDeploy() throws ConnectorTaskException;
    
    public void doOnUndeploy() throws ConnectorTaskException;

    public void doOnStart() throws ConnectorTaskException;

    public void doOnStop() throws ConnectorTaskException;

    public void doOnHalt() throws ConnectorTaskException;
}
