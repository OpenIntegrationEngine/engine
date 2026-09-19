// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.connectors.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Exercises {@link TableMetadataReader} against a real embedded Derby database rather than mocks, so
 * that the statements it builds are actually parsed and executed by a database engine. Derby is
 * already a server dependency and is used by other tests here.
 */
public class TableMetadataReaderTest {

    private static final String URL = "jdbc:derby:memory:tablemetadata";

    /** Derby folds unquoted identifiers to upper case, and the default schema is APP. */
    private static final String SCHEMA = "APP";

    /** What Derby, and the SQL standard, quote identifiers with. */
    private static final String DOUBLE_QUOTE = "\"";

    /** What MySQL quotes identifiers with unless ANSI_QUOTES is set. */
    private static final String BACKTICK = "`";

    /** No schema is selected unless it matches the user the connection was opened as. */
    private static final String NO_USER = "";

    private static Connection connection;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        connection = DriverManager.getConnection(URL + ";create=true");

        execute("CREATE TABLE PLAIN_TABLE (ID INTEGER NOT NULL, NAME VARCHAR(20), AMOUNT DECIMAL(9,2))");
        execute("CREATE TABLE PLAIN_OTHER (ID INTEGER)");
        // Needs delimiting: unquoted, the space is a syntax error and the case would be folded away.
        execute("CREATE TABLE \"My Table\" (ID INTEGER, \"Mixed Case\" VARCHAR(5))");
        // The identifier delimiter itself, which has to be doubled inside the quotes.
        execute("CREATE TABLE \"Quote\"\"Table\" (ID INTEGER)");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (connection != null) {
            connection.close();
        }
        try {
            DriverManager.getConnection(URL + ";drop=true");
        } catch (SQLException e) {
            // Derby always reports dropping an in-memory database as an exception (SQLState 08006).
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static SortedSet<Table> getTables(Connection connection, String pattern, String username) throws SQLException {
        return TableMetadataReader.getTables(connection, new HashSet<String>(Collections.singletonList(pattern)), username);
    }

    private static Table single(SortedSet<Table> tables) {
        assertEquals("expected exactly one table, got " + tables, 1, tables.size());
        return tables.first();
    }

    private static List<String> columnNames(Table table) {
        List<String> names = new ArrayList<String>();
        for (Column column : table.getColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    @Test
    public void buildsTheColumnQuery() {
        assertEquals("SELECT * FROM \"PLAIN_TABLE\" WHERE 1 = 0", TableMetadataReader.buildColumnQuery(DOUBLE_QUOTE, null, "PLAIN_TABLE"));
        assertEquals("SELECT * FROM \"APP\".\"PLAIN_TABLE\" WHERE 1 = 0", TableMetadataReader.buildColumnQuery(DOUBLE_QUOTE, SCHEMA, "PLAIN_TABLE"));
        assertEquals("SELECT * FROM \"My Table\" WHERE 1 = 0", TableMetadataReader.buildColumnQuery(DOUBLE_QUOTE, "", "My Table"));
    }

    /** MySQL quotes with a backtick; a double quoted name there is a string literal, not a table. */
    @Test
    public void quotesWithWhateverTheDriverReports() {
        assertEquals("SELECT * FROM `PLAIN_TABLE` WHERE 1 = 0", TableMetadataReader.buildColumnQuery(BACKTICK, null, "PLAIN_TABLE"));
        assertEquals("SELECT * FROM `mydb`.`PLAIN_TABLE` WHERE 1 = 0", TableMetadataReader.buildColumnQuery(BACKTICK, "mydb", "PLAIN_TABLE"));
        assertEquals("SELECT * FROM `Quote``Table` WHERE 1 = 0", TableMetadataReader.buildColumnQuery(BACKTICK, null, "Quote`Table"));
    }

    /**
     * The JDBC contract returns a single space when the database does not support quoting, and
     * there is then no safe way to name the table.
     */
    @Test
    public void buildsNoQueryWhenTheDatabaseCannotQuoteIdentifiers() {
        assertNull(TableMetadataReader.buildColumnQuery(" ", null, "PLAIN_TABLE"));
        assertNull(TableMetadataReader.buildColumnQuery("", null, "PLAIN_TABLE"));
        assertNull(TableMetadataReader.buildColumnQuery(null, null, "PLAIN_TABLE"));
    }

    @Test
    public void readsColumnNamesTypesAndPrecisions() throws Exception {
        Table table = single(getTables(connection, "PLAIN_TABLE", NO_USER));

        assertEquals("PLAIN_TABLE", table.getName());
        assertEquals(3, table.getColumns().size());

        Column id = table.getColumns().get(0);
        assertEquals("ID", id.getName());
        assertEquals("INTEGER", id.getType());
        assertEquals(10, id.getPrecision());

        Column name = table.getColumns().get(1);
        assertEquals("NAME", name.getName());
        assertEquals("VARCHAR", name.getType());
        assertEquals(20, name.getPrecision());

        Column amount = table.getColumns().get(2);
        assertEquals("AMOUNT", amount.getName());
        assertEquals("DECIMAL", amount.getType());
        assertEquals(9, amount.getPrecision());
    }

    /**
     * The generated statement has to quote the table name: unquoted, "My Table" would not parse.
     */
    @Test
    public void readsTableNamesThatRequireDelimiting() throws Exception {
        Table table = single(getTables(connection, "My Table", NO_USER));

        assertEquals("My Table", table.getName());
        assertEquals("[ID, Mixed Case]", columnNames(table).toString());
    }

    /** The schema is used when it matches the connecting user, which qualifies the statement. */
    @Test
    public void qualifiesWithTheSchemaMatchingTheUsername() throws Exception {
        Table table = single(getTables(connection, "PLAIN_TABLE", SCHEMA));

        assertEquals("PLAIN_TABLE", table.getName());
        assertEquals("[ID, NAME, AMOUNT]", columnNames(table).toString());
    }

    @Test
    public void translatesStarWildcardsToPercent() throws Exception {
        SortedSet<Table> tables = getTables(connection, "PLAIN_*", NO_USER);

        List<String> names = new ArrayList<String>();
        for (Table table : tables) {
            names.add(table.getName());
        }
        assertEquals("[PLAIN_OTHER, PLAIN_TABLE]", names.toString());
    }

    @Test
    public void emptyPatternSetRetrievesEveryTable() throws Exception {
        SortedSet<Table> tables = TableMetadataReader.getTables(connection, new HashSet<String>(), NO_USER);

        Set<String> names = new HashSet<String>();
        for (Table table : tables) {
            names.add(table.getName());
        }
        assertTrue("expected the user tables to be present, got " + names, names.containsAll(new HashSet<String>(Arrays.asList("PLAIN_TABLE", "PLAIN_OTHER", "My Table", "Quote\"Table"))));
    }

    /**
     * Whatever the statement cannot describe still has to come back, via the generic (slower)
     * DatabaseMetaData path.
     */
    @Test
    public void fallsBackToDatabaseMetaDataWhenTheStatementFails() throws Exception {
        Table table = single(getTables(connectionFailingToCreateStatement(), "PLAIN_TABLE", NO_USER));

        assertEquals("PLAIN_TABLE", table.getName());
        assertEquals("[ID, NAME, AMOUNT]", columnNames(table).toString());
    }

    /**
     * A table name containing the identifier delimiter must have it doubled rather than closing the
     * quoted identifier early, which would leave a statement that does not parse.
     */
    @Test
    public void escapesTheIdentifierQuoteCharacter() throws Exception {
        assertEquals("SELECT * FROM \"Quote\"\"Table\" WHERE 1 = 0", TableMetadataReader.buildColumnQuery(DOUBLE_QUOTE, null, "Quote\"Table"));

        Table table = single(getTables(connection, "Quote\"Table", NO_USER));

        assertEquals("Quote\"Table", table.getName());
        assertEquals("[ID]", columnNames(table).toString());
    }

    /** Wraps the live connection so that creating any statement fails. */
    private static Connection connectionFailingToCreateStatement() {
        return (Connection) Proxy.newProxyInstance(TableMetadataReaderTest.class.getClassLoader(), new Class<?>[] {
                Connection.class }, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("createStatement".equals(method.getName())) {
                            throw new SQLException("simulated driver failure");
                        }

                        assertNotNull(connection);
                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
                });
    }
}
