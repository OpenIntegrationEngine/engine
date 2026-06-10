// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse;

import java.io.File;
import java.util.Iterator;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.connectors.dimse.dicom.DicomConstants;
import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory;
import com.mirth.connect.connectors.dimse.dicom.OieDicomElement;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;
import com.mirth.connect.connectors.dimse.dicom.OieDimseRspHandler;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.event.ConnectionStatusEventType;
import com.mirth.connect.donkey.model.event.ErrorEventType;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.DestinationConnector;
import com.mirth.connect.donkey.server.event.ConnectionStatusEvent;
import com.mirth.connect.donkey.server.event.ErrorEvent;
import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.util.TemplateValueReplacer;
import com.mirth.connect.util.ErrorMessageBuilder;

public class DICOMDispatcher extends DestinationConnector {
    private Logger logger = LogManager.getLogger(this.getClass());
    private DICOMDispatcherProperties connectorProperties;

    private EventController eventController = ControllerFactory.getFactory().createEventController();
    private ConfigurationController configurationController = ControllerFactory.getFactory().createConfigurationController();
    private TemplateValueReplacer replacer = new TemplateValueReplacer();
    protected DICOMConfiguration configuration = null;

    @Override
    public void onDeploy() throws ConnectorTaskException {
        this.connectorProperties = (DICOMDispatcherProperties) getConnectorProperties();

        // load the default configuration
        String configurationClass = configurationController.getProperty(connectorProperties.getProtocol(), "dicomConfigurationClass");

        configuration = DicomLibraryFactory.loadConfiguration(configurationClass);

        try {
            configuration.configureConnectorDeploy(this);
        } catch (Exception e) {
            throw new ConnectorTaskException(e);
        }
    }

    @Override
    public void onUndeploy() throws ConnectorTaskException {}

    @Override
    public void onStart() throws ConnectorTaskException {}

    @Override
    public void onStop() throws ConnectorTaskException {}

    @Override
    public void onHalt() throws ConnectorTaskException {}

    @Override
    public void replaceConnectorProperties(ConnectorProperties connectorProperties, ConnectorMessage connectorMessage) {
        DICOMDispatcherProperties dicomDispatcherProperties = (DICOMDispatcherProperties) connectorProperties;

        dicomDispatcherProperties.setHost(replacer.replaceValues(dicomDispatcherProperties.getHost(), connectorMessage));
        dicomDispatcherProperties.setPort(replacer.replaceValues(dicomDispatcherProperties.getPort(), connectorMessage));

        dicomDispatcherProperties.setLocalHost(replacer.replaceValues(dicomDispatcherProperties.getLocalHost(), connectorMessage));
        dicomDispatcherProperties.setLocalPort(replacer.replaceValues(dicomDispatcherProperties.getLocalPort(), connectorMessage));

        dicomDispatcherProperties.setApplicationEntity(replacer.replaceValues(dicomDispatcherProperties.getApplicationEntity(), connectorMessage));
        dicomDispatcherProperties.setLocalApplicationEntity(replacer.replaceValues(dicomDispatcherProperties.getLocalApplicationEntity(), connectorMessage));

        dicomDispatcherProperties.setUsername(replacer.replaceValues(dicomDispatcherProperties.getUsername(), connectorMessage));
        dicomDispatcherProperties.setPasscode(replacer.replaceValues(dicomDispatcherProperties.getPasscode(), connectorMessage));

        dicomDispatcherProperties.setTemplate(replacer.replaceValues(dicomDispatcherProperties.getTemplate(), connectorMessage));

        dicomDispatcherProperties.setKeyStore(replacer.replaceValues(dicomDispatcherProperties.getKeyStore(), connectorMessage));
        dicomDispatcherProperties.setKeyStorePW(replacer.replaceValues(dicomDispatcherProperties.getKeyStorePW(), connectorMessage));

        dicomDispatcherProperties.setTrustStore(replacer.replaceValues(dicomDispatcherProperties.getTrustStore(), connectorMessage));
        dicomDispatcherProperties.setTrustStorePW(replacer.replaceValues(dicomDispatcherProperties.getTrustStorePW(), connectorMessage));

        dicomDispatcherProperties.setKeyPW(replacer.replaceValues(dicomDispatcherProperties.getKeyPW(), connectorMessage));
    }

