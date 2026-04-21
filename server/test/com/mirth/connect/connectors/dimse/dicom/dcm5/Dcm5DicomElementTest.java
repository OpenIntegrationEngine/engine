// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.dcm5;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.Test;

public class Dcm5DicomElementTest {

    @Test
    public void testValueModeTag() {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, VR.PN, "Test");
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PatientName, attrs);
        assertEquals(Tag.PatientName, elem.tag());
    }

    @Test
    public void testValueModeVr() {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, VR.PN, "Test");
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PatientName, attrs);
        assertEquals("PN", elem.vr().toString());
    }

    @Test
    public void testValueModeGetValueAsString() {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, VR.PN, "Doe^John");
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PatientName, attrs);
        assertEquals("Doe^John", elem.getValueAsString(0));
    }

    @Test
    public void testValueModeGetBytes() {
        Attributes attrs = new Attributes();
        byte[] data = new byte[] { 0x01, 0x02, 0x03 };
        attrs.setBytes(Tag.PixelData, VR.OB, data);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PixelData, attrs);
        assertArrayEquals(data, elem.getBytes());
    }

    @Test
    public void testValueModeLength() {
        Attributes attrs = new Attributes();
        byte[] data = new byte[] { 0x01, 0x02, 0x03 };
        attrs.setBytes(Tag.PixelData, VR.OB, data);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PixelData, attrs);
        assertEquals(3, elem.length());
    }

    @Test
    public void testValueModeHasItemsFalse() {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, VR.PN, "Test");
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PatientName, attrs);
        assertFalse(elem.hasItems());
        assertEquals(0, elem.countItems());
    }

    @Test
    public void testValueModeGetDicomObjectNull() {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, VR.PN, "Test");
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PatientName, attrs);
        assertNull(elem.getDicomObject());
    }

    @Test
    public void testSequenceModeVrIsSQ() {
        Attributes attrs = new Attributes();
        Sequence seq = attrs.newSequence(Tag.ReferencedStudySequence, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.ReferencedStudySequence, attrs, seq);
        assertEquals("SQ", elem.vr().toString());
    }

    @Test
    public void testSequenceModeHasItemsEmpty() {
        Attributes attrs = new Attributes();
        Sequence seq = attrs.newSequence(Tag.ReferencedStudySequence, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.ReferencedStudySequence, attrs, seq);
        assertFalse(elem.hasItems());
        assertEquals(0, elem.countItems());
    }

    @Test
    public void testSequenceModeAddAndGetDicomObject() {
        Attributes attrs = new Attributes();
        Sequence seq = attrs.newSequence(Tag.ReferencedStudySequence, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.ReferencedStudySequence, attrs, seq);

        Dcm5DicomObject item = new Dcm5DicomObject();
        item.putString(Tag.StudyInstanceUID, "UI", "1.2.3");
        elem.addDicomObject(item);

        assertTrue(elem.hasItems());
        assertEquals(1, elem.countItems());
        assertNotNull(elem.getDicomObject());
        assertEquals("1.2.3", elem.getDicomObject().getString(Tag.StudyInstanceUID));
    }

    @Test
    public void testSequenceModeLength() {
        Attributes attrs = new Attributes();
        Sequence seq = attrs.newSequence(Tag.ReferencedStudySequence, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.ReferencedStudySequence, attrs, seq);
        assertEquals(-1, elem.length());
    }

    @Test
    public void testSequenceModeGetValueAsStringNull() {
        Attributes attrs = new Attributes();
        Sequence seq = attrs.newSequence(Tag.ReferencedStudySequence, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.ReferencedStudySequence, attrs, seq);
        assertNull(elem.getValueAsString(0));
    }

    @Test
    public void testSequenceModeGetBytesNull() {
        Attributes attrs = new Attributes();
        Sequence seq = attrs.newSequence(Tag.ReferencedStudySequence, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.ReferencedStudySequence, attrs, seq);
        assertNull(elem.getBytes());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSequenceModeGetFragmentThrows() {
        Attributes attrs = new Attributes();
        Sequence seq = attrs.newSequence(Tag.ReferencedStudySequence, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.ReferencedStudySequence, attrs, seq);
        elem.getFragment(0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSequenceModeAddFragmentThrows() {
        Attributes attrs = new Attributes();
        Sequence seq = attrs.newSequence(Tag.ReferencedStudySequence, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.ReferencedStudySequence, attrs, seq);
        elem.addFragment(new byte[] { 1 });
    }

    @Test
    public void testFragmentsModeAddAndGetFragment() {
        Attributes attrs = new Attributes();
        Fragments frags = attrs.newFragments(Tag.PixelData, VR.OB, 2);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PixelData, attrs, frags, "OB");

        byte[] fragment = new byte[] { 0x10, 0x20, 0x30 };
        elem.addFragment(fragment);

        assertTrue(elem.hasItems());
        assertEquals(1, elem.countItems());
        assertArrayEquals(fragment, elem.getFragment(0));
    }

    @Test
    public void testFragmentsModeVr() {
        Attributes attrs = new Attributes();
        Fragments frags = attrs.newFragments(Tag.PixelData, VR.OB, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PixelData, attrs, frags, "OB");
        assertEquals("OB", elem.vr().toString());
    }

    @Test
    public void testFragmentsModeLength() {
        Attributes attrs = new Attributes();
        Fragments frags = attrs.newFragments(Tag.PixelData, VR.OB, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PixelData, attrs, frags, "OB");
        assertEquals(-1, elem.length());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testFragmentsModeAddDicomObjectThrows() {
        Attributes attrs = new Attributes();
        Fragments frags = attrs.newFragments(Tag.PixelData, VR.OB, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PixelData, attrs, frags, "OB");
        elem.addDicomObject(new Dcm5DicomObject());
    }

    @Test
    public void testFragmentsModeGetDicomObjectNull() {
        Attributes attrs = new Attributes();
        Fragments frags = attrs.newFragments(Tag.PixelData, VR.OB, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PixelData, attrs, frags, "OB");
        assertNull(elem.getDicomObject());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testValueModeGetFragmentThrows() {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, VR.PN, "Test");
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PatientName, attrs);
        elem.getFragment(0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testValueModeAddFragmentThrows() {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, VR.PN, "Test");
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PatientName, attrs);
        elem.addFragment(new byte[] { 1 });
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testValueModeAddDicomObjectThrows() {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, VR.PN, "Test");
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PatientName, attrs);
        elem.addDicomObject(new Dcm5DicomObject());
    }

    @Test
    public void testValueModeUnwrapReturnsParent() {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, VR.PN, "Test");
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PatientName, attrs);
        assertTrue(elem.unwrap() == attrs);
    }

    @Test
    public void testSequenceModeUnwrapReturnsSequence() {
        Attributes attrs = new Attributes();
        Sequence seq = attrs.newSequence(Tag.ReferencedStudySequence, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.ReferencedStudySequence, attrs, seq);
        assertTrue(elem.unwrap() == seq);
    }

    @Test
    public void testFragmentsModeUnwrapReturnsFragments() {
        Attributes attrs = new Attributes();
        Fragments frags = attrs.newFragments(Tag.PixelData, VR.OB, 0);
        Dcm5DicomElement elem = new Dcm5DicomElement(Tag.PixelData, attrs, frags, "OB");
        assertTrue(elem.unwrap() == frags);
    }
}
