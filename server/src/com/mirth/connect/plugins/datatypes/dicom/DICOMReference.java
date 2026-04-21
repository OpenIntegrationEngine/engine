// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.plugins.datatypes.dicom;

import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory;
import com.mirth.connect.connectors.dimse.dicom.OieDicomConverter;

public class DICOMReference {
    private static DICOMReference instance = null;
    private OieDicomConverter converter = null;

    private DICOMReference() {
        converter = DicomLibraryFactory.getConverter();
    }

    public static DICOMReference getInstance() {
        synchronized (DICOMReference.class) {
            if (instance == null)
                instance = new DICOMReference();
            return instance;
        }
    }

    public String getDescription(String key, String version) {
        if (key != null && !key.equals("")) {
            try {
                return converter.getElementName(Integer.decode("0x" + key).intValue());
            } catch (NumberFormatException e) {
                return "";
            }
        }
        return "";
    }
}
