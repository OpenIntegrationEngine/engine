/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.ReloadingFileBasedConfigurationBuilder;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.configuration2.reloading.PeriodicReloadingTrigger;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

public class PropertiesConfigurationUtilTest {

    /*
     * The production reload path is deliberately slow: a one second trigger period on top of the
     * five second refresh delay that FileHandlerReloadingDetector defaults to. Driving both down to
     * milliseconds keeps the reload behavior under test while removing seven seconds of sleeping.
     */
    private static final long RELOAD_REFRESH_DELAY_MILLIS = 10;
    private static final long RELOAD_TRIGGER_PERIOD_MILLIS = 10;
    private static final long RELOAD_TIMEOUT_MILLIS = 10000;
    private static final long QUIET_PERIOD_MILLIS = 200;

    @Test
    public void testCreateBuilder1() throws Exception {
        FileBasedConfigurationBuilder<PropertiesConfiguration> builder = PropertiesConfigurationUtil.createBuilder();
        PropertiesConfiguration config = builder.getConfiguration();
        assertTrue(config.getListDelimiterHandler() == DisabledListDelimiterHandler.INSTANCE);
    }

    @Test
    public void testCreateBuilder2() throws Exception {
        File file = new File(UUID.randomUUID().toString());
        assertFalse(file.exists());

        FileBasedConfigurationBuilder<PropertiesConfiguration> builder = PropertiesConfigurationUtil.createBuilder(file);
        assertTrue(file.exists());

        PropertiesConfiguration config = builder.getConfiguration();
        assertTrue(config.getListDelimiterHandler() == DisabledListDelimiterHandler.INSTANCE);

        file.delete();
    }

    @Test
    public void testCreateBuilder3() throws Exception {
        File file = new File(UUID.randomUUID().toString());
        FileUtils.writeStringToFile(file, getTestFile(), "UTF-8");
        assertTrue(file.exists());

        FileBasedConfigurationBuilder<PropertiesConfiguration> builder = PropertiesConfigurationUtil.createBuilder(file);

        PropertiesConfiguration config = builder.getConfiguration();
        assertTrue(config.getListDelimiterHandler() == DisabledListDelimiterHandler.INSTANCE);
        verifyTestProperties(config);

        file.delete();
    }

    @Test
    public void testCreate1() throws Exception {
        PropertiesConfiguration config = PropertiesConfigurationUtil.create();
        assertTrue(config.getListDelimiterHandler() == DisabledListDelimiterHandler.INSTANCE);
    }

    @Test
    public void testCreate2() throws Exception {
        File file = new File(UUID.randomUUID().toString());
        assertFalse(file.exists());

        PropertiesConfiguration config = PropertiesConfigurationUtil.create(file);
        assertTrue(file.exists());
        assertTrue(config.getListDelimiterHandler() == DisabledListDelimiterHandler.INSTANCE);

        file.delete();
    }

    @Test
    public void testCreate3() throws Exception {
        File file = new File(UUID.randomUUID().toString());
        FileUtils.writeStringToFile(file, getTestFile(), "UTF-8");
        assertTrue(file.exists());

        PropertiesConfiguration config = PropertiesConfigurationUtil.create(file);
        assertTrue(config.getListDelimiterHandler() == DisabledListDelimiterHandler.INSTANCE);
        verifyTestProperties(config);

        file.delete();
    }

    @Test
    public void testCreate4() throws Exception {
        InputStream is = new ByteArrayInputStream(getTestFile().getBytes("UTF-8"));

        PropertiesConfiguration config = PropertiesConfigurationUtil.create(is);
        assertTrue(config.getListDelimiterHandler() == DisabledListDelimiterHandler.INSTANCE);
        verifyTestProperties(config);
    }

    @Test
    public void testCreateReloadingBuilder1() throws Exception {
        File file = new File(UUID.randomUUID().toString());
        file.createNewFile();

        ReloadingFileBasedConfigurationBuilder<PropertiesConfiguration> builder = PropertiesConfigurationUtil.createReloadingBuilder(file, false, RELOAD_REFRESH_DELAY_MILLIS);

        PropertiesConfiguration config = builder.getConfiguration();
        assertTrue(config.getListDelimiterHandler() == DisabledListDelimiterHandler.INSTANCE);
        assertFalse(config.getKeys().hasNext());

        PeriodicReloadingTrigger trigger = PropertiesConfigurationUtil.createReloadTrigger(builder, RELOAD_TRIGGER_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        trigger.start();

        try {
            // The file is still empty, so many trigger cycles must not invent any content.
            Thread.sleep(QUIET_PERIOD_MILLIS);
            assertFalse(builder.getConfiguration().getKeys().hasNext());

            FileUtils.writeStringToFile(file, getTestFile(), "UTF-8");
            PropertiesConfiguration config2 = PropertiesConfigurationUtil.create(file);
            verifyTestProperties(config2);

            verifyTestProperties(awaitReloadedConfiguration(builder));
        } finally {
            trigger.shutdown();
            file.delete();
        }
    }

    /**
     * Polls the builder until the trigger has picked up the changed file. The reload normally lands
     * within a few trigger periods; the timeout only exists so a genuine failure reports as a failed
     * assertion instead of hanging.
     */
    private static PropertiesConfiguration awaitReloadedConfiguration(ReloadingFileBasedConfigurationBuilder<PropertiesConfiguration> builder) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RELOAD_TIMEOUT_MILLIS);
        PropertiesConfiguration config = builder.getConfiguration();

