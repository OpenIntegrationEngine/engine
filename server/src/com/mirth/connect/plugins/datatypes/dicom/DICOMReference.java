/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

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
