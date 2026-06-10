// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom;

import java.io.IOException;

/**
 * Version-neutral interface for DICOM conversion operations. Abstracts the byte-array,
 * XML, and object conversion logic across dcm4che library versions.
 */
public interface OieDicomConverter {

    /**
     * Parses a byte array into a DICOM object.
     *
     * @param bytes       The binary DICOM data
     * @param decodeBase64 If true, the input is Base64-decoded before parsing
     * @return The parsed DICOM object
     */
    OieDicomObject byteArrayToDicomObject(byte[] bytes, boolean decodeBase64) throws IOException;

    /**
     * Serializes a DICOM object to a byte array. Note: the DICOM object is cleared
     * after serialization as a memory optimization.
     *
     * @param dicomObject The DICOM object to serialize
     * @return The serialized byte array
     */
    byte[] dicomObjectToByteArray(OieDicomObject dicomObject) throws IOException;

    /**
     * Creates a new empty DICOM object.
     */
    OieDicomObject createDicomObject();

    /**
     * Converts Base64-encoded DICOM data to its XML representation.
     * The XML uses dcm4che's native format with &lt;attr&gt; elements.
     *
     * @param encodedDicomBytes ASCII bytes of Base64-encoded DICOM data
     * @return The XML string representation
     */
    String dicomBytesToXml(byte[] encodedDicomBytes) throws Exception;

    /**
     * Parses XML (in dcm4che attr format) into a DICOM object.
     *
     * @param xml     The XML string in dcm4che format
     * @param charset The character set for the XML bytes
     * @return The parsed DICOM object
     */
    OieDicomObject xmlToDicomObject(String xml, String charset) throws Exception;

    /**
     * Returns the human-readable name for a DICOM tag.
     *
     * @param tag The DICOM tag number
     * @return The element name, or empty string if unknown
     */
    String getElementName(int tag);
}
