// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.client.ui;

/** Utility class to check if the client prerequisites are met. */
public class ClientPrerequisites {

    private ClientPrerequisites() {
    }

    /** Checks if the client prerequisites are met. */
    public static String getMissing() {
        // Can't meaningfully check for Java 17 since the entrypoint is compiled with
        // class file version 61, which will fail to load on Java 8.
        if (!hasJavaFx()) {
            return "JavaFX";
        }
        
        return null;
    }

    private static boolean hasJavaFx() {
        // Use JPMS to check for JavaFX - the classes are on the classpath
        // even in Java SE, but the module will not be present except in FX
        return ModuleLayer.boot().findModule("javafx.graphics").isPresent();
    }
}