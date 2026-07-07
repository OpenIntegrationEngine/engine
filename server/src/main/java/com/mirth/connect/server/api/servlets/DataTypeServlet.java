package com.mirth.connect.server.api.servlets;

import java.io.StringReader;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.client.core.api.servlets.DataTypeServletInterface;
import com.mirth.connect.donkey.model.message.SerializationType;
import com.mirth.connect.model.converters.IMessageSerializer;
import com.mirth.connect.model.datatype.DataTypeProperties;
import com.mirth.connect.model.datatype.SerializationProperties;
import com.mirth.connect.model.datatype.SerializerProperties;
import com.mirth.connect.model.util.MessageVocabulary;
import com.mirth.connect.plugins.DataTypeServerPlugin;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;

public class DataTypeServlet extends MirthServlet implements DataTypeServletInterface {

    private static final ExtensionController extensionController = ControllerFactory.getFactory().createExtensionController();

    public DataTypeServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc);
    }

    @Override
    public Response serializeMessage(String dataType, String props, String message) {
        // Look the data type up in the installed plugins (keyed by the data type name — "HL7V2",
        // "XML", "EDI/X12", …). This uses only core interfaces, so no concrete datatype import.
        DataTypeServerPlugin plugin = dataType == null ? null : extensionController.getDataTypePlugins().get(dataType);
        if (plugin == null) {
            throw new MirthApiException(Status.BAD_REQUEST);
        }
        String msg = message == null ? "" : message;

        try {
            DataTypeProperties dtProps = plugin.getDefaultProperties();
            applyOverrides(dtProps, props);

            SerializerProperties serializerProps = dtProps.getSerializerProperties();
            IMessageSerializer serializer = plugin.getSerializer(serializerProps);

            boolean json = SerializationType.JSON.equals(plugin.getDefaultSerializationType());
            String data = json ? serializer.toJSON(msg) : serializer.toXML(msg);
            if (data == null) {
                data = "";
            }

            // Message type/version from the serializer's own metadata, then the data type's
            // vocabulary (element descriptions) — the same text the Swing tree shows. Only the
            // XML-serialized types decorate nodes; JSON is a plain object tree.
            String[] tv = typeAndVersion(serializer, msg);
            MessageVocabulary vocab = safeVocab(plugin, tv[1], tv[0]);
            String root = buildRoot(tv[0], tv[1], vocab);
            String descriptions = (!json && vocab != null) ? buildDescriptions(data, vocab) : "{}";

            String out = "{"
                    + "\"format\":" + jsonString(json ? "json" : "xml") + ","
                    + "\"data\":" + jsonString(data) + ","
                    + "\"meta\":{\"root\":" + jsonString(root) + ",\"descriptions\":" + descriptions + "}"
                    + "}";
            return Response.ok(out).type(MediaType.APPLICATION_JSON).build();
        } catch (MirthApiException e) {
            throw e;
        } catch (Exception e) {
            throw new MirthApiException(e);
        }
    }

    // Apply newline-separated key=value overrides to the data type's SerializationProperties,
    // coercing each value to the type the property currently holds (boolean/int/String). Only keys
    // the property group already exposes are touched; unknown keys are ignored. Mirrors the sidecar.
    private static void applyOverrides(DataTypeProperties dtProps, String props) {
        if (StringUtils.isBlank(props)) {
            return;
        }
        SerializationProperties serProp = dtProps.getSerializationProperties();
        if (serProp == null) {
            return;
        }
        Map<String, Object> current = serProp.getProperties();
        if (current == null || current.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (String line : props.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1);
            if (!current.containsKey(key)) {
                continue;
            }
            Object cur = current.get(key);
            Object coerced;
            if (cur instanceof Boolean) {
                coerced = Boolean.parseBoolean(val.trim());
            } else if (cur instanceof Integer) {
                try {
                    coerced = Integer.parseInt(val.trim());
                } catch (NumberFormatException nfe) {
                    continue;
                }
            } else {
                coerced = val;
            }
            current.put(key, coerced);
            changed = true;
        }
        if (changed) {
            serProp.setProperties(current);
        }
    }

    // [type, version] from the serializer's own message metadata (mirth_type / mirth_version).
    private static String[] typeAndVersion(IMessageSerializer serializer, String message) {
        try {
            Map<String, Object> md = serializer.getMetaDataFromMessage(message);
            if (md != null) {
                Object t = md.get("mirth_type");
                Object v = md.get("mirth_version");
                return new String[] { t == null ? "" : t.toString().trim(), v == null ? "" : v.toString().trim() };
            }
        } catch (Exception e) {
            // no metadata for this type
        }
        return new String[] { "", "" };
    }

    private static MessageVocabulary safeVocab(DataTypeServerPlugin plugin, String version, String type) {
        try {
            return plugin.getVocabulary(version, type);
        } catch (Exception e) {
            return null;
        }
    }

    // "<type> (<version>)" plus the type's own description when the vocabulary has one — the
    // message-tree root label. Empty when the data type reports no type (e.g. XML/JSON).
    private static String buildRoot(String type, String version, MessageVocabulary vocab) {
        if (type.isEmpty()) {
            return "";
        }
        String root = type + " (" + (version.isEmpty() ? "Unknown version" : version) + ")";
        String desc = vocab == null ? "" : safeDesc(vocab, type.replace("-", ""));
        if (!desc.isEmpty()) {
            root += " (" + desc + ")";
        }
        return root;
    }

    private static String safeDesc(MessageVocabulary vocab, String elementId) {
        try {
            String r = vocab.getDescription(elementId);
            return r == null ? "" : r;
        } catch (Exception e) {
            return "";
        }
    }

    // Build a { "<nodeName>": "<description>" } JSON object from the serialized XML by walking each
    // distinct element and looking up its vocabulary description. Best-effort: any failure yields
    // "{}" so the client falls back to bare node names.
    private static String buildDescriptions(String xml, MessageVocabulary vocab) {
        if (StringUtils.isBlank(xml)) {
            return "{}";
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            // XXE hardening: this parses the engine serializer's own output, but the factory runs
            // on the full engine classpath behind a network endpoint — disable DOCTYPE/entities.
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setExpandEntityReferences(false);
            Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

            Map<String, String> map = new LinkedHashMap<String, String>();
            walk(doc.getDocumentElement(), vocab, map, new HashSet<String>());

            StringBuilder b = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (!first) {
                    b.append(",");
                }
                b.append(jsonString(e.getKey())).append(":").append(jsonString(e.getValue()));
                first = false;
            }
            return b.append("}").toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private static void walk(Element el, MessageVocabulary vocab, Map<String, String> map, Set<String> seen) {
        if (el == null) {
            return;
        }
        String name = el.getTagName();
        if (seen.add(name)) {
            String d = safeDesc(vocab, name);
            if (!d.isEmpty()) {
                map.put(name, d);
            }
        }
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node k = kids.item(i);
            if (k instanceof Element) {
                walk((Element) k, vocab, map, seen);
            }
        }
    }

    private static String jsonString(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.append('"').toString();
    }
}
