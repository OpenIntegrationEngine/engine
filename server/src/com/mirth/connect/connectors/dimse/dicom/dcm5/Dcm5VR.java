// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

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
