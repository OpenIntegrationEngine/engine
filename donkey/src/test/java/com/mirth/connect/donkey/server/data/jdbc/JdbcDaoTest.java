/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.data.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.mirth.connect.donkey.model.channel.MetaDataColumn;
import com.mirth.connect.donkey.model.message.ContentType;
import com.mirth.connect.donkey.model.message.MessageContent;
import com.mirth.connect.donkey.model.message.attachment.Attachment;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.channel.Statistics;
import com.mirth.connect.donkey.server.data.DonkeyDaoException;
import com.mirth.connect.donkey.server.data.StatisticsUpdater;
import com.mirth.connect.donkey.util.SerializerProvider;

public class JdbcDaoTest {
    private static final String BATCH_CHANNEL_ID = "batchChannel";

    private JdbcDao dao;
    private Donkey donkey;
    private Connection connection;

    @Before
    public void before() {
        donkey = mock(Donkey.class);
        connection = mock(Connection.class);
        QuerySource querySource = mock(QuerySource.class);
        SerializerProvider serializerProvider = mock(SerializerProvider.class);
        boolean encryptMessageContent = false;
        boolean encryptAttachments = false;
        boolean encryptCustomMetaData = false;
        boolean decryptData = false;
        StatisticsUpdater statisticsUpdater = mock(StatisticsUpdater.class);
        Statistics currentStats = mock(Statistics.class);
        Statistics totalStats = mock(Statistics.class);
        String statsServerId = "";
        dao = new JdbcDao(donkey, connection, querySource, serializerProvider, encryptMessageContent, encryptAttachments, encryptCustomMetaData, decryptData, statisticsUpdater, currentStats, totalStats, statsServerId);
    }

    @Test
    public void testGetNoMetaDataColumns() throws SQLException  {
        String channelId = "abc";

        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet metaDataResultSet = mock(ResultSet.class);
        when(metaData.getColumns(any(), any(), any(), any())).thenReturn(metaDataResultSet);
        when(metaDataResultSet.next()).thenReturn(true, false);
        when(metaDataResultSet.getInt(any())).thenReturn(Types.INTEGER);
        when(connection.getMetaData()).thenReturn(metaData);
        setLocalChannelId(channelId, 1L);

        when(metaDataResultSet.getString("COLUMN_NAME")).thenReturn("metadata_id");
        assertEquals(0, dao.getMetaDataColumns(channelId).size());
    }

    @Test
    public void testGetTwoMetaDataColumns() throws SQLException  {
        String channelId = "abc";

        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet metaDataResultSet = mock(ResultSet.class);
        when(metaData.getColumns(any(), any(), any(), any())).thenReturn(metaDataResultSet);
        when(metaDataResultSet.next()).thenReturn(true, true, true, false);
        when(metaDataResultSet.getInt(any())).thenReturn(Types.INTEGER);
        when(connection.getMetaData()).thenReturn(metaData);
        setLocalChannelId(channelId, 1L);

        when(metaDataResultSet.getString("COLUMN_NAME")).thenReturn("myColumn", "metadata_id", "another_Column", "message_id");
        List<MetaDataColumn> columns =  dao.getMetaDataColumns(channelId);
        assertEquals(2, columns.size());
        assertEquals("MYCOLUMN", columns.get(0).getName());
        assertEquals("ANOTHER_COLUMN", columns.get(1).getName());
    }

