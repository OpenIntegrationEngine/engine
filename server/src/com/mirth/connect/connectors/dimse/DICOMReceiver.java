// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.connectors.dimse.dicom.DicomConstants;
import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory;
import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.donkey.model.event.ConnectionStatusEventType;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.event.ConnectionStatusEvent;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.util.TemplateValueReplacer;

public class DICOMReceiver extends SourceConnector {
    private Logger logger = LogManager.getLogger(this.getClass());
    protected DICOMReceiverProperties connectorProperties;
    protected EventController eventController = ControllerFactory.getFactory().createEventController();
    private ConfigurationController configurationController = ControllerFactory.getFactory().createConfigurationController();
    private TemplateValueReplacer replacer = new TemplateValueReplacer();
    protected DICOMConfiguration configuration = null;
    protected OieDicomReceiver dicomReceiver;

    @Override
    public void onDeploy() throws ConnectorTaskException {
        this.connectorProperties = (DICOMReceiverProperties) getConnectorProperties();

        // load the default configuration
        String configurationClass = configurationController.getProperty(connectorProperties.getProtocol(), "dicomConfigurationClass");

        configuration = DicomLibraryFactory.loadConfiguration(configurationClass);

        try {
            configuration.configureConnectorDeploy(this);
        } catch (Exception e) {
            throw new ConnectorTaskException(e);
        }

        dicomReceiver = createDicomReceiver(configuration);
    }

    @Override
    public void onUndeploy() throws ConnectorTaskException {}

