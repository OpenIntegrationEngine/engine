/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom;

import java.util.Date;
import java.util.Iterator;

/**
 * Version-neutral interface for DICOM data objects. Wraps dcm4che2 DicomObject/BasicDicomObject
 * or dcm4che5 Attributes, providing a consistent API across library versions.
 */
public interface OieDicomObject {

    String getString(int tag);

    /**
     * Returns the string value for the given tag, or {@code defaultValue}
     * if the tag is not present.
     */
    default String getString(int tag, String defaultValue) {
        String val = getString(tag);
        return val != null ? val : defaultValue;
    }

    int getInt(int tag);

    /**
     * Returns the integer value for the given tag, or {@code defaultValue}
     * if the tag is not present.
     */
    default int getInt(int tag, int defaultValue) {
        OieDicomElement elem = get(tag);
        return elem != null ? getInt(tag) : defaultValue;
    }

    OieDicomElement get(int tag);

    boolean contains(int tag);

    int size();

    boolean isEmpty();

    byte[] getBytes(int tag);

    int[] getInts(int tag);

    String[] getStrings(int tag);

    float getFloat(int tag, float defaultValue);

    float[] getFloats(int tag);

    double getDouble(int tag, double defaultValue);

    double[] getDoubles(int tag);

    Date getDate(int tag);

    Date[] getDates(int tag);

    OieDicomObject getNestedDicomObject(int tag);

    /** Value multiplicity for the element at {@code tag}. Returns 0 when absent. */
    int vm(int tag);

    /** Two-letter VR code for {@code tag}, derived from the DICOM dictionary. */
    String vrOf(int tag);

    /** Human-readable attribute name for {@code tag}, or {@code null} when unknown. */
    String nameOf(int tag);

    void putString(int tag, String vr, String value);

    void putInt(int tag, String vr, int value);

    void putBytes(int tag, String vr, byte[] value);

    OieDicomElement putSequence(int tag);

    OieDicomElement putFragments(int tag, String vr, boolean bigEndian, int capacity);

    void add(OieDicomElement element);

    OieDicomElement remove(int tag);

    void clear();

    boolean hasFileMetaInfo();

    void initFileMetaInformation(String cuid, String iuid, String tsuid);

    boolean bigEndian();

    /**
     * Returns a read-only iterator over the command elements (group 0x0000) of this DICOM object.
     * Used for building response XML from DIMSE command responses.
     *
     * <p>The returned iterator does not support {@code remove()}. Callers must not
     * attempt to modify the iteration — behavior varies by implementation.
     */
    Iterator<OieDicomElement> commandIterator();

    /**
     * Returns the underlying library-specific DICOM object (e.g., dcm4che2 DicomObject
     * or dcm4che5 Attributes). Use with caution — this breaks version independence.
     *
     * <p>Example — accessing dcm4che2 APIs from a user script:
     * <pre>{@code
     * OieDicomObject oie = DICOMUtil.byteArrayToDicomObject(bytes, false);
     * DicomObject dcm = (DicomObject) oie.unwrap();
     * dcm.getString(Tag.PatientName, "UNKNOWN");
     * }</pre>
     */
    Object unwrap();
}