    // Test with no current attachment data in database
    @Test
    public void testUpdateMessageAttachment1() throws Exception {
        String channelId = "testchannel";
        long messageId = 1;
        Attachment attachment = new Attachment("testattachment", "testing".getBytes(), "text/plain");

        ResultSet segmentCountResult = mock(ResultSet.class);
        PreparedStatement segmentCountStatement = mock(PreparedStatement.class);
        PreparedStatement updateStatement = mock(PreparedStatement.class);
        PreparedStatement insertStatement = mock(PreparedStatement.class);
        PreparedStatement deleteStatement = mock(PreparedStatement.class);
        JdbcDao dao = getDao(channelId, segmentCountResult, segmentCountStatement, updateStatement, insertStatement, deleteStatement);

        when(segmentCountResult.getInt(1)).thenReturn(0);

        dao.updateMessageAttachment(channelId, messageId, attachment);

        verify(segmentCountStatement, times(1)).setString(1, attachment.getId());
        verify(segmentCountStatement, times(1)).setLong(2, messageId);
        verify(segmentCountStatement, times(1)).executeQuery();

        verify(updateStatement, times(1)).setString(1, attachment.getType());
        verify(updateStatement, times(1)).setString(4, null);
        verify(updateStatement, times(1)).setString(5, attachment.getId());
        verify(updateStatement, times(1)).setLong(6, messageId);

        verify(insertStatement, times(1)).setString(1, attachment.getId());
        verify(insertStatement, times(1)).setLong(2, messageId);
        verify(insertStatement, times(1)).setString(3, attachment.getType());

        verify(insertStatement, times(1)).setInt(4, 1);
        verify(insertStatement, times(1)).setInt(5, attachment.getContent().length);
        verify(insertStatement, times(1)).setBytes(6, attachment.getContent());
        verify(insertStatement, times(1)).setString(7, null);
        verify(insertStatement, times(1)).executeUpdate();

        verify(updateStatement, times(1)).clearParameters();
        verify(insertStatement, times(1)).clearParameters();

        verify(deleteStatement, times(0)).setString(1, attachment.getId());
        verify(deleteStatement, times(0)).setLong(2, messageId);
        verify(deleteStatement, times(0)).setInt(eq(3), anyInt());
        verify(deleteStatement, times(0)).executeUpdate();
    }

    // Test with attachment data in database
    @Test
    public void testUpdateMessageAttachment2() throws Exception {
        String channelId = "testchannel";
        long messageId = 1;
        Attachment attachment = new Attachment("testattachment", "testing".getBytes(), "text/plain");

        ResultSet segmentCountResult = mock(ResultSet.class);
        PreparedStatement segmentCountStatement = mock(PreparedStatement.class);
        PreparedStatement updateStatement = mock(PreparedStatement.class);
        PreparedStatement insertStatement = mock(PreparedStatement.class);
        PreparedStatement deleteStatement = mock(PreparedStatement.class);
        JdbcDao dao = getDao(channelId, segmentCountResult, segmentCountStatement, updateStatement, insertStatement, deleteStatement);

        when(segmentCountResult.getInt(1)).thenReturn(2);

        dao.updateMessageAttachment(channelId, messageId, attachment);

        verify(segmentCountStatement, times(1)).setString(1, attachment.getId());
        verify(segmentCountStatement, times(1)).setLong(2, messageId);
        verify(segmentCountStatement, times(1)).executeQuery();

        verify(updateStatement, times(1)).setString(1, attachment.getType());
        verify(updateStatement, times(1)).setString(4, null);
        verify(updateStatement, times(1)).setString(5, attachment.getId());
        verify(updateStatement, times(1)).setLong(6, messageId);

        verify(insertStatement, times(1)).setString(1, attachment.getId());
        verify(insertStatement, times(1)).setLong(2, messageId);
        verify(insertStatement, times(1)).setString(3, attachment.getType());
        verify(insertStatement, times(1)).setString(7, null);

        verify(updateStatement, times(1)).setInt(7, 1);
        verify(updateStatement, times(1)).setInt(2, attachment.getContent().length);
        verify(updateStatement, times(1)).setBytes(3, attachment.getContent());
        verify(updateStatement, times(1)).executeUpdate();

        verify(updateStatement, times(1)).clearParameters();
        verify(insertStatement, times(1)).clearParameters();

        verify(deleteStatement, times(1)).setString(1, attachment.getId());
        verify(deleteStatement, times(1)).setLong(2, messageId);
        verify(deleteStatement, times(1)).setInt(eq(3), eq(2));
        verify(deleteStatement, times(1)).executeUpdate();
    }

