/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.userutil;

/**
 * This object is returned from {@link EncryptionUtil#encrypt(byte[])}.
 */
public class EncryptedData {

    private String header;
    private byte[] encryptedData;

    /**
     * Instantiates a new EncryptedData object with the specified header and encrypted data.
     * 
     * @param header
     *            The meta-information about the encrypted data, including the algorithm and
     *            initialization vector used.
     * @param encryptedData
     *            The encrypted data as a byte array.
     */
    public EncryptedData(String header, byte[] encryptedData) {
        this.header = header;
        this.encryptedData = encryptedData;
    }

    /**
     * Returns the meta-information about the encrypted data. Includes the algorithm and
     * initialization vector used.
     * 
     * @return The header information as a string.
     */
    public String getHeader() {
        return header;
    }

    /**
     * Returns the encrypted data as a byte array.
     * 
     * @return The encrypted data.
     */
    public byte[] getEncryptedData() {
        return encryptedData;
    }
}
