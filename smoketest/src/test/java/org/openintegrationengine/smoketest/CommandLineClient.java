// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the packaged command-line client ({@code mirth-cli-launcher.jar}) as a child
 * process.
 *
 * <p>The launcher resolves {@code cli-lib/} and {@code ./extensions} relative to its
 * working directory and reads its log configuration from {@code conf/} via its manifest
 * {@code Class-Path}, so it only runs from a staged distribution.
 */
final class CommandLineClient {

    /** Distribution root; ci/run-harness.sh points this at the tree in the harness image. */
    private static final Path HOME = Path.of(System.getProperty("oie.cliHome", "/opt/engine"));

    private static final long TIMEOUT_SECONDS = HarnessConfig.TIMEOUT.toSeconds();

    private CommandLineClient() {
    }

    /** Runs the CLI against the server under test, feeding it {@code statements} as a script. */
    static Result runScript(String... statements) throws Exception {
        return runScriptAgainst(HarnessConfig.BASE_URL, HarnessConfig.USERNAME, HarnessConfig.PASSWORD,
                statements);
    }

    /** As {@link #runScript}, against an address and credentials of the caller's choosing. */
    static Result runScriptAgainst(String address, String user, String password, String... statements)
            throws Exception {
        Path script = Files.createTempFile("oie-cli-", ".script");
        try {
            Files.writeString(script, String.join("\n", statements) + "\n", StandardCharsets.UTF_8);
            return run("-a", address, "-u", user, "-p", password, "-s", script.toString());
        } finally {
            Files.deleteIfExists(script);
        }
    }

    /** Runs the CLI with exactly {@code args}. */
    static Result run(String... args) throws Exception {
        Path launcher = HOME.resolve("mirth-cli-launcher.jar");
        if (!Files.isRegularFile(launcher) || !Files.isRegularFile(HOME.resolve("cli-lib/mirth-cli.jar"))) {
            throw new AssertionError("No command-line client staged at " + HOME
                    + "; the harness image is built to carry one (see the Dockerfile"
                    + " smoketest-harness target) and ci/run-harness.sh sets oie.cliHome.");
        }

        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-jar");
        command.add(launcher.getFileName().toString());
        command.addAll(Arrays.asList(args));

        // A file rather than a pipe: reading a pipe would block forever on a CLI that
        // never exits, so the timeout below could never fire.
        Path outputFile = Files.createTempFile("oie-cli-", ".out");
        try {
            Process process = new ProcessBuilder(command)
                    .directory(HOME.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();

            // Without -s the CLI reads stdin; close it so such a run cannot wait forever.
            process.getOutputStream().close();

            boolean exited;
            try {
                exited = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } finally {
                process.destroyForcibly();
            }

            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            if (!exited) {
                throw new AssertionError("The CLI did not exit within " + TIMEOUT_SECONDS + "s: "
                        + String.join(" ", command) + "\n--- output ---\n" + output + "--- end output ---");
            }
            return new Result(command, process.exitValue(), output);
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    record Result(List<String> command, int exitCode, String output) {

        @Override
        public String toString() {
            return "exit=" + exitCode + " from " + String.join(" ", command)
                    + "\n--- output ---\n" + output + "--- end output ---";
        }
    }
}
