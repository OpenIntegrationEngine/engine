/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import static org.junit.Assert.assertArrayEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.util.MessageMaps;

public class HttpUtilTest {

    @Test
    public void testMapOrderPreserved() throws Exception {
        final String MAP_KEY = "MyMap";

        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("First", 1);
        initial.put("Second", 2);
        initial.put("Third", 3);

        ConnectorMessage cm = new ConnectorMessage();
        cm.getSourceMap().put(MAP_KEY, initial);

        Map<String, ?> copied = HttpUtil.getTableMap(MAP_KEY, new MessageMaps(), cm);
        assertArrayEquals("Failed to preserve key order", initial.keySet().toArray(), copied.keySet().toArray());
    }
}
