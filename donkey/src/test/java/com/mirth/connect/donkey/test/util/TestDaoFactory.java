/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.test.util;

import java.sql.Connection;
import java.sql.SQLException;

import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.controllers.ChannelController;
import com.mirth.connect.donkey.server.data.DonkeyDaoException;
import com.mirth.connect.donkey.server.data.jdbc.JdbcDao;
import com.mirth.connect.donkey.server.data.jdbc.JdbcDaoFactory;
import com.mirth.connect.donkey.util.Serializer;
import com.mirth.connect.donkey.util.SerializerProvider;
import com.mirth.connect.donkey.util.xstream.XStreamSerializer;

public class TestDaoFactory extends JdbcDaoFactory {
    private Donkey donkey;
    private ChannelController channelController;
    private SerializerProvider serializerProvider;
    private int errorPct = 0;
    private int hangPct = 0;
    private int hangMillis = 0;

    public TestDaoFactory() {
        donkey = Donkey.getInstance();
        channelController = ChannelController.getInstance();
        serializerProvider = new SerializerProvider() {
            @Override
            public Serializer getSerializer(Integer metaDataId) {
                return new XStreamSerializer();
            }
        };
    }

    public int getErrorPct() {
        return errorPct;
    }

    public void setErrorPct(int errorPct) {
        this.errorPct = errorPct;
    }

    public int getHangPct() {
        return hangPct;
    }

    public void setHangPct(int hangPct) {
        this.hangPct = hangPct;
    }

    public int getHangMillis() {
        return hangMillis;
    }

    public void setHangMillis(int hangMillis) {
        this.hangMillis = hangMillis;
    }

    @Override
    public JdbcDao getDao() {
        Connection connection;

        try {
            connection = getConnectionPool().getConnection();
        } catch (SQLException e) {
            throw new DonkeyDaoException(e);
        }

        return new TestDao(donkey, connection, getQuerySource(), serializerProvider, channelController.getStatistics(), channelController.getTotalStatistics(), getStatsServerId(), errorPct, hangPct, hangMillis);
    }
}
