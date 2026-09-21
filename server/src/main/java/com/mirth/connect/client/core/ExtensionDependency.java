/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.client.core;

import java.io.Serializable;

/** A required engine API or plugin version declared by an extension. */
public class ExtensionDependency implements Serializable {
    private static final long serialVersionUID = 1L;

    private String type;
    private String name;
    private String minVersion;

    public ExtensionDependency() {}

    public ExtensionDependency(String type, String name, String minVersion) {
        this.type = type;
        this.name = name;
        this.minVersion = minVersion;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public void setMinVersion(String minVersion) {
        this.minVersion = minVersion;
    }
}
