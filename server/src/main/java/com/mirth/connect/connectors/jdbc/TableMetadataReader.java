// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.connectors.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reads table and column metadata from an open JDBC connection, for the Database Reader/Writer
 * "select tables" dialog.
 *
 * <p>
 * Lifted out of {@link DatabaseConnectorServlet} so that it can be exercised directly against a
 * real database without a server, a connection pool or a servlet context.
 * </p>
 */
final class TableMetadataReader {

    private static final String[] TABLE_TYPES = { "TABLE", "VIEW" };
    private static final Logger logger = LogManager.getLogger(TableMetadataReader.class);

    private TableMetadataReader() {}

    /**
     * Retrieves the tables and views matching the given name patterns, along with their columns.
     *
     * @param connection
     *            an open connection to the database to inspect
     * @param tableNamePatterns
     *            patterns to filter table names by, in the syntax described by
     *            {@link #translateTableNamePatterns(Set)}; empty retrieves every table
     * @param username
     *            the user the connection was opened as, used to select a schema when the database
     *            has one named after the user (MIRTH-1045, which affects Oracle); cannot be NULL
     */
    static SortedSet<Table> getTables(Connection connection, Set<String> tableNamePatterns, String username) throws SQLException {
        DatabaseMetaData dbMetaData = connection.getMetaData();
        String schema = getSchema(dbMetaData, username);

        // the sorted set to hold the table information
        SortedSet<Table> tableInfoList = new TreeSet<Table>();

        // for each table, grab their column information
        for (String tableName : getTableNames(dbMetaData, schema, tableNamePatterns)) {
            tableInfoList.add(new Table(tableName, getColumns(connection, dbMetaData, schema, tableName)));
        }

        return tableInfoList;
    }

    /**
     * Use a schema if the user name matches one of the schemas. Fix for Oracle: MIRTH-1045
     */
    private static String getSchema(DatabaseMetaData dbMetaData, String username) throws SQLException {
        String schema = null;

        try (ResultSet schemasResult = dbMetaData.getSchemas()) {
            while (schemasResult.next()) {
                String schemaResult = schemasResult.getString(1);
                if (username.equalsIgnoreCase(schemaResult)) {
                    schema = schemaResult;
                }
            }
        }

        return schema;
    }

    /**
     * Based on the table name pattern, attempt to retrieve the table information.
     */
    private static List<String> getTableNames(DatabaseMetaData dbMetaData, String schema, Set<String> tableNamePatterns) throws SQLException {
        List<String> tableNameList = new ArrayList<String>();

        // go through each possible table name patterns and query for the tables
        for (String tableNamePattern : translateTableNamePatterns(tableNamePatterns)) {
            try (ResultSet rs = dbMetaData.getTables(null, schema, tableNamePattern, TABLE_TYPES)) {
                // based on the result set, loop through to store the table name so it can be used
                // to retrieve the table's column information
                while (rs.next()) {
                    tableNameList.add(rs.getString("TABLE_NAME"));
                }
            }
        }

        return tableNameList;
    }

    /**
     * Retrieves the column information for a single table.
     *
     * <p>
     * Apparently it's much more efficient to use ResultSetMetaData to retrieve column information,
     * so a select against the table is used to describe its columns. If that select fails we fall
     * back to the generic method of getting column information, but this could be extremely slow.
     * </p>
     */
    private static List<Column> getColumns(Connection connection, DatabaseMetaData dbMetaData, String schema, String tableName) throws SQLException {
        final String queryString = buildColumnQuery(dbMetaData.getIdentifierQuoteString(), schema, tableName);

        if (queryString != null) {
            try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(queryString)) {
                List<Column> columnList = new ArrayList<Column>();
                ResultSetMetaData rsmd = rs.getMetaData();

                // retrieve all relevant column information
                for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
                    columnList.add(new Column(rsmd.getColumnName(i), rsmd.getColumnTypeName(i), rsmd.getPrecision(i)));
                }

                return columnList;
            } catch (SQLException sqle) {
                logger.info("Failed to execute '" + queryString + "', fall back to generic approach to retrieve column information");
            }
        }

        // failed to use the select method, so we need to fall back to generic
        // if this generic approach fails, then there's nothing we can do
        logger.debug("Using fallback method for retrieving columns");
        return getGenericColumns(dbMetaData, tableName);
    }

    /**
     * Builds the statement used to describe a table's columns: the table name quoted the way the
     * connected database expects, qualified with the schema when one was selected, and a WHERE
     * clause that no row can satisfy so that only the shape of the result set comes back.
     *
     * @param identifierQuote
     *            the quote string the driver reports, from
     *            {@link DatabaseMetaData#getIdentifierQuoteString()}; not every database uses the
     *            SQL standard double quote, and MySQL uses a backtick
     * @return the statement, or NULL if the database does not support quoted identifiers, in which
     *         case there is no safe way to name the table and the caller has to fall back to the
     *         generic column metadata
     */
    static String buildColumnQuery(String identifierQuote, String schema, String tableName) {
        // The JDBC contract returns a single space when the database does not support quoting.
        final String quote = StringUtils.trimToNull(identifierQuote);
        if (quote == null) {
            return null;
        }

        final String quotedTableName = quote(quote, tableName);
        final String schemaTableName = StringUtils.isNotEmpty(schema) ? quote(quote, schema) + "." + quotedTableName : quotedTableName;
        return "SELECT * FROM " + schemaTableName + " WHERE 1 = 0";
    }

    /**
     * Quotes an identifier, doubling any embedded quote character so that it is taken literally
     * rather than closing the quoted identifier early.
     */
    private static String quote(String quote, String identifier) {
        return quote + StringUtils.replace(identifier, quote, quote + quote) + quote;
    }

    private static List<Column> getGenericColumns(DatabaseMetaData dbMetaData, String tableName) throws SQLException {
        List<Column> columnList = new ArrayList<Column>();

        try (ResultSet rs = dbMetaData.getColumns(null, null, tableName.replace("/", "//"), null)) {
            // retrieve all relevant column information
            while (rs.next()) {
                columnList.add(new Column(rs.getString("COLUMN_NAME"), rs.getString("TYPE_NAME"), rs.getInt("COLUMN_SIZE")));
            }
        }

        return columnList;
    }

    /**
     * Translate the given pattern expression so that it can be used properly for searching tables
     * in the database. Multiple table name patterns are delimited by comma (,)
     * <p>
     * This interpret and translate to the following:
     * <p>
     * <ul>
     * <li>"*" = wild card for more than one character, will be converted to be used as '%'</li>
     * <li>"_" = one character wild card</li>
     * <li>"" = empty string will retrieve all tables
     * </ul>
     * <p>
     * <i>Eg. rad*,table*test =&gt; Find all tables starts with 'rad' AND tables prefix with 'table'
     * and postfix with 'test'</i>
     *
     * @param tableNamePatterns
     *            pattern expressions to translate, cannot be NULL.
     * @return If table name pattern is an empty string, it'll never return NULL.
     */
    private static Set<String> translateTableNamePatterns(Set<String> tableNamePatterns) {
        if (tableNamePatterns == null) {
            throw new IllegalArgumentException("Parameter 'tableNamePatterns' cannot be NULL'");
        }

        Set<String> patterns = new HashSet<String>();
        if (tableNamePatterns.isEmpty()) {
            patterns.add("%");
        } else {
            for (String pattern : tableNamePatterns) {
                patterns.add(pattern.trim().replaceAll("\\*", "%"));
            }
        }
        return patterns;
    }
}
