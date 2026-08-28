// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.util.LinkedHashMap;
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

    private static final Pattern RESPONSE_ENVELOPE = Pattern.compile("^\\s*<response[\\s>].*", Pattern.DOTALL);

    /** Destination assertion files are {@code dest<NN>} plus an optional suffix. */
    private static final Pattern DEST_NAME = Pattern.compile("dest(\\d+)(_transformed|_response|_status|_metadata\\.yml)?");

    /** Source connector metadata id; destination N is metadata id N. */
    private static final int SOURCE_META_DATA_ID = 0;

    private MessageAssertions() {
    }

    /**
     * Applies one fixture file's assertion to the message.
     *
     * @param fileName the fixture file name, e.g. {@code source_status} or {@code dest01}
     * @param content  that file's text, already loaded from the classpath
     */
    static void assertFixtureFile(Message message, String fileName, String content) {
        switch (fileName) {
            case "source_status" -> assertStatus("source status", content,
                    connector(message, SOURCE_META_DATA_ID, fileName).getStatus());
            case "source_transformed" -> assertContent("source transformed", content,
                    content(connector(message, SOURCE_META_DATA_ID, fileName).getTransformed()));
            case "source_response" -> assertResponse("source response", content,
                    connector(message, SOURCE_META_DATA_ID, fileName));
            case "source_metadata.yml" -> assertMetadata("source_metadata.yml", parseYamlMap(content),
                    connector(message, SOURCE_META_DATA_ID, fileName));
            default -> assertDestination(message, fileName, content);
        }
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
            case "_response" -> assertResponse(fileName, content, destination);
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
    private static void assertResponse(String label, String expected, ConnectorMessage connectorMessage) {
        String stored = content(connectorMessage.getResponse());
        String actual = stored;
        if (stored != null && RESPONSE_ENVELOPE.matcher(stored).matches()) {
            Response response = ObjectXMLSerializer.getInstance().deserialize(stored.trim(), Response.class);
            String payload = response == null ? null : response.getMessage();
            actual = payload == null ? null : payload.replace("\r\n", "\n").replace('\r', '\n');
        }
        assertMatches(label, expected, actual);
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
            if (!actual.containsKey(entry.getKey())) {
                throw new AssertionError("Metadata mismatch for " + label + ": missing key " + keyPath
                        + "; present keys: " + actual.keySet());
            }

            Object expectedValue = entry.getValue();
            Object actualValue = actual.get(entry.getKey());
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
        return String.valueOf(expected).equals(String.valueOf(actual));
    }

    /** Compares an assertion file to actual content, honouring {@value #ANY_WILDCARD}. */
    private static void assertMatches(String label, String expected, String actual) {
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
        return value == null ? "<none>" : "\"" + value + "\"";
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
