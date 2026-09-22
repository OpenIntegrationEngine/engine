// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.MessageContent;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.model.message.attachment.Attachment;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

/**
 * Fixture-file assertions against a retrieved {@link Message}.
 *
 * <p>Every value compared here comes off a typed model object, so there is no XML parsing:
 * statuses are {@link Status} enums, content comes from {@link MessageContent#getContent()},
 * and metadata comes from {@link ConnectorMessage#getMetaDataMap()}. The generator supplies
 * each fixture file's name and its already-loaded text; the name selects the connector and
 * the kind of assertion exactly as ci/README.md describes.
 */
final class MessageAssertions {

    /** Fixture wildcard: matches any run of characters, for timestamps and generated ids. */
    private static final String ANY_WILDCARD = "((ANY))";

    /**
     * Fixture sentinel asserting that nothing is stored: a whole content file, or one key of a
     * metadata file.
     */
    private static final String NONE_SENTINEL = "((NONE))";

    private static final Pattern RESPONSE_ENVELOPE = Pattern.compile("^\\s*<response[\\s>].*", Pattern.DOTALL);

    /**
     * Attachment assertion files are {@code attachment<NN>} plus an optional suffix, where
     * {@code NN} is the attachment's position in the message rather than its id, which is a
     * UUID the server generates.
     */
    private static final Pattern ATTACHMENT_NAME = Pattern.compile("attachment(\\d+)(_type)?");

    /** The token an attachment handler leaves in the message where it took an attachment out. */
    private static final Pattern ATTACHMENT_TOKEN = Pattern.compile("\\$\\{ATTACH:([^}]+)\\}");

    /** Destination assertion files are {@code dest<NN>} plus an optional suffix. */
    private static final Pattern DEST_NAME = Pattern.compile(
            "dest(\\d+)(_transformed|_response|_processed_response|_processing_error|_response_error|_status|_metadata\\.yml)?");

    /** Source connector metadata id; destination N is metadata id N. */
    private static final int SOURCE_META_DATA_ID = 0;

    private MessageAssertions() {
    }

    /**
     * True for a fixture file that asserts an attachment rather than something on the message.
     * Attachments are stored in their own table, so the harness has to ask the server for them
     * separately, and only does so when a fixture names one.
     */
    static boolean isAttachmentFixture(String fileName) {
        return ATTACHMENT_NAME.matcher(fileName).matches();
    }

    /**
     * The charset a fixture file is read with. An attachment's content is arbitrary bytes - an
     * image, a DICOM object - so it is read as ISO-8859-1, which maps every byte to one char and
     * back without loss, making the comparison a byte-for-byte one. Everything else is text.
     */
    static Charset charsetFor(String fileName) {
        Matcher matcher = ATTACHMENT_NAME.matcher(fileName);
        return matcher.matches() && matcher.group(2) == null ? StandardCharsets.ISO_8859_1
                : StandardCharsets.UTF_8;
    }

    /**
     * Applies one fixture file's assertion to the message.
     *
     * @param attachments the message's attachments, empty unless a fixture asked for them
     * @param fileName    the fixture file name, e.g. {@code source_status} or {@code dest01}
     * @param content     that file's text, already loaded from the classpath
     */
    static void assertFixtureFile(Message message, List<Attachment> attachments, String fileName, String content) {
        if (isAttachmentFixture(fileName)) {
            assertAttachment(message, attachments, fileName, content);
            return;
        }
        switch (fileName) {
            case "source_status" -> assertStatus("source status", content,
                    connector(message, SOURCE_META_DATA_ID, fileName).getStatus());
            case "source_raw" -> assertContent("source raw", content,
                    content(connector(message, SOURCE_META_DATA_ID, fileName).getRaw()));
            case "source_transformed" -> assertContent("source transformed", content,
                    content(connector(message, SOURCE_META_DATA_ID, fileName).getTransformed()));
            case "source_encoded" -> assertContent("source encoded", content,
                    content(connector(message, SOURCE_META_DATA_ID, fileName).getEncoded()));
            case "source_processing_error" -> assertContent("source processing error", content,
                    connector(message, SOURCE_META_DATA_ID, fileName).getProcessingError());
            case "source_response" -> assertResponse("source response", content,
                    connector(message, SOURCE_META_DATA_ID, fileName).getResponse());
            case "source_metadata.yml" -> assertMetadata("source_metadata.yml", parseYamlMap(content),
                    connector(message, SOURCE_META_DATA_ID, fileName));
            default -> assertDestination(message, fileName, content);
        }
    }

    /**
     * Asserts one attachment's content or mime type. {@code attachment01} is the attachment whose
     * token appears first in the source raw content, not the first the server hands back: that
     * list is ordered by id, which is a generated UUID and so bears no relation to the message.
     * An attachment the message does not reference sorts after the ones it does, by id, so a
     * handler that stores attachments without leaving tokens behind still has a stable order.
     */
    private static void assertAttachment(Message message, List<Attachment> attachments, String fileName,
            String content) {
        Matcher matcher = ATTACHMENT_NAME.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalStateException("Unrecognised fixture file name: " + fileName);
        }
        int position = Integer.parseInt(matcher.group(1));
        List<Attachment> ordered = order(message, attachments);
        Attachment attachment = position >= 1 && position <= ordered.size() ? ordered.get(position - 1) : null;

