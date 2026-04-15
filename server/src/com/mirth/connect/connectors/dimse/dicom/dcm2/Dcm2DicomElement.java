/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom.dcm2;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;

import com.mirth.connect.connectors.dimse.dicom.OieDicomElement;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.OieVR;

/**
 * dcm4che2 implementation of OieDicomElement, wrapping DicomElement.
 */
public class Dcm2DicomElement implements OieDicomElement {

    private final DicomElement delegate;

    public Dcm2DicomElement(DicomElement delegate) {
        this.delegate = delegate;
    }

    @Override
    public int tag() {
        return delegate.tag();
    }

    @Override
    public int length() {
        return delegate.length();
    }

    @Override
    public OieVR vr() {
        return new Dcm2VR(delegate.vr());
    }

    @Override
    public String getValueAsString(int index) {
        return delegate.getValueAsString(null, index);
    }

    @Override
    public boolean hasItems() {
        return delegate.hasItems();
    }

    @Override
    public int countItems() {
        return delegate.countItems();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean hasDicomObjects() {
        return delegate.hasDicomObjects();
    }

    @Override
    public boolean hasFragments() {
        return delegate.hasFragments();
    }

    @Override
    public byte[] getFragment(int index) {
        return delegate.getFragment(index);
    }

    @Override
    public byte[] getBytes() {
        return delegate.getBytes();
    }

    @Override
    public String[] getStrings() {
        return delegate.getStrings(null, false);
    }

    @Override
    public int getInt(int defaultValue) {
        return delegate.isEmpty() ? defaultValue : delegate.getInt(false);
    }

    @Override
    public int[] getInts() {
        return delegate.getInts(false);
    }

    @Override
    public float getFloat(float defaultValue) {
        return delegate.isEmpty() ? defaultValue : delegate.getFloat(false);
    }

    @Override
    public float[] getFloats() {
        return delegate.getFloats(false);
    }

    @Override
    public double getDouble(double defaultValue) {
        return delegate.isEmpty() ? defaultValue : delegate.getDouble(false);
    }

    @Override
    public double[] getDoubles() {
        return delegate.getDoubles(false);
    }

    @Override
    public java.util.Date getDate() {
        return delegate.getDate(false);
    }

    @Override
    public java.util.Date[] getDates() {
        return delegate.getDates(false);
    }

    @Override
    public void addFragment(byte[] data) {
        delegate.addFragment(data);
    }

    @Override
    public OieDicomObject getDicomObject() {
        DicomObject obj = delegate.getDicomObject();
        return obj != null ? new Dcm2DicomObject(obj) : null;
    }

    @Override
    public OieDicomObject getDicomObject(int index) {
        if (index < 0 || index >= delegate.countItems()) {
            return null;
        }
        DicomObject obj = delegate.getDicomObject(index);
        return obj != null ? new Dcm2DicomObject(obj) : null;
    }

    @Override
    public void addDicomObject(OieDicomObject obj) {
        delegate.addDicomObject((DicomObject) obj.unwrap());
    }

    @Override
    public Object unwrap() {
        return delegate;
    }
}
