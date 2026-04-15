/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom.dcm2;

import java.util.Map;

import org.dcm4che2.net.Association;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.tool.dcmrcv.MirthDcmRcv;
import org.dcm4che2.tool.dcmsnd.MirthDcmSnd;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.connectors.dimse.DICOMDispatcher;
import com.mirth.connect.connectors.dimse.DICOMDispatcherProperties;
import com.mirth.connect.connectors.dimse.DICOMReceiver;
import com.mirth.connect.connectors.dimse.DICOMReceiverProperties;
import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;

/**
 * dcm4che2-specific extension of {@link DICOMConfiguration}. Provides the
 * legacy dcm4che2-typed method signatures and bridge defaults that delegate
 * the version-neutral methods to the legacy ones.
 *
 * <p>Custom implementations that need dcm4che2 API access should implement
 * this interface. The method signatures match the original pre-abstraction
 * {@code DICOMConfiguration} interface, so migrating is a one-line change:
 * replace {@code implements DICOMConfiguration} with
 * {@code implements Dcm2DICOMConfiguration}.
 */
public interface Dcm2DICOMConfiguration extends DICOMConfiguration {

    // Legacy dcm4che2-typed methods (same signatures as original DICOMConfiguration)

    void configureDcmRcv(MirthDcmRcv dcmrcv, DICOMReceiver connector,
                         DICOMReceiverProperties connectorProperties) throws Exception;

    void configureDcmSnd(MirthDcmSnd dcmsnd, DICOMDispatcher connector,
                         DICOMDispatcherProperties connectorProperties) throws Exception;

    Map<String, Object> getCStoreRequestInformation(Association association);

    NetworkConnection createLegacyNetworkConnection();

    // Bridge defaults: version-neutral methods delegate to legacy-typed methods

    @Override
    default void configureReceiver(OieDicomReceiver receiver, DICOMReceiver connector,
                                   DICOMReceiverProperties connectorProperties) throws Exception {
        configureDcmRcv((MirthDcmRcv) receiver.unwrap(), connector, connectorProperties);
    }

    @Override
    default void configureSender(OieDicomSender sender, DICOMDispatcher connector,
                                 DICOMDispatcherProperties connectorProperties) throws Exception {
        configureDcmSnd((MirthDcmSnd) sender.unwrap(), connector, connectorProperties);
    }

    @Override
    default Map<String, Object> getCStoreRequestInformation(Object association) {
        return getCStoreRequestInformation((Association) association);
    }

    @Override
    default Object createNetworkConnection() {
        return createLegacyNetworkConnection();
    }
}
