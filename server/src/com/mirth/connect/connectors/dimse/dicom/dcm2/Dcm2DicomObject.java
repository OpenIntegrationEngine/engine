// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.dcm2;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.VR;

import com.mirth.connect.connectors.dimse.dicom.OieDicomElement;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.OieVR;

/**
 * dcm4che2 implementation of OieDicomObject, wrapping BasicDicomObject/DicomObject.
 */
public class Dcm2DicomObject implements OieDicomObject {

    static VR toVR(String vrName) {
        if (vrName == null || vrName.length() != 2) {
            throw new IllegalArgumentException("Invalid VR: " + vrName);
        }
        return VR.valueOf((vrName.charAt(0) << 8) | vrName.charAt(1));
    }

    private final DicomObject delegate;

    public Dcm2DicomObject(DicomObject delegate) {
        this.delegate = delegate;
    }

    public Dcm2DicomObject() {
        this(new BasicDicomObject());
    }

    @Override
    public String getString(int tag) {
        return delegate.getString(tag);
    }

    @Override
    public int getInt(int tag) {
        return delegate.getInt(tag);
    }

    @Override
    public OieDicomElement get(int tag) {
        DicomElement element = delegate.get(tag);
        return element != null ? new Dcm2DicomElement(element) : null;
    }

    @Override
    public boolean contains(int tag) {
        return delegate.contains(tag);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public byte[] getBytes(int tag) {
        return delegate.getBytes(tag);
    }

    @Override
    public int[] getInts(int tag) {
        return delegate.getInts(tag);
    }

    @Override
    public String[] getStrings(int tag) {
        return delegate.getStrings(tag);
    }

    @Override
    public float getFloat(int tag, float defaultValue) {
        return delegate.getFloat(tag, defaultValue);
    }

    @Override
    public float[] getFloats(int tag) {
        return delegate.getFloats(tag);
    }

    @Override
    public double getDouble(int tag, double defaultValue) {
        return delegate.getDouble(tag, defaultValue);
    }

    @Override
    public double[] getDoubles(int tag) {
        return delegate.getDoubles(tag);
    }

    @Override
    public java.util.Date getDate(int tag) {
        return delegate.getDate(tag);
    }

    @Override
    public java.util.Date[] getDates(int tag) {
        return delegate.getDates(tag);
    }

    @Override
    public OieDicomObject getNestedDicomObject(int tag) {
        DicomObject nested = delegate.getNestedDicomObject(tag);
        return nested != null ? new Dcm2DicomObject(nested) : null;
    }

    @Override
    public int vm(int tag) {
        // dcm4che2 returns -1 when the tag is absent; normalize to 0
        // so callers can treat the result as a simple cardinality.
        int vm = delegate.vm(tag);
        return vm < 0 ? 0 : vm;
    }

    @Override
    public String vrOf(int tag) {
        VR vr = delegate.vrOf(tag);
        return vr != null ? vr.toString() : null;
    }

    @Override
    public String nameOf(int tag) {
        return delegate.nameOf(tag);
    }

    @Override
    public void putString(int tag, String vr, String value) {
        delegate.putString(tag, toVR(vr), value);
    }

    @Override
    public void putInt(int tag, String vr, int value) {
        delegate.putInt(tag, toVR(vr), value);
    }

    @Override
    public void putBytes(int tag, String vr, byte[] value) {
        delegate.putBytes(tag, toVR(vr), value);
    }

    @Override
    public OieDicomElement putSequence(int tag) {
        DicomElement element = delegate.putSequence(tag);
        return new Dcm2DicomElement(element);
    }

    @Override
    public OieDicomElement putFragments(int tag, String vr, boolean bigEndian, int capacity) {
        DicomElement element = delegate.putFragments(tag, toVR(vr), bigEndian, capacity);
        return new Dcm2DicomElement(element);
    }

    @Override
    public void add(OieDicomElement element) {
        if (element instanceof Dcm2DicomElement) {
            delegate.add((DicomElement) element.unwrap());
        } else {
            // Cross-library element: extract via interface methods.
            // Sequences cannot be fully reconstructed across library boundaries without
            // a converter round-trip; fragments and plain values transfer cleanly via bytes.
            int tag = element.tag();
            String vrName = String.valueOf(element.vr());
            if (element.hasItems() && !"SQ".equals(vrName)) {
                // Fragments — binary-compatible across libraries
                VR vr = toVR(vrName);
                DicomElement frags = delegate.putFragments(tag, vr, false, element.countItems());
                for (int i = 0; i < element.countItems(); i++) {
                    frags.addFragment(element.getFragment(i));
                }
            } else if (!"SQ".equals(vrName)) {
                // Plain value — copy raw bytes
                byte[] bytes = element.getBytes();
                if (bytes != null) {
                    delegate.putBytes(tag, toVR(vrName), bytes);
                }
            }
            // Sequences from another library are silently skipped — use the converter
            // for full cross-library DICOM object conversion instead.
        }
    }

    @Override
    public OieDicomElement remove(int tag) {
        DicomElement element = delegate.remove(tag);
        return element != null ? new Dcm2DicomElement(element) : null;
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean hasFileMetaInfo() {
        if (delegate instanceof BasicDicomObject) {
            return !((BasicDicomObject) delegate).fileMetaInfo().isEmpty();
        }
        return false;
    }

    @Override
    public void initFileMetaInformation(String cuid, String iuid, String tsuid) {
        if (delegate instanceof BasicDicomObject) {
            ((BasicDicomObject) delegate).initFileMetaInformation(cuid, iuid, tsuid);
        }
    }

    @Override
    public boolean bigEndian() {
        return delegate.bigEndian();
    }

    @Override
    public Iterator<OieDicomElement> commandIterator() {
        if (delegate instanceof BasicDicomObject) {
            final Iterator<DicomElement> dcmIt = ((BasicDicomObject) delegate).commandIterator();
            return new Iterator<OieDicomElement>() {
                @Override
                public boolean hasNext() {
                    return dcmIt.hasNext();
                }

                @Override
                public OieDicomElement next() {
                    if (!dcmIt.hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return new Dcm2DicomElement(dcmIt.next());
                }

                @Override
                public void remove() {
                    dcmIt.remove();
                }
            };
        }
        return java.util.Collections.emptyIterator();
    }

    @Override
    public Object unwrap() {
        return delegate;
    }
}
