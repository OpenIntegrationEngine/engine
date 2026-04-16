package com.mirth.connect.connectors.dimse.dicom.dcm5;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.junit.Test;

import com.mirth.connect.connectors.dimse.dicom.OieDicomElement;

public class Dcm5DicomObjectTest {

    @Test
    public void testCreateEmpty() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        assertNotNull(obj.unwrap());
        assertFalse(obj.hasFileMetaInfo());
        assertFalse(obj.bigEndian());
    }

    @Test
    public void testPutAndGetString() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        obj.putString(Tag.PatientName, "PN", "Doe^John");
        assertEquals("Doe^John", obj.getString(Tag.PatientName));
    }

    @Test
    public void testPutAndGetInt() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        obj.putInt(Tag.Rows, "US", 512);
        assertEquals(512, obj.getInt(Tag.Rows));
    }

    @Test
    public void testPutAndGetBytes() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        byte[] data = new byte[] { 1, 2, 3, 4 };
        obj.putBytes(Tag.PixelData, "OB", data);
        OieDicomElement elem = obj.get(Tag.PixelData);
        assertNotNull(elem);
        assertNotNull(elem.getBytes());
        assertEquals(4, elem.getBytes().length);
    }

    @Test
    public void testGetReturnsNullForMissingTag() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        assertNull(obj.get(Tag.PatientName));
        assertNull(obj.getString(Tag.PatientName));
        assertEquals(0, obj.getInt(Tag.Rows));
    }

    @Test
    public void testPutStringAcceptsObjectVrViaToString() {
        // Simulates a user transformer script passing a library-specific VR constant
        // (e.g., dcm4che2 VR.PN) — the Object overload delegates via toString().
        Object libraryVr = new Object() {
            @Override public String toString() { return "PN"; }
        };
        Dcm5DicomObject obj = new Dcm5DicomObject();
        obj.putString(Tag.PatientName, libraryVr, "Doe^John");
        assertEquals("Doe^John", obj.getString(Tag.PatientName));
    }

    @Test
    public void testPutIntAcceptsObjectVrViaToString() {
        Object libraryVr = new Object() {
            @Override public String toString() { return "US"; }
        };
        Dcm5DicomObject obj = new Dcm5DicomObject();
        obj.putInt(Tag.Rows, libraryVr, 512);
        assertEquals(512, obj.getInt(Tag.Rows));
    }

    @Test
    public void testPutSequence() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        OieDicomElement seq = obj.putSequence(Tag.ReferencedStudySequence);
        assertNotNull(seq);
        assertEquals("SQ", seq.vr().toString());
        assertFalse(seq.hasItems());
        assertEquals(0, seq.countItems());
    }

    @Test
    public void testSequenceAddAndGet() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        OieDicomElement seq = obj.putSequence(Tag.ReferencedStudySequence);

        Dcm5DicomObject item = new Dcm5DicomObject();
        item.putString(Tag.StudyInstanceUID, "UI", "1.2.3.4");
        seq.addDicomObject(item);

        assertTrue(seq.hasItems());
        assertEquals(1, seq.countItems());
        assertNotNull(seq.getDicomObject());
        assertEquals("1.2.3.4", seq.getDicomObject().getString(Tag.StudyInstanceUID));
    }

    @Test
    public void testPutFragments() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        OieDicomElement frags = obj.putFragments(Tag.PixelData, "OB", false, 2);
        assertNotNull(frags);
        assertEquals("OB", frags.vr().toString());
    }

    @Test
    public void testRemove() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        obj.putString(Tag.PatientName, "PN", "Test");
        assertNotNull(obj.get(Tag.PatientName));

        OieDicomElement removed = obj.remove(Tag.PatientName);
        assertNotNull(removed);
        assertNull(obj.get(Tag.PatientName));
    }

    @Test
    public void testRemoveNonexistent() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        assertNull(obj.remove(Tag.PatientName));
    }

    @Test
    public void testClear() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        obj.putString(Tag.PatientName, "PN", "Test");
        obj.putString(Tag.PatientID, "LO", "12345");
        obj.clear();
        assertNull(obj.get(Tag.PatientName));
        assertNull(obj.get(Tag.PatientID));
    }

    @Test
    public void testInitFileMetaInformation() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        assertFalse(obj.hasFileMetaInfo());

        obj.initFileMetaInformation("1.2.840.10008.5.1.4.1.1.2", "1.2.3.4.5", "1.2.840.10008.1.2");
        assertTrue(obj.hasFileMetaInfo());
        assertNotNull(obj.getFmi());
    }

    @Test
    public void testCommandIterator() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        // Command group tags have group 0x0000
        obj.putString(0x00000002, "UI", "1.2.840.10008.1.1");
        obj.putInt(0x00000100, "US", 0x0001);
        // Non-command tag should not appear
        obj.putString(Tag.PatientName, "PN", "Test");

        Iterator<OieDicomElement> it = obj.commandIterator();
        int count = 0;
        while (it.hasNext()) {
            OieDicomElement elem = it.next();
            assertTrue((elem.tag() >>> 16) == 0x0000);
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    public void testWrapExistingAttributes() {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, org.dcm4che3.data.VR.PN, "Wrapped");
        Dcm5DicomObject obj = new Dcm5DicomObject(attrs);
        assertEquals("Wrapped", obj.getString(Tag.PatientName));
        assertTrue(obj.unwrap() == attrs);
    }

    @Test
    public void testWrapWithFmi() {
        Attributes fmi = Attributes.createFileMetaInformation("1.2.3", "1.2.840.10008.5.1.4.1.1.2", "1.2.840.10008.1.2");
        Attributes dataset = new Attributes();
        Dcm5DicomObject obj = new Dcm5DicomObject(fmi, dataset);
        assertTrue(obj.hasFileMetaInfo());
        assertTrue(obj.getFmi() == fmi);
    }

    @Test
    public void testDefaultGetStringReturnsDefault() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        assertEquals("FALLBACK", obj.getString(Tag.PatientName, "FALLBACK"));
    }

    @Test
    public void testDefaultGetIntReturnsDefault() {
        Dcm5DicomObject obj = new Dcm5DicomObject();
        assertEquals(42, obj.getInt(Tag.Rows, 42));
    }
}
