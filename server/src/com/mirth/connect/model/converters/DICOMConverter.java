/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.model.converters;

import java.io.IOException;

import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory;
import com.mirth.connect.connectors.dimse.dicom.OieDicomConverter;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;

public class DICOMConverter {

    private static OieDicomConverter getConverter() {
        return DicomLibraryFactory.getConverter();
    }

    public static OieDicomObject byteArrayToDicomObject(byte[] bytes, boolean decodeBase64) throws IOException {
        return getConverter().byteArrayToDicomObject(bytes, decodeBase64);
    }

    public static byte[] dicomObjectToByteArray(OieDicomObject dicomObject) throws IOException {
        return getConverter().dicomObjectToByteArray(dicomObject);
    }
}
