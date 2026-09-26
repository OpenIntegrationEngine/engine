// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests for the packaged command-line client, run as a child process against the
 * live server.
 *
 * <p>Assertions are on output rather than on the exit code: the CLI exits 0 whether or
 * not it could log in or run the statements it was given.
 */
@DisplayName("Command-line client")
class CommandLineInterfaceTest {

    @Test
    @DisplayName("prints usage for -h without contacting a server")
    void printsUsageForHelp() throws Exception {
        CommandLineClient.Result result = CommandLineClient.run("-h");

        assertEquals(0, result.exitCode(), () -> "-h should exit 0, got " + result);
        assertTrue(result.output().contains("usage: Shell"), () -> "no usage text in " + result);

        for (String option : List.of("-a <address>", "-u <user>", "-p <password>",
                "-s <script>", "-c <config file>", "-trust <trust>")) {
            assertTrue(result.output().contains(option),
                    () -> "usage does not offer " + option + " in " + result);
        }
    }

    @Test
    @DisplayName("logs in to the live server and reports its version")
    void logsInAndReportsServerVersion() throws Exception {
        // Read over the API, so a match proves the CLI reached this server rather than
        // echoing back the address it was given.
        String version = SharedServer.get().version();

        CommandLineClient.Result result = CommandLineClient.runScript("status");

        assertTrue(result.output().contains("Server @ " + HarnessConfig.BASE_URL + " (" + version + ")"),
                () -> "the CLI did not report a connection to " + HarnessConfig.BASE_URL
                        + " running " + version + ": " + result);
        // Printed only after a successful logout.
        assertTrue(result.output().contains("Disconnected from server."),
                () -> "the CLI did not disconnect cleanly: " + result);
    }

    @Test
    @DisplayName("exits instead of hanging when the server is unreachable")
    void exitsWhenServerIsUnreachable() throws Exception {
        // The client's connection monitor is a non-daemon thread: a CLI that leaves it
        // running never exits, and this fails on the timeout rather than an assertion.
        CommandLineClient.Result result = CommandLineClient.runScriptAgainst(
                "https://127.0.0.1:1", HarnessConfig.PINNED_CLIENT_TRUST, "status");

        assertFalse(result.output().contains("Server @ "),
                () -> "the CLI reported a connection it could not have made: " + result);
        assertTrue(result.output().contains("ClientException"),
                () -> "the CLI did not report why it could not connect: " + result);
    }

    @Test
    @DisplayName("connects when the server's own certificate is pinned with -trust")
    void connectsWithPinnedCertificate() throws Exception {
        // No pki, no localhost, and a hostname the certificate cannot match: the pin is
        // the only thing that can make this connect.
        String thumbprint = ServerCertificate.discoverThumbprint(
                HarnessConfig.BASE_URL, HarnessConfig.REQUEST_TIMEOUT_MILLIS);

        CommandLineClient.Result result = CommandLineClient.runScriptAgainst(
                HarnessConfig.BASE_URL, thumbprint, "status");

        assertTrue(result.output().contains("Server @ " + HarnessConfig.BASE_URL),
                () -> "pinning the server's certificate did not let the CLI connect: " + result);
        assertFalse(result.output().contains("Error:"), () -> "a statement failed: " + result);
    }

    @Test
    @DisplayName("runs read-only commands against the live server")
    void runsReadOnlyCommands() throws Exception {
        CommandLineClient.Result result =
                CommandLineClient.runScript("status", "channel list", "user list");

        assertTrue(result.output().contains("Server @ " + HarnessConfig.BASE_URL),
                () -> "the CLI never reported a connection: " + result);

        assertTrue(result.output().contains("Status\t\tName"),
                () -> "no status listing: " + result);
        assertTrue(result.output().contains("Enabled\t\tName"),
                () -> "no channel listing: " + result);
        assertTrue(result.output().contains("User Name"),
                () -> "no user listing: " + result);
        assertTrue(result.output().contains(HarnessConfig.USERNAME),
                () -> "user listing omits " + HarnessConfig.USERNAME + ": " + result);

        // A failed statement prints "Error: ..." and still exits 0.
        assertFalse(result.output().contains("Error:"),
                () -> "a statement failed: " + result);
    }
}
