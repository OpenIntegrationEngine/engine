// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

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
