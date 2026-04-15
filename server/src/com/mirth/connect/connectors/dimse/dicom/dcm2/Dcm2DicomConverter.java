/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse.dicom.dcm2;

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
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.ElementDictionary;
import org.dcm4che2.data.TransferSyntax;
import org.dcm4che2.io.ContentHandlerAdapter;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.io.SAXWriter;
import org.xml.sax.InputSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.connectors.dimse.dicom.OieDicomConverter;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.donkey.util.ByteCounterOutputStream;

/**
 * dcm4che2 implementation of OieDicomConverter. Handles all byte-array, XML, and
 * DICOM object conversion using the dcm4che 2.0.29 library.
 */
public class Dcm2DicomConverter implements OieDicomConverter {

    private static final Logger logger = LogManager.getLogger(Dcm2DicomConverter.class);

    @Override
    public OieDicomObject byteArrayToDicomObject(byte[] bytes, boolean decodeBase64) throws IOException {
        DicomObject basicDicomObject = new BasicDicomObject();
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
            /*
             * This parameter was added in dcm4che 2.0.28. We use it to retain the memory allocation
             * behavior from 2.0.25. http://www.mirthcorp.com/community/issues/browse/MIRTH-2166
             * http://www.dcm4che.org/jira/browse/DCM-554
             */
            dis.setAllocateLimit(-1);
            dis.readDicomObject(basicDicomObject, -1);
        } catch (IOException e) {
            throw e;
        } finally {
            IOUtils.closeQuietly(dis);
        }

        return new Dcm2DicomObject(basicDicomObject);
    }

    @Override
    public byte[] dicomObjectToByteArray(OieDicomObject dicomObject) throws IOException {
        BasicDicomObject basicDicomObject = (BasicDicomObject) dicomObject.unwrap();
        DicomOutputStream dos = null;

        try {
            ByteCounterOutputStream bcos = new ByteCounterOutputStream();
            ByteArrayOutputStream baos;

            if (basicDicomObject.fileMetaInfo().isEmpty()) {
                try {
                    dos = new DicomOutputStream(bcos);
                    dos.writeDataset(basicDicomObject, TransferSyntax.ImplicitVRLittleEndian);
                } finally {
                    IOUtils.closeQuietly(dos);
                }

                baos = new ByteArrayOutputStream(bcos.size());
                dos = new DicomOutputStream(baos);
                dos.writeDataset(basicDicomObject, TransferSyntax.ImplicitVRLittleEndian);
            } else {
                try {
                    dos = new DicomOutputStream(bcos);
                    dos.writeDicomFile(basicDicomObject);
                } finally {
                    IOUtils.closeQuietly(dos);
                }

                baos = new ByteArrayOutputStream(bcos.size());
                dos = new DicomOutputStream(baos);
                dos.writeDicomFile(basicDicomObject);
            }

            // Memory Optimization since the dicom object is no longer needed at this point.
            dicomObject.clear();

            return baos.toByteArray();
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            logger.error("Failed to serialize DICOM object to byte array", t);
            throw new IOException("DICOM serialization failed", t);
        } finally {
            IOUtils.closeQuietly(dos);
        }
    }

    @Override
    public OieDicomObject createDicomObject() {
        return new Dcm2DicomObject();
    }

    @Override
    public String dicomBytesToXml(byte[] encodedDicomBytes) throws Exception {
        StringWriter output = new StringWriter();
        DicomInputStream dis = new DicomInputStream(new BufferedInputStream(new Base64InputStream(new ByteArrayInputStream(encodedDicomBytes))));
        dis.setAllocateLimit(-1);

        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            SAXTransformerFactory factory = (SAXTransformerFactory) tf;
            TransformerHandler handler = factory.newTransformerHandler();
            handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
            handler.setResult(new StreamResult(output));

            final SAXWriter writer = new SAXWriter(handler, null);
            dis.setHandler(writer);
            dis.readDicomObject(new BasicDicomObject(), -1);

            return output.toString();
        } finally {
            IOUtils.closeQuietly(dis);
            IOUtils.closeQuietly(output);
        }
    }

    @Override
    public OieDicomObject xmlToDicomObject(String xml, String charset) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        SAXParser parser = factory.newSAXParser();
        DicomObject dicomObject = new BasicDicomObject();
        ContentHandlerAdapter contentHandler = new ContentHandlerAdapter(dicomObject);
        byte[] documentBytes = xml.trim().getBytes(charset);
        parser.parse(new InputSource(new ByteArrayInputStream(documentBytes)), contentHandler);
        return new Dcm2DicomObject(dicomObject);
    }

    @Override
    public String getElementName(int tag) {
        try {
            return ElementDictionary.getDictionary().nameOf(tag);
        } catch (Exception e) {
            return "";
        }
    }
}
