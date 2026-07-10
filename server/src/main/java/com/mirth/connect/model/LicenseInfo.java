/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class LicenseInfo implements Serializable {

    public static final LicenseInfo INSTANCE = new LicenseInfo();

    private boolean activated;
    private boolean online;
    private Long expirationDate;
    private Long warningPeriod;
    private Long gracePeriod;
    private boolean error = false;
    private String errorReason = null;
    private boolean warning = false;
    private String warningReason = null;
    private boolean padlockWarning = false;
    private boolean padlock = false;
    private boolean expired = false;
    private boolean keyNotFound = false;
    private boolean unauthorized = false;
    private Set<String> extensions = new HashSet<String>();
    private Set<String> unpermittedExtensions = new HashSet<String>();
    private Set<String> downloadedExtensions = new HashSet<String>();
    
    public LicenseInfo() {}    

	public boolean isPadlockWarning() {
		return padlockWarning;
	}

	public void setPadlockWarning(boolean padlockWarning) {
		this.padlockWarning = padlockWarning;
	}

	public boolean isPadlock() {
		return padlock;
	}

	public void setPadlock(boolean padlock) {
		this.padlock = padlock;
	}

	public boolean isExpired() {
		return expired;
	}

	public void setExpired(boolean expired) {
		this.expired = expired;
	}

	public boolean isKeyNotFound() {
		return keyNotFound;
	}

	public void setKeyNotFound(boolean keyNotFound) {
		this.keyNotFound = keyNotFound;
	}

	public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public Long getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(Long expirationDate) {
        this.expirationDate = expirationDate;
    }

    public Long getWarningPeriod() {
        return warningPeriod;
    }

    public void setWarningPeriod(Long warningPeriod) {
        this.warningPeriod = warningPeriod;
    }

    public Long getGracePeriod() {
        return gracePeriod;
    }

    public void setGracePeriod(Long gracePeriod) {
        this.gracePeriod = gracePeriod;
    }

    public Set<String> getExtensions() {
        return extensions;
    }

    public void setExtensions(Set<String> extensions) {
        this.extensions = extensions;
    }

    public Set<String> getDownloadedExtensions() {
        return downloadedExtensions;
    }

    public void setDownloadedExtensions(Set<String> downloadedExtensions) {
        this.downloadedExtensions = downloadedExtensions;
    }

	public boolean isUnauthorized() {
		return unauthorized;
	}

	public void setUnauthorized(boolean unauthorized) {
		this.unauthorized = unauthorized;
	}

	public Set<String> getUnpermittedExtensions() {
		return unpermittedExtensions;
	}

	public void setUnpermittedExtensions(Set<String> unpermittedExtensions) {
		this.unpermittedExtensions = unpermittedExtensions;
	}

	public String getErrorReason() {
		return errorReason;
	}

	public void setErrorReason(String errorReason) {
		this.errorReason = errorReason;
	}

	public String getWarningReason() {
		return warningReason;
	}

	public void setWarningReason(String warningReason) {
		this.warningReason = warningReason;
	}

	public boolean isWarning() {
		return warning;
	}

	public void setWarning(boolean warning) {
		this.warning = warning;
	}

	public boolean isError() {
		return error;
	}

	public void setError(boolean error) {
		this.error = error;
	}

}
