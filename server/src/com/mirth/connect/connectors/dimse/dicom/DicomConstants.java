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
 * Version-neutral DICOM constants. These values are defined by the DICOM standard
 * and are identical across all dcm4che library versions.
 */
public final class DicomConstants {

    private DicomConstants() {}

    // DICOM Tags (from the DICOM standard, version-independent)
    public static final int TAG_PIXEL_DATA = 0x7FE00010;
    public static final int TAG_STATUS = 0x00000900;
    public static final int TAG_FAILED_SOP_SEQUENCE = 0x00081198;
    public static final int TAG_FAILURE_REASON = 0x00081197;
    public static final int TAG_AFFECTED_SOP_CLASS_UID = 0x00000002;
    public static final int TAG_AFFECTED_SOP_INSTANCE_UID = 0x00001000;
    public static final int TAG_REQUESTED_SOP_CLASS_UID = 0x00000003;
    public static final int TAG_REQUESTED_SOP_INSTANCE_UID = 0x00001001;

    // DICOM Value Representations
    public static final String VR_OB = "OB";
    public static final String VR_UI = "UI";
    public static final String VR_IS = "IS";

    // Transfer Syntax UIDs
    public static final String IMPLICIT_VR_LITTLE_ENDIAN = "1.2.840.10008.1.2";
    public static final String EXPLICIT_VR_LITTLE_ENDIAN = "1.2.840.10008.1.2.1";
    public static final String EXPLICIT_VR_BIG_ENDIAN = "1.2.840.10008.1.2.2";
    public static final String DEFLATED_EXPLICIT_VR_LITTLE_ENDIAN = "1.2.840.10008.1.2.1.99";
    public static final String JPEG_BASELINE = "1.2.840.10008.1.2.4.50";
    public static final String JPEG_EXTENDED = "1.2.840.10008.1.2.4.51";
    public static final String JPEG_LOSSLESS_NH14 = "1.2.840.10008.1.2.4.57";
    public static final String JPEG_LOSSLESS_SV1 = "1.2.840.10008.1.2.4.70";
    public static final String JPEG_LS_LOSSLESS = "1.2.840.10008.1.2.4.80";
    public static final String JPEG_LS_NEAR_LOSSLESS = "1.2.840.10008.1.2.4.81";
    public static final String JPEG_2000_LOSSLESS = "1.2.840.10008.1.2.4.90";
    public static final String JPEG_2000 = "1.2.840.10008.1.2.4.91";
    public static final String MPEG2 = "1.2.840.10008.1.2.4.100";
    public static final String RLE_LOSSLESS = "1.2.840.10008.1.2.5";

    // DICOM Status Codes
    public static final int STATUS_SUCCESS = 0x0000;
    public static final int STATUS_WARNING_COERCION = 0xB000;
    public static final int STATUS_WARNING_ELEMENTS_DISCARDED = 0xB006;
    public static final int STATUS_WARNING_DATA_SET_MISMATCH = 0xB007;
    public static final int STATUS_PROCESSING_FAILURE = 0x0110;

    /**
     * Formats a 16-bit status code as a 4-character hex string (e.g., 0xB000 → "B000").
     * Equivalent to dcm4che2's StringUtils.shortToHex().
     */
    public static String shortToHex(int val) {
        return String.format("%04X", val & 0xFFFF);
    }
}
