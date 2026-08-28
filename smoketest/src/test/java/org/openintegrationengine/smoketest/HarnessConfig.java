// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.time.Duration;

/**
 * System properties the harness is driven by. ci/run-harness.sh sets these inside the
 * harness container; a developer running from an IDE or from
 * {@code ./gradlew :smoketest:test} passes them with {@code -Doie.*}.
 */
final class HarnessConfig {

    /** Base URL of the server under test, e.g. {@code https://oie:8443}. Required. */
    static final String BASE_URL = require("oie.baseUrl");

    static final String USERNAME = System.getProperty("oie.username", "admin");

    /**
     * Password of {@link #USERNAME}. Required: a server generates a random admin password on
     * first boot unless {@code server.initialadminpassword} was set before it, so there is no
     * default worth guessing.
     */
    static final String PASSWORD = require("oie.password");

    /**
     * Name of the compose configuration being exercised, used to honour each test
     * directory's optional {@code configurations} file. Empty means "unfiltered": every
     * discovered test runs regardless of what it declares. That is the useful default for
     * a developer pointing the harness at a server by hand.
     */
    static final String CONFIGURATION = System.getProperty("oie.configuration", "");

    /** Ceiling on waiting for a channel to start or a message to reach its asserted state. */
    static final Duration TIMEOUT =
            Duration.ofSeconds(Long.parseLong(System.getProperty("oie.timeoutSeconds", "90")));

    /**
     * Per-request socket timeout. {@code new Client(address)} defaults to an infinite
     * timeout, which would let a wedged server hang CI instead of failing it.
     */
    static final int REQUEST_TIMEOUT_MILLIS =
            Integer.parseInt(System.getProperty("oie.requestTimeoutMillis", "15000"));

    private HarnessConfig() {
    }

    private static String require(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required system property " + key + " is not set");
        }
        return value;
    }
}
