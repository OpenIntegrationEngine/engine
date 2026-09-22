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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.donkey.util.SerializerProvider;

public class MysqlDaoFactory extends JdbcDaoFactory {
    private Logger logger = LogManager.getLogger(this.getClass());

    @Override
    public JdbcDao getDao(SerializerProvider serializerProvider) {
        JdbcDao dao = super.getDao(serializerProvider);
        dao.setQuoteChar('`');
        checkTransactionIsolation(dao);
        return dao;
    }

    /**
     * Warns once if the connection is using the default REPEATABLE-READ transaction isolation level.
     */
    private void checkTransactionIsolation(JdbcDao dao) {
        try {
            if (dao.getConnection().getTransactionIsolation() == Connection.TRANSACTION_REPEATABLE_READ) {
                logger.error("This MySQL connection is not using the READ-COMMITTED transaction isolation level.  The MySQL REPEATABLE-READ will cause intermittent issues.  Set the transaction isolation level in the database URL or the database server to READ-COMMITTED to avoid these problems.");
            }
        } catch (SQLException e) {
            logger.error("Failed to check the transaction isolation level for this MySQL connection.", e);
        }
    }
}
