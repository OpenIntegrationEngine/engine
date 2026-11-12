// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine Contributors

package com.mirth.connect.server.util;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import org.junit.Test;

public class DatabaseUtilTest {

    private static Connection connectionFor(String productName, DatabaseMetaData metaData, boolean tableFound) throws Exception {
        Connection connection = mock(Connection.class);
        ResultSet tables = mock(ResultSet.class);
        ResultSet indexes = mock(ResultSet.class);

        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("oie_b");
        when(metaData.getDatabaseProductName()).thenReturn(productName);
        when(metaData.getTables(any(), any(), any(), any())).thenReturn(tables);
        when(tables.next()).thenReturn(tableFound);
        when(metaData.getIndexInfo(any(), any(), any(), anyBoolean(), anyBoolean())).thenReturn(indexes);
        when(indexes.next()).thenReturn(false);
        return connection;
    }

    /**
     * On MySQL, tableExists must scope its metadata lookup to the connection's own catalog. A null
     * catalog lets Connector/J match tables in any database the login can see, so a second OIE
     * database on a shared login is never migrated. See OpenIntegrationEngine/engine#489.
     */
    @Test
    public void tableExists_onMySql_scopesLookupToConnectionCatalog() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Connection connection = connectionFor("MySQL", metaData, false);

        assertFalse(DatabaseUtil.tableExists(connection, "CONFIGURATION"));

        verify(metaData).getTables(eq("oie_b"), isNull(), eq("CONFIGURATION"), any());
        verify(metaData).getTables(eq("oie_b"), isNull(), eq("configuration"), any());
    }

    /** Other databases keep the existing unscoped (null catalog) lookup. */
    @Test
    public void tableExists_onOtherDatabases_keepsNullCatalog() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Connection connection = connectionFor("PostgreSQL", metaData, false);

        assertFalse(DatabaseUtil.tableExists(connection, "CONFIGURATION"));

        verify(metaData).getTables(isNull(), isNull(), eq("CONFIGURATION"), any());
        verify(metaData).getTables(isNull(), isNull(), eq("configuration"), any());
        verify(connection, never()).getCatalog();
    }

    /**
     * On MySQL, indexExists must scope its index lookup the same way. Otherwise an engine that has
     * lost one of its own indexes finds another database's index of the same name and is never
     * offered the task that recreates it.
     */
    @Test
    public void indexExists_onMySql_scopesLookupToConnectionCatalog() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Connection connection = connectionFor("MySQL", metaData, true);

        assertFalse(DatabaseUtil.indexExists(connection, "D_MM1", "D_MM1_INDEX3"));

        verify(metaData).getIndexInfo(eq("oie_b"), isNull(), eq("D_MM1"), eq(false), eq(false));
        verify(metaData).getIndexInfo(eq("oie_b"), isNull(), eq("d_mm1"), eq(false), eq(false));
    }

    /** Other databases keep the existing unscoped (null catalog) index lookup. */
    @Test
    public void indexExists_onOtherDatabases_keepsNullCatalog() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Connection connection = connectionFor("PostgreSQL", metaData, true);

        assertFalse(DatabaseUtil.indexExists(connection, "D_MM1", "D_MM1_INDEX3"));

        verify(metaData).getIndexInfo(isNull(), isNull(), eq("D_MM1"), eq(false), eq(false));
        verify(metaData).getIndexInfo(isNull(), isNull(), eq("d_mm1"), eq(false), eq(false));
        verify(connection, never()).getCatalog();
    }
}
