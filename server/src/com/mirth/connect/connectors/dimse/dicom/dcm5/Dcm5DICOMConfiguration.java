/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom.dcm5;

import java.util.Map;

import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.connectors.dimse.DICOMDispatcher;
import com.mirth.connect.connectors.dimse.DICOMDispatcherProperties;
import com.mirth.connect.connectors.dimse.DICOMReceiver;
import com.mirth.connect.connectors.dimse.DICOMReceiverProperties;
import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;

/**
 * dcm4che5-specific extension of {@link DICOMConfiguration}. Provides dcm4che5-typed
 * method signatures and bridge defaults that delegate the version-neutral methods.
 *
 * <p>Custom implementations that need dcm4che5 API access should implement this interface.
 */
public interface Dcm5DICOMConfiguration extends DICOMConfiguration {

    // dcm4che5-typed methods

    void configureDcm5Receiver(Dcm5DicomReceiver receiver, DICOMReceiver connector,
                                DICOMReceiverProperties connectorProperties) throws Exception;

    void configureDcm5Sender(Dcm5DicomSender sender, DICOMDispatcher connector,
                              DICOMDispatcherProperties connectorProperties) throws Exception;

    Map<String, Object> getCStoreRequestInformation(Association association);

    Connection createDcm5Connection();

    // Bridge defaults: version-neutral methods delegate to dcm5-typed methods

    @Override
    default void configureReceiver(OieDicomReceiver receiver, DICOMReceiver connector,
                                   DICOMReceiverProperties connectorProperties) throws Exception {
        // Cast receiver directly — Dcm5DicomReceiver IS the composition (no unwrap)
        configureDcm5Receiver((Dcm5DicomReceiver) receiver, connector, connectorProperties);
    }

    @Override
    default void configureSender(OieDicomSender sender, DICOMDispatcher connector,
                                 DICOMDispatcherProperties connectorProperties) throws Exception {
        configureDcm5Sender((Dcm5DicomSender) sender, connector, connectorProperties);
    }

    @Override
    default Map<String, Object> getCStoreRequestInformation(Object association) {
        return getCStoreRequestInformation((Association) association);
    }

    @Override
    default Object createNetworkConnection() {
        return createDcm5Connection();
    }
}
