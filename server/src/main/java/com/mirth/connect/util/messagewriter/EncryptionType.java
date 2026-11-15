// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation

package com.mirth.connect.util.messagewriter;

import net.lingala.zip4j.model.enums.AesKeyStrength;

public enum EncryptionType {
    STANDARD("Standard", null), AES128("AES-128", AesKeyStrength.KEY_STRENGTH_128), AES256("AES-256",
            AesKeyStrength.KEY_STRENGTH_256);

    private String name;
    private AesKeyStrength keyStrength;

    private EncryptionType(String name, AesKeyStrength keyStrength) {
        this.name = name;
        this.keyStrength = keyStrength;
    }

    public String getDisplayName() {
        return name;
    }

    /** The AES key strength, or null for standard (ZipCrypto) encryption. */
    public AesKeyStrength getKeyStrength() {
        return keyStrength;
    }

    public static EncryptionType fromDisplayName(String displayName) {
        for (EncryptionType type : EncryptionType.values()) {
            if (type.getDisplayName().equals(displayName)) {
                return type;
            }
        }

        return null;
    }
}