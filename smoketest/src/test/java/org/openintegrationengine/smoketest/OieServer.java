// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.donkey.model.channel.DeployedState;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.model.filters.MessageFilter;
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
        Channel channel = ObjectXMLSerializer.getInstance().deserialize(xml, Channel.class);
        String channelId = channel.getId();
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("Channel fixture has no id: " + label);
        }

        // A previous run may have left the channel behind; overwrite rather than fail.
        if (!client.createChannel(channel)) {
            client.updateChannel(channel, true, null);
        }
        deployedChannelIds.push(channelId);

        // returnErrors=true so a deploy failure surfaces here instead of only as a status
        // that never reaches STARTED. The String overload avoids DebuggerUtil parsing.
        client.deployChannel(channelId, true, "");
        awaitStarted(channelId, label);
        return channelId;
    }

    private void awaitStarted(String channelId, String label) throws Exception {
        long deadline = System.nanoTime() + HarnessConfig.TIMEOUT.toNanos();
        DeployedState lastState = null;
        while (System.nanoTime() < deadline) {
            var status = client.getChannelStatus(channelId);
            lastState = status == null ? null : status.getState();
            if (lastState == DeployedState.STARTED) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Channel " + label + " (" + channelId + ") did not start within "
                + HarnessConfig.TIMEOUT.toSeconds() + "s; last state was " + lastState);
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

    /** Reads one message back, with content, so assertions can inspect every connector. */
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
