// SPDX-License-Identifier: MPL-2.0

package com.mirth.connect.client.ui.browsers.message;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.jdesktop.swingx.JXTaskPane;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.PaginatedMessageList;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.components.MirthTreeTable;
import com.mirth.connect.model.filters.MessageFilter;

public class MessageBrowserSearchTest {
    private MessageBrowser browser;
    private Frame parent;
    private Client client;
    private JButton search;
    private JTextArea criteria;
    private final BlockingQueue<String> completed = new LinkedBlockingQueue<>();
    private final List<CountDownLatch> releases = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                browser = mock(MessageBrowser.class);
                parent = mock(Frame.class);
                client = mock(Client.class, RETURNS_DEEP_STUBS);
                parent.mirthClient = client;
                parent.activeBrowser = browser;
                parent.messageTasks = new JXTaskPane();
                parent.messagePopupMenu = new JPopupMenu();
                for (int i = 0; i < 11; i++) {
                    parent.messageTasks.getContentPane().add(new JButton());
                    parent.messagePopupMenu.add(new JMenuItem());
                }
                when(parent.getComponentTaskMap()).thenReturn(Collections.emptyMap());
                doCallRealMethod().when(parent).setVisibleTasks(any(), any(), anyInt(), anyInt(), anyBoolean());
                doCallRealMethod().when(parent).doRefreshMessages();
                doCallRealMethod().when(parent).doRemoveFilteredMessages();
                doCallRealMethod().when(parent).doReprocessFilteredMessages();
                doCallRealMethod().when(parent).doExportMessages();
                browser.parent = parent;
                browser.metaDataColumns = Collections.emptyList();
                browser.columnMap = new TreeMap<>();
                browser.messageCache = new HashMap<>();
                browser.attachmentCache = new HashMap<>();
                browser.messageTreeTable = mock(MirthTreeTable.class);
                when(browser.messageTreeTable.getCustomHiddenColumnMap()).thenReturn(new HashMap<>());
                when(browser.messageTreeTable.getColumnFactory()).thenReturn(mock(MessageBrowserTableColumnFactory.class));
                when(browser.createCustomMetaDataColumns()).thenReturn(Collections.emptySet());
                browser.advancedSearchPopup = mock(MessageBrowserAdvancedFilter.class);
                search = new JButton();
                criteria = new JTextArea();
                put("filterButton", search);
                put("lastSearchCriteria", criteria);
                put("channelId", "channel-a");
                put("connectors", new HashMap<>());
                for (String name : new String[] { "nextPageButton", "previousPageButton", "countButton", "pageGoButton",
                        "mirthDatePicker1", "mirthDatePicker2", "mirthTimePicker1", "mirthTimePicker2",
                        "regexTextSearchCheckBox", "statusBoxReceived", "statusBoxTransformed", "statusBoxFiltered",
                        "statusBoxSent", "statusBoxError", "statusBoxQueued", "statusBoxPending", "formatMessageCheckBox",
                        "recentFiltersButton", "tableModel", "pageNumberField" }) {
                    mockField(name);
                }
                JTextField text = (JTextField) mockField("textSearchField");
                when(text.getText()).thenReturn("");
                JTextField pageSize = (JTextField) mockField("pageSizeField");
                when(pageSize.getText()).thenReturn("20");
                doCallRealMethod().when(browser).runSearch();
                doCallRealMethod().when(browser).generateMessageFilter();
                doCallRealMethod().when(browser).configurePaginatedMessageList();
                doCallRealMethod().when(browser).clearCache();
                doCallRealMethod().when(browser).refresh(any(), anyBoolean());
                doCallRealMethod().when(browser).getMessageCount();
                doCallRealMethod().when(browser).getMessageFilter();
                doCallRealMethod().when(browser).getChannelId();
                AtomicInteger working = new AtomicInteger();
                when(parent.startWorking(anyString())).thenAnswer(i -> "working-" + working.incrementAndGet());
                doAnswer(i -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    completed.add(i.getArgument(0));
                    return null;
                }).when(parent).stopWorking(anyString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @After
    public void releaseRequests() throws Exception {
        for (CountDownLatch release : releases) {
            release.countDown();
        }
        SwingWorker<?, ?> worker = (SwingWorker<?, ?>) get("worker");
        if (worker != null) {
            worker.cancel(true);
        }
        SwingUtilities.invokeAndWait(() -> {});
    }

    @Test
    public void generatingFilterDoesNotMakeAnHttpRequest() throws Exception {
        when(client.getMaxMessageId("channel-a")).thenReturn(41L);
        SwingUtilities.invokeAndWait(() -> assertTrue(browser.generateMessageFilter()));
        verify(client, never()).getMaxMessageId(anyString());
    }

    @Test
    public void generatingFilterPreservesRecentSearchHistory() throws Exception {
        MessageBrowserRecentFilterStore recent = mock(MessageBrowserRecentFilterStore.class);
        put("recentFilterStore", recent);
        doAnswer(i -> {
            ((MessageFilter) i.getArgument(0)).setTextSearch("patient");
            return null;
        }).when(browser.advancedSearchPopup).applySelectionsToFilter(any(MessageFilter.class));
        SwingUtilities.invokeAndWait(() -> assertTrue(browser.generateMessageFilter()));
        verify(recent).addRecentFilter(browser.messageFilter);
        verify((javax.swing.JComponent) get("recentFiltersButton")).setEnabled(true);
        verify(client, never()).getMaxMessageId(anyString());
    }

    @Test
    public void capRequestRunsOffEdtAndRepeatedSearchDoesNotReplaceFilter() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = releaseLatch();
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
        await(entered);
        assertFalse("The maximum-ID request must not block the EDT", onEdt.get());
        MessageFilter first = browser.messageFilter;
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(search.isEnabled());
            browser.runSearch();
            assertSame(first, browser.messageFilter);
        });
        verify(browser, times(1)).generateMessageFilter();
        verify(client, times(1)).getMaxMessageId("channel-a");
        release.countDown();
        awaitCompletion("working-1");
        assertEquals(Long.valueOf(41), first.getMaxMessageId());
        verify(browser).loadPageNumber(1);
        verify(browser).auditSearch();
        SwingUtilities.invokeAndWait(() -> assertTrue(search.isEnabled()));
        assertFalse(criteria.getText().isEmpty());
    }

    @Test
    public void suppliedMaximumSkipsTheCapRequest() throws Exception {
        doAnswer(i -> {
            ((MessageFilter) i.getArgument(0)).setMaxMessageId(27L);
            return null;
        }).when(browser.advancedSearchPopup).applySelectionsToFilter(any(MessageFilter.class));
        SwingUtilities.invokeAndWait(browser::runSearch);
        awaitCompletion("working-1");
        verify(client, never()).getMaxMessageId(anyString());
        assertEquals(Long.valueOf(27), browser.messageFilter.getMaxMessageId());
        verify(browser).loadPageNumber(1);
    }

    @Test
    public void failedCapRestoresSearchAndAllowsRetry() throws Exception {
        ClientException failure = new ClientException("Cannot retrieve the maximum ID");
        when(client.getMaxMessageId("channel-a")).thenThrow(failure).thenReturn(53L);
        SwingUtilities.invokeAndWait(browser::runSearch);
        awaitCompletion("working-1");
        verify(parent).alertThrowable(parent, failure);
        verify(browser, never()).loadPageNumber(anyInt());
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(search.isEnabled());
            browser.runSearch();
        });
        awaitCompletion("working-2");
        assertEquals(Long.valueOf(53), browser.messageFilter.getMaxMessageId());
        verify(browser).loadPageNumber(1);
    }

    @Test
    public void paginationFailureDoesNotLoadAPageAndRestoresSearch() throws Exception {
        when(client.getMaxMessageId("channel-a")).thenReturn(41L);
        doThrow(new NumberFormatException("Invalid page size")).doThrow(new Exception("Configuration failed"))
                .when(browser).configurePaginatedMessageList();
        SwingUtilities.invokeAndWait(browser::runSearch);
        awaitCompletion("working-1");
        verify(parent).alertError(parent, "Invalid page size.");
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(search.isEnabled());
            browser.runSearch();
        });
        awaitCompletion("working-2");
        verify(parent).alertError(parent, "Error configuring paginated message list: Configuration failed");
        verify(browser, never()).loadPageNumber(anyInt());
        SwingUtilities.invokeAndWait(() -> assertTrue(search.isEnabled()));
    }

    @Test
    public void switchingChannelDiscardsLateCapAndKeepsNewSearchDisabled() throws Exception {
        CountDownLatch enteredA = new CountDownLatch(1);
        CountDownLatch enteredB = new CountDownLatch(1);
        CountDownLatch releaseA = releaseLatch();
        CountDownLatch releaseB = releaseLatch();
        when(client.getMaxMessageId("channel-a")).thenAnswer(i -> {
            enteredA.countDown();
            while (releaseA.getCount() != 0) {
                try {
                    assertTrue(releaseA.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    // An HTTP request can finish after cancellation.
                }
            }
            return 41L;
        });
        when(client.getMaxMessageId("channel-b")).thenAnswer(i -> {
            enteredB.countDown();
            assertTrue(releaseB.await(10, TimeUnit.SECONDS));
            return 83L;
        });
        SwingUtilities.invokeAndWait(browser::runSearch);
        await(enteredA);
        MessageFilter filterA = browser.messageFilter;
        doCallRealMethod().when(browser).loadChannel(any(MessageBrowserChannelModel.class));
        MessageBrowserChannelModel channelB = new MessageBrowserChannelModel("channel-b", "Channel B", new HashMap<>(),
                Collections.emptyList(), Collections.emptyList(), true);
        SwingUtilities.invokeAndWait(() -> browser.loadChannel(channelB));
        await(enteredB);
        awaitCompletion("working-1");
        releaseA.countDown();
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(search.isEnabled());
            assertNull(browser.messageFilter.getMaxMessageId());
        });
        assertNull(filterA.getMaxMessageId());
        verify(browser, never()).loadPageNumber(anyInt());
        releaseB.countDown();
        awaitCompletion("working-2");
        assertEquals(Long.valueOf(83), browser.messageFilter.getMaxMessageId());
        verify(browser).loadPageNumber(1);
        verify(browser, never()).auditSearch();
    }

    @Test
    public void pageWorkerKeepsSearchDisabledAfterCapCompletes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = releaseLatch();
        when(client.getMaxMessageId("channel-a")).thenReturn(41L);
        when(client.getMessages(eq("channel-a"), any(MessageFilter.class), anyBoolean(), anyInt(), anyInt())).thenAnswer(i -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return Collections.emptyList();
        });
        doCallRealMethod().when(browser).loadPageNumber(anyInt());
        SwingUtilities.invokeAndWait(browser::runSearch);
        await(entered);
        awaitCompletion("working-1");
        SwingUtilities.invokeAndWait(() -> assertFalse(search.isEnabled()));
        release.countDown();
        awaitCompletion("working-2");
        SwingUtilities.invokeAndWait(() -> assertTrue(search.isEnabled()));
    }

    @Test
    public void rejectedFilterRestoresSearchWithoutStartingARequest() throws Exception {
        JButton nextPage = new JButton();
        put("nextPageButton", nextPage);
        doReturn(false).when(browser).generateMessageFilter();
        SwingUtilities.invokeAndWait(browser::runSearch);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(search.isEnabled());
            assertTrue(nextPage.isEnabled());
        });
        verify(client, never()).getMaxMessageId(anyString());
        verify(parent, never()).startWorking(anyString());
    }

    @Test
    public void cancelledPageDoesNotEnableSearchForAnotherChannel() throws Exception {
        CountDownLatch pageEntered = new CountDownLatch(1);
        CountDownLatch pageRelease = releaseLatch();
        CountDownLatch capEntered = new CountDownLatch(1);
        CountDownLatch capRelease = releaseLatch();
        when(client.getMaxMessageId("channel-a")).thenReturn(41L);
        when(client.getMessages(eq("channel-a"), any(MessageFilter.class), anyBoolean(), anyInt(), anyInt())).thenAnswer(i -> {
            pageEntered.countDown();
            while (pageRelease.getCount() != 0) {
                try {
                    assertTrue(pageRelease.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    // An HTTP request can finish after cancellation.
                }
            }
            return Collections.emptyList();
        });
        when(client.getMaxMessageId("channel-b")).thenAnswer(i -> {
            capEntered.countDown();
            assertTrue(capRelease.await(10, TimeUnit.SECONDS));
            return 83L;
        });
        doCallRealMethod().when(browser).loadPageNumber(anyInt());
        SwingUtilities.invokeAndWait(browser::runSearch);
        await(pageEntered);
        awaitCompletion("working-1");
        doNothing().when(browser).loadPageNumber(anyInt());
        loadChannelB();
        await(capEntered);
        awaitCompletion("working-2");
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(search.isEnabled());
            assertSearchTasksVisible(false);
        });
        capRelease.countDown();
        awaitCompletion("working-3");
        assertEquals(Long.valueOf(83), browser.messageFilter.getMaxMessageId());
    }

    @Test
    public void cancelledCountDoesNotModifyNewSearchOrEnableItsControls() throws Exception {
        CountDownLatch countEntered = new CountDownLatch(1);
        CountDownLatch countRelease = releaseLatch();
        CountDownLatch countFinished = new CountDownLatch(1);
        CountDownLatch capEntered = new CountDownLatch(1);
        CountDownLatch capRelease = releaseLatch();
        PaginatedMessageList oldMessages = mock(PaginatedMessageList.class);
        browser.messages = oldMessages;
        browser.messageFilter = new MessageFilter();
        when(client.getMessageCount(eq("channel-a"), any(MessageFilter.class))).thenAnswer(i -> {
            countEntered.countDown();
            while (countRelease.getCount() != 0) {
                try {
                    assertTrue(countRelease.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    // An HTTP request can finish after cancellation.
                }
            }
            return 21L;
        });
        doAnswer(i -> {
            countFinished.countDown();
            return null;
        }).when(oldMessages).setItemCount(21L);
        when(client.getMaxMessageId("channel-b")).thenAnswer(i -> {
            capEntered.countDown();
            assertTrue(capRelease.await(10, TimeUnit.SECONDS));
            return 83L;
        });
        Method count = MessageBrowser.class.getDeclaredMethod("countButtonActionPerformed", java.awt.event.ActionEvent.class);
        count.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                count.invoke(browser, new Object[] { null });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        await(countEntered);
        loadChannelB();
        await(capEntered);
        awaitCompletion("working-1");
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(search.isEnabled());
            assertSearchTasksVisible(false);
        });
        capRelease.countDown();
        awaitCompletion("working-2");
        countRelease.countDown();
        await(countFinished);
        assertNull(browser.messages.getItemCount());
        assertEquals(Long.valueOf(83), browser.messageFilter.getMaxMessageId());
    }

    @Test
    public void pendingSearchBlocksRefreshAndResultActionsUntilPageCompletes() throws Exception {
        preparePreviousResults();
        put("isChannelDeployed", true);
        CountDownLatch capEntered = new CountDownLatch(1);
        CountDownLatch capRelease = releaseLatch();
        CountDownLatch pageEntered = new CountDownLatch(1);
        CountDownLatch pageRelease = releaseLatch();
        when(client.getMaxMessageId("channel-a")).thenAnswer(i -> {
            capEntered.countDown();
            assertTrue(capRelease.await(10, TimeUnit.SECONDS));
            return 41L;
        });
        when(client.getMessages(eq("channel-a"), any(MessageFilter.class), anyBoolean(), anyInt(), anyInt())).thenAnswer(i -> {
            pageEntered.countDown();
            assertTrue(pageRelease.await(10, TimeUnit.SECONDS));
            return Collections.emptyList();
        });
        doCallRealMethod().when(browser).loadPageNumber(anyInt());
        doCallRealMethod().when(browser).taskPaneWhenSelectingMessages();
        doCallRealMethod().when(browser).taskPaneWhenClearingDescription();
        SwingUtilities.invokeAndWait(browser::runSearch);
        await(capEntered);
        SwingWorker<?, ?> cap = (SwingWorker<?, ?>) get("worker");
        SwingUtilities.invokeAndWait(() -> {
            browser.taskPaneWhenSelectingMessages();
            browser.taskPaneWhenClearingDescription();
            assertSearchTasksVisible(false);
            parent.doRefreshMessages();
            parent.doRemoveFilteredMessages();
            parent.doReprocessFilteredMessages();
            parent.doExportMessages();
            assertFalse(cap.isCancelled());
        });
        verify(browser, never()).loadPageNumber(anyInt());
        verify(parent, never()).alertOption(any(), anyString());
        verify(client, never()).removeMessages(anyString(), any(MessageFilter.class));
        capRelease.countDown();
        await(pageEntered);
        awaitCompletion("working-1");
        SwingUtilities.invokeAndWait(() -> assertSearchTasksVisible(false));
        pageRelease.countDown();
        awaitCompletion("working-2");
        SwingUtilities.invokeAndWait(() -> assertSearchTasksVisible(true));
        assertEquals(Long.valueOf(41), browser.getMessageFilter().getMaxMessageId());
    }

    @Test
    public void firstSearchCannotBeRefreshedBeforeMessagesExist() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = releaseLatch();
        when(client.getMaxMessageId("channel-a")).thenAnswer(i -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return 41L;
        });
        SwingUtilities.invokeAndWait(browser::runSearch);
        await(entered);
        SwingWorker<?, ?> cap = (SwingWorker<?, ?>) get("worker");
        SwingUtilities.invokeAndWait(() -> {
            parent.doRefreshMessages();
            assertFalse(cap.isCancelled());
            assertSearchTasksVisible(false);
        });
        release.countDown();
        awaitCompletion("working-1");
        verify(browser).loadPageNumber(1);
    }

    @Test
    public void failedPreparationRestoresPreviousResultsAndTasks() throws Exception {
        MessageFilter previous = preparePreviousResults();
        PaginatedMessageList previousMessages = browser.messages;
        put("isChannelDeployed", true);
        ClientException failure = new ClientException("Maximum ID unavailable");
        when(client.getMaxMessageId("channel-a")).thenThrow(failure).thenReturn(41L);
        SwingUtilities.invokeAndWait(browser::runSearch);
        awaitCompletion("working-1");
        SwingUtilities.invokeAndWait(() -> {
            assertSame(previous, browser.getMessageFilter());
            assertSame(previousMessages, browser.messages);
            assertSearchTasksVisible(true);
        });
        when(((JTextField) get("pageSizeField")).getText()).thenReturn("invalid");
        SwingUtilities.invokeAndWait(browser::runSearch);
        awaitCompletion("working-2");
        SwingUtilities.invokeAndWait(() -> {
            assertSame(previous, browser.getMessageFilter());
            assertSame(previousMessages, browser.messages);
            assertSearchTasksVisible(true);
        });
        verify(parent).alertError(parent, "Invalid page size.");
        verify(browser, never()).loadPageNumber(anyInt());
    }

    @Test
    public void firstLoadFailureKeepsResultActionsHidden() throws Exception {
        when(client.getMaxMessageId("channel-a")).thenThrow(new ClientException("Maximum ID unavailable"));
        SwingUtilities.invokeAndWait(browser::runSearch);
        awaitCompletion("working-1");
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(search.isEnabled());
            assertSearchTasksVisible(false);
            parent.doRemoveFilteredMessages();
            parent.doExportMessages();
            parent.doReprocessFilteredMessages();
            parent.doRefreshMessages();
        });
        verify(parent, never()).alertOption(any(), anyString());
        verify(browser, never()).loadPageNumber(anyInt());
    }

    @Test
    public void removeResultsCapturesThePreparedChannelAndFilter() throws Exception {
        MessageFilter filter = preparePreviousResults();
        CountDownLatch removed = new CountDownLatch(1);
        when(parent.alertOption(any(), anyString())).thenReturn(true);
        MessageBrowser replacement = mock(MessageBrowser.class);
        when(replacement.getChannelId()).thenReturn("channel-b");
        when(replacement.getMessageFilter()).thenReturn(new MessageFilter());
        when(parent.startWorking("Removing messages...")).thenAnswer(i -> {
            parent.activeBrowser = replacement;
            return "remove";
        });
        doAnswer(i -> {
            removed.countDown();
            return null;
        }).when(client).removeMessages("channel-a", filter);
        java.util.prefs.Preferences previousPreferences = Frame.userPreferences;
        Frame.userPreferences = mock(java.util.prefs.Preferences.class);
        try {
            SwingUtilities.invokeAndWait(parent::doRemoveFilteredMessages);
            await(removed);
            awaitCompletion("remove");
            verify(client).removeMessages("channel-a", filter);
            verify(client, never()).removeMessages(eq("channel-b"), any(MessageFilter.class));
        } finally {
            Frame.userPreferences = previousPreferences;
        }
    }

    @Test
    public void removeResultsRechecksVisibilityAfterConfirmation() throws Exception {
        preparePreviousResults();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = releaseLatch();
        when(client.getMaxMessageId("channel-a")).thenAnswer(i -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return 41L;
        });
        when(parent.alertOption(any(), anyString())).thenAnswer(i -> {
            browser.runSearch();
            return true;
        });
        java.util.prefs.Preferences previousPreferences = Frame.userPreferences;
        Frame.userPreferences = mock(java.util.prefs.Preferences.class);
        try {
            SwingUtilities.invokeAndWait(parent::doRemoveFilteredMessages);
            await(entered);
            verify(parent, never()).startWorking("Removing messages...");
            verify(client, never()).removeMessages(anyString(), any(MessageFilter.class));
            release.countDown();
            awaitCompletion("working-1");
        } finally {
            Frame.userPreferences = previousPreferences;
        }
    }

    @Test
    public void countUsesTheBrowserCountHook() throws Exception {
        preparePreviousResults();
        doReturn(21L).when(browser).getMessageCount();
        Method count = MessageBrowser.class.getDeclaredMethod("countButtonActionPerformed", java.awt.event.ActionEvent.class);
        count.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                count.invoke(browser, new Object[] { null });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        awaitCompletion("working-1");
        verify(browser).getMessageCount();
        verify(client, never()).getMessageCount(anyString(), any(MessageFilter.class));
        assertEquals(Long.valueOf(21), browser.messages.getItemCount());
    }

    private MessageFilter preparePreviousResults() throws Exception {
        MessageFilter previous = new MessageFilter();
        previous.setMaxMessageId(10L);
        previous.setTextSearch("old");
        browser.messageFilter = previous;
        SwingUtilities.invokeAndWait(() -> {
            try {
                browser.configurePaginatedMessageList();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return previous;
    }

    private void assertSearchTasksVisible(boolean expected) {
        assertTrue(SwingUtilities.isEventDispatchThread());
        for (int index : new int[] { 0, 3, 5, 7 }) {
            assertEquals("Task " + index, expected, parent.messageTasks.getContentPane().getComponent(index).isVisible());
            assertEquals("Popup " + index, expected, parent.messagePopupMenu.getComponent(index).isVisible());
        }
    }

    private void loadChannelB() throws Exception {
        doCallRealMethod().when(browser).loadChannel(any(MessageBrowserChannelModel.class));
        MessageBrowserChannelModel model = new MessageBrowserChannelModel("channel-b", "Channel B", new HashMap<>(),
                Collections.emptyList(), Collections.emptyList(), true);
        SwingUtilities.invokeAndWait(() -> browser.loadChannel(model));
    }

    private CountDownLatch releaseLatch() {
        CountDownLatch release = new CountDownLatch(1);
        releases.add(release);
        return release;
    }

    private void await(CountDownLatch latch) throws Exception {
        assertTrue("Request did not start", latch.await(10, TimeUnit.SECONDS));
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

    private Object get(String name) throws Exception {
        Field field = MessageBrowser.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(browser);
    }
}
