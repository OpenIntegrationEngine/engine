// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.dcm5;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;

import com.mirth.connect.connectors.dimse.dicom.OieDicomElement;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.OieVR;

/**
 * Synthetic dcm4che5 implementation of OieDicomElement. dcm4che5 has no standalone
 * DicomElement class — all element access is done through the parent Attributes object.
 *
 * <p>Three modes:
 * <ul>
 *   <li><b>VALUE</b> — references parent Attributes + tag for plain value access</li>
 *   <li><b>SEQUENCE</b> — wraps a {@link Sequence} (list of Attributes)</li>
 *   <li><b>FRAGMENTS</b> — wraps a {@link Fragments} collection</li>
 * </ul>
 */
public class Dcm5DicomElement implements OieDicomElement {

    private enum Mode { VALUE, SEQUENCE, FRAGMENTS }

    private final int tag;
    private final Attributes parent;
    private final Mode mode;
    private final Sequence sequence;
    private final Fragments fragments;
    private final String vrName;

    /** VALUE mode constructor. */
    Dcm5DicomElement(int tag, Attributes parent) {
        this.tag = tag;
        this.parent = parent;
        this.mode = Mode.VALUE;
        this.sequence = null;
        this.fragments = null;
        VR vr = parent.getVR(tag);
        this.vrName = vr != null ? vr.name() : "UN";
    }

    /** SEQUENCE mode constructor. */
    Dcm5DicomElement(int tag, Attributes parent, Sequence sequence) {
        this.tag = tag;
        this.parent = parent;
        this.mode = Mode.SEQUENCE;
        this.sequence = sequence;
        this.fragments = null;
        this.vrName = "SQ";
    }

    /** FRAGMENTS mode constructor. */
    Dcm5DicomElement(int tag, Attributes parent, Fragments fragments, String vrName) {
        this.tag = tag;
        this.parent = parent;
        this.mode = Mode.FRAGMENTS;
        this.sequence = null;
        this.fragments = fragments;
        this.vrName = vrName;
    }

    /** Copies this element's data into the target Attributes. Package-visible for Dcm5DicomObject.add(). */
    void copyTo(Attributes target) {
        switch (mode) {
            case SEQUENCE:
                Sequence targetSeq = target.newSequence(tag, sequence.size());
                for (Attributes item : sequence) {
                    targetSeq.add(new Attributes(item));
                }
                break;
            case FRAGMENTS:
                Fragments targetFrags = target.newFragments(tag, VR.valueOf(vrName), fragments.size());
                for (Object frag : fragments) {
                    targetFrags.add(frag);
                }
                break;
            default:
                try {
                    byte[] bytes = parent.getBytes(tag);
                    if (bytes != null) {
                        target.setBytes(tag, VR.valueOf(vrName), bytes);
                    } else {
                        String val = parent.getString(tag);
                        if (val != null) {
                            target.setString(tag, VR.valueOf(vrName), val);
                        }
                    }
                } catch (java.io.IOException e) {
                    throw new RuntimeException("Failed to copy element " + Integer.toHexString(tag), e);
                }
                break;
        }
    }

    @Override
    public int tag() {
        return tag;
    }

    @Override
    public int length() {
        switch (mode) {
            case VALUE:
                try {
                    byte[] bytes = parent.getBytes(tag);
                    return bytes != null ? bytes.length : -1;
                } catch (Exception e) {
                    return -1;
                }
            default:
                return -1;
        }
    }

    @Override
    public OieVR vr() {
        return new Dcm5VR(VR.valueOf(vrName));
    }

    @Override
    public String getValueAsString(int index) {
        if (mode != Mode.VALUE) {
            return null;
        }
        try {
            String[] values = parent.getStrings(tag);
            if (values != null && index >= 0 && index < values.length) {
                return values[index];
            }
        } catch (Exception e) {
            // Fall through
        }
        return null;
    }

