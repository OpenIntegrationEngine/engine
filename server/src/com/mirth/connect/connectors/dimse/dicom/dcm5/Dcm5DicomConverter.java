/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom.dcm5;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.ContentHandlerAdapter;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.SAXWriter;
import org.xml.sax.InputSource;

import com.mirth.connect.connectors.dimse.dicom.OieDicomConverter;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;

/**
 * dcm4che5 implementation of OieDicomConverter. Handles all byte-array, XML, and
 * DICOM object conversion using the dcm4che 5.34.3 library.
 *
 * <p>Key differences from dcm2:
 * <ul>
 *   <li>No {@code setAllocateLimit(-1)} needed</li>
 *   <li>Single-pass write (no ByteCounterOutputStream)</li>
 *   <li>FMI is a separate Attributes object</li>
 *   <li>SAXWriter.write(dataset) instead of stream handler</li>
 * </ul>
 */
public class Dcm5DicomConverter implements OieDicomConverter {

    private static final Logger logger = LogManager.getLogger(Dcm5DicomConverter.class);

    @Override
    public OieDicomObject byteArrayToDicomObject(byte[] bytes, boolean decodeBase64) throws IOException {
        DicomInputStream dis = null;

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            InputStream inputStream;
            if (decodeBase64) {
                inputStream = new BufferedInputStream(new Base64InputStream(bais));
            } else {
                inputStream = bais;
            }
            dis = new DicomInputStream(inputStream);
            Attributes fmi = dis.readFileMetaInformation();
            Attributes dataset = dis.readDataset(-1, -1);
            return new Dcm5DicomObject(fmi, dataset);
        } catch (IOException e) {
            throw e;
        } finally {
            IOUtils.closeQuietly(dis);
        }
    }

    @Override
    public byte[] dicomObjectToByteArray(OieDicomObject dicomObject) throws IOException {
        Dcm5DicomObject dcm5obj = (Dcm5DicomObject) dicomObject;
        Attributes dataset = (Attributes) dcm5obj.unwrap();
        Attributes fmi = dcm5obj.getFmi();

        DicomOutputStream dos = null;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            if (fmi != null && !fmi.isEmpty()) {
                dos = new DicomOutputStream(baos, UID.ExplicitVRLittleEndian);
                dos.writeDataset(fmi, dataset);
            } else {
                dos = new DicomOutputStream(baos, UID.ImplicitVRLittleEndian);
                dos.writeDataset(null, dataset);
            }

            // Memory optimization since the dicom object is no longer needed at this point.
            dicomObject.clear();

            return baos.toByteArray();
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            logger.error("Error serializing DICOM object to byte array", t);
            return null;
        } finally {
            IOUtils.closeQuietly(dos);
        }
    }

    @Override
    public OieDicomObject createDicomObject() {
        return new Dcm5DicomObject();
    }

    @Override
    public String dicomBytesToXml(byte[] encodedDicomBytes) throws Exception {
        DicomInputStream dis = new DicomInputStream(new BufferedInputStream(new Base64InputStream(new ByteArrayInputStream(encodedDicomBytes))));

        try {
            Attributes fmi = dis.readFileMetaInformation();
            Attributes dataset = dis.readDataset(-1, -1);

            StringWriter output = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            SAXTransformerFactory factory = (SAXTransformerFactory) tf;
            TransformerHandler handler = factory.newTransformerHandler();
            handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
            handler.setResult(new StreamResult(output));

            SAXWriter writer = new SAXWriter(handler);
            writer.setIncludeKeyword(true);
            writer.write(dataset);

            return output.toString();
        } finally {
            IOUtils.closeQuietly(dis);
        }
    }

    @Override
    public OieDicomObject xmlToDicomObject(String xml, String charset) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        SAXParser parser = factory.newSAXParser();
        Attributes dataset = new Attributes();
        ContentHandlerAdapter contentHandler = new ContentHandlerAdapter(dataset);
        byte[] documentBytes = xml.trim().getBytes(charset);
        parser.parse(new InputSource(new ByteArrayInputStream(documentBytes)), contentHandler);
        return new Dcm5DicomObject(dataset);
    }

    @Override
    public String getElementName(int tag) {
        try {
            String keyword = ElementDictionary.keywordOf(tag, null);
            return keyword != null ? keyword : "";
        } catch (Exception e) {
            return "";
        }
    }
}
