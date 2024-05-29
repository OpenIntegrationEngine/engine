package com.mirth.connect.connectors.core.tcp;

import java.util.Set;

import com.mirth.connect.donkey.model.channel.ConnectorPluginProperties;

public interface ITcpReceiverProperties extends TcpConnectorProperties {
	
    public static final int SAME_CONNECTION = 0;
    public static final int NEW_CONNECTION = 1;
    public static final int NEW_CONNECTION_ON_RECOVERY = 2;
	
	public int getRespondOnNewConnection();
	
	public String getResponseAddress();
	
	public Set<ConnectorPluginProperties> getResponseConnectorPluginProperties();
}
