// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package org.openintegrationengine.smoketest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openintegrationengine.smoketest.Harness;
import org.openintegrationengine.smoketest.SharedServer;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.model.ServerEvent;
import com.mirth.connect.model.User;
import com.mirth.connect.model.filters.EventFilter;

/**
 * Searches and pages through the audit events, as the administrator's Events view does.
 * Audited operations record their parameters as event attributes, so a run of
 * "Set user preference" calls carrying a unique marker gives a known set of events to find.
 * The preferences go on a user the test creates and removes, so none are left behind.
 */
@DisplayName("Event browser")
class EventBrowserTest {

    private static final int EVENTS = 12;
    private static final int PAGE = 5;

    private static final String NAME = "Set user preference";
    private static final String MARKER = "smoke-event-" + UUID.randomUUID().toString().substring(0, 8);

    private static Integer userId;

    @BeforeAll
    static void createEvents() throws Exception {
        User user = new User();
        user.setUsername(MARKER);
        user.setFirstName("Smoke");
        user.setLastName("Events");
        user.setEmail(MARKER + "@example.invalid");
        admin().createUser(user);
        userId = admin().getUser(MARKER).getId();

        for (int i = 0; i < EVENTS; i++) {
            admin().setUserPreference(userId, "smoke.event", MARKER + "-" + i);
        }

        // Events may be written after the call returns.
        Harness.awaitAsserted(() -> assertEquals(EVENTS, admin().getEventCount(filter()).longValue(), "events recorded for " + MARKER));
    }

    @AfterAll
    static void removeUser() throws Exception {
        if (userId != null) {
            admin().removeUser(userId);
        }
    }

    @Test
    @DisplayName("finds events by name and attribute")
    void findsEventsByNameAndAttribute() throws Exception {
        List<ServerEvent> events = admin().getEvents(filter(), 0, EVENTS + 1);

        assertEquals(EVENTS, events.size(), "events found");
        for (ServerEvent event : events) {
            assertEquals(NAME, event.getName(), "event " + event.getId());
        }

        // No event is named after the marker, so this finds the events above only if the name
        // is ignored.
        EventFilter otherName = filter();
        otherName.setName(MARKER);
        assertEquals(0, admin().getEventCount(otherName).longValue(), "events under another name");
    }

    @Test
    @DisplayName("pages through the matching events newest first")
    void pagesNewestFirst() throws Exception {
        List<ServerEvent> all = new ArrayList<>();
        for (int offset = 0; offset < EVENTS; offset += PAGE) {
            List<ServerEvent> page = admin().getEvents(filter(), offset, PAGE);
            assertEquals(Math.min(PAGE, EVENTS - offset), page.size(), "events at offset " + offset);
            all.addAll(page);
        }
        assertTrue(admin().getEvents(filter(), EVENTS, PAGE).isEmpty(), "page past the last event");

        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            assertTrue(ids.add(all.get(i).getId()), "event " + all.get(i).getId() + " appears on two pages");
            if (i > 0) {
                assertTrue(all.get(i - 1).getId() > all.get(i).getId(), "events are not in descending id order: " + ids);
            }
        }
    }

    private static EventFilter filter() {
        EventFilter filter = new EventFilter();
        filter.setName(NAME);
        filter.setAttributeSearch(MARKER);
        return filter;
    }

    private static Client admin() {
        return SharedServer.get().client();
    }
}
