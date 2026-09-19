// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mirth.connect.connectors.jdbc.Column;
import com.mirth.connect.connectors.jdbc.DatabaseConnectorServletInterface;
import com.mirth.connect.connectors.jdbc.Table;

/** Exercises the Database Connector's "get tables" API */
@DisplayName("Database Connector table metadata")
class DatabaseMetadataTest {

    /** Created by every engine's schema script with the same three columns. */
    private static final String TABLE = "CONFIGURATION";

    private static final List<String> EXPECTED_COLUMNS = List.of("CATEGORY", "NAME", "VALUE");

    @BeforeAll
    static void requireDatabaseCoordinates() {
        assumeTrue(HarnessConfig.DB_URL != null && HarnessConfig.DB_DRIVER != null,
                "configuration declares no separately reachable database");
    }

    @Test
    @DisplayName("returns the columns of a known table")
    void returnsColumnsOfKnownTable() throws Exception {
        Table table = getTable(TABLE);

        assertEquals(TABLE, table.getName().toUpperCase(), "table name");
        assertEquals(EXPECTED_COLUMNS, columnNames(table), "columns of " + table.getName());
    }

    /**
     * Column metadata is only useful if it carries the type and size the connector shows in the
     * dialog, which is where the old per-driver query mattered.
     */
    @Test
    @DisplayName("reports a type and a sensible precision for each column")
    void reportsTypeAndPrecision() throws Exception {
        Table table = getTable(TABLE);

        for (Column column : table.getColumns()) {
            assertNotNull(column.getType(), "type of " + column.getName());
            assertEquals(false, column.getType().isBlank(), "type of " + column.getName());
        }

        Column category = table.getColumns().get(0);
        assertEquals("CATEGORY", category.getName().toUpperCase());
        assertEquals(255, category.getPrecision(), "CATEGORY is declared VARCHAR(255) everywhere");
    }

    /**
     * A "*" in the pattern has to be translated to the SQL wildcard "%"; untranslated it matches
     * nothing. The result is only checked for containment because the pattern also reaches whatever
     * system tables the engine exposes - SQL Server answers with sys.configurations too.
     */
    @Test
    @DisplayName("filters by wildcard table name pattern")
    void filtersByWildcardPattern() throws Exception {
        SortedSet<Table> tables = getTables(Set.of("CONFIGURATIO*", "configuratio*"));

        List<String> names = new ArrayList<String>();
        for (Table table : tables) {
            names.add(table.getName().toUpperCase());
        }
        assertTrue(names.contains(TABLE), "expected the wildcard to match " + TABLE + ", got " + names);
    }

    private static Table getTable(String name) throws Exception {
        // Engines differ on identifier folding: Postgres lower-cases unquoted names while Oracle and
        // Derby upper-case them, and the pattern is matched against whatever is stored.
        SortedSet<Table> tables = getTables(Set.of(name.toUpperCase(), name.toLowerCase()));

        assertEquals(1, tables.size(), "expected exactly one " + name + " table, got " + tables);
        return tables.first();
    }

    private static SortedSet<Table> getTables(Set<String> patterns) throws Exception {
        SortedSet<Table> tables = SharedServer.get()
                .servlet(DatabaseConnectorServletInterface.class)
                .getTables("smoketest", "smoketest", HarnessConfig.DB_DRIVER, HarnessConfig.DB_URL,
                        HarnessConfig.DB_USERNAME, HarnessConfig.DB_PASSWORD, patterns, null, Collections.emptySet());

        return tables == null ? new TreeSet<Table>() : tables;
    }

    private static List<String> columnNames(Table table) {
        List<String> names = new ArrayList<String>();
        for (Column column : table.getColumns()) {
            names.add(column.getName().toUpperCase());
        }
        return names;
    }
}
