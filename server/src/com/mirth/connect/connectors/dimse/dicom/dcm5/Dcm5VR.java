/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom.dcm5;

import org.dcm4che3.data.VR;

import com.mirth.connect.connectors.dimse.dicom.OieVR;

public class Dcm5VR implements OieVR {

    private final VR vr;

    public Dcm5VR(VR vr) {
        this.vr = vr;
    }

    @Override
    public String toString() {
        return vr.toString();
    }

    @Override
    public int code() {
        return vr.code();
    }

    @Override
    public int padding() {
        return vr.paddingByte();
    }

    @Override
    public Object unwrap() {
        return vr;
    }
}
