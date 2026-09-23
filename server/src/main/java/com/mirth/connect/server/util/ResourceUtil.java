/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import com.mirth.connect.server.tools.ClassPathResource;

public class ResourceUtil {

    /**
     * Returns the mirth.properties.d drop-in directory next to the mirth.properties resolved from
     * the classpath, or null when mirth.properties does not resolve to a file on disk.
     */
    public static File getMirthPropertiesDropInDirectory() {
        URI uri = ClassPathResource.getResourceURI("mirth.properties");

        if (uri != null && "file".equals(uri.getScheme())) {
            return new File(new File(uri).getParentFile(), "mirth.properties.d");
        }

        return null;
    }

    /**
     * Returns a resource as a stream by checking:
     * 
     * 1. The classpath for a resource with the specified name 2. For a file with the specified path
     * 
     * @param resourceName
     * @param path
     * @return
     * @throws FileNotFoundException
     */
    public static InputStream getResourceStream(Class<?> clazz, String resourceName) throws FileNotFoundException {
        String cpResourceName = null;

        if (!resourceName.startsWith("/")) {
            cpResourceName = "/" + resourceName;
        } else {
            cpResourceName = resourceName;
        }

        InputStream is = clazz.getResourceAsStream(cpResourceName);

        if (is == null) {
            is = new FileInputStream(resourceName);
        }

        return is;
    }

    public static void closeResourceQuietly(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
}
