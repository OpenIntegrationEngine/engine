/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom;

/**
 * Version-neutral interface for handling DIMSE responses. Replaces dcm4che2's
 * CustomDimseRSPHandler with a version-independent callback.
 */
public interface OieDimseRspHandler {

    /**
     * Called when a DIMSE response is received.
     *
     * @param cmd  The command DICOM object from the response
     * @param data The data DICOM object from the response (may be null)
     */
    void onDimseRSP(OieDicomObject cmd, OieDicomObject data);
}
