/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.data.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

public class HikariConnectionPool implements ConnectionPool {
    private HikariDataSource dataSource;

    public HikariConnectionPool(String driver, String url, String username, String password, int maxConnections, boolean jdbc4, String testQuery, boolean readOnly) {
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driver);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setConnectionTimeout(0);
        dataSource.setAutoCommit(false);
        dataSource.setMaximumPoolSize(maxConnections);
        dataSource.setMinimumIdle(0);
        dataSource.setReadOnly(readOnly);

        if (!jdbc4) {
            dataSource.setConnectionTestQuery(testQuery);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public DataSource getDataSource() {
        return dataSource;
    }
}
