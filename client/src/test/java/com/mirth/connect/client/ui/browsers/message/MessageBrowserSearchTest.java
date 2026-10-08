// SPDX-License-Identifier: MPL-2.0

package com.mirth.connect.client.ui.browsers.message;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.PaginatedMessageList;
import com.mirth.connect.client.core.ServerConnection;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.components.MirthTreeTable;
import com.mirth.connect.model.filters.MessageFilter;

public class MessageBrowserSearchTest {
    // The progress messages of the maximum ID request and of the page load
    private static final String REQUEST = "Getting the newest message ID...";
    private static final String PAGE = "Loading page...";

    private MessageBrowser browser;
    private Frame parent;
    private Client client;
    private JTextArea criteria;
    private final BlockingQueue<String> completed = new LinkedBlockingQueue<>();
    private final CountDownLatch release = new CountDownLatch(1);

    @Before
    public void setUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                browser = mock(MessageBrowser.class);
                parent = mock(Frame.class);
                client = mock(Client.class);
                when(client.getServerConnection()).thenReturn(mock(ServerConnection.class));
                when(client.getMessages(anyString(), any(), anyBoolean(), anyInt(), anyInt())).thenReturn(Collections.emptyList());
                parent.mirthClient = client;
                parent.activeBrowser = browser;
                Frame.userPreferences = mock(Preferences.class);
                doCallRealMethod().when(parent).doRemoveFilteredMessages();
                browser.parent = parent;
                browser.messageCache = new HashMap<>();
                browser.attachmentCache = new HashMap<>();
                browser.messageTreeTable = mock(MirthTreeTable.class);
                browser.advancedSearchPopup = mock(MessageBrowserAdvancedFilter.class);
                put("filterButton", new JButton());
                criteria = new JTextArea();
                put("lastSearchCriteria", criteria);
                put("channelName", "Channel A");
                put("isCURESPHILoggingOn", true);
                put("channelId", "channel-a");
                put("connectors", new HashMap<>());
                for (String name : new String[] { "nextPageButton", "previousPageButton", "countButton", "pageGoButton",
                        "mirthDatePicker1", "mirthDatePicker2", "mirthTimePicker1", "mirthTimePicker2",
                        "regexTextSearchCheckBox", "statusBoxReceived", "statusBoxTransformed", "statusBoxFiltered",
                        "statusBoxSent", "statusBoxError", "statusBoxQueued", "statusBoxPending", "tableModel",
                        "pageNumberField" }) {
                    mockField(name);
                }
                JTextField text = (JTextField) mockField("textSearchField");
                when(text.getText()).thenReturn("");
                JTextField pageSize = (JTextField) mockField("pageSizeField");
                when(pageSize.getText()).thenReturn("20");
                doCallRealMethod().when(browser).runSearch();
                doCallRealMethod().when(browser).generateMessageFilter();
                doCallRealMethod().when(browser).configurePaginatedMessageList();
                doCallRealMethod().when(browser).loadPageNumber(anyInt());
                doCallRealMethod().when(browser).clearCache();
                doCallRealMethod().when(browser).auditSearch();
                doCallRealMethod().when(browser).auditSearch(anyString(), anyString());
                doCallRealMethod().when(browser).getMessageFilter();
                doCallRealMethod().when(browser).getChannelId();
                doCallRealMethod().when(browser).refresh(any(), anyBoolean());
                when(parent.startWorking(anyString())).thenAnswer(i -> i.getArgument(0));
                doAnswer(i -> {
                    completed.add(i.getArgument(0));
                    return null;
                }).when(parent).stopWorking(anyString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @After
    public void releaseRequests() {
        release.countDown();
        Frame.userPreferences = null;
    }

    @Test
    public void maximumIdRequestRunsOffEdt() throws Exception {
        MessageFilter previous = showSearch("channel-a", 10L);
        PaginatedMessageList previousList = browser.messages;
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean onEdt = new AtomicBoolean();
        when(client.getMaxMessageId("channel-a")).thenAnswer(i -> {
            onEdt.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            if (!onEdt.get()) {
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
            return 41L;
        });

        SwingUtilities.invokeAndWait(browser::runSearch);
        assertTrue("The maximum ID request did not start", entered.await(10, TimeUnit.SECONDS));
        assertFalse("The maximum ID request must not block the EDT", onEdt.get());

        // The search that is listed is left alone until the maximum ID arrives
        assertSame(previous, browser.messageFilter);
        assertSame(previousList, browser.messages);
        assertEquals("", criteria.getText());
        verify(client, never()).getMessages(anyString(), any(), anyBoolean(), anyInt(), anyInt());

        release.countDown();
        awaitCompletion(PAGE);
        assertNotSame(previous, browser.messageFilter);
        verify(client).getMessages(eq("channel-a"), argThat(f -> Long.valueOf(41).equals(f.getMaxMessageId())), anyBoolean(), eq(0), anyInt());
    }

    @Test
    public void searchIsShownAndAuditedOnceWithTheMaximumId() throws Exception {
        when(client.getMaxMessageId("channel-a")).thenReturn(41L);

        SwingUtilities.invokeAndWait(browser::runSearch);
        awaitCompletion(PAGE);

        assertTrue(criteria.getText(), criteria.getText().contains("Max Message Id: 41"));
        verify(client, times(1)).auditQueriedPHIMessage(argThat(m -> m.get("filter").contains("41")));
    }

    @Test
    public void searchWhileTheMaximumIdIsPendingIsIgnored() throws Exception {
        blockMaximumIdRequest(41L);

        SwingUtilities.invokeAndWait(browser::runSearch);
        SwingUtilities.invokeAndWait(browser::runSearch);

        release.countDown();
        awaitCompletion(PAGE);
        verify(client, times(1)).getMaxMessageId("channel-a");
        verify(client, times(1)).getMessages(anyString(), any(), anyBoolean(), anyInt(), anyInt());
        verify(client, times(1)).auditQueriedPHIMessage(any());
    }

    @Test
    public void searchWithAMaximumIdFromTheFormMakesNoRequest() throws Exception {
        when(client.getMaxMessageId("channel-a")).thenReturn(41L);
        doAnswer(i -> {
            ((MessageFilter) i.getArgument(0)).setMaxMessageId(27L);
            return null;
        }).when(browser.advancedSearchPopup).applySelectionsToFilter(any());

        SwingUtilities.invokeAndWait(browser::runSearch);
        awaitCompletion(PAGE);

        verify(client).getMessages(eq("channel-a"), argThat(f -> Long.valueOf(27).equals(f.getMaxMessageId())), anyBoolean(), eq(0), anyInt());
        verify(client, never()).getMaxMessageId(anyString());
    }

    @Test
    public void failedMaximumIdRequestChangesNothingAndAllowsRetry() throws Exception {
        MessageFilter previous = showSearch("channel-a", 10L);
        PaginatedMessageList previousList = browser.messages;
        ClientException failure = new ClientException("no answer");
        when(client.getMaxMessageId("channel-a")).thenThrow(failure).thenReturn(53L);

        SwingUtilities.invokeAndWait(browser::runSearch);
        verify(parent, timeout(10000)).alertThrowable(eq(parent), same(failure));
        awaitCompletion(REQUEST);
        assertSame(previous, browser.messageFilter);
        assertSame(previousList, browser.messages);
        verify(client, never()).getMessages(anyString(), any(), anyBoolean(), anyInt(), anyInt());
        verify(client, never()).auditQueriedPHIMessage(any());

        SwingUtilities.invokeAndWait(browser::runSearch);
        awaitCompletion(PAGE);
        verify(client).getMessages(eq("channel-a"), argThat(f -> Long.valueOf(53).equals(f.getMaxMessageId())), anyBoolean(), eq(0), anyInt());
    }

    @Test
    public void answerOfADroppedSearchIsIgnored() throws Exception {
        MessageFilter previous = showSearch("channel-a", 10L);
        PaginatedMessageList previousList = browser.messages;
        blockMaximumIdRequest(41L);

        SwingUtilities.invokeAndWait(browser::runSearch);
        // As when another channel is loaded or the message browser is left
        put("maxMessageIdWorker", null);
        release.countDown();
        awaitCompletion(REQUEST);

        verify(client, after(500).never()).getMessages(anyString(), any(), anyBoolean(), anyInt(), anyInt());
        verify(client, never()).auditQueriedPHIMessage(any());
        assertSame(previous, browser.messageFilter);
        assertSame(previousList, browser.messages);
    }

    @Test
    public void connectorSelectionOfTheListedResultsIsKeptUntilTheSearchStarts() throws Exception {
        showSearch("channel-a", 10L);
        put("selectedMetaDataIds", Arrays.asList(1));
        doAnswer(i -> {
            ((MessageFilter) i.getArgument(0)).setIncludedMetaDataIds(Arrays.asList(2));
            return null;
        }).when(browser.advancedSearchPopup).applySelectionsToFilter(any());
        blockMaximumIdRequest(41L);

        SwingUtilities.invokeAndWait(browser::runSearch);
        assertEquals(Arrays.asList(1), field("selectedMetaDataIds"));

        release.countDown();
        awaitCompletion(PAGE);
        assertEquals(Arrays.asList(2), field("selectedMetaDataIds"));
    }

    @Test
    public void listedResultsStayUsableWhileTheMaximumIdIsPending() throws Exception {
        MessageFilter previous = showSearch("channel-a", 10L);
        when(parent.alertOption(any(), anyString())).thenReturn(true);
        blockMaximumIdRequest(41L);

        SwingUtilities.invokeAndWait(browser::runSearch);
        SwingUtilities.invokeAndWait(() -> {
            browser.refresh(null, false);
            parent.doRemoveFilteredMessages();
        });

        verify(client, timeout(10000)).getMessages(eq("channel-a"), same(previous), anyBoolean(), anyInt(), anyInt());
        verify(client, timeout(10000)).removeMessages(eq("channel-a"), same(previous));
    }

    @Test
    public void openingAChannelListsItsFirstSearchWithTheMaximumId() throws Exception {
        // The results of another channel are still listed, as when a channel is opened
        showSearch("channel-z", 5L);
        when(client.getMaxMessageId("channel-a")).thenReturn(41L);

        SwingUtilities.invokeAndWait(() -> {
            browser.isChannelMessagesPanelFirstLoadSearch = true;
            browser.runSearch();
            browser.isChannelMessagesPanelFirstLoadSearch = false;
        });
        awaitCompletion(PAGE);

        verify(client).getMessages(eq("channel-a"), argThat(f -> Long.valueOf(41).equals(f.getMaxMessageId())), anyBoolean(), eq(0), anyInt());
        assertTrue(criteria.getText(), criteria.getText().contains("Max Message Id: 41"));
        verify(client, never()).auditQueriedPHIMessage(any());
    }

    @Test
    public void failedMaximumIdRequestWhileOpeningAChannelStartsNoSearch() throws Exception {
        showSearch("channel-z", 5L);
        ClientException failure = new ClientException("no answer");
        when(client.getMaxMessageId("channel-a")).thenThrow(failure);

        SwingUtilities.invokeAndWait(() -> {
            browser.isChannelMessagesPanelFirstLoadSearch = true;
            browser.runSearch();
            browser.isChannelMessagesPanelFirstLoadSearch = false;
        });

        verify(parent).alertThrowable(eq(parent), same(failure));
        verify(client, never()).getMessages(anyString(), any(), anyBoolean(), anyInt(), anyInt());
        verify(client, never()).auditQueriedPHIMessage(any());
    }

    @Test
    public void removeResultsKeepsTheSearchThatWasShownWhenTheCommandWasChosen() throws Exception {
        MessageFilter shown = showSearch("channel-a", 10L);
        when(parent.alertOption(any(), anyString())).thenAnswer(i -> {
            // Another channel is loaded and a search finishes while the confirmation is open
            put("channelId", "channel-b");
            put("messageFilter", new MessageFilter());
            return true;
        });

        SwingUtilities.invokeAndWait(parent::doRemoveFilteredMessages);

        verify(client, timeout(10000)).removeMessages(eq("channel-a"), same(shown));
    }

    // A search that is listed, as after a successful search of the channel
    private MessageFilter showSearch(String channelId, Long maxMessageId) throws Exception {
        MessageFilter filter = new MessageFilter();
        filter.setMaxMessageId(maxMessageId);
        PaginatedMessageList list = new PaginatedMessageList();
        list.setClient(client);
        list.setChannelId(channelId);
        list.setMessageFilter(filter);
        list.setPageSize(20);
        browser.messageFilter = filter;
        browser.messages = list;
        return filter;
    }

    private void blockMaximumIdRequest(Long maxMessageId) throws Exception {
        when(client.getMaxMessageId("channel-a")).thenAnswer(i -> {
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return maxMessageId;
        });
    }

    private Object field(String name) throws Exception {
        Field field = MessageBrowser.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(browser);
    }

    private void awaitCompletion(String expected) throws Exception {
        String token;
        do {
            token = completed.poll(10, TimeUnit.SECONDS);
            assertNotNull("Worker completion did not run", token);
        } while (!expected.equals(token));
    }

    private Object mockField(String name) throws Exception {
        Field field = MessageBrowser.class.getDeclaredField(name);
        Object value = mock(field.getType());
        put(name, value);
        return value;
    }

    private void put(String name, Object value) throws Exception {
        Field field = MessageBrowser.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(browser, value);
    }
}
