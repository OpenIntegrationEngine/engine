// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.server.migration;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.mirth.connect.model.util.MigrationException;

public class MigratorTest {

    private static Migrator migratorFor(String databaseType) {
        Migrator migrator = new Migrator() {
            @Override
            public void migrate() throws MigrationException {}

            @Override
            public void migrateSerializedData() throws MigrationException {}
        };
        migrator.setDatabaseType(databaseType);
        return migrator;
    }

    @Test
    public void quoteColumnQuotesForMysql() {
        assertEquals("`LIBRARY`", migratorFor("mysql").quoteColumn("LIBRARY"));
    }

    @Test
    public void quoteColumnLeavesOtherDatabasesUnquoted() {
        for (String databaseType : new String[] { "derby", "postgres", "oracle", "sqlserver", null }) {
            assertEquals(databaseType, "LIBRARY", migratorFor(databaseType).quoteColumn("LIBRARY"));
        }
    }
}
