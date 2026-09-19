// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2017 Mirth Corporation
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.plugins.datatypes.dicom;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Serializes every sample in tests/dicom and compares it against the committed XML, which is the
 * serializer's own output and so is already indented. Drop a new .dcm and its .xml into that
 * directory and it is picked up without touching this class; see the README there for provenance.
 */
@RunWith(Parameterized.class)
public class DICOMSerializerTest {

    // server/build.gradle runs tests with workingDir = projectDir
    private static final File SAMPLES = new File("tests/dicom");

    @Parameters(name = "{0}")
    public static List<String> samples() {
        String[] names = SAMPLES.list((dir, name) -> name.endsWith(".dcm"));
        assertTrue("No DICOM samples found in " + SAMPLES.getAbsolutePath(), names != null && names.length > 0);
        Arrays.sort(names);
        return Arrays.asList(names);
    }

    @Parameter
    public String name;

    @Test
    public void toXml() throws Exception {
        // the committed XML is of the header alone; pixel data would dwarf it
        byte[] header = DICOMSerializer.removePixelData(FileUtils.readFileToByteArray(new File(SAMPLES, name)));
        String actual = new DICOMSerializer().toXML(Base64.getEncoder().encodeToString(header));
        String expected = FileUtils.readFileToString(new File(SAMPLES, StringUtils.removeEnd(name, ".dcm") + ".xml"), UTF_8);

        assertEquals(expected, actual);
    }
}
