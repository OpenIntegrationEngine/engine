package com.mirth.connect.model.converters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.donkey.util.xstream.SerializerException;
import com.mirth.connect.util.JsonXmlUtil;

public class ObjectJSONSerializer {

    private static final ObjectJSONSerializer instance = new ObjectJSONSerializer();
    
    private Logger logger = LogManager.getLogger(getClass());
    
    public static ObjectJSONSerializer getInstance() {
        return instance;
    }
    
    // Object -> XML -> JSON
    public void serialize(Object object, OutputStream outputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter xmlWriter = new OutputStreamWriter(baos, "UTF-8");
        ObjectXMLSerializer.getInstance().serialize(object, xmlWriter);
        
        String xmlString = baos.toString();
        String jsonString = "";
        
        // Our xml util xml to json
        try {
            jsonString = xmlToJson(xmlString);
        } catch (Exception e) {
            logger.error(e);
            throw new SerializerException(e);
        }
        
        outputStream.write(jsonString.getBytes());
    }
    
    /**
     * A character reference to a character that XML 1.0 does not allow, such as U+FFFE or a C0
     * control other than tab, LF or CR. XStream writes these for such characters in free text
     * (code templates, scripts, descriptions), and the XML parser in JsonXmlUtil rejects them, which
     * failed the whole response. See OpenIntegrationEngine/engine#566.
     */
    private static final Pattern CHARACTER_REFERENCE = Pattern.compile("&#(?:[xX]([0-9a-fA-F]+)|([0-9]+));");

    /**
     * Converts the XML to JSON. Character references that XML 1.0 does not allow are swapped for a
     * private-use placeholder that does not occur in this document, and turned back into JSON
     * escapes afterwards, so the characters come through unchanged.
     */
    static String xmlToJson(String xml) throws Exception {
        char marker = unusedPrivateUseCharacter(xml);
        StringBuilder protectedXml = new StringBuilder(xml.length());
        Matcher matcher = CHARACTER_REFERENCE.matcher(xml);
        boolean replaced = false;

        while (matcher.find()) {
            int codePoint = parseCodePoint(matcher);
            String replacement = matcher.group();

            if (codePoint >= 0 && codePoint <= 0xFFFF && !isXml10Char(codePoint)) {
                replacement = marker + String.format("%04x", codePoint);
                replaced = true;
            }

            matcher.appendReplacement(protectedXml, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(protectedXml);

        String json = JsonXmlUtil.xmlToJson(replaced ? protectedXml.toString() : xml);
        return replaced ? json.replaceAll(Pattern.quote(String.valueOf(marker)) + "([0-9a-f]{4})", "\\\\u$1") : json;
    }

    private static int parseCodePoint(Matcher matcher) {
        try {
            return matcher.group(1) != null ? Integer.parseInt(matcher.group(1), 16) : Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isXml10Char(int codePoint) {
        return codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD || (codePoint >= 0x20 && codePoint <= 0xD7FF) || (codePoint >= 0xE000 && codePoint <= 0xFFFD);
    }

    private static char unusedPrivateUseCharacter(String text) {
        for (char candidate = ''; candidate <= ''; candidate++) {
            if (text.indexOf(candidate) < 0) {
                return candidate;
            }
        }
        throw new IllegalStateException("No unused private-use character is available for the XML to JSON conversion");
    }

    /* Converts a source JSON string to XML then calls ObjectXMLSerializer.deserialize(...)
     * JSON -> XML -> Object
     */
    public <T> T deserialize(String serializedObject, Class<T> expectedClass) {
        String xmlSerializedObject = "";
        
        // Our json -> xml
        try {
            xmlSerializedObject = JsonXmlUtil.jsonToXml(serializedObject);
        } catch (Exception e) {
            logger.error(e);
            throw new SerializerException(e);
        }
        
        return ObjectXMLSerializer.getInstance().deserialize(xmlSerializedObject, expectedClass);
    }
    
    /**
     * Converts a source JSON string to XML then calls ObjectXMLSerializer.deserializeList(...).
     * JSON -> XML -> Object
     */
    public <T> List<T> deserializeList(String serializedObject, Class<T> expectedListItemClass) {
        String xmlSerializedObject = "";
        
        // Our json -> xml
        try {
            xmlSerializedObject = JsonXmlUtil.jsonToXml(serializedObject);
        } catch (Exception e) {
            logger.error(e);
            throw new SerializerException(e);
        }
        
        return ObjectXMLSerializer.getInstance().deserializeList(xmlSerializedObject, expectedListItemClass);
    }
}