        if ("_type".equals(matcher.group(2))) {
            assertMatches(fileName, content.trim(), attachment == null ? null : attachment.getType());
        } else {
            assertMatches(fileName, content, attachment == null ? null
                    : new String(attachment.getContent(), StandardCharsets.ISO_8859_1));
        }
    }

    /**
     * Puts a message's attachments into the order a fixture numbers them by: the order their
     * tokens appear in the source raw content, then whatever is left over, by id.
     */
    static List<Attachment> order(Message message, List<Attachment> attachments) {
        Map<String, Attachment> byId = new LinkedHashMap<>();
        attachments.forEach(attachment -> byId.put(attachment.getId(), attachment));

        List<Attachment> ordered = new ArrayList<>();
        Map<Integer, ConnectorMessage> connectorMessages = message.getConnectorMessages();
        ConnectorMessage source = connectorMessages == null ? null : connectorMessages.get(SOURCE_META_DATA_ID);
        String raw = source == null ? null : content(source.getRaw());
        if (raw != null) {
            Matcher tokens = ATTACHMENT_TOKEN.matcher(raw);
            while (tokens.find()) {
                Attachment referenced = byId.remove(tokens.group(1));
                if (referenced != null) {
                    ordered.add(referenced);
                }
            }
        }

        byId.values().stream().sorted(Comparator.comparing(Attachment::getId)).forEach(ordered::add);
        return ordered;
    }

    private static void assertDestination(Message message, String fileName, String content) {
        Matcher matcher = DEST_NAME.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalStateException("Unrecognised fixture file name: " + fileName);
        }

        int metaDataId = Integer.parseInt(matcher.group(1));
        String suffix = matcher.group(2) == null ? "" : matcher.group(2);
        ConnectorMessage destination = connector(message, metaDataId, fileName);

        switch (suffix) {
            case "" -> assertContent(fileName, content, content(destination.getSent()));
            case "_transformed" -> assertContent(fileName, content, content(destination.getTransformed()));
            case "_response" -> assertResponse(fileName, content, destination.getResponse());
            case "_processed_response" -> assertResponse(fileName, content, destination.getProcessedResponse());
            case "_processing_error" -> assertContent(fileName, content, destination.getProcessingError());
            case "_response_error" -> assertContent(fileName, content, destination.getResponseError());
            case "_status" -> assertStatus(fileName, content, destination.getStatus());
            case "_metadata.yml" -> assertMetadata(fileName, parseYamlMap(content), destination);
            default -> throw new IllegalStateException("Unhandled fixture suffix: " + suffix);
        }
    }

    /**
     * The source map is serialized by XStream on its way to the server, which cannot convert
     * the JDK's immutable map implementations (and would need
     * {@code --add-opens java.base/java.util} to try), so always hand back a plain mutable map.
     */
    static Map<String, Object> parseSourceMap(String content) {
        return new LinkedHashMap<>(parseYamlMap(content));
    }

    private static ConnectorMessage connector(Message message, int metaDataId, String label) {
        Map<Integer, ConnectorMessage> connectorMessages = message.getConnectorMessages();
        ConnectorMessage connectorMessage = connectorMessages == null ? null : connectorMessages.get(metaDataId);
        if (connectorMessage == null) {
            throw new AssertionError("Message " + message.getMessageId() + " has no connector with metadata id "
                    + metaDataId + " (needed by " + label + "); present ids: "
                    + (connectorMessages == null ? "none" : connectorMessages.keySet()));
        }
        return connectorMessage;
    }

    private static void assertStatus(String label, String expectedText, Status actual) {
        String expected = expectedText.trim();
        if (actual == null || !expected.equals(actual.name())) {
            throw new AssertionError("Expected " + label + " to be " + expected + ", found " + actual);
        }
    }

    private static void assertContent(String label, String expected, String actual) {
        assertMatches(label, expected, actual);
    }

    /**
     * Response content is a serialised {@link Response}, so unwrap it to the payload the
     * fixture actually describes. Line endings are normalised because HL7 acknowledgements
     * come back CR-delimited while the fixture files are LF-delimited.
     */
    private static void assertResponse(String label, String expected, MessageContent responseContent) {
        assertMatches(label, expected, responsePayload(content(responseContent)));
    }

    /** Unwraps a stored {@link Response} to the payload a fixture describes, or passes it through. */
    static String responsePayload(String stored) {
        if (stored == null || !RESPONSE_ENVELOPE.matcher(stored).matches()) {
            return stored;
        }
        Response response = ObjectXMLSerializer.getInstance().deserialize(stored.trim(), Response.class);
        String payload = response == null ? null : response.getMessage();
        return payload == null ? null : payload.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * Metadata assertions are a subset check: the fixture lists only the keys it cares about.
     * Custom metadata columns land in the connector map and message metadata map, so both are
     * consulted, with the metadata map winning on conflict.
     */
    private static void assertMetadata(String label, Map<String, Object> expected, ConnectorMessage connectorMessage) {
        Map<String, Object> actual = new LinkedHashMap<>();
        putAll(actual, connectorMessage.getConnectorMap());
        putAll(actual, connectorMessage.getMetaDataMap());
        assertSubset(label, "", expected, actual);
    }

    private static void putAll(Map<String, Object> target, Map<String, Object> source) {
        if (source != null) {
            target.putAll(source);
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertSubset(String label, String path, Map<String, Object> expected,
            Map<String, Object> actual) {
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            String keyPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            Object expectedValue = entry.getValue();
            Object actualValue = actual.get(entry.getKey());

            // A key whose value is the sentinel asserts the opposite: nothing was stored for it.
            if (NONE_SENTINEL.equals(expectedValue)) {
                if (actualValue != null) {
                    throw new AssertionError("Metadata mismatch for " + label + " at " + keyPath
                            + ": expected no value, found " + describe(actualValue));
                }
                continue;
            }

            if (!actual.containsKey(entry.getKey())) {
                throw new AssertionError("Metadata mismatch for " + label + ": missing key " + keyPath
                        + "; present keys: " + actual.keySet());
            }

            if (expectedValue instanceof Map<?, ?> expectedMap) {
                if (!(actualValue instanceof Map<?, ?> actualMap)) {
                    throw new AssertionError("Metadata mismatch for " + label + " at " + keyPath
                            + ": expected a mapping, found " + describe(actualValue));
                }
                assertSubset(label, keyPath, (Map<String, Object>) expectedMap, (Map<String, Object>) actualMap);
            } else if (!scalarsEqual(expectedValue, actualValue)) {
                throw new AssertionError("Metadata mismatch for " + label + " at " + keyPath + ": expected "
                        + describe(expectedValue) + ", found " + describe(actualValue));
            }
        }
    }

    /**
     * YAML gives Strings, Integers and Booleans while the server may return any of those, so
     * compare by string form rather than by type.
     */
    private static boolean scalarsEqual(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return Objects.equals(expected, actual);
        }
        return scalarText(expected).equals(scalarText(actual));
    }

    /**
     * Renders a scalar the way a fixture writes it. A TIMESTAMP custom metadata column comes back
     * as a {@link Calendar}, whose {@code toString} spells out every field and the JVM's time zone,
     * so it is rendered as its UTC instant instead: a fixture writes {@code 2010-01-02T13:01:02Z}.
     *
     * <p>A NUMBER column comes back as a {@link BigDecimal} whose scale is the dialect's business:
     * the column is {@code DECIMAL(31, 15)} everywhere, and derby, postgres, mysql and sqlserver
     * all return the column's scale, so {@code 1234.5678} arrives as {@code 1234.567800000000000},
     * while oracle returns the scale the value was stored with. Trailing zeros are therefore
     * dropped, which leaves a fixture free to write the value it sent.
     */
    private static String scalarText(Object value) {
        if (value instanceof Calendar calendar) {
            return Instant.ofEpochMilli(calendar.getTimeInMillis()).toString();
        }
        if (value instanceof BigDecimal number) {
            return number.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    /**
     * Compares an assertion file to actual content, honouring {@value #ANY_WILDCARD} and
     * {@value #NONE_SENTINEL}.
     */
    private static void assertMatches(String label, String expected, String actual) {
        if (NONE_SENTINEL.equals(expected.trim())) {
            if (actual != null) {
                throw new AssertionError("Expected " + label + " content to be absent, found " + describe(actual));
            }
            return;
        }
        if (actual == null) {
            throw new AssertionError("Expected " + label + " content but the server stored none");
        }
        if (!toPattern(expected).matcher(actual).matches()) {
            throw new AssertionError("Content mismatch for " + label
                    + "\n  expected: " + describe(expected)
                    + "\n    actual: " + describe(actual));
        }
    }

    /** Quotes the fixture text, leaving {@value #ANY_WILDCARD} as a lazy match-anything. */
    private static Pattern toPattern(String expected) {
        StringBuilder regex = new StringBuilder();
        int position = 0;
        while (true) {
            int wildcard = expected.indexOf(ANY_WILDCARD, position);
            if (wildcard < 0) {
                break;
            }
            if (wildcard > position) {
                regex.append(Pattern.quote(expected.substring(position, wildcard)));
            }
            regex.append(".*?");
            position = wildcard + ANY_WILDCARD.length();
        }
        if (position < expected.length()) {
            regex.append(Pattern.quote(expected.substring(position)));
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }

    private static String content(MessageContent messageContent) {
        return messageContent == null ? null : messageContent.getContent();
    }

    private static String describe(Object value) {
        return value == null ? "<none>" : "\"" + scalarText(value) + "\"";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseYamlMap(String content) {
        Object loaded = new Yaml().load(content);
        if (loaded == null) {
            return Map.of();
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Expected a YAML mapping but found: " + content);
        }
        return (Map<String, Object>) map;
    }
}
