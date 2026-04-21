// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom;

/**
 * Version-neutral interface for DICOM data elements. Wraps dcm4che2 DicomElement
 * or dcm4che5 element access on Attributes.
 */
public interface OieDicomElement {

    int tag();

    int length();

    OieVR vr();

    String getValueAsString(int index);

    boolean hasItems();

    int countItems();

    boolean isEmpty();

    boolean hasDicomObjects();

    boolean hasFragments();

    byte[] getFragment(int index);

    byte[] getBytes();

    String[] getStrings();

    int getInt(int defaultValue);

    int[] getInts();

    float getFloat(float defaultValue);

    float[] getFloats();

    double getDouble(double defaultValue);

    double[] getDoubles();

    java.util.Date getDate();

    java.util.Date[] getDates();

    void addFragment(byte[] data);

    OieDicomObject getDicomObject();

    /**
     * Returns the sequence item at the given index, or null if the index is out of range
     * or this element is not a sequence.
     */
    default OieDicomObject getDicomObject(int index) {
        return index == 0 ? getDicomObject() : null;
    }

    void addDicomObject(OieDicomObject obj);

    /**
     * Returns the underlying library-specific element. Use with caution.
     */
    Object unwrap();
}
