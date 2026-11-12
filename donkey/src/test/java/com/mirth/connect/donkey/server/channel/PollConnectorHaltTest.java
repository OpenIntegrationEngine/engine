// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine Contributors

package com.mirth.connect.donkey.server.channel;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com.mirth.connect.donkey.server.event.EventDispatcher;
import com.mirth.connect.donkey.test.util.TestPollConnectorProperties;

/**
 * A halted poll connector must release its Quartz scheduler, so the channel can be deployed and
 * started again on the same engine. See OpenIntegrationEngine/engine#571.
 */
public class PollConnectorHaltTest {

    private static class NoopPollConnector extends PollConnector {
        @Override
        public void onDeploy() {}

        @Override
        public void onUndeploy() {}

        @Override
        public void onStart() {}

        @Override
        public void onStop() {}

        @Override
        public void onHalt() {}

        @Override
        protected void poll() {}

        @Override
        public void handleRecoveredResponse(DispatchResult dispatchResult) {}
    }

    private static PollConnector connector(String channelId) {
        Channel channel = mock(Channel.class);
        when(channel.getEventDispatcher()).thenReturn(mock(EventDispatcher.class));

        PollConnector connector = new NoopPollConnector();
        connector.setChannelId(channelId);
        connector.setChannel(channel);
        connector.setConnectorProperties(new TestPollConnectorProperties());
        return connector;
    }

    /** Before the fix, the second start threw ObjectAlreadyExistsException for the poll job. */
    @Test
    public void startAfterHalt_onANewConnectorForTheSameChannel() throws Exception {
        String channelId = "poll-halt-redeploy-test";

        PollConnector first = connector(channelId);
        first.start();
        first.halt();

        PollConnector second = connector(channelId);
        second.start();
        second.stop();
    }

    /** Stop already released the scheduler; it keeps working. */
    @Test
    public void startAfterStop_onANewConnectorForTheSameChannel() throws Exception {
        String channelId = "poll-stop-redeploy-test";

        PollConnector first = connector(channelId);
        first.start();
        first.stop();

        PollConnector second = connector(channelId);
        second.start();
        second.stop();
    }
}