        while (!config.getKeys().hasNext() && System.nanoTime() < deadline) {
            Thread.sleep(RELOAD_TRIGGER_PERIOD_MILLIS);
            config = builder.getConfiguration();
        }

        assertTrue("Configuration was not reloaded after the file changed", config.getKeys().hasNext());
        return config;
    }

    @Test
    public void testCreateReloadingBuilderCommaDelimited() throws Exception {
        File file = new File(UUID.randomUUID().toString());
        file.createNewFile();
        FileUtils.writeStringToFile(file, getTestFile(), "UTF-8");
        
        ReloadingFileBasedConfigurationBuilder<PropertiesConfiguration> builder = PropertiesConfigurationUtil.createReloadingBuilder(file, true);

        PropertiesConfiguration config = builder.getConfiguration();
        assertTrue(config.getListDelimiterHandler() != DisabledListDelimiterHandler.INSTANCE);
        verifyCommaDelimitedTestProperties(config);

        file.delete();
    }

    private static void verifyTestProperties(PropertiesConfiguration config) {
        Iterator<String> keys = config.getKeys();

        assertEquals(TEST_KEY1, keys.next());
        assertEquals(TEST_VAL1, config.getString(TEST_KEY1));
        assertEquals(TEST_KEY2, keys.next());
        assertEquals(TEST_VAL2, config.getString(TEST_KEY2));
        assertEquals(TEST_KEY3_COMMENT, config.getLayout().getComment(TEST_KEY3));
        assertEquals(TEST_KEY3, keys.next());
        assertEquals(TEST_VAL3, config.getString(TEST_KEY3));
        assertEquals(TEST_KEY4, keys.next());
        assertEquals(TEST_VAL4, config.getString(TEST_KEY4));
        assertEquals(TEST_VAL4, config.getStringArray(TEST_KEY4)[0]);
        assertEquals(TEST_KEY5, keys.next());
        assertEquals(3, config.getStringArray(TEST_KEY5).length);
        assertEquals(TEST_VAL5_1, config.getStringArray(TEST_KEY5)[0]);
        assertEquals(TEST_VAL5_2, config.getStringArray(TEST_KEY5)[1]);
        assertEquals(TEST_VAL5_3, config.getStringArray(TEST_KEY5)[2]);
        assertFalse(keys.hasNext());
    }
    
    private static void verifyCommaDelimitedTestProperties(PropertiesConfiguration config) {
        Iterator<String> keys = config.getKeys();

        assertEquals(TEST_KEY1, keys.next());
        assertEquals(TEST_VAL1, config.getString(TEST_KEY1));
        assertEquals(TEST_KEY2, keys.next());
        assertEquals(TEST_VAL2, config.getString(TEST_KEY2));
        assertEquals(TEST_KEY3_COMMENT, config.getLayout().getComment(TEST_KEY3));
        assertEquals(TEST_KEY3, keys.next());
        assertEquals(TEST_VAL3, config.getString(TEST_KEY3));
        assertEquals(TEST_KEY4, keys.next());
        String[] testVal4Array = config.getStringArray(TEST_KEY4);
        assertEquals(3, testVal4Array.length);
        assertEquals(TEST_VAL4_1, testVal4Array[0]);
        assertEquals(TEST_VAL4_2, testVal4Array[1]);
        assertEquals(TEST_VAL4_3, testVal4Array[2]);
        assertEquals(TEST_KEY5, keys.next());
        assertEquals(3, config.getStringArray(TEST_KEY5).length);
        assertEquals(TEST_VAL5_1, config.getStringArray(TEST_KEY5)[0]);
        assertEquals(TEST_VAL5_2, config.getStringArray(TEST_KEY5)[1]);
        assertEquals(TEST_VAL5_3, config.getStringArray(TEST_KEY5)[2]);
        assertFalse(keys.hasNext());
    }

    private static String getTestFile() {
        // @formatter:off
        return TEST_KEY1 + " = " + TEST_VAL1 + "\n" +
               TEST_KEY2 + " = " + TEST_VAL2 + "\n" +
               "\n" +
               TEST_KEY3_COMMENT + "\n" +
               TEST_KEY3 + " = " + TEST_VAL3 + "\n" +
               "\n" +
               TEST_KEY4 + " = " + TEST_VAL4_1 + "," + TEST_VAL4_2 + "," + TEST_VAL4_3 + "\n" +
               "\n" +
               TEST_KEY5 + " = " + TEST_VAL5_1 + "\n" +
               TEST_KEY5 + " = " + TEST_VAL5_2 + "\n" +
               TEST_KEY5 + " = " + TEST_VAL5_3 + "\n";
        // @formatter:on               
    }

    private static final String TEST_KEY1 = "key1";
    private static final String TEST_VAL1 = "value1";
    private static final String TEST_KEY2 = "key2";
    private static final String TEST_VAL2 = "value2";
    private static final String TEST_KEY3_COMMENT = "# test comment";
    private static final String TEST_KEY3 = "key3";
    private static final String TEST_VAL3 = "value3";
    private static final String TEST_KEY4 = "key4";
    private static final String TEST_VAL4 = "a,b,c";
    private static final String TEST_VAL4_1 = "a";
    private static final String TEST_VAL4_2 = "b";
    private static final String TEST_VAL4_3 = "c";
    private static final String TEST_KEY5 = "key5";
    private static final String TEST_VAL5_1 = "d";
    private static final String TEST_VAL5_2 = "e";
    private static final String TEST_VAL5_3 = "f";
}
