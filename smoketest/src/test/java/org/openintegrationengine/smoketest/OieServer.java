// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.donkey.model.channel.DeployedState;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.model.message.attachment.Attachment;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ChannelStatistics;
import com.mirth.connect.model.DashboardStatus;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.model.filters.MessageFilter;
import com.mirth.connect.util.ConfigurationProperty;
import com.mirth.connect.util.MirthSSLUtil;

/**
 * The harness's view of a live server: log in, deploy a channel fixture, submit a message,
 * read the resulting message back.
 *
 * <p>This is a thin façade over {@link Client}, which already handles everything the
 * previous Python runner had to hand-roll: it trusts the server's self-signed certificate
 * ({@code TrustSelfSignedStrategy} plus {@code NoopHostnameVerifier}), sends the mandatory
 * {@code X-Requested-With} header, keeps the session cookie, and serialises the OIE model
 * classes. Assertions therefore run against typed objects rather than scraped XML.
 */
final class OieServer implements AutoCloseable {

    private static boolean serializerInitialized;

    private final Client client;
    /** Deployed channel ids, newest first, so teardown unwinds in reverse order. */
    private final Deque<String> deployedChannelIds = new ArrayDeque<>();

    private OieServer(Client client) {
        this.client = client;
    }

    static OieServer connect() {
        Client client;
        try {
            client = new Client(HarnessConfig.BASE_URL, HarnessConfig.REQUEST_TIMEOUT_MILLIS,
                    MirthSSLUtil.DEFAULT_HTTPS_CLIENT_PROTOCOLS, MirthSSLUtil.DEFAULT_HTTPS_CIPHER_SUITES);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create a client for " + HarnessConfig.BASE_URL, e);
        }

        try {
            LoginStatus status = client.login(HarnessConfig.USERNAME, HarnessConfig.PASSWORD);
            if (status == null || !status.isSuccess()) {
                throw new IllegalStateException("Login as " + HarnessConfig.USERNAME + " failed: "
                        + (status == null ? "no response" : status.getStatus() + " " + status.getMessage()));
            }
            initSerializer(client.getVersion());
        } catch (RuntimeException e) {
            client.close();
            throw e;
        } catch (Exception e) {
            client.close();
            throw new IllegalStateException("Could not log in to " + HarnessConfig.BASE_URL, e);
        }

        return new OieServer(client);
    }

    /**
     * {@code ObjectXMLSerializer.init} throws if called twice, and the singleton outlives
     * any one connection, so initialise it at most once per JVM.
     */
    private static synchronized void initSerializer(String serverVersion) throws Exception {
        if (!serializerInitialized) {
            ObjectXMLSerializer.getInstance().init(serverVersion);
            serializerInitialized = true;
        }
    }

    /**
     * Deploys an exported channel and waits for it to reach {@link DeployedState#STARTED}.
     *
     * @param xml   the exported channel XML
     * @param label a human-readable name for the channel, used only in error messages
     * @return the deployed channel's id
     */
    String deployChannel(String xml, String label) throws Exception {
        return deployChannel(ObjectXMLSerializer.getInstance().deserialize(xml, Channel.class), label);
    }

    /**
     * Deploys a channel model, creating or overwriting whatever is on the server under its id.
     * Deploying the same id twice redeploys it, which is how a test changes a channel in place.
     *
     * @param channel the channel to deploy
     * @param label   a human-readable name for the channel, used only in error messages
     * @return the deployed channel's id
     */
    String deployChannel(Channel channel, String label) throws Exception {
        String channelId = channel.getId();
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("Channel fixture has no id: " + label);
        }

        // A previous run may have left the channel behind; overwrite rather than fail.
        if (!client.createChannel(channel)) {
            client.updateChannel(channel, true, null);
        }
        if (!deployedChannelIds.contains(channelId)) {
            deployedChannelIds.push(channelId);
        }

