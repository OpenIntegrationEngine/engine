// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Tony Germano

package org.openintegrationengine.engine.plugins.datatypes;

import java.io.IOException;

import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

/**
 * A base class for custom XMLReaders used in data type plugins.
 * <p>
 * This class handles the boilerplate requirements of the {@link XMLReader} interface, 
 * such as storing handler references and managing standard features/properties. 
 * Subclasses only need to implement the specific {@link #parse(InputSource)} logic.
 * </p>
 */
public abstract class AbstractXMLReader implements XMLReader {

    /** The ContentHandler to receive SAX events. */
    protected ContentHandler contentHandler;

    /** The ErrorHandler to receive error notifications. */
    protected ErrorHandler errorHandler;

    /** The DTDHandler to receive DTD events (rarely used for data types). */
    protected DTDHandler dtdHandler;

    /** The EntityResolver to resolve external entities. */
    protected EntityResolver entityResolver;

    /** Reusable instance that is cleared on each call of the accessor */
    private final AttributesImpl emptyAttributes = new AttributesImpl();

    /**
     * Helper method for subclasses to ensure the ContentHandler is configured.
     * <p>
     * Subclasses should call this at the beginning of their {@code parse} method.
     * </p>
     * 
     * @throws SAXException if the ContentHandler has not been set.
     */
    protected void ensureHandlerSet() throws SAXException {
        if (contentHandler == null) {
            throw new SAXException("ContentHandler not set");
        }
    }

    /**
     * Helper method for subclasses to get an empty instance of {@link AttributesImpl}.
     * <p>
     * This method reuses the same instance of {@link AttributesImpl} and clears it
     * before use.
     * </p>
     * 
     * @return An empty instance of {@link AttributesImpl}
     */
    protected AttributesImpl getEmptyAttributes() {
        emptyAttributes.clear();
        return emptyAttributes;
    }

    /**
     * Parse an XML document from the given input source.
     * <p>
     * Subclasses must implement this method to translate their specific data format
     * into SAX events on the {@code contentHandler}.
     * </p>
     *
     * @param input The input source for the top-level of the XML document.
     * @throws IOException If an I/O error occurs.
     * @throws SAXException If any SAX errors occur during processing.
     */
    @Override
    public abstract void parse(InputSource input) throws IOException, SAXException;

    /**
     * Parse an XML document from a system identifier (URI).
     * <p>
     * This implementation delegates to {@link #parse(InputSource)}.
     * </p>
     *
     * @param systemId The system identifier (URI).
     * @throws IOException If an I/O error occurs.
     * @throws SAXException If any SAX errors occur during processing.
     */
    @Override
    public void parse(String systemId) throws IOException, SAXException {
        parse(new InputSource(systemId));
    }

    // ---------------------------------------------------------
    // Standard Handler Getters/Setters
    // ---------------------------------------------------------

    /**
     * Return the current content handler.
     *
     * @return The current content handler, or null if none has been registered.
     */
    @Override
    public ContentHandler getContentHandler() {
        return contentHandler;
    }

    /**
     * Allow an application to register a content event handler.
     *
     * @param handler The content handler.
     */
    @Override
    public void setContentHandler(ContentHandler handler) {
        this.contentHandler = handler;
    }

    /**
     * Return the current error handler.
     *
     * @return The current error handler, or null if none has been registered.
     */
    @Override
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    /**
     * Allow an application to register an error event handler.
     *
     * @param handler The error handler.
     */
    @Override
    public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = handler;
    }

    /**
     * Return the current DTD handler.
     *
     * @return The current DTD handler, or null if none has been registered.
     */
    @Override
    public DTDHandler getDTDHandler() {
        return dtdHandler;
    }

    /**
     * Allow an application to register a DTD event handler.
     *
     * @param handler The DTD handler.
     */
    @Override
    public void setDTDHandler(DTDHandler handler) {
        this.dtdHandler = handler;
    }

    /**
     * Return the current entity resolver.
     *
     * @return The current entity resolver, or null if none has been registered.
     */
    @Override
    public EntityResolver getEntityResolver() {
        return entityResolver;
    }

    /**
     * Allow an application to register an entity resolver.
     *
     * @param resolver The entity resolver.
     */
    @Override
    public void setEntityResolver(EntityResolver resolver) {
        this.entityResolver = resolver;
    }

    // ---------------------------------------------------------
    // Strict Feature/Property Compliance
    // ---------------------------------------------------------

    /**
     * Look up the value of a feature flag.
     * <p>
     * This implementation enforces mandatory SAX2 features:
     * <ul>
     * <li>{@code http://xml.org/sax/features/namespaces}: Always returns {@code true}.</li>
     * <li>{@code http://xml.org/sax/features/namespace-prefixes}: Always returns {@code false}.</li>
     * </ul>
     * All other features throw {@link SAXNotRecognizedException}.
     * </p>
     *
     * @param name The feature name, which is a fully-qualified URI.
     * @return The current value of the feature (true or false).
     * @throws SAXNotRecognizedException If the feature value can't be assigned or
     *                                   retrieved.
     * @throws SAXNotSupportedException  When the feature name is recognized but its
     *                                   value cannot be determined at this time.
     */
    @Override
    public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        if ("http://xml.org/sax/features/namespaces".equals(name)) {
            return true;
        }
        else if ("http://xml.org/sax/features/namespace-prefixes".equals(name)) {
            return false;
        }
        throw new SAXNotRecognizedException("Feature not recognized: " + name);
    }

    /**
     * Set the value of a feature flag.
     * <p>
     * This implementation prevents modifying mandatory SAX2 features to ensure the parser 
     * functions correctly with downstream transformers.
     * </p>
     *
     * @param name The feature name, which is a fully-qualified URI.
     * @param value The requested value of the feature (true or false).
     * @throws SAXNotRecognizedException If the feature value can't be assigned or retrieved.
     * @throws SAXNotSupportedException When the feature name is recognized but its
     *                                   value cannot be set to the requested value.
     */
    @Override
    public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if ("http://xml.org/sax/features/namespaces".equals(name)) {
            if (!value) {
                throw new SAXNotSupportedException("Cannot disable 'namespaces' feature for this parser");
            }
            return;
        }
        else if ("http://xml.org/sax/features/namespace-prefixes".equals(name)) {
            if (value) {
                throw new SAXNotSupportedException("Cannot enable 'namespace-prefixes' feature for this parser");
            }
            return;
        }
        throw new SAXNotRecognizedException("Feature not recognized: " + name);
    }

    /**
     * Look up the value of a property.
     *
     * @param name The property name, which is a fully-qualified URI.
     * @return The current value of the property.
     * @throws SAXNotRecognizedException If the property value can't be assigned or retrieved.
     * @throws SAXNotSupportedException When the property name is recognized but its
     *                                   value cannot be determined at this time.
     */
    @Override
    public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        throw new SAXNotRecognizedException("Property not recognized: " + name);
    }

    /**
     * Set the value of a property.
     *
     * @param name The property name, which is a fully-qualified URI.
     * @param value The requested value for the property.
     * @throws SAXNotRecognizedException If the property value can't be assigned or retrieved.
     * @throws SAXNotSupportedException When the property name is recognized but its
     *                                   value cannot be set to the requested value.
     */
    @Override
    public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        throw new SAXNotRecognizedException("Property not recognized: " + name);
    }
}
