// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.dcm5;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;

import com.mirth.connect.connectors.dimse.dicom.OieDicomElement;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.OieVR;

/**
 * dcm4che5 implementation of OieDicomObject, wrapping Attributes.
 *
 * <p>Key difference from dcm2: File Meta Information is stored as a separate
 * Attributes object (not embedded in the dataset).
 */
public class Dcm5DicomObject implements OieDicomObject {

    private final Attributes dataset;
    private Attributes fmi;

    public Dcm5DicomObject() {
        this(new Attributes());
    }

    public Dcm5DicomObject(Attributes dataset) {
        this(null, dataset);
    }

    public Dcm5DicomObject(Attributes fmi, Attributes dataset) {
        this.fmi = fmi;
        this.dataset = dataset != null ? dataset : new Attributes();
    }

    /** Package-visible accessor for Dcm5DicomConverter. */
    Attributes getFmi() {
        return fmi;
    }

    @Override
    public String getString(int tag) {
        return dataset.getString(tag);
    }

    @Override
    public int getInt(int tag) {
        return dataset.getInt(tag, 0);
    }

    @Override
    public OieDicomElement get(int tag) {
        if (!dataset.contains(tag)) {
            return null;
        }
        VR vr = dataset.getVR(tag);
        if (vr == VR.SQ) {
            Sequence seq = dataset.getSequence(tag);
            return seq != null ? new Dcm5DicomElement(tag, dataset, seq) : null;
        }
        Object value = dataset.getValue(tag);
        if (value instanceof Fragments) {
            return new Dcm5DicomElement(tag, dataset, (Fragments) value, vr.name());
        }
        return new Dcm5DicomElement(tag, dataset);
    }

    @Override
    public boolean contains(int tag) {
        return dataset.contains(tag);
    }

    @Override
    public int size() {
        return dataset.size();
    }

    @Override
    public boolean isEmpty() {
        return dataset.isEmpty();
    }

    @Override
    public byte[] getBytes(int tag) {
        try {
            return dataset.getBytes(tag);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Unable to read bytes for tag " + Integer.toHexString(tag), e);
        }
    }

    @Override
    public int[] getInts(int tag) {
        return dataset.getInts(tag);
    }

    @Override
    public String[] getStrings(int tag) {
        return dataset.getStrings(tag);
    }

    @Override
    public float getFloat(int tag, float defaultValue) {
        return dataset.getFloat(tag, defaultValue);
    }

    @Override
    public float[] getFloats(int tag) {
        return dataset.getFloats(tag);
    }

    @Override
    public double getDouble(int tag, double defaultValue) {
        return dataset.getDouble(tag, defaultValue);
    }

    @Override
    public double[] getDoubles(int tag) {
        return dataset.getDoubles(tag);
    }

    @Override
    public java.util.Date getDate(int tag) {
        return dataset.getDate(tag);
    }

    @Override
    public java.util.Date[] getDates(int tag) {
        return dataset.getDates(tag);
    }

    @Override
    public OieDicomObject getNestedDicomObject(int tag) {
        Attributes nested = dataset.getNestedDataset(tag);
        return nested != null ? new Dcm5DicomObject(nested) : null;
    }

    @Override
    public int vm(int tag) {
        if (!dataset.contains(tag)) {
            return 0;
        }
        String[] strings = dataset.getStrings(tag);
        return strings != null ? strings.length : 1;
    }

    @Override
    public String vrOf(int tag) {
        VR vr = ElementDictionary.vrOf(tag, dataset.getPrivateCreator(tag));
        return vr != null ? vr.name() : null;
    }

    @Override
    public String nameOf(int tag) {
        return ElementDictionary.keywordOf(tag, dataset.getPrivateCreator(tag));
    }

    @Override
    public void putString(int tag, String vr, String value) {
        dataset.setString(tag, VR.valueOf(vr), value);
    }

    @Override
    public void putInt(int tag, String vr, int value) {
        dataset.setInt(tag, VR.valueOf(vr), value);
    }

    @Override
    public void putBytes(int tag, String vr, byte[] value) {
        dataset.setBytes(tag, VR.valueOf(vr), value);
    }

    @Override
    public OieDicomElement putSequence(int tag) {
        Sequence seq = dataset.newSequence(tag, 0);
        return new Dcm5DicomElement(tag, dataset, seq);
    }

    @Override
    public OieDicomElement putFragments(int tag, String vr, boolean bigEndian, int capacity) {
        Fragments frags = dataset.newFragments(tag, VR.valueOf(vr), capacity);
        return new Dcm5DicomElement(tag, dataset, frags, vr);
    }

    @Override
    public void add(OieDicomElement element) {
        if (element instanceof Dcm5DicomElement) {
            ((Dcm5DicomElement) element).copyTo(dataset);
        } else {
            // Cross-library element: extract via interface methods
            int tag = element.tag();
            String vrName = String.valueOf(element.vr());
            if ("SQ".equals(vrName)) {
                Sequence seq = dataset.newSequence(tag, element.countItems());
                for (int i = 0; i < element.countItems(); i++) {
                    OieDicomObject item = element.getDicomObject(i);
                    if (item != null) {
                        seq.add(new Attributes((Attributes) item.unwrap()));
                    }
                }
            } else if (element.hasItems()) {
                Fragments frags = dataset.newFragments(tag, VR.valueOf(vrName), element.countItems());
                for (int i = 0; i < element.countItems(); i++) {
                    frags.add(element.getFragment(i));
                }
            } else {
                byte[] bytes = element.getBytes();
                if (bytes != null) {
                    dataset.setBytes(tag, VR.valueOf(vrName), bytes);
                }
            }
        }
    }

    @Override
    public OieDicomElement remove(int tag) {
        if (!dataset.contains(tag)) {
            return null;
        }
        OieDicomElement removed = get(tag);
        dataset.remove(tag);
        return removed;
    }

    @Override
    public void clear() {
        dataset.clear();
    }

    @Override
    public boolean hasFileMetaInfo() {
        return fmi != null && !fmi.isEmpty();
    }

    @Override
    public void initFileMetaInformation(String cuid, String iuid, String tsuid) {
        // dcm4che5 param order: (iuid, cuid, tsuid) — swapped from our interface
        this.fmi = Attributes.createFileMetaInformation(iuid, cuid, tsuid);
    }

    @Override
    public boolean bigEndian() {
        return dataset.bigEndian();
    }

    @Override
    public Iterator<OieDicomElement> commandIterator() {
        final List<OieDicomElement> commandElements = new ArrayList<>();
        try {
            dataset.accept(new Attributes.Visitor() {
                @Override
                public boolean visit(Attributes attrs, int tag, VR vr, Object value) {
                    // Command group elements have tag group 0x0000
                    if ((tag >>> 16) == 0x0000) {
                        commandElements.add(new Dcm5DicomElement(tag, attrs));
                    }
                    return true;
                }
            }, false);
        } catch (Exception e) {
            // Visitor should not throw in practice
        }
        return Collections.unmodifiableList(commandElements).iterator();
    }

    @Override
    public Object unwrap() {
        return dataset;
    }
}