    @Override
    public Response send(ConnectorProperties connectorProperties, ConnectorMessage connectorMessage) {
        DICOMDispatcherProperties dicomDispatcherProperties = (DICOMDispatcherProperties) connectorProperties;

        String info = "Host: " + dicomDispatcherProperties.getHost();
        eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getDestinationName(), ConnectionStatusEventType.WRITING, info));

        String responseData = null;
        String responseError = null;
        String responseStatusMessage = null;
        Status responseStatus = Status.QUEUED;

        File tempFile = null;
        OieDicomSender dcmSnd = createDicomSender(configuration);

        try {
            tempFile = File.createTempFile("temp", "tmp");

            FileUtils.writeByteArrayToFile(tempFile, getAttachmentHandlerProvider().reAttachMessage(dicomDispatcherProperties.getTemplate(), connectorMessage, null, true, dicomDispatcherProperties.getDestinationConnectorProperties().isReattachAttachments()));

            dcmSnd.setCalledAET("DCMRCV");
            dcmSnd.setRemoteHost(dicomDispatcherProperties.getHost());
            dcmSnd.setRemotePort(NumberUtils.toInt(dicomDispatcherProperties.getPort()));

            if ((dicomDispatcherProperties.getApplicationEntity() != null) && !dicomDispatcherProperties.getApplicationEntity().equals("")) {
                dcmSnd.setCalledAET(dicomDispatcherProperties.getApplicationEntity());
            }

            if ((dicomDispatcherProperties.getLocalApplicationEntity() != null) && !dicomDispatcherProperties.getLocalApplicationEntity().equals("")) {
                dcmSnd.setCalling(dicomDispatcherProperties.getLocalApplicationEntity());
            }

            if ((dicomDispatcherProperties.getLocalHost() != null) && !dicomDispatcherProperties.getLocalHost().equals("")) {
                dcmSnd.setLocalHost(dicomDispatcherProperties.getLocalHost());
                dcmSnd.setLocalPort(NumberUtils.toInt(dicomDispatcherProperties.getLocalPort()));
            }

            dcmSnd.addFile(tempFile);

            //TODO Allow variables
            int value = NumberUtils.toInt(dicomDispatcherProperties.getAcceptTo());
            if (value != 5)
                dcmSnd.setAcceptTimeout(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getAsync());
            if (value > 0)
                dcmSnd.setMaxOpsInvoked(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getBufSize());
            if (value != 1)
                dcmSnd.setTranscoderBufferSize(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getConnectTo());
            if (value > 0)
                dcmSnd.setConnectTimeout(value);
            if (dicomDispatcherProperties.getPriority().equals("med"))
                dcmSnd.setPriority(0);
            else if (dicomDispatcherProperties.getPriority().equals("low"))
                dcmSnd.setPriority(1);
            else if (dicomDispatcherProperties.getPriority().equals("high"))
                dcmSnd.setPriority(2);
            if (dicomDispatcherProperties.getUsername() != null && !dicomDispatcherProperties.getUsername().equals("")) {
                String username = dicomDispatcherProperties.getUsername();
                String passcode = dicomDispatcherProperties.getPasscode();
                dcmSnd.setUserIdentity(username, passcode, dicomDispatcherProperties.isUidnegrsp());
            }
            dcmSnd.setPackPDV(dicomDispatcherProperties.isPdv1());

            value = NumberUtils.toInt(dicomDispatcherProperties.getRcvpdulen());
            if (value != 16)
                dcmSnd.setMaxPDULengthReceive(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getReaper());
            if (value != 10)
                dcmSnd.setAssociationReaperPeriod(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getReleaseTo());
            if (value != 5)
                dcmSnd.setReleaseTimeout(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getRspTo());
            if (value != 60)
                dcmSnd.setDimseRspTimeout(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getShutdownDelay());
            if (value != 1000)
                dcmSnd.setShutdownDelay(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getSndpdulen());
            if (value != 16)
                dcmSnd.setMaxPDULengthSend(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getSoCloseDelay());
            if (value != 50)
                dcmSnd.setSocketCloseDelay(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getSorcvbuf());
            if (value > 0)
                dcmSnd.setReceiveBufferSize(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getSosndbuf());
            if (value > 0)
                dcmSnd.setSendBufferSize(value);

            dcmSnd.setStorageCommitment(dicomDispatcherProperties.isStgcmt());
            dcmSnd.setTcpNoDelay(!dicomDispatcherProperties.isTcpDelay());

            configuration.configureSender(dcmSnd, this, dicomDispatcherProperties);

            dcmSnd.setOfferDefaultTransferSyntaxInSeparatePresentationContext(dicomDispatcherProperties.isTs1());
            dcmSnd.configureTransferCapability();
            dcmSnd.start();

            dcmSnd.open();
            CommandDataDimseRSPHandler rspHandler = new CommandDataDimseRSPHandler();
            dcmSnd.send(rspHandler);

            boolean storageCommitmentFailed = false;
            String storageCommitmentFailureReason = "Unknown";
            if (dcmSnd.isStorageCommitment()) {
                if (dcmSnd.commit()) {
                    OieDicomObject cmtrslt = dcmSnd.waitForStgCmtResult();
                    if (cmtrslt != null) {
                        OieDicomElement failedSOPSq = cmtrslt.get(DicomConstants.TAG_FAILED_SOP_SEQUENCE);
                        if (failedSOPSq != null && failedSOPSq.countItems() > 0) {
                            storageCommitmentFailed = true;
                            OieDicomObject failedSOPItem = failedSOPSq.getDicomObject();
                            if (failedSOPItem != null) {
                                int failureReason = failedSOPItem.getInt(DicomConstants.TAG_FAILURE_REASON);
                                if (failureReason != 0) {
                                    storageCommitmentFailureReason = String.valueOf(failureReason);
                                }
                            }
                        }
                    } else {
                        logger.warn("Storage commitment result was null — remote SCP may not have responded");
                        storageCommitmentFailed = true;
                    }
                } else {
                    storageCommitmentFailed = true;
                }
            }

            dcmSnd.close();

            int status = rspHandler.getStatus();

            if (status == DicomConstants.STATUS_SUCCESS) {
                responseStatusMessage = "DICOM message successfully sent";
                responseStatus = Status.SENT;
            } else if (status == DicomConstants.STATUS_WARNING_COERCION || status == DicomConstants.STATUS_WARNING_ELEMENTS_DISCARDED || status == DicomConstants.STATUS_WARNING_DATA_SET_MISMATCH) {
                // These status codes are used in DcmSnd.onDimseRSP to flag warnings
                responseStatusMessage = "DICOM message successfully sent with warning status code: 0x" + DicomConstants.shortToHex(status);
                responseStatus = Status.SENT;
            } else {
                // Any other status is considered unsuccessful
                responseStatusMessage = "Error status code received from DICOM server: 0x" + DicomConstants.shortToHex(status);
                responseStatus = Status.QUEUED;
            }

            if (storageCommitmentFailed && responseStatus == Status.SENT) {
                responseStatusMessage += " but Storage Commitment failed with reason: " + storageCommitmentFailureReason;
                responseStatus = Status.QUEUED;
            }

            responseData = rspHandler.getCommandData();
        } catch (Exception e) {
            responseStatusMessage = ErrorMessageBuilder.buildErrorResponse(e.getMessage(), e);
            responseError = ErrorMessageBuilder.buildErrorMessage(connectorProperties.getName(), e.getMessage(), null);
            eventController.dispatchEvent(new ErrorEvent(getChannelId(), getMetaDataId(), connectorMessage.getMessageId(), ErrorEventType.DESTINATION_CONNECTOR, getDestinationName(), connectorProperties.getName(), e.getMessage(), null));
        } finally {
            try {
                dcmSnd.close();
            } catch (Exception e) {
                logger.debug("Error closing DICOM sender association", e);
            }
            dcmSnd.stop();

            if (tempFile != null) {
                tempFile.delete();
            }

            eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getDestinationName(), ConnectionStatusEventType.IDLE));
        }

        return new Response(responseStatus, responseData, responseStatusMessage, responseError);
    }

    protected OieDicomSender createDicomSender(DICOMConfiguration configuration) {
        return DicomLibraryFactory.createSender(configuration);
    }

    protected class CommandDataDimseRSPHandler implements OieDimseRspHandler {

        private OieDicomObject cmd;

        @Override
        public void onDimseRSP(OieDicomObject cmd, OieDicomObject data) {
            this.cmd = cmd;
        }

        public int getStatus() {
            if (cmd != null) {
                return cmd.getInt(DicomConstants.TAG_STATUS);
            } else {
                return 0;
            }
        }

        public String getCommandData() {
            if (cmd != null) {
                try {
                    DonkeyElement dicom = new DonkeyElement("<dicom/>");

                    for (Iterator<OieDicomElement> it = cmd.commandIterator(); it.hasNext();) {
                        OieDicomElement element = it.next();
                        String tag = DicomConstants.shortToHex(element.tag() >> 16) + DicomConstants.shortToHex(element.tag());

                        DonkeyElement child = dicom.addChildElement("tag" + tag, element.getValueAsString(0));
                        child.setAttribute("len", String.valueOf(element.length()));
                        child.setAttribute("tag", tag);
                        child.setAttribute("vr", String.valueOf(element.vr()));
                    }

                    return dicom.toXml();
                } catch (Throwable t) {
                    logger.error("Unable to extract DICOM command data from response", t);
                }
            }
            return null;
        }
    }
}
