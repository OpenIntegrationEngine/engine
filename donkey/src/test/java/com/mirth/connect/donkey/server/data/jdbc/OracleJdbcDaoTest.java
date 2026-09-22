/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.data.jdbc;

import static org.mockito.ArgumentMatchers.eq;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.mirth.connect.donkey.model.message.ContentType;
import com.mirth.connect.donkey.model.message.MessageContent;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.channel.Statistics;
import com.mirth.connect.donkey.server.data.DonkeyDaoException;
import com.mirth.connect.donkey.server.data.StatisticsUpdater;
import com.mirth.connect.donkey.util.SerializerProvider;

/**
 * Oracle is the only DAO that actually closes statements, so it is the only one where closing
 * the wrong statement at the wrong time is observable.
 */
public class OracleJdbcDaoTest {

    private static final String CHANNEL_ID = "abc";

    private OracleJdbcDao dao;
    private PreparedStatement statement;

    @Before
    public void before() throws SQLException {
        Donkey donkey = mock(Donkey.class);
        Connection connection = mock(Connection.class);
        QuerySource querySource = mock(QuerySource.class);
        PreparedStatementSource statementSource = mock(PreparedStatementSource.class);
        SerializerProvider serializerProvider = mock(SerializerProvider.class);
        StatisticsUpdater statisticsUpdater = mock(StatisticsUpdater.class);
        Statistics currentStats = mock(Statistics.class);
        Statistics totalStats = mock(Statistics.class);

        dao = spy(new OracleJdbcDao(donkey, connection, querySource, statementSource, serializerProvider, false, false, false, false, statisticsUpdater, currentStats, totalStats, ""));

        statement = mock(PreparedStatement.class);
        doReturn(statement).when(dao).prepareStatement(eq("batchInsertMessageContent"), eq(CHANNEL_ID));
    }

    /**
     * The batch lives on the cached statement, so the statement has to survive every
     * batchInsertMessageContent() call and only be closed once the batch has been executed.
     * Closing it earlier silently discarded the source content on Oracle.
     */
    @Test
    public void testBatchInsertMessageContentKeepsStatementOpenUntilExecuted() throws SQLException {
        dao.batchInsertMessageContent(content(ContentType.PROCESSED_RAW, "processed raw"));
        dao.batchInsertMessageContent(content(ContentType.TRANSFORMED, "transformed"));
        dao.batchInsertMessageContent(content(ContentType.ENCODED, "encoded"));

        verify(statement, times(3)).addBatch();
        verify(statement, never()).close();

        dao.executeBatchInsertMessageContent(CHANNEL_ID);

        InOrder inOrder = inOrder(statement);
        inOrder.verify(statement, times(3)).addBatch();
        inOrder.verify(statement).executeBatch();
        inOrder.verify(statement).clearBatch();
        inOrder.verify(statement).close();
    }

    /** A failed batch must not be left behind for the next message to execute. */
    @Test
    public void testFailedBatchInsertClearsTheBatch() throws SQLException {
        doThrow(new SQLException("no")).when(statement).addBatch();

        try {
            dao.batchInsertMessageContent(content(ContentType.ENCODED, "encoded"));
            fail("Expected a DonkeyDaoException");
        } catch (DonkeyDaoException e) {
            // expected
        }

        verify(statement).clearBatch();
    }

    private static MessageContent content(ContentType contentType, String content) {
        return new MessageContent(CHANNEL_ID, 1L, 0, contentType, content, "RAW", false);
    }
}
