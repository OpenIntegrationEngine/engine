// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine Contributors

package com.mirth.connect.server.util;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
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

    private static Connection connectionFor(String productName, DatabaseMetaData metaData) throws Exception {
        Connection connection = mock(Connection.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("oie_b");
        when(metaData.getDatabaseProductName()).thenReturn(productName);
        when(metaData.getTables(any(), any(), any(), any())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
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
        Connection connection = connectionFor("MySQL", metaData);

        assertFalse(DatabaseUtil.tableExists(connection, "CONFIGURATION"));

        verify(metaData).getTables(eq("oie_b"), isNull(), eq("CONFIGURATION"), any());
        verify(metaData).getTables(eq("oie_b"), isNull(), eq("configuration"), any());
    }

    /** Other databases keep the existing unscoped (null catalog) lookup. */
    @Test
    public void tableExists_onOtherDatabases_keepsNullCatalog() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Connection connection = connectionFor("PostgreSQL", metaData);

        assertFalse(DatabaseUtil.tableExists(connection, "CONFIGURATION"));

        verify(metaData).getTables(isNull(), isNull(), eq("CONFIGURATION"), any());
        verify(metaData).getTables(isNull(), isNull(), eq("configuration"), any());
        verify(connection, never()).getCatalog();
    }
}
