// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.donkey.server.message.batch;

import java.util.ArrayList;
import java.util.List;

import com.mirth.connect.donkey.server.channel.DispatchResult;

/**
 * Reports every message of a batch, where {@link ResponseHandler#getResultForResponse()} reports
 * only the one selected for the response.
 */
public class CollectingResponseHandler extends SimpleResponseHandler {

    private final List<Long> messageIds = new ArrayList<Long>();

    @Override
    public void setDispatchResult(DispatchResult dispatchResult) {
        super.setDispatchResult(dispatchResult);

        if (dispatchResult != null) {
            messageIds.add(dispatchResult.getMessageId());
        }
    }

    public List<Long> getMessageIds() {
        return messageIds;
    }

}
