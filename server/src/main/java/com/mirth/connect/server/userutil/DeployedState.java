/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.userutil;

import org.apache.commons.text.WordUtils;

/**
 * States of UNDEPLOYED, DEPLOYING, UNDEPLOYING, STARTING, STARTED, PAUSING, PAUSED, STOPPING,
 * STOPPED
 */
public enum DeployedState {
    /** The channel is disabled or not yet deployed. */
    UNDEPLOYED, 
    /** The channel is in the process of being deployed. */
    DEPLOYING, 
    /** The channel is in the process of being undeployed. */
    UNDEPLOYING, 
    /** The channel is in the process of starting. */
    STARTING, 
    /** The channel is running. */
    STARTED, 
    /** The channel is in the process of pausing. */
    PAUSING, 
    /** The channel is paused. */
    PAUSED, 
    /** The channel is in the process of stopping. */
    STOPPING, 
    /** The channel is stopped. */
    STOPPED, 
    /** The channel is in the process of syncing. */
    SYNCING, 
    /** The channel state is unknown. */
    UNKNOWN;

    private DeployedState() {}

    @Override
    public String toString() {
        return WordUtils.capitalizeFully(super.toString());
    }

    static DeployedState fromDonkeyDeployedState(com.mirth.connect.donkey.model.channel.DeployedState deployedState) {
        switch (deployedState) {
            case UNDEPLOYED:
                return UNDEPLOYED;
            case DEPLOYING:
                return DEPLOYING;
            case UNDEPLOYING:
                return UNDEPLOYING;
            case STARTING:
                return STARTING;
            case STARTED:
                return STARTED;
            case PAUSING:
                return PAUSING;
            case PAUSED:
                return PAUSED;
            case STOPPING:
                return STOPPING;
            case STOPPED:
                return STOPPED;
            case SYNCING:
                return SYNCING;
            case UNKNOWN:
                return UNKNOWN;
            default:
                return null;
        }
    }
}