// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse;

import java.util.HashMap;
import java.util.Map;

import org.dcm4che2.net.Association;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.tool.dcmrcv.MirthDcmRcv;
import org.dcm4che2.tool.dcmsnd.MirthDcmSnd;

import com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DICOMConfiguration;
import com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DicomSender;
import com.mirth.connect.donkey.server.channel.Connector;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.util.MirthSSLUtil;

public class DefaultDICOMConfiguration implements Dcm2DICOMConfiguration {

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
    public void configureDcmRcv(MirthDcmRcv dcmrcv, DICOMReceiver connector,
                                DICOMReceiverProperties connectorProperties) throws Exception {
        DICOMConfigurationUtil.configureReceiver(new Dcm2DicomReceiver(dcmrcv), connector, connectorProperties, protocols);
    }

    @Override
    public void configureDcmSnd(MirthDcmSnd dcmsnd, DICOMDispatcher connector,
                                DICOMDispatcherProperties connectorProperties) throws Exception {
        DICOMConfigurationUtil.configureSender(new Dcm2DicomSender(dcmsnd), connector, connectorProperties, protocols);
    }

    @Override
    public Map<String, Object> getCStoreRequestInformation(Association association) {
        return new HashMap<String, Object>();
    }

    @Override
    public NetworkConnection createLegacyNetworkConnection() {
        return new NetworkConnection();
    }
}
