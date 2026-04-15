/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom;

/**
 * Version-neutral wrapper for a DICOM Value Representation. Matches the runtime
 * shape of dcm4che2's {@code VR} class so existing JavaScript transformer
 * scripts calling {@code elem.vr().code()} or {@code String(elem.vr())}
 * continue to work regardless of the configured DICOM backend.
 */
public interface OieVR {

    /** Two-character VR code (e.g. {@code "UI"}, {@code "SQ"}, {@code "OB"}). */
    String toString();

    /** Packed 16-bit representation of the VR code, as stored on the wire. */
    int code();

    /** Pad byte used when the VR's encoded length would otherwise be odd. */
    int padding();

    /** Underlying library-specific VR object. Use with caution. */
    Object unwrap();
}
