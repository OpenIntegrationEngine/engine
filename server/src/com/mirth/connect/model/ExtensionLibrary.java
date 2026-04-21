// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.model;

import java.io.Serializable;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

@XStreamAlias("extensionLibrary")
public class ExtensionLibrary implements Serializable {
    public enum Type {
        SERVER, CLIENT, SHARED
    }

    @XStreamAsAttribute
    private String path;

    @XStreamAsAttribute
    private Type type;

    @XStreamAsAttribute
    private String variant;

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, CalendarToStringStyle.instance());
    }
}