// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>
package com.mirth.connect.plugins.datatypes.xml;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.xml.sax.SAXParseException;

import com.mirth.connect.donkey.model.message.BatchRawMessage;
import com.mirth.connect.donkey.server.message.batch.BatchMessageException;
import com.mirth.connect.donkey.server.message.batch.BatchMessageReader;
import com.mirth.connect.plugins.datatypes.xml.XMLBatchProperties.SplitType;

/** Covered end to end by ci/tests/210-xml-batch-xxe. */
public class XMLBatchAdaptorTest {

    @Test
    public void externalEntityIsNotResolved() throws Exception {
        File secret = File.createTempFile("xxe", ".txt");
        secret.deleteOnExit();
        FileUtils.write(secret, "canary", UTF_8);

        XMLBatchAdaptor adaptor = adaptor("<!DOCTYPE Batch [ <!ENTITY xxe SYSTEM \"" + secret.toURI() + "\"> ]><Batch><Message>&xxe;</Message></Batch>");

        try {
            fail("Expected the external entity to be rejected, but got: " + adaptor.getMessage());
        } catch (BatchMessageException e) {
            assertTrue(String.valueOf(e.getCause()), e.getCause() instanceof SAXParseException);
            assertFalse(String.valueOf(e), String.valueOf(e).contains("canary"));
        }
    }

    @Test
    public void batchSplitsOnElementName() throws Exception {
        XMLBatchAdaptor adaptor = adaptor("<Batch><Message>alpha</Message><Message>bravo</Message></Batch>");

        assertEquals("<Message>alpha</Message>", adaptor.getMessage().trim());
        assertEquals("<Message>bravo</Message>", adaptor.getMessage().trim());
        assertNull(adaptor.getMessage());
    }

    /** Splitting is namespace aware, as it was when XPath parsed the InputSource itself. */
    @Test
    public void batchSplitKeepsNamespaceDeclarations() throws Exception {
        XMLBatchAdaptor adaptor = adaptor("<b:Batch xmlns:b=\"urn:b\"><b:Message>alpha</b:Message></b:Batch>");

        assertEquals("<b:Message xmlns:b=\"urn:b\">alpha</b:Message>", adaptor.getMessage().trim());
        assertNull(adaptor.getMessage());
    }

    private static XMLBatchAdaptor adaptor(String xml) {
        XMLBatchProperties properties = new XMLBatchProperties();
        properties.setSplitType(SplitType.Element_Name);
        properties.setElementName("Message");

        XMLBatchAdaptor adaptor = new XMLBatchAdaptor(null, null, new BatchRawMessage(new BatchMessageReader(xml)));
        adaptor.setBatchProperties(properties);
        return adaptor;
    }
}