        // returnErrors=true so a deploy failure surfaces here instead of only as a status
        // that never reaches STARTED. The String overload avoids DebuggerUtil parsing.
        client.deployChannel(channelId, true, "");
        awaitStarted(channelId, label);
        return channelId;
    }

    private void awaitStarted(String channelId, String label) throws Exception {
        try {
            awaitState(channelId, DeployedState.STARTED);
        } catch (AssertionError e) {
            throw new AssertionError("Channel " + label + " (" + channelId + ") did not start: " + e.getMessage(), e);
        }
    }

    private void awaitState(String channelId, DeployedState state) throws Exception {
        long deadline = System.nanoTime() + HarnessConfig.TIMEOUT.toNanos();
        DeployedState lastState = null;
        while (System.nanoTime() < deadline) {
            DashboardStatus status = client.getChannelStatus(channelId);
            lastState = status == null ? null : status.getState();
            if (lastState == state) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Channel " + channelId + " did not reach " + state + " within "
                + HarnessConfig.TIMEOUT.toSeconds() + "s; last state was " + lastState);
    }

    /**
     * The number of messages queued for one connector, as the dashboard reports it: the source
     * queue for metadata id 0, a destination's queue otherwise. Returns null while the channel
     * has no status for that connector.
     */
    Long queueSize(String channelId, int metaDataId) throws ClientException {
        DashboardStatus status = client.getChannelStatus(channelId);
        if (status == null) {
            return null;
        }
        for (DashboardStatus connectorStatus : status.getChildStatuses()) {
            if (Integer.valueOf(metaDataId).equals(connectorStatus.getMetaDataId())) {
                return connectorStatus.getQueued();
            }
        }
        return Integer.valueOf(metaDataId).equals(status.getMetaDataId()) ? status.getQueued() : null;
    }

    /** Stops a channel, leaving it deployed, and waits for it to report {@link DeployedState#STOPPED}. */
    void stopChannel(String channelId) throws Exception {
        client.stopChannel(channelId, true);
        awaitState(channelId, DeployedState.STOPPED);
    }

    /**
     * Halts a channel, which interrupts whatever it is processing instead of waiting for it, and
     * waits for it to report {@link DeployedState#STOPPED}.
     */
    void haltChannel(String channelId) throws Exception {
        client.haltChannel(channelId, true);
        awaitState(channelId, DeployedState.STOPPED);
    }

    /**
     * Starts a channel and waits for it to report {@link DeployedState#STARTED}, doing nothing if
     * it is already started. Tolerating that lets a test restore a channel it stopped from a
     * {@code finally} without having to know whether the stop got that far.
     */
    void startChannel(String channelId) throws Exception {
        DashboardStatus status = client.getChannelStatus(channelId);
        if (status != null && status.getState() == DeployedState.STARTED) {
            return;
        }
        client.startChannel(channelId, true);
        awaitState(channelId, DeployedState.STARTED);
    }

    /** Submits a source payload and returns the new message id. */
    long submitMessage(String channelId, String rawData, Map<String, Object> sourceMap) throws ClientException {
        RawMessage rawMessage = new RawMessage(rawData, null, sourceMap);
        Long messageId = client.processMessage(channelId, rawMessage);
        if (messageId == null) {
            throw new AssertionError("Server returned no message id for channel " + channelId);
        }
        return messageId;
    }

    /**
     * Sets one configuration map entry, leaving the rest alone. This is the only server-side
     * state a client can write that channel scripts can read back, which makes it the harness's
     * way to signal a running script.
     *
     * <p>The server does the read-modify-write under its own lock, so test classes running in
     * parallel can set their own gate properties without dropping each other's.
     */
    void setConfigurationProperty(String key, String value) throws ClientException {
        client.setConfigurationProperty(key, new ConfigurationProperty(value, null));
    }

    /**
     * Reads one message back, with content, so assertions can inspect every connector. Asking
     * for the content switches the server's DAO out of decrypting mode, so the content is
     * whatever is stored - ciphertext for a channel with {@code encryptData} set. See
     * {@link #fetchDecryptedMessage}.
     */
    Message fetchMessage(String channelId, long messageId) throws ClientException {
        MessageFilter filter = new MessageFilter();
        filter.setMinMessageId(messageId);
        filter.setMaxMessageId(messageId);

        List<Message> messages = client.getMessages(channelId, filter, true, 0, 1);
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.get(0);
    }

    /**
     * Reads one message back through the decrypting path. {@link #fetchMessage} asks the server
     * for the content as stored, so an encrypting channel's content arrives as ciphertext; this
     * call leaves the DAO decrypting, which is what the administrator sees in the message browser.
     */
    Message fetchDecryptedMessage(String channelId, long messageId, List<Integer> metaDataIds)
            throws ClientException {
        return client.getMessageContent(channelId, messageId, metaDataIds);
    }

    /**
     * Every attachment stored against one message, with its content. Attachments live in their
     * own table rather than on the message, so they never appear in {@link #fetchMessage}; the
     * harness makes this second read only when a fixture names an {@code attachmentNN} file.
     */
    List<Attachment> fetchAttachments(String channelId, long messageId) throws ClientException {
        List<Attachment> attachments = client.getAttachmentsByMessageId(channelId, messageId);
        return attachments == null ? List.of() : attachments;
    }

    /**
     * Deletes one message, or one of its connector messages when {@code metaDataId} is not null.
     * This is the message browser's delete: the server turns it into a one-message filter, so
     * asking for metadata id 0 deletes the whole message rather than only the source.
     */
    void removeMessage(String channelId, long messageId, Integer metaDataId) throws ClientException {
        client.removeMessage(channelId, messageId, metaDataId, null);
    }

    /**
     * Deletes every message in a channel, stopping and restarting it if it is running, and
     * optionally resetting its statistics at the same time.
     */
    void removeAllMessages(String channelId, boolean clearStatistics) throws ClientException {
        client.removeAllMessages(channelId, true, clearStatistics);
    }

    /** How many messages the channel still holds. */
    long messageCount(String channelId) throws ClientException {
        Long count = client.getMessageCount(channelId, new MessageFilter());
        return count == null ? 0L : count;
    }

    /**
     * The channel's aggregate lifetime counters, as the dashboard's statistics view reports them.
     *
     * <p>For a deployed channel these come from the running engine's own tallies. For a channel
     * that has been undeployed without being removed they come from the database, which is the
     * only way a client sees what was actually stored.
     */
    ChannelStatistics statistics(String channelId) throws ClientException {
        return client.getStatistics(channelId);
    }

    /** Undeploys a channel without removing it, so its stored rows stay where they are. */
    void undeployChannel(String channelId) throws ClientException {
        client.undeployChannel(channelId, true);
    }

    /**
     * Every statistics counter the dashboard shows for one channel, keyed by connector: 0 for the
     * source, its metadata id for each destination, and null for the channel's own aggregate row.
     * Only the statuses in {@code com.mirth.connect.donkey.server.channel.Statistics}'s
     * {@code TRACKED_STATUSES} are counted, so each map holds RECEIVED, FILTERED, SENT and ERROR
     * and nothing else.
     */
    Map<Integer, Map<Status, Long>> connectorStatistics(String channelId) throws ClientException {
        DashboardStatus status = client.getChannelStatus(channelId);
        if (status == null) {
            throw new AssertionError("Channel " + channelId + " has no dashboard status");
        }

        Map<Integer, Map<Status, Long>> statistics = new LinkedHashMap<>();
        // The channel's own status carries the aggregate row, which the table stores under a null
        // metadata id; its children carry the per-connector rows.
        statistics.put(null, status.getStatistics());
        for (DashboardStatus connectorStatus : status.getChildStatuses()) {
            statistics.put(connectorStatus.getMetaDataId(), connectorStatus.getStatistics());
        }
        return statistics;
    }

    /** Undeploys and removes a channel, tolerating failures so teardown always continues. */
    void removeChannel(String channelId) {
        deployedChannelIds.remove(channelId);
        tolerate("undeploy", channelId, () -> client.undeployChannel(channelId, false));
        tolerate("remove", channelId, () -> client.removeChannel(channelId));
    }

    @Override
    public void close() {
        // Safety net for channels whose per-channel teardown never ran.
        while (!deployedChannelIds.isEmpty()) {
            removeChannel(deployedChannelIds.peek());
        }
        try {
            client.logout();
        } catch (Exception e) {
            System.err.println("Ignoring logout failure: " + e);
        }
        client.close();
    }

    private void tolerate(String action, String channelId, ClientAction body) {
        try {
            body.run();
        } catch (Exception e) {
            System.err.println("Ignoring " + action + " failure for channel " + channelId + ": " + e);
        }
    }

    private interface ClientAction {
        void run() throws ClientException, IOException;
    }
}