    // Test with large attachment data
    @Test
    public void testUpdateMessageAttachment3() throws Exception {
        String channelId = "testchannel";
        long messageId = 1;
        Attachment attachment = new Attachment("testattachment", StringUtils.repeat("testtest", "", 10 * 1024 * 1024 / 8).getBytes(), "text/plain");

        ResultSet segmentCountResult = mock(ResultSet.class);
        PreparedStatement segmentCountStatement = mock(PreparedStatement.class);
        PreparedStatement updateStatement = mock(PreparedStatement.class);
        PreparedStatement insertStatement = mock(PreparedStatement.class);
        PreparedStatement deleteStatement = mock(PreparedStatement.class);
        JdbcDao dao = getDao(channelId, segmentCountResult, segmentCountStatement, updateStatement, insertStatement, deleteStatement);

        when(segmentCountResult.getInt(1)).thenReturn(1);

        dao.updateMessageAttachment(channelId, messageId, attachment);

        verify(segmentCountStatement, times(1)).setString(1, attachment.getId());
        verify(segmentCountStatement, times(1)).setLong(2, messageId);
        verify(segmentCountStatement, times(1)).executeQuery();

        verify(updateStatement, times(1)).setString(1, attachment.getType());
        verify(updateStatement, times(1)).setString(4, null);
        verify(updateStatement, times(1)).setString(5, attachment.getId());
        verify(updateStatement, times(1)).setLong(6, messageId);

        verify(insertStatement, times(1)).setString(1, attachment.getId());
        verify(insertStatement, times(1)).setLong(2, messageId);
        verify(insertStatement, times(1)).setString(3, attachment.getType());

        verify(updateStatement, times(1)).setInt(7, 1);
        verify(updateStatement, times(1)).setInt(2, 10000000);
        verify(updateStatement, times(1)).setBytes(3, Arrays.copyOfRange(attachment.getContent(), 0, 10000000));
        verify(updateStatement, times(1)).executeUpdate();

        verify(insertStatement, times(1)).setInt(4, 2);
        verify(insertStatement, times(1)).setInt(5, attachment.getContent().length - 10000000);
        verify(insertStatement, times(1)).setBytes(6, Arrays.copyOfRange(attachment.getContent(), 10000000, attachment.getContent().length));
        verify(insertStatement, times(1)).setString(7, null);
        verify(insertStatement, times(1)).executeUpdate();

        verify(updateStatement, times(1)).clearParameters();
        verify(insertStatement, times(1)).clearParameters();

        verify(deleteStatement, times(0)).setString(1, attachment.getId());
        verify(deleteStatement, times(0)).setLong(2, messageId);
        verify(deleteStatement, times(0)).setInt(eq(3), anyInt());
        verify(deleteStatement, times(0)).executeUpdate();
    }

    // Test with no current attachment data in database and encryption
    @Test
    public void testUpdateMessageAttachment4() throws Exception {
        String channelId = "testchannel";
        long messageId = 1;
        Attachment attachment = new Attachment("testattachment", "testing".getBytes(), "text/plain", "test");

        ResultSet segmentCountResult = mock(ResultSet.class);
        PreparedStatement segmentCountStatement = mock(PreparedStatement.class);
        PreparedStatement updateStatement = mock(PreparedStatement.class);
        PreparedStatement insertStatement = mock(PreparedStatement.class);
        PreparedStatement deleteStatement = mock(PreparedStatement.class);
        JdbcDao dao = getDao(channelId, segmentCountResult, segmentCountStatement, updateStatement, insertStatement, deleteStatement);

        when(segmentCountResult.getInt(1)).thenReturn(0);

        dao.updateMessageAttachment(channelId, messageId, attachment);

        verify(segmentCountStatement, times(1)).setString(1, attachment.getId());
        verify(segmentCountStatement, times(1)).setLong(2, messageId);
        verify(segmentCountStatement, times(1)).executeQuery();

        verify(updateStatement, times(1)).setString(1, attachment.getType());
        verify(updateStatement, times(1)).setString(4, attachment.getEncryptionHeader());
        verify(updateStatement, times(1)).setString(5, attachment.getId());
        verify(updateStatement, times(1)).setLong(6, messageId);

        verify(insertStatement, times(1)).setString(1, attachment.getId());
        verify(insertStatement, times(1)).setLong(2, messageId);
        verify(insertStatement, times(1)).setString(3, attachment.getType());

        verify(insertStatement, times(1)).setInt(4, 1);
        verify(insertStatement, times(1)).setInt(5, attachment.getContent().length);
        verify(insertStatement, times(1)).setBytes(6, attachment.getContent());
        verify(insertStatement, times(1)).setString(7, attachment.getEncryptionHeader());
        verify(insertStatement, times(1)).executeUpdate();

        verify(updateStatement, times(1)).clearParameters();
        verify(insertStatement, times(1)).clearParameters();

        verify(deleteStatement, times(0)).setString(1, attachment.getId());
        verify(deleteStatement, times(0)).setLong(2, messageId);
        verify(deleteStatement, times(0)).setInt(eq(3), anyInt());
        verify(deleteStatement, times(0)).executeUpdate();
    }

