/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.test;

import static org.junit.Assert.assertEquals;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.StartException;
import com.mirth.connect.donkey.server.controllers.ChannelController;
import com.mirth.connect.donkey.server.data.timed.TimedDaoFactory;
import com.mirth.connect.donkey.test.util.TestUtils;
import com.mirth.connect.donkey.util.ActionTimer;

/**
 * What is left of these tests after the statistics ones moved to the CI harness: the statistics
 * cases live in {@code smoketest/.../*StatisticsTest.java} (250-statistics), which reads the same
 * counters over the client API against every dialect instead of against the database directly.
 */
public class ChannelControllerTests {
    private static int TEST_SIZE = 10;
    private static String channelId = TestUtils.DEFAULT_CHANNEL_ID;
    private static ActionTimer daoTimer = new ActionTimer();
    private Logger logger = LogManager.getLogger(this.getClass());

    @BeforeClass
    final public static void beforeClass() throws StartException {
        Donkey donkey = Donkey.getInstance();
        donkey.startEngine(TestUtils.getDonkeyTestConfiguration());
        donkey.setDaoFactory(new TimedDaoFactory(donkey.getDaoFactory(), daoTimer));
    }

    @AfterClass
    final public static void afterClass() throws StartException {
        Donkey.getInstance().stopEngine();
    }

    @Before
    final public void before() {
        daoTimer.reset();
    }

    /*
     * Remove the channel corresponding to channelId, if applicable Call getLocalChannelId, assert
     * that: - The channel row was inserted - All channel message tables were created
     * 
     * Call getLocalChannelId again, assert that: - The ID returned is the same one returned
     * previously
     */
    @Test
    final public void testGetLocalChannelId() throws Exception {
        try {
            logger.info("Testing ChannelController.getLocalChannelId...");

            for (int i = 1; i <= TEST_SIZE; i++) {
                ChannelController.getInstance().removeChannel(channelId);
                long localChannelId = ChannelController.getInstance().getLocalChannelId(channelId);

                // Assert that the channel was created
                TestUtils.assertChannelExists(channelId, true);

                // Assert that subsequent calls return the same local channel ID
                assertEquals(localChannelId, (long) ChannelController.getInstance().getLocalChannelId(channelId));
            }

            System.out.println(daoTimer.getLog());
        } finally {
            ChannelController.getInstance().removeChannel(channelId);
        }
    }
}