    @Override
    public void onStart() throws ConnectorTaskException {
        try {
            dicomReceiver.setPort(NumberUtils.toInt(replacer.replaceValues(connectorProperties.getListenerConnectorProperties().getPort(), getChannelId(), getChannel().getName())));
            dicomReceiver.setHostname(replacer.replaceValues(connectorProperties.getListenerConnectorProperties().getHost(), getChannelId(), getChannel().getName()));

            String[] only_def_ts = { DicomConstants.IMPLICIT_VR_LITTLE_ENDIAN };
            String[] native_le_ts = { DicomConstants.EXPLICIT_VR_LITTLE_ENDIAN,
                                      DicomConstants.IMPLICIT_VR_LITTLE_ENDIAN };
            String[] native_ts = { DicomConstants.EXPLICIT_VR_LITTLE_ENDIAN,
                                   DicomConstants.EXPLICIT_VR_BIG_ENDIAN,
                                   DicomConstants.IMPLICIT_VR_LITTLE_ENDIAN };
            String[] non_retired_ts = {
                DicomConstants.JPEG_LS_LOSSLESS,
                DicomConstants.JPEG_LOSSLESS_SV1,
                DicomConstants.JPEG_LOSSLESS_NH14,
                DicomConstants.JPEG_2000_LOSSLESS,
                DicomConstants.DEFLATED_EXPLICIT_VR_LITTLE_ENDIAN,
                DicomConstants.RLE_LOSSLESS,
                DicomConstants.EXPLICIT_VR_LITTLE_ENDIAN,
                DicomConstants.EXPLICIT_VR_BIG_ENDIAN,
                DicomConstants.IMPLICIT_VR_LITTLE_ENDIAN,
                DicomConstants.JPEG_BASELINE,
                DicomConstants.JPEG_EXTENDED,
                DicomConstants.JPEG_LS_NEAR_LOSSLESS,
                DicomConstants.JPEG_2000,
                DicomConstants.MPEG2,
            };

            String destination = replacer.replaceValues(connectorProperties.getDest(), getChannelId(), getChannel().getName());
            if (StringUtils.isNotBlank(destination)) {
                dicomReceiver.setDestination(destination);
            }

            if (connectorProperties.isDefts()) {
                dicomReceiver.setTransferSyntax(only_def_ts);
            } else if (connectorProperties.isNativeData()) {
                if (connectorProperties.isBigEndian()) {
                    dicomReceiver.setTransferSyntax(native_ts);
                } else {
                    dicomReceiver.setTransferSyntax(native_le_ts);
                }
            } else if (connectorProperties.isBigEndian()) {
                dicomReceiver.setTransferSyntax(non_retired_ts);
            }

            String aeTitle = replacer.replaceValues(connectorProperties.getApplicationEntity(), getChannelId(), getChannel().getName());
            aeTitle = StringUtils.defaultIfBlank(aeTitle, null);
            dicomReceiver.setAEtitle(aeTitle);

            //TODO Allow variables
            int value = NumberUtils.toInt(connectorProperties.getReaper());
            if (value != 10) {
                dicomReceiver.setAssociationReaperPeriod(value);
            }

            value = NumberUtils.toInt(connectorProperties.getIdleTo());
            if (value != 60) {
                dicomReceiver.setIdleTimeout(value);
            }

            value = NumberUtils.toInt(connectorProperties.getRequestTo());
            if (value != 5) {
                dicomReceiver.setRequestTimeout(value);
            }

            value = NumberUtils.toInt(connectorProperties.getReleaseTo());
            if (value != 5) {
                dicomReceiver.setReleaseTimeout(value);
            }

            value = NumberUtils.toInt(connectorProperties.getSoCloseDelay());
            if (value != 50) {
                dicomReceiver.setSocketCloseDelay(value);
            }

            value = NumberUtils.toInt(connectorProperties.getRspDelay());
            if (value > 0) {
                dicomReceiver.setDimseRspDelay(value);
            }

            value = NumberUtils.toInt(connectorProperties.getRcvpdulen());
            if (value != 16) {
                dicomReceiver.setMaxPDULengthReceive(value);
            }

            value = NumberUtils.toInt(connectorProperties.getSndpdulen());
            if (value != 16) {
                dicomReceiver.setMaxPDULengthSend(value);
            }

            value = NumberUtils.toInt(connectorProperties.getSosndbuf());
            if (value > 0) {
                dicomReceiver.setSendBufferSize(value);
            }

            value = NumberUtils.toInt(connectorProperties.getSorcvbuf());
            if (value > 0) {
                dicomReceiver.setReceiveBufferSize(value);
            }

            value = NumberUtils.toInt(connectorProperties.getBufSize());
            if (value != 1) {
                dicomReceiver.setFileBufferSize(value);
            }

            dicomReceiver.setPackPDV(connectorProperties.isPdv1());
            dicomReceiver.setTcpNoDelay(!connectorProperties.isTcpDelay());

            value = NumberUtils.toInt(connectorProperties.getAsync());
            if (value > 0) {
                dicomReceiver.setMaxOpsPerformed(value);
            }

            dicomReceiver.initTransferCapability();

            configuration.configureReceiver(dicomReceiver, this, connectorProperties);

            // start the DICOM port
            dicomReceiver.start();

            eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getSourceName(), ConnectionStatusEventType.IDLE));
        } catch (Exception e) {
            throw new ConnectorTaskException("Failed to start DICOM Listener", e);
        }
    }

    @Override
    public void onStop() throws ConnectorTaskException {
        try {
            dicomReceiver.stop();
        } catch (Exception e) {
            logger.error("Unable to close DICOM port.", e);
        } finally {
            eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getSourceName(), ConnectionStatusEventType.DISCONNECTED));
        }

        logger.debug("closed DICOM port");
    }

    @Override
    public void onHalt() throws ConnectorTaskException {
        onStop();
    }

    @Override
    public void handleRecoveredResponse(DispatchResult dispatchResult) {
        finishDispatch(dispatchResult);
    }

    public TemplateValueReplacer getReplacer() {
        return replacer;
    }

    protected OieDicomReceiver createDicomReceiver(DICOMConfiguration configuration) {
        return DicomLibraryFactory.createReceiver(this, configuration);
    }
}
