/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.ui.browsers.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;

import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.model.filters.MessageFilter;

class MessageBrowserRecentFilterStore {
    private static final int MAX_RECENT_FILTERS = 10;
    private static final String RECENT_FILTERS_PREFERENCE_PREFIX = "messageBrowserRecentFilters.";
    // TODO: Replace with a cross-session time-limited encrypted store
    // (In-memory assuming text searches represent PHI)
    private static final Map<String, String> RECENT_FILTERS_BY_CHANNEL = new ConcurrentHashMap<>();

    private final String prefKey;

    public MessageBrowserRecentFilterStore(String channelId) {
        this.prefKey = RECENT_FILTERS_PREFERENCE_PREFIX + channelId;
    }

    public List<MessageFilter> getRecentFilters() {
        try {
            String serialized = RECENT_FILTERS_BY_CHANNEL.getOrDefault(prefKey, "");
            if (StringUtils.isBlank(serialized)) return List.of();

            var result = ObjectXMLSerializer.getInstance().deserialize(serialized, List.class);
            if (result == null) return List.of();
            
            return (List<MessageFilter>) result;
        } catch (Exception e) {
            // Fail quietly if the stored filters cannot be deserialized for any reason.
            e.printStackTrace();
            return List.of();
        }
    }

    public void addRecentFilter(MessageFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("Filter cannot be null");
        }

        var filters = new ArrayList<MessageFilter>(getRecentFilters());

        // Remove then re-add to avoid duplicates
        filters.remove(filter);
        filters.add(0, filter);

        // Trim to the maximum number of recent filters
        if (filters.size() > MAX_RECENT_FILTERS) {
            filters.subList(MAX_RECENT_FILTERS, filters.size()).clear();
        }

        try {
            RECENT_FILTERS_BY_CHANNEL.put(prefKey, ObjectXMLSerializer.getInstance().serialize(filters));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}