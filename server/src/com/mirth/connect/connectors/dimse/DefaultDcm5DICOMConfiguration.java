/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse;

import java.util.HashMap;
import java.util.Map;

import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;

import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DICOMConfiguration;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomSender;
import com.mirth.connect.donkey.server.channel.Connector;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.util.MirthSSLUtil;

public class DefaultDcm5DICOMConfiguration implements Dcm5DICOMConfiguration {

    private ConfigurationController configurationController = ControllerFactory.getFactory().createConfigurationController();
    private String[] protocols;

    @Override
    public void configureConnectorDeploy(Connector connector) throws Exception {
        if (connector instanceof DICOMReceiver) {
            protocols = MirthSSLUtil.getEnabledHttpsProtocols(configurationController.getHttpsServerProtocols());
        } else {
            protocols = MirthSSLUtil.getEnabledHttpsProtocols(configurationController.getHttpsClientProtocols());
        }
    }

    @Override
    public void configureDcm5Receiver(Dcm5DicomReceiver receiver, DICOMReceiver connector,
                                       DICOMReceiverProperties connectorProperties) throws Exception {
        DICOMConfigurationUtil.configureReceiver(receiver, connector, connectorProperties, protocols);
    }

    @Override
    public void configureDcm5Sender(Dcm5DicomSender sender, DICOMDispatcher connector,
                                     DICOMDispatcherProperties connectorProperties) throws Exception {
        DICOMConfigurationUtil.configureSender(sender, connector, connectorProperties, protocols);
    }

    @Override
    public Map<String, Object> getCStoreRequestInformation(Association association) {
        return new HashMap<String, Object>();
    }

    @Override
    public Connection createDcm5Connection() {
        return new Connection();
    }
}
