/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.userutil;

/**
 * Denotes the status of a connector message or response. Available statuses:
 * 
 * RECEIVED, FILTERED, TRANSFORMED, SENT, QUEUED, ERROR, PENDING
 */
public enum Status {
    /** The message has been received by the connector. */
    RECEIVED,
    /** The message has been filtered by the connector. */
    FILTERED,
    /** The message has been transformed by the connector. */
    TRANSFORMED,
    /** The message has been sent by the connector and received a successful response. */
    SENT,
    /** The message is queued for processing. */
    QUEUED,
    /** The message has encountered an error. */
    ERROR,
    /** The message is pending processing. */
    PENDING;

    private Status() {}

    static Status fromDonkeyStatus(com.mirth.connect.donkey.model.message.Status status) {
        switch (status) {
            case RECEIVED:
                return RECEIVED;
            case FILTERED:
                return FILTERED;
            case TRANSFORMED:
                return TRANSFORMED;
            case SENT:
                return SENT;
            case QUEUED:
                return QUEUED;
            case ERROR:
                return ERROR;
            case PENDING:
                return PENDING;
            default:
                return null;
        }
    }

    com.mirth.connect.donkey.model.message.Status toDonkeyStatus() {
        switch (this) {
            case RECEIVED:
                return com.mirth.connect.donkey.model.message.Status.RECEIVED;
            case FILTERED:
                return com.mirth.connect.donkey.model.message.Status.FILTERED;
            case TRANSFORMED:
                return com.mirth.connect.donkey.model.message.Status.TRANSFORMED;
            case SENT:
                return com.mirth.connect.donkey.model.message.Status.SENT;
            case QUEUED:
                return com.mirth.connect.donkey.model.message.Status.QUEUED;
            case ERROR:
                return com.mirth.connect.donkey.model.message.Status.ERROR;
            case PENDING:
                return com.mirth.connect.donkey.model.message.Status.PENDING;
            default:
                return null;
        }
    }
}
