// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Mitch Gaffigan

package com.mirth.connect.client.ui.browsers.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.model.filters.MessageFilter;

class MessageBrowserRecentFilterStore {
    private static final int MAX_RECENT_FILTERS = 10;
    private static final String RECENT_FILTERS_PREFERENCE_PREFIX = "messageBrowserRecentFilters.";
    // TODO: Replace with a cross-session time-limited encrypted store
    // (In-memory assuming text searches represent PHI)
    private static final Map<String, String> RECENT_FILTERS_BY_CHANNEL = new ConcurrentHashMap<>();
    private Logger logger = LogManager.getLogger(this.getClass());

    private final String prefKey;

    public MessageBrowserRecentFilterStore(String channelId) {
        this.prefKey = RECENT_FILTERS_PREFERENCE_PREFIX + channelId;
    }

    public List<MessageFilter> getRecentFilters() {
        try {
            String serialized = RECENT_FILTERS_BY_CHANNEL.getOrDefault(prefKey, "");
            if (serialized.isBlank()) return List.of();

            var result = ObjectXMLSerializer.getInstance().deserializeList(serialized, MessageFilter.class);
            if (result == null) return List.of();
            
            return result;
        } catch (Exception e) {
            // Fail quietly if the stored filters cannot be deserialized for any reason.
            logger.warn("Failed to deserialize recent message filters for key {}.", prefKey, e);
            return List.of();
        }
    }

    public void addRecentFilter(MessageFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("Filter cannot be null");
        }

        var filters = new ArrayList<>(getRecentFilters());

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
            logger.warn("Failed to serialize recent message filters for key {}.", prefKey, e);
        }
    }
}