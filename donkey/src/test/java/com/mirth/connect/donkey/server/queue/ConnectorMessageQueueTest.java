// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.donkey.server.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.event.EventDispatcher;

/**
 * The buffer arithmetic in {@link ConnectorMessageQueue}: how much of the queue is held in
 * memory, what {@code add} does once the buffer is full, and that polling still walks the
 * whole queue in order by going back to the data source.
 *
 * <p>The queue is a window onto rows the engine has already committed, so the data source
 * here is a plain map standing in for those rows rather than a database. That is the whole
 * point of testing it at this level: the arithmetic is the same on every dialect, and a fake
 * makes the buffer boundaries exact instead of a race against a live channel.
 */
public class ConnectorMessageQueueTest {

    private static final String CHANNEL_ID = "ConnectorMessageQueueTest";
    private static final String CHANNEL_NAME = "ConnectorMessageQueueTest";
    private static final String SERVER_ID = "ConnectorMessageQueueTestServer";
    private static final int META_DATA_ID = 0;

    private static final int BUFFER_CAPACITY = 3;
    private static final int QUEUE_SIZE = 10;

    private FakeDataSource dataSource;
    private SourceQueue queue;

    /**
     * {@link ConnectorMessageQueue} dispatches a queue-size event on every mutation and reads
     * its dispatcher off the {@link Donkey} singleton, which is only populated by a started
     * engine. Inject a mock one instead, as ChannelTest does.
     */
    @BeforeClass
    public static void injectDonkey() {
        Donkey donkey = mock(Donkey.class);
        when(donkey.getEventDispatcher()).thenReturn(mock(EventDispatcher.class));

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(Donkey.class);
                bind(Donkey.class).toInstance(donkey);
            }
        });
        injector.getInstance(Donkey.class);

        assertTrue("Donkey singleton injection failed", donkey == Donkey.getInstance());
    }

    @Before
    public void setUp() {
        dataSource = new FakeDataSource();
        queue = new SourceQueue();
        queue.setBufferCapacity(BUFFER_CAPACITY);
        queue.setDataSource(dataSource);
    }

    /**
     * The buffer is a window, not a copy: filling it from a data source holding more messages
     * than the buffer can take leaves the queue reporting the full size and the buffer the
     * capacity.
     */
    @Test
    public void fillBufferReadsAtMostTheBufferCapacity() {
        storeMessages(QUEUE_SIZE);

        queue.updateSize();
        queue.fillBuffer();

        assertEquals(BUFFER_CAPACITY, queue.getBufferCapacity());
        assertEquals(QUEUE_SIZE, queue.size());
        assertEquals(BUFFER_CAPACITY, queue.getBufferSize());
    }

    /**
     * {@code add} is called once per message the engine has just committed. Up to the capacity
     * it puts the message straight into the buffer; past it the buffer stops growing while the
     * queue keeps counting, so the extra messages are only reachable through the data source.
     */
    @Test
    public void addPastTheBufferCapacityGrowsTheQueueButNotTheBuffer() {
        syncWithEmptyDataSource();

        for (int i = 1; i <= BUFFER_CAPACITY; i++) {
            queue.add(storeMessage(i));
            assertEquals("queue size after adding message " + i, i, queue.size());
            assertEquals("buffer size after adding message " + i, i, queue.getBufferSize());
        }

        for (int i = BUFFER_CAPACITY + 1; i <= QUEUE_SIZE; i++) {
            queue.add(storeMessage(i));
            assertEquals("queue size after adding message " + i, i, queue.size());
            assertEquals("buffer size after adding message " + i, BUFFER_CAPACITY, queue.getBufferSize());
        }
    }

    /**
     * Polling past the buffer is the behaviour the capacity exists for: the queue refills from
     * the data source when the buffer runs dry, so every message comes out exactly once and in
     * the order the data source holds them, and the queue ends empty.
     *
     * <p>Every message here is committed and then added, which is what the engine guarantees by
     * doing both under this queue's own lock.
     * {@link #rowsCommittedWithoutBeingAddedAreStrandedUntilTheQueueResyncs} is the counterfactual
     * for what that lock is holding off.
     */
    @Test
    public void pollingRefillsTheBufferAndReturnsEveryMessageInOrder() {
        syncWithEmptyDataSource();
        for (int i = 1; i <= QUEUE_SIZE; i++) {
            queue.add(storeMessage(i));
        }

        List<Long> polled = drain();

        assertEquals(expectedIds(QUEUE_SIZE), polled);
        assertEquals(0, queue.size());
        assertTrue("the buffer only holds " + BUFFER_CAPACITY + " of " + QUEUE_SIZE + " messages, so"
                + " draining the queue must have gone back to the data source for more",
                dataSource.readCount > 1);
    }

    /**
     * The queue's size is a count of what has been added to it, not of what is committed, so a
     * row committed without a matching {@code add} is invisible to it: the queue hands over the
     * message it does know about, then reports itself empty while those rows are still there.
     *
     * <p>This is why the engine commits a source message and adds it to the queue under the same
     * lock. Nothing a client can do opens this window - it takes a write that bypasses the queue,
     * which is what this test does directly - but it is also what an unclean shutdown leaves
     * behind, and the recovery at the end is the same resync a redeploy performs.
     */
    @Test
    public void rowsCommittedWithoutBeingAddedAreStrandedUntilTheQueueResyncs() {
        syncWithEmptyDataSource();

        // Committed, but the add never happened.
        int strandedCount = BUFFER_CAPACITY + 1;
        storeMessages(strandedCount);

        // A later message committed and added the ordinary way.
        long addedId = strandedCount + 1;
        queue.add(storeMessage(addedId));

        assertEquals("the queue counts what was added to it", 1, queue.size());
        assertEquals("every message is committed", strandedCount + 1, dataSource.getSize());

        // The one message the queue knows about comes out, and then it calls itself empty.
        ConnectorMessage polled = queue.poll();
        assertEquals(addedId, polled.getMessageId());
        dataSource.rows.remove(polled.getMessageId());
        queue.finish(polled);
        assertNull("the queue reports itself empty while committed rows are still there",
                queue.poll());

        // Resyncing against the data source is what finds them again, in order.
        queue.invalidate(false, true);
        queue.updateSize();

        assertEquals(strandedCount, queue.size());
        assertEquals(expectedIds(strandedCount), drain());
    }

    /**
     * Shrinking the capacity discards the buffer rather than truncating it, because the
     * messages it holds may no longer be the ones a smaller window should start from. The
     * queue size is untouched and the next poll refills, so nothing is lost or reordered.
     */
    @Test
    public void shrinkingTheBufferCapacityDiscardsTheBufferWithoutLosingMessages() {
        storeMessages(QUEUE_SIZE);
        queue.updateSize();
        queue.fillBuffer();
        assertEquals(BUFFER_CAPACITY, queue.getBufferSize());

        queue.setBufferCapacity(1);

        assertEquals(0, queue.getBufferSize());
        assertEquals(QUEUE_SIZE, queue.size());
        assertEquals(expectedIds(QUEUE_SIZE), drain());
    }

    /**
     * Brings the queue in sync with an empty data source. {@code setDataSource} invalidates the
     * queue, and until it is filled once, {@code add} takes its resync path rather than the
     * buffering path under test.
     */
    private void syncWithEmptyDataSource() {
        queue.updateSize();
        queue.fillBuffer();
        assertEquals(0, queue.size());
    }

    /**
     * Polls the queue dry the way a queue thread does: every message that comes out is finished
     * and its row removed, so a refill sees only what is genuinely still queued.
     */
    private List<Long> drain() {
        List<Long> polled = new ArrayList<Long>();

        ConnectorMessage connectorMessage;
        while ((connectorMessage = queue.poll()) != null) {
            polled.add(connectorMessage.getMessageId());
            dataSource.rows.remove(connectorMessage.getMessageId());
            queue.finish(connectorMessage);
        }

        assertNull(queue.poll());
        return polled;
    }

    private void storeMessages(int count) {
        for (int i = 1; i <= count; i++) {
            storeMessage(i);
        }
    }

    private ConnectorMessage storeMessage(long messageId) {
        ConnectorMessage connectorMessage = new ConnectorMessage(CHANNEL_ID, CHANNEL_NAME, messageId, META_DATA_ID,
                SERVER_ID, Calendar.getInstance(), Status.RECEIVED);
        dataSource.rows.put(messageId, connectorMessage);
        return connectorMessage;
    }

    private static List<Long> expectedIds(int count) {
        List<Long> ids = new ArrayList<Long>();
        for (long i = 1; i <= count; i++) {
            ids.add(i);
        }
        return ids;
    }

    /**
     * The committed queue rows, in message id order. The real data source runs a bounded
     * {@code getConnectorMessages} query against the channel's queue table; this one pages the
     * same way over a map, and counts the reads so a test can tell a refill happened.
     */
    private static final class FakeDataSource extends ConnectorMessageQueueDataSource {

        final Map<Long, ConnectorMessage> rows = new LinkedHashMap<Long, ConnectorMessage>();
        int readCount;

        FakeDataSource() {
            super(CHANNEL_ID, SERVER_ID, META_DATA_ID, Status.RECEIVED, false, null);
        }

        @Override
        public int getSize() {
            return rows.size();
        }

        @Override
        public Map<Long, ConnectorMessage> getItems(int offset, int limit) {
            readCount++;

            Map<Long, ConnectorMessage> page = new LinkedHashMap<Long, ConnectorMessage>();
            for (ConnectorMessage connectorMessage : rows.values()) {
                if (offset > 0) {
                    offset--;
                } else if (page.size() < limit) {
                    page.put(connectorMessage.getMessageId(), connectorMessage);
                } else {
                    break;
                }
            }
            return page;
        }
    }
}
