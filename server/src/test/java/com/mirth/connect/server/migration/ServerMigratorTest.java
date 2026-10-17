// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.server.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.client.core.Version;
import com.mirth.connect.model.util.MigrationException;

/**
 * Runs {@link ServerMigrator#migrate()} against an embedded Derby database that already has a
 * schema, so the version recorded in SCHEMA_INFO decides what happens.
 */
public class ServerMigratorTest {

    private static final String URL = "jdbc:derby:memory:servermigrator";

    /** Looks like a version from a newer release, which is how an unknown version shows up. */
    private static final String UNRECOGNIZED_VERSION = "99.0.0";

    private Connection connection;

    @Before
    public void setUp() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        connection = DriverManager.getConnection(URL + ";create=true");

        // An existing CONFIGURATION table means the schema is installed and must be migrated.
        execute("CREATE TABLE CONFIGURATION (CATEGORY VARCHAR(255) NOT NULL, NAME VARCHAR(255) NOT NULL, VALUE CLOB)");
        execute("CREATE TABLE SCHEMA_INFO (VERSION VARCHAR(40))");
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
        try {
            DriverManager.getConnection(URL + ";drop=true");
        } catch (SQLException e) {
            // Derby always reports dropping an in-memory database as an exception (SQLState 08006).
        }
    }

    @Test
    public void testLatestVersionIsLeftUnchanged() throws Exception {
        setSchemaVersion(Version.getLatest().getSchemaVersion());

        newMigrator().migrate();

        assertEquals(Version.getLatest().getSchemaVersion(), getOnlySchemaVersion());
    }

    @Test
    public void testUnrecognizedVersionIsRejected() throws Exception {
        assertNull("UNRECOGNIZED_VERSION is now a real version; pick another", Version.fromString(UNRECOGNIZED_VERSION));
        setSchemaVersion(UNRECOGNIZED_VERSION);

        MigrationException e = assertThrows(MigrationException.class, () -> newMigrator().migrate());

        assertTrue(e.getMessage(), e.getMessage().contains("\"" + UNRECOGNIZED_VERSION + "\""));
        assertEquals(UNRECOGNIZED_VERSION, getOnlySchemaVersion());
    }

    private ServerMigrator newMigrator() {
        ServerMigrator migrator = new ServerMigrator();
        migrator.setConnection(connection);
        migrator.setDatabaseType("derby");
        return migrator;
    }

    private void setSchemaVersion(String version) throws SQLException {
        execute("INSERT INTO SCHEMA_INFO (VERSION) VALUES ('" + version + "')");
    }

    /** Fails if SCHEMA_INFO does not hold exactly one row. */
    private String getOnlySchemaVersion() throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT VERSION FROM SCHEMA_INFO")) {
            assertTrue("SCHEMA_INFO is empty", resultSet.next());
            String version = resultSet.getString(1);
            assertFalse("SCHEMA_INFO has more than one row", resultSet.next());
            return version;
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