    // Test with attachment data in database and encryption
    @Test
    public void testUpdateMessageAttachment5() throws Exception {
        String channelId = "testchannel";
        long messageId = 1;
        Attachment attachment = new Attachment("testattachment", "testing".getBytes(), "text/plain", "test");

        ResultSet segmentCountResult = mock(ResultSet.class);
        PreparedStatement segmentCountStatement = mock(PreparedStatement.class);
        PreparedStatement updateStatement = mock(PreparedStatement.class);
        PreparedStatement insertStatement = mock(PreparedStatement.class);
        PreparedStatement deleteStatement = mock(PreparedStatement.class);
        JdbcDao dao = getDao(channelId, segmentCountResult, segmentCountStatement, updateStatement, insertStatement, deleteStatement);

        when(segmentCountResult.getInt(1)).thenReturn(2);

        dao.updateMessageAttachment(channelId, messageId, attachment);

        verify(segmentCountStatement, times(1)).setString(1, attachment.getId());
        verify(segmentCountStatement, times(1)).setLong(2, messageId);
        verify(segmentCountStatement, times(1)).executeQuery();

        verify(updateStatement, times(1)).setString(1, attachment.getType());
        verify(updateStatement, times(1)).setString(4, attachment.getEncryptionHeader());
        verify(updateStatement, times(1)).setString(5, attachment.getId());
        verify(updateStatement, times(1)).setLong(6, messageId);

        verify(insertStatement, times(1)).setString(1, attachment.getId());
        verify(insertStatement, times(1)).setLong(2, messageId);
        verify(insertStatement, times(1)).setString(3, attachment.getType());
        verify(insertStatement, times(1)).setString(7, attachment.getEncryptionHeader());

        verify(updateStatement, times(1)).setInt(7, 1);
        verify(updateStatement, times(1)).setInt(2, attachment.getContent().length);
        verify(updateStatement, times(1)).setBytes(3, attachment.getContent());
        verify(updateStatement, times(1)).executeUpdate();

        verify(updateStatement, times(1)).clearParameters();
        verify(insertStatement, times(1)).clearParameters();

        verify(deleteStatement, times(1)).setString(1, attachment.getId());
        verify(deleteStatement, times(1)).setLong(2, messageId);
        verify(deleteStatement, times(1)).setInt(eq(3), eq(2));
        verify(deleteStatement, times(1)).executeUpdate();
    }

    // Test with large attachment data and encryption
    @Test
    public void testUpdateMessageAttachment6() throws Exception {
        String channelId = "testchannel";
        long messageId = 1;
        Attachment attachment = new Attachment("testattachment", StringUtils.repeat("testtest", "", 10 * 1024 * 1024 / 8).getBytes(), "text/plain", "test");

        ResultSet segmentCountResult = mock(ResultSet.class);
        PreparedStatement segmentCountStatement = mock(PreparedStatement.class);
        PreparedStatement updateStatement = mock(PreparedStatement.class);
        PreparedStatement insertStatement = mock(PreparedStatement.class);
        PreparedStatement deleteStatement = mock(PreparedStatement.class);
        JdbcDao dao = getDao(channelId, segmentCountResult, segmentCountStatement, updateStatement, insertStatement, deleteStatement);

        when(segmentCountResult.getInt(1)).thenReturn(1);

        dao.updateMessageAttachment(channelId, messageId, attachment);

        verify(segmentCountStatement, times(1)).setString(1, attachment.getId());
        verify(segmentCountStatement, times(1)).setLong(2, messageId);
        verify(segmentCountStatement, times(1)).executeQuery();

        verify(updateStatement, times(1)).setString(1, attachment.getType());
        verify(updateStatement, times(1)).setString(4, attachment.getEncryptionHeader());
        verify(updateStatement, times(1)).setString(5, attachment.getId());
        verify(updateStatement, times(1)).setLong(6, messageId);

        verify(insertStatement, times(1)).setString(1, attachment.getId());
        verify(insertStatement, times(1)).setLong(2, messageId);
        verify(insertStatement, times(1)).setString(3, attachment.getType());

        verify(updateStatement, times(1)).setInt(7, 1);
        verify(updateStatement, times(1)).setInt(2, 10000000);
        verify(updateStatement, times(1)).setBytes(3, Arrays.copyOfRange(attachment.getContent(), 0, 10000000));
        verify(updateStatement, times(1)).executeUpdate();

        verify(insertStatement, times(1)).setInt(4, 2);
        verify(insertStatement, times(1)).setInt(5, attachment.getContent().length - 10000000);
        verify(insertStatement, times(1)).setBytes(6, Arrays.copyOfRange(attachment.getContent(), 10000000, attachment.getContent().length));
        verify(insertStatement, times(1)).setString(7, attachment.getEncryptionHeader());
        verify(insertStatement, times(1)).executeUpdate();

        verify(updateStatement, times(1)).clearParameters();
        verify(insertStatement, times(1)).clearParameters();

        verify(deleteStatement, times(0)).setString(1, attachment.getId());
        verify(deleteStatement, times(0)).setLong(2, messageId);
        verify(deleteStatement, times(0)).setInt(eq(3), anyInt());
        verify(deleteStatement, times(0)).executeUpdate();
    }