    @Override
    public boolean hasItems() {
        switch (mode) {
            case SEQUENCE:
                return !sequence.isEmpty();
            case FRAGMENTS:
                return !fragments.isEmpty();
            default:
                return false;
        }
    }

    @Override
    public int countItems() {
        switch (mode) {
            case SEQUENCE:
                return sequence.size();
            case FRAGMENTS:
                return fragments.size();
            default:
                return 0;
        }
    }

    @Override
    public boolean isEmpty() {
        switch (mode) {
            case SEQUENCE:
                return sequence.isEmpty();
            case FRAGMENTS:
                return fragments.isEmpty();
            default:
                Object v = parent.getValue(tag);
                if (v == null) {
                    return true;
                }
                if (v instanceof byte[]) {
                    return ((byte[]) v).length == 0;
                }
                return false;
        }
    }

    @Override
    public boolean hasDicomObjects() {
        return mode == Mode.SEQUENCE;
    }

    @Override
    public boolean hasFragments() {
        return mode == Mode.FRAGMENTS;
    }

    @Override
    public byte[] getFragment(int index) {
        if (mode != Mode.FRAGMENTS) {
            throw new UnsupportedOperationException("getFragment not supported in " + mode + " mode");
        }
        Object frag = fragments.get(index);
        return frag instanceof byte[] ? (byte[]) frag : null;
    }

    @Override
    public byte[] getBytes() {
        if (mode != Mode.VALUE) {
            return null;
        }
        try {
            return parent.getBytes(tag);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    @Override
    public String[] getStrings() {
        return mode == Mode.VALUE ? parent.getStrings(tag) : null;
    }

    @Override
    public int getInt(int defaultValue) {
        return mode == Mode.VALUE ? parent.getInt(tag, defaultValue) : defaultValue;
    }

    @Override
    public int[] getInts() {
        return mode == Mode.VALUE ? parent.getInts(tag) : null;
    }

    @Override
    public float getFloat(float defaultValue) {
        return mode == Mode.VALUE ? parent.getFloat(tag, defaultValue) : defaultValue;
    }

    @Override
    public float[] getFloats() {
        return mode == Mode.VALUE ? parent.getFloats(tag) : null;
    }

    @Override
    public double getDouble(double defaultValue) {
        return mode == Mode.VALUE ? parent.getDouble(tag, defaultValue) : defaultValue;
    }

    @Override
    public double[] getDoubles() {
        return mode == Mode.VALUE ? parent.getDoubles(tag) : null;
    }

    @Override
    public java.util.Date getDate() {
        return mode == Mode.VALUE ? parent.getDate(tag) : null;
    }

    @Override
    public java.util.Date[] getDates() {
        return mode == Mode.VALUE ? parent.getDates(tag) : null;
    }

    @Override
    public void addFragment(byte[] data) {
        if (mode != Mode.FRAGMENTS) {
            throw new UnsupportedOperationException("addFragment not supported in " + mode + " mode");
        }
        fragments.add(data);
    }

    @Override
    public OieDicomObject getDicomObject() {
        if (mode != Mode.SEQUENCE || sequence.isEmpty()) {
            return null;
        }
        return new Dcm5DicomObject(sequence.get(0));
    }

    @Override
    public OieDicomObject getDicomObject(int index) {
        if (mode != Mode.SEQUENCE || index < 0 || index >= sequence.size()) {
            return null;
        }
        return new Dcm5DicomObject(sequence.get(index));
    }

    @Override
    public void addDicomObject(OieDicomObject obj) {
        if (mode != Mode.SEQUENCE) {
            throw new UnsupportedOperationException("addDicomObject not supported in " + mode + " mode");
        }
        sequence.add((Attributes) obj.unwrap());
    }

    @Override
    public Object unwrap() {
        switch (mode) {
            case SEQUENCE:
                return sequence;
            case FRAGMENTS:
                return fragments;
            default:
                return parent;
        }
    }
}
