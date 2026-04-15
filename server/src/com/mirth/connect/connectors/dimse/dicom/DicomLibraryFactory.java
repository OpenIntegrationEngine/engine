/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.donkey.server.channel.SourceConnector;

/**
 * Factory for creating version-neutral DICOM library instances. Reads the
 * {@code dicom.library} property from mirth.properties to determine which
 * backend to use. Defaults to {@code dcm4che2}.
 *
 * <p>All backend classes are loaded via {@code Class.forName} to avoid
 * compile-time dependencies on variant-specific code.
 */
public final class DicomLibraryFactory {

    private static final Logger logger = LogManager.getLogger(DicomLibraryFactory.class);

    private static final String MIRTH_PROPERTIES_FILE = "./conf/mirth.properties";
    private static final String PROPERTY_DICOM_LIBRARY = "dicom.library";

    // dcm4che2 backend class names
    private static final String DCM2_CONVERTER = "com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DicomConverter";
    private static final String DCM2_SENDER = "com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DicomSender";
    private static final String DCM2_RECEIVER = "com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DicomReceiver";
    private static final String DCM2_DEFAULT_CONFIG = "com.mirth.connect.connectors.dimse.DefaultDICOMConfiguration";

    // dcm4che5 backend class names
    private static final String DCM5_CONVERTER = "com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomConverter";
    private static final String DCM5_SENDER = "com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomSender";
    private static final String DCM5_RECEIVER = "com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomReceiver";
    private static final String DCM5_DEFAULT_CONFIG = "com.mirth.connect.connectors.dimse.DefaultDcm5DICOMConfiguration";

    public enum DicomLibrary {
        DCM4CHE2, DCM4CHE5
    }

    private static volatile DicomLibrary activeLibrary;
    private static volatile OieDicomConverter converterInstance;

    private DicomLibraryFactory() {}

    /**
     * Returns the active DICOM library backend, reading from mirth.properties on first access.
     */
    public static DicomLibrary getActiveLibrary() {
        if (activeLibrary == null) {
            synchronized (DicomLibraryFactory.class) {
                if (activeLibrary == null) {
                    activeLibrary = detectLibrary();
                }
            }
        }
        return activeLibrary;
    }

    /**
     * Returns a singleton converter instance for the configured DICOM library version.
     */
    public static OieDicomConverter getConverter() {
        if (converterInstance == null) {
            synchronized (DicomLibraryFactory.class) {
                if (converterInstance == null) {
                    converterInstance = createConverterInstance();
                }
            }
        }
        return converterInstance;
    }

    /**
     * Creates a new DICOM sender for the configured library version.
     */
    public static OieDicomSender createSender(DICOMConfiguration configuration) {
        try {
            String className = getSenderClassName();
            Class<?> clazz = Class.forName(className);
            Constructor<?> ctor = clazz.getConstructor(DICOMConfiguration.class);
            return (OieDicomSender) ctor.newInstance(configuration);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create DICOM sender for library: " + getActiveLibrary(), e);
        }
    }

    /**
     * Creates a new DICOM receiver for the configured library version.
     */
    public static OieDicomReceiver createReceiver(SourceConnector connector, DICOMConfiguration configuration) {
        try {
            String className = getReceiverClassName();
            Class<?> clazz = Class.forName(className);
            Constructor<?> ctor = clazz.getConstructor(SourceConnector.class, DICOMConfiguration.class);
            return (OieDicomReceiver) ctor.newInstance(connector, configuration);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create DICOM receiver for library: " + getActiveLibrary(), e);
        }
    }

    /**
     * Creates the default DICOMConfiguration for the configured library version.
     */
    public static DICOMConfiguration createDefaultConfiguration() {
        try {
            String className = getDefaultConfigClassName();
            return (DICOMConfiguration) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create default DICOMConfiguration for library: " + getActiveLibrary(), e);
        }
    }

    /**
     * Loads a DICOMConfiguration by class name. Falls back to the default configuration
     * if the class cannot be loaded or does not implement DICOMConfiguration.
     *
     * @param className the fully qualified class name, or null/empty for the default
     * @return a DICOMConfiguration instance
     */
    public static DICOMConfiguration loadConfiguration(String className) {
        if (className == null || className.trim().isEmpty()) {
            return createDefaultConfiguration();
        }
        try {
            Object instance = Class.forName(className.trim()).getDeclaredConstructor().newInstance();
            if (instance instanceof DICOMConfiguration) {
                return (DICOMConfiguration) instance;
            }
            logger.warn("Custom DICOMConfiguration class does not implement current interface: "
                    + className + ". Using default. If this is a legacy class, recompile against Dcm2DICOMConfiguration.");
            return createDefaultConfiguration();
        } catch (Exception e) {
            logger.warn("Could not load custom DICOMConfiguration class, using default: " + className, e);
            return createDefaultConfiguration();
        }
    }

    private static OieDicomConverter createConverterInstance() {
        try {
            String className = getConverterClassName();
            return (OieDicomConverter) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create DICOM converter for library: " + getActiveLibrary(), e);
        }
    }

    private static String getConverterClassName() {
        return getActiveLibrary() == DicomLibrary.DCM4CHE5 ? DCM5_CONVERTER : DCM2_CONVERTER;
    }

    private static String getSenderClassName() {
        return getActiveLibrary() == DicomLibrary.DCM4CHE5 ? DCM5_SENDER : DCM2_SENDER;
    }

    private static String getReceiverClassName() {
        return getActiveLibrary() == DicomLibrary.DCM4CHE5 ? DCM5_RECEIVER : DCM2_RECEIVER;
    }

    private static String getDefaultConfigClassName() {
        return getActiveLibrary() == DicomLibrary.DCM4CHE5 ? DCM5_DEFAULT_CONFIG : DCM2_DEFAULT_CONFIG;
    }

    private static DicomLibrary detectLibrary() {
        DicomLibrary library = DicomLibrary.DCM4CHE2;
        try (FileInputStream is = new FileInputStream(new File(MIRTH_PROPERTIES_FILE))) {
            Properties props = new Properties();
            props.load(is);
            String value = props.getProperty(PROPERTY_DICOM_LIBRARY, "dcm4che2").trim();
            if ("dcm4che5".equalsIgnoreCase(value)) {
                library = DicomLibrary.DCM4CHE5;
            } else if (!value.isEmpty() && !"dcm4che2".equalsIgnoreCase(value)) {
                logger.warn("Unrecognized value for {}: '{}'. Supported values are 'dcm4che2' and 'dcm4che5'. Defaulting to dcm4che2.",
                        PROPERTY_DICOM_LIBRARY, value);
            }
        } catch (Exception e) {
            // Default to dcm4che2 if properties cannot be read (e.g., in tests)
        }
        logger.info("DICOM library backend: {}", library);
        return library;
    }

    /**
     * Resets factory state for testing. Not for production use.
     */
    public static void resetForTesting(DicomLibrary override) {
        synchronized (DicomLibraryFactory.class) {
            activeLibrary = override;
            converterInstance = null;
        }
    }
}