    /**
     * Every row has to make it onto the batch, and the batch has to run before the statement is
     * closed -- closing it early silently discarded the source content.
     */
    @Test
    public void insertMessageContentBatchBatchesEveryRowThenExecutesOnce() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        JdbcDao batchDao = batchDao(statement);

        batchDao.insertMessageContentBatch(Arrays.asList(content(ContentType.PROCESSED_RAW, "processed raw"), content(ContentType.TRANSFORMED, "transformed"), content(ContentType.ENCODED, "encoded")));

        InOrder inOrder = inOrder(statement);
        inOrder.verify(statement, times(3)).addBatch();
        inOrder.verify(statement).executeBatch();
        inOrder.verify(statement).close();
    }

    /** A batch that fails half way through must execute nothing and still release the statement. */
    @Test
    public void insertMessageContentBatchClosesTheStatementWhenBindingFails() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        doThrow(new SQLException("no")).when(statement).addBatch();
        JdbcDao batchDao = batchDao(statement);

        try {
            batchDao.insertMessageContentBatch(Arrays.asList(content(ContentType.ENCODED, "encoded")));
            fail("Expected a DonkeyDaoException");
        } catch (DonkeyDaoException e) {
            // expected
        }

        verify(statement, never()).executeBatch();
        verify(statement).close();
    }

    private JdbcDao batchDao(PreparedStatement statement) throws SQLException {
        JdbcDao batchDao = spy(dao);
        doReturn(statement).when(batchDao).prepareStatement(eq("insertMessageContent"), eq(BATCH_CHANNEL_ID));
        return batchDao;
    }

    private static MessageContent content(ContentType contentType, String content) {
        return new MessageContent(BATCH_CHANNEL_ID, 1L, 0, contentType, content, "RAW", false);
    }

    private JdbcDao getDao(String channelId, ResultSet segmentCountResult, PreparedStatement segmentCountStatement, PreparedStatement updateStatement, PreparedStatement insertStatement, PreparedStatement deleteStatement) throws Exception {
        Donkey donkey = mock(Donkey.class);
        Connection connection = mock(Connection.class);
        QuerySource querySource = mock(QuerySource.class);
        SerializerProvider serializerProvider = mock(SerializerProvider.class);
        boolean encryptMessageContent = false;
        boolean encryptAttachments = false;
        boolean encryptCustomMetaData = false;
        boolean decryptData = false;
        StatisticsUpdater statisticsUpdater = mock(StatisticsUpdater.class);
        Statistics currentStats = mock(Statistics.class);
        Statistics totalStats = mock(Statistics.class);
        String statsServerId = "";

        JdbcDao dao = spy(new JdbcDao(donkey, connection, querySource, serializerProvider, encryptMessageContent, encryptAttachments, encryptCustomMetaData, decryptData, statisticsUpdater, currentStats, totalStats, statsServerId));

        when(segmentCountStatement.executeQuery()).thenReturn(segmentCountResult);
        doReturn(segmentCountStatement).when(dao).prepareStatement(eq("selectMessageAttachmentSegmentCount"), eq(channelId));

        doReturn(updateStatement).when(dao).prepareStatement(eq("updateMessageAttachment"), eq(channelId));

        doReturn(insertStatement).when(dao).prepareStatement(eq("insertMessageAttachment"), eq(channelId));

        doReturn(deleteStatement).when(dao).prepareStatement(eq("deleteMessageAttachmentLingeringSegments"), eq(channelId));

        return dao;
    }

    private void setLocalChannelId(String channelId, long localChannelId) {
        Channel channel = mock(Channel.class);
        when(channel.getLocalChannelId()).thenReturn(localChannelId);
        Map<String, Channel> channels = new HashMap<>();
        channels.put(channelId, channel);
        when(donkey.getDeployedChannels()).thenReturn(channels);
    }
}
