// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation

package com.mirth.connect.model;

import java.io.Serializable;

public class LoginStatus implements Serializable {

    public enum Status {
        SUCCESS, SUCCESS_GRACE_PERIOD, FAIL, FAIL_EXPIRED, FAIL_LOCKED_OUT, FAIL_VERSION_MISMATCH
    }

    private Status status;
    private String message;
    private String updatedUsername;

    public LoginStatus(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public LoginStatus(Status status, String message, String updatedUsername) {
        this.status = status;
        this.message = message;
        this.updatedUsername = updatedUsername;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getUpdatedUsername() {
        return updatedUsername;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.SUCCESS_GRACE_PERIOD;
    }
}
