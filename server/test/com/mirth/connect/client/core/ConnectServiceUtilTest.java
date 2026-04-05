package com.mirth.connect.client.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.http.HttpEntity;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHeader;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zafarkhaja.semver.Version;
import com.mirth.connect.model.notification.Notification;

public class ConnectServiceUtilTest {
    private static final String FILE_POPULATED = "notifications-populated.json";
    private static final String FILE_EMPTY = "notifications-empty.json";

    /**
     * Creates a streamable entity from the given file name in test resources.
     *
     * @param filename The name of the file in the test resources.
     * @return An {@link InputStreamEntity} created from the file.
     * @throws Exception if the file (resource) was not found.
     */
    private InputStreamEntity createEntityFromFile(String filename) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(filename);

        if (is == null) {
            throw new Exception("Failed to find resource: " + filename);
        }

        return new InputStreamEntity(is);
    }

    /**
     * Helper method to create a simple JsonNode with a "tag_name" property.
     * @param tagName The value for the "tag_name" property.
     * @return A {@link JsonNode} object.
     */
    private JsonNode createJsonNodeWithTag(String tagName) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.createObjectNode().put("tag_name", tagName);
    }

    @Test
    public void test_jsonStreamPopulated() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Stream<JsonNode> stream = ConnectServiceUtil.toJsonStream(entity);
        assertEquals("Expected all 25 elements to be present", 25L, stream.count());
    }

    @Test
    public void test_jsonStreamEmpty() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_EMPTY);
        Stream<JsonNode> stream = ConnectServiceUtil.toJsonStream(entity);
        assertEquals("Expected no elements in stream", 0L, stream.count());
    }

    @Test
    public void test_getCharsetWhenMissing() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Charset charset = ConnectServiceUtil.getCharset(entity);
        assertEquals(StandardCharsets.UTF_8.name(), charset.name());
    }

    @Test
    public void test_getCharsetWhenSet() throws Exception {
        //inspired by https://github.com/apache/httpcomponents-core/blob/4.4.x/httpcore/src/test/java/org/apache/http/entity/TestContentType.java#L173
        BasicHttpEntity entity = new BasicHttpEntity();
        String expectedCharset = "UTF-16";
        entity.setContentType(new BasicHeader("Content-Type", "application/json; charset=" + expectedCharset));
        // Set some dummy content for the entity to be valid, though getCharset only looks at headers
        entity.setContent(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        Charset charset = ConnectServiceUtil.getCharset(entity);
        assertEquals(expectedCharset, charset.name());
    }

    @Test
    public void test_getCharsetWhenSetButNoCharsetParam() throws Exception {
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentType(new BasicHeader("Content-Type", "application/json"));
        entity.setContent(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        Charset charset = ConnectServiceUtil.getCharset(entity);
        assertEquals(StandardCharsets.UTF_8.name(), charset.name());
    }

    @Test
    public void test_dropOlderThan_currentIsOlderMajorVersion() throws Exception {
        Version current = Version.parse("3.12.0");
        Predicate<JsonNode> predicate = ConnectServiceUtil.dropOlderThan(current);
        assertTrue("Predicate should be true if current version is older (major)",
                predicate.test(createJsonNodeWithTag("4.0.0")));
    }

    @Test
    public void test_dropOlderThan_currentIsNewerMajorVersion() throws Exception {
        Version current = Version.parse("4.0.0");
        Predicate<JsonNode> predicate = ConnectServiceUtil.dropOlderThan(current);
        assertFalse("Predicate should be false if current version is newer (major)",
                predicate.test(createJsonNodeWithTag("3.12.0")));
    }

    @Test
    public void test_dropOlderThan_currentIsSameVersion() throws Exception {
        Version current = Version.parse("4.0.0");
        Predicate<JsonNode> predicate = ConnectServiceUtil.dropOlderThan(current);
        assertFalse("Predicate should be false if current version is the same",
                predicate.test(createJsonNodeWithTag("4.0.0")));
    }

    @Test
    public void test_dropOlderThan_currentIsOlderMinor() throws Exception {
        Version current = Version.parse("4.0.1");
        Predicate<JsonNode> predicate = ConnectServiceUtil.dropOlderThan(current);
        assertTrue("Predicate should be true if current version is older (minor)",
                predicate.test(createJsonNodeWithTag("4.1.1")));
    }

    @Test
    public void test_dropOlderThan_currentIsNewerMinor() throws Exception {
        Version current = Version.parse("4.1.1");
        Predicate<JsonNode> predicate = ConnectServiceUtil.dropOlderThan(current);
        assertFalse("Predicate should be false if current version is newer (minor)",
                predicate.test(createJsonNodeWithTag("4.0.1")));
    }

    @Test
    public void test_dropOlderThan_currentIsOlderRevision() throws Exception {
        Version current = Version.parse("4.0.0");
        Predicate<JsonNode> predicate = ConnectServiceUtil.dropOlderThan(current);
        assertTrue("Predicate should be true if current version is older (revision)",
                predicate.test(createJsonNodeWithTag("4.0.1")));
    }

    @Test
    public void test_dropOlderThan_currentIsNewerRevision() throws Exception {
        Version current = Version.parse("4.0.1");
        Predicate<JsonNode> predicate = ConnectServiceUtil.dropOlderThan(current);
        assertFalse("Predicate should be false if current version is newer (revision)",
                predicate.test(createJsonNodeWithTag("4.0.0")));
    }

    @Test
    public void test_dropOlderThan_predicateIsFalseForInvalidOtherVersion() throws Exception {
        Version current = Version.parse("4.0.1");
        Predicate<JsonNode> predicate = ConnectServiceUtil.dropOlderThan(current);
        assertFalse("Predicate should be false if the other version string is invalid",
                predicate.test(createJsonNodeWithTag("INVALID_VERSION_STRING")));
    }

    // Tests for notification filtering using dropOlderThan
    @Test
    public void test_filterNotifications_currentVersionNewerThanAllOther() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Version current = Version.parse("100.0.0");
        Stream<JsonNode> notifications = ConnectServiceUtil.toJsonStream(entity)
                .filter(ConnectServiceUtil.dropOlderThan(current));
        assertEquals("Expected no notifications given a current version newer than all others", 0L, notifications.count());
    }

    @Test
    public void test_filterNotifications_emptyComparisonList() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_EMPTY);
        Version current = Version.parse("1.0.0");
        Stream<JsonNode> notifications = ConnectServiceUtil.toJsonStream(entity)
                .filter(ConnectServiceUtil.dropOlderThan(current));
        assertEquals("Expected no notifications given an empty comparison list", 0L, notifications.count());
    }

    @Test
    public void test_filterNotifications_matchingLatestVersion() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Version current = Version.parse("99.99.99");
        Stream<JsonNode> notifications = ConnectServiceUtil.toJsonStream(entity)
                .filter(ConnectServiceUtil.dropOlderThan(current));
        assertEquals("Expected no notifications given a current version matching the latest in other list", 0L, notifications.count());
    }

    @Test
    public void test_filterNotifications_someVersionsNewer() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Version current = Version.parse("4.2.0");
        Stream<JsonNode> notifications = ConnectServiceUtil.toJsonStream(entity)
                .filter(ConnectServiceUtil.dropOlderThan(current));
        assertEquals("Expected newer versions to generate notifications", 7L, notifications.count());
    }

    @Test
    public void test_filterNotifications_allVersionsNewer() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Version current = Version.parse("1.0.0");
        Stream<JsonNode> notifications = ConnectServiceUtil.toJsonStream(entity)
                .filter(ConnectServiceUtil.dropOlderThan(current));
        assertEquals("Expected all versions in populated list to be newer", 25L, notifications.count());
    }

    @Test
    public void test_notificationInfo() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Optional<JsonNode> firstNode = ConnectServiceUtil.toJsonStream(entity).findFirst();

        assertTrue("A node was expected to act upon", firstNode.isPresent());

        JsonNode orig = firstNode.get();

        Notification created = ConnectServiceUtil.toNotification(orig);
        assertEquals(orig.get("id").asInt(), (int) created.getId());
        assertEquals(orig.get("name").asText(), created.getName());
        assertEquals(orig.get("published_at").asText(), created.getDate());
        assertEquals(orig.get("body_html").asText(), created.getContent());
    }
}
