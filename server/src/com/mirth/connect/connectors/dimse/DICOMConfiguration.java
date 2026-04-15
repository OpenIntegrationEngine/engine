/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse;

import java.util.Map;

import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;
import com.mirth.connect.donkey.server.channel.Connector;

/**
 * Version-neutral interface for DICOM connector configuration. Implementations
 * configure the DICOM sender and receiver without direct dcm4che library dependencies.
 *
 * <p>Custom implementations can cast the sender/receiver to the dcm4che-specific
 * type (e.g., Dcm2DicomSender) and call {@code unwrap()} to access the underlying
 * MirthDcmSnd/MirthDcmRcv if needed.
 */
public interface DICOMConfiguration {

    void configureConnectorDeploy(Connector connector) throws Exception;

    void configureReceiver(OieDicomReceiver receiver, DICOMReceiver connector, DICOMReceiverProperties connectorProperties) throws Exception;

    void configureSender(OieDicomSender sender, DICOMDispatcher connector, DICOMDispatcherProperties connectorProperties) throws Exception;

    /**
     * Extracts additional information from a DICOM C-STORE association request.
     * The association parameter is the library-specific association object.
     *
     * <p>For the dcm4che2 backend the runtime type is
     * {@code org.dcm4che2.net.Association}. Custom implementations should cast
     * accordingly:
     * <pre>{@code
     * Association as = (Association) association;
     * map.put("calledAET", as.getCalledAET());
     * }</pre>
     *
     * @param association The library-specific association object
     * @return Additional key-value pairs to add to the source map
     */
    Map<String, Object> getCStoreRequestInformation(Object association);

    /**
     * Optional factory method for creating a custom {@code NetworkConnection}.
     * The returned object must be an instance of the library-specific
     * NetworkConnection class (e.g., {@code org.dcm4che2.net.NetworkConnection}
     * for the dcm4che2 backend). If {@code null} is returned, the default
     * NetworkConnection is used.
     *
     * @return A library-specific NetworkConnection, or {@code null} for the default
     */
    default Object createNetworkConnection() { return null; }
}
