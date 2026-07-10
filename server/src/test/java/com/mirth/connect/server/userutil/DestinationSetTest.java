// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Richard Ogin
// SPDX-FileCopyrightText: 2025 Tony Germano <tony@germano.name>

package com.mirth.connect.server.userutil;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.server.Constants;
import com.mirth.connect.userutil.ImmutableConnectorMessage;

public class DestinationSetTest {
    private Set<Integer> createMetadataIds() {
        Set<Integer> metaDataIds = new HashSet<>();
        metaDataIds.add(1);
        metaDataIds.add(3);
        metaDataIds.add(5);
        metaDataIds.add(7);
        metaDataIds.add(9);

        return metaDataIds;
    }

    private Map<String, Integer> createDestinationIdMap() {
        Map<String, Integer> destinationIdMap = new HashMap<>();
        destinationIdMap.put("One", 1);
        destinationIdMap.put("Three", 3);
        destinationIdMap.put("Five", 5);
        destinationIdMap.put("Seven", 7);
        destinationIdMap.put("Nine", 9);

        return destinationIdMap;
    }

    private ImmutableConnectorMessage createMessage(Map<String, Integer> destinationIdMap, Set<Integer> metadataIds) {
        ConnectorMessage cm = new ConnectorMessage();

        if (metadataIds != null) {
            cm.getSourceMap().put(Constants.DESTINATION_SET_KEY, metadataIds);
        }

        if (destinationIdMap != null) {
            return new ImmutableConnectorMessage(cm, true, destinationIdMap);
        } else {
            return new ImmutableConnectorMessage(cm, true);
        }
    }

    // --- Tests for remove(Object) ---
    @Test
    public void test_removeObject_withSourceMap_removeForMatchingMetadataId() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.remove(3));
        assertFalse(metaDataIds.contains(3));
        assertTrue(metaDataIds.size() == 4);
    }

    @Test
    public void test_removeObject_withSourceMap_removeForMatchingConnectorName() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.remove("Five"));
        assertFalse(metaDataIds.contains(5));
        assertTrue(metaDataIds.size() == 4);
    }

    @Test
    public void test_removeObject_withSourceMap_noRemovalForNonExistentName() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertFalse(destinationSet.remove("I_DONT_EXIST"));
        assertTrue(metaDataIds.size() == 5);
    }

    @Test
    public void test_removeObject_noSourceMap_noRemoval() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));
        assertFalse(destinationSet.remove(3));
    }

    // --- Tests for remove(Collection) ---
    @Test
    public void test_removeCollection_withSourceMap_removeForMatchingItems() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        Collection<Object> toRemove = Arrays.asList(1, "Three", 99); // "99" does not exist
        assertTrue(destinationSet.remove(toRemove));
        assertFalse(metaDataIds.contains(1));
        assertFalse(metaDataIds.contains(3));
        assertTrue(metaDataIds.size() == 3);
    }
    
    @Test
    public void test_removeCollection_withSourceMap_noRemovalForNonExistentItems() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        Collection<Object> toRemove = Arrays.asList(100, "OneHundred");
        assertFalse(destinationSet.remove(toRemove));
        assertTrue(metaDataIds.size() == 5);
    }

    @Test
    public void test_removeCollection_noSourceMap_noRemoval() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));
        assertFalse(destinationSet.remove(Arrays.asList(1, "Three")));
    }

    // --- Tests for removeAll() ---
    @Test
    public void test_removeAll_withSourceMap_removesAll() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAll());
        assertTrue(metaDataIds.isEmpty());
    }
    
    @Test
    public void test_removeAll_withSourceMap_returnsFalseWhenAlreadyEmpty() throws Exception {
        Set<Integer> metaDataIds = new HashSet<>();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertFalse(destinationSet.removeAll());
    }

    @Test
    public void test_removeAll_noSourceMap_noRemoval() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));
        assertFalse(destinationSet.removeAll());
    }

    // --- Tests for removeAllExcept(Collection) ---
    @Test
    public void test_removeAllExceptCollection_withSourceMap_retainsMatchingItems() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        Collection<Object> toRetain = Arrays.asList(1, "Five", 99); // "99" does not exist
        assertTrue(destinationSet.removeAllExcept(toRetain));
        assertTrue(metaDataIds.size() == 2);
        assertTrue(metaDataIds.contains(1));
        assertTrue(metaDataIds.contains(5));
    }

    @Test
    public void test_removeAllExceptCollection_withSourceMap_removesAllForNonExistentItems() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        Collection<Object> toRetain = Arrays.asList(100, "OneHundred");
        assertTrue(destinationSet.removeAllExcept(toRetain));
        assertTrue(metaDataIds.isEmpty());
    }

    @Test
    public void test_removeAllExceptCollection_noSourceMap_noRemoval() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));
        assertFalse(destinationSet.removeAllExcept(Arrays.asList(1, "Five")));
    }
    
    // --- Tests for removeAllExcept(Object) ---
    @Test
    public void test_removeAllExceptObject_withSourceMap_removeAllForMetadataIdWhichDoesNotExist() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAllExcept("I_DONT_EXIST"));
        assertTrue(metaDataIds.isEmpty());
    }

    @Test
    public void test_removeAllExceptObject_withSourceMap_removeForMatchingMetadataId() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAllExcept(3));
        assertTrue(metaDataIds.size() == 1);
    }

    @Test
    public void test_removeAllExceptObject_withSourceMap_removeForMatchingConnectorName() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAllExcept("Seven"));
        assertTrue(metaDataIds.size() == 1);
    }

    @Test
    public void test_removeAllExceptObject_noSourceMap_noRemovalForMetadataIdWhichDoesNotExist() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));

        assertFalse(destinationSet.removeAllExcept("I_DONT_EXIST"));
    }

    @Test
    public void test_removeAllExceptObject_noSourceMap_noRemovalForMatchingMetadataId() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));

        assertFalse(destinationSet.removeAllExcept(3));
    }

    @Test
    public void test_removeAllExceptObject_noSourceMap_noRemovalForMatchingConnectorName() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));

        assertFalse(destinationSet.removeAllExcept("Seven"));
    }
}
