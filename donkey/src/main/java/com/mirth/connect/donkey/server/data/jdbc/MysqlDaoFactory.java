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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.donkey.server.data.DonkeyDaoException;
import com.mirth.connect.donkey.util.SerializerProvider;

public class MysqlDaoFactory extends JdbcDaoFactory {
    private static final Logger logger = LogManager.getLogger(MysqlDaoFactory.class);
    private final AtomicBoolean hasCheckedTransactionIsolation = new AtomicBoolean(false);

    @Override
    public JdbcDao getDao(SerializerProvider serializerProvider) {
        JdbcDao dao = super.getDao(serializerProvider);
        dao.setQuoteChar('`');
        checkTransactionIsolation(dao);
        return dao;
    }

    @Override
    public void checkNewDatabase(Connection connection) {
        try {
            if (connection.getTransactionIsolation() == Connection.TRANSACTION_REPEATABLE_READ) {
                throw new DonkeyDaoException("Creating a new database with REPEATABLE-READ transaction " 
                    + "isolation is not allowed.  Set the transaction isolation level in the database "
                    + "URL or the database server to READ-COMMITTED.");
            }
        } catch (SQLException e) {
            throw new DonkeyDaoException("Failed to check the transaction isolation level for this MySQL connection.", e);
        }
    }

    /**
     * Warns once if the connection is using the default REPEATABLE-READ transaction isolation level.
     */
    private void checkTransactionIsolation(JdbcDao dao) {
        if (!hasCheckedTransactionIsolation.compareAndSet(false, true)) {
            return;
        }
        
        try {
            if (dao.getConnection().getTransactionIsolation() == Connection.TRANSACTION_REPEATABLE_READ) {
                logger.error("This MySQL connection is using the REPEATABLE-READ transaction isolation level.  The MySQL REPEATABLE-READ will cause intermittent issues.  Set the transaction isolation level in the database URL or the database server to READ-COMMITTED to avoid these problems.");
            }
        } catch (SQLException e) {
            logger.error("Failed to check the transaction isolation level for this MySQL connection.", e);
        }
    }
}
