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
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.postgresql.PGStatement;

import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.channel.Statistics;
import com.mirth.connect.donkey.server.data.StatisticsUpdater;
import com.mirth.connect.donkey.util.SerializerProvider;

public class PostgresqlJdbcDao extends JdbcDao {

    public PostgresqlJdbcDao(Donkey donkey, Connection connection, QuerySource querySource, PreparedStatementSource statementSource, SerializerProvider serializerProvider, boolean encryptMessageContent, boolean encryptAttachments, boolean encryptCustomMetaData, boolean decryptData, StatisticsUpdater statisticsUpdater, Statistics currentStats, Statistics totalStats, String statsServerId) {
        super(donkey, connection, querySource, statementSource, serializerProvider, encryptMessageContent, encryptAttachments, encryptCustomMetaData, decryptData, statisticsUpdater, currentStats, totalStats, statsServerId);
    }

    /** Excludes a statement from caching.  Required when schema changes at runtime. */
    @Override
    protected void disableServerSidePlanCache(PreparedStatement statement) throws SQLException {
        statement.unwrap(PGStatement.class).setPrepareThreshold(0);
    }

}
