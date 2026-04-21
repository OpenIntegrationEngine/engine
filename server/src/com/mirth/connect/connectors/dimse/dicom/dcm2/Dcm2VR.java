// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.dcm2;

import org.dcm4che2.data.VR;

import com.mirth.connect.connectors.dimse.dicom.OieVR;

public class Dcm2VR implements OieVR {

    private final VR vr;

    public Dcm2VR(VR vr) {
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
        return vr.padding();
    }

    @Override
    public Object unwrap() {
        return vr;
    }
}
