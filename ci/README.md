# CI Smoke Tests

The CI smoke tests exercise the Docker image against each supported runtime and
database configuration. They deploy exported channels, submit messages through the
OIE client API, and assert the resulting source and destination messages.

Most tests are filesystem fixtures. Use a Java test when the scenario needs logic
that cannot be expressed as input data and expected results.

## What Is Tested

Every configuration in `ci/configurations/` runs the applicable test cases in
`ci/tests/`.

A fixture test case can contain one or more channels. Each channel is deployed from
its exported `channel.xml`; its message fixtures are then run in lexicographic order.
The test case, channel, and message directory names appear in the test report, so
use names that describe the behavior being checked.

## Add a Fixture Test

Create a directory under `ci/tests/` using this structure:

```text
ci/tests/
  200-my-feature/
    configurations                 # optional
    channels/
      01-my-channel/
        channel.xml
        messages/
          01-happy-path/
            source
            dest01
```

`channel.xml` is an exported OIE channel. Every message fixture requires `source`,
the payload sent to the channel. The other files are optional assertions:

| File | Assertion |
| --- | --- |
| `source_sourcemap.yml` | Source map supplied with `source` |
| `source_metadata.yml` | Selected source message metadata |
| `source_status` | Source status |
| `source_response` | Source response payload |
| `source_transformed` | Transformed source payload |
| `destNN` | Sent payload for destination `NN` |
| `destNN_transformed` | Transformed payload for destination `NN` |
| `destNN_response` | Response payload from destination `NN` |
| `destNN_metadata.yml` | Selected metadata for destination `NN` |
| `destNN_status` | Status for destination `NN` |

`NN` is the destination connector's metadata ID: `dest01` is the first
destination, `dest02` the second, and so on. Metadata files are YAML mappings; list
only the keys that matter to the test. Content assertions are byte-for-byte, except
that `((ANY))` matches variable content such as generated IDs or timestamps.

A fixture case runs in every configuration by default. To limit it, add a
`configurations` file at the case root with one configuration name per line:

```text
alpine-temurin21-postgres
ubuntu-temurin21-postgres
```

## Add a Configuration

Add a Compose file at `ci/configurations/<name>.compose.yml`. Configuration names
normally follow `<os>-<jvm>-<database>`, such as
`alpine-temurin21-postgres`.

The Compose file must:

- define the OIE service as `oie`;
- use `${OIE_IMAGE}` for that service's image;
- provide Docker healthchecks for OIE and any database dependency.

The new configuration will run all fixture cases unless their `configurations` file
excludes it. Add an entry there when a fixture depends on a specific database,
operating system, or JVM behavior.

## Add a Java Test

Put hand-written JUnit 5 tests in
`smoketest/src/test/java/org/openintegrationengine/smoketest/`. Use the existing
`Harness` and `OieServer` helpers to work with the live server. Java tests run with
the fixture tests and are appropriate for multi-step workflows, computed
expectations, or assertions that do not fit the fixture files.

## Run Locally

Run every configuration:

```sh
ci/runtests.sh
```

Run one configuration:

```sh
ci/runtests.sh alpine-temurin21-derby
```

To iterate against an already-running server, pass that server's admin password.
`ci/runtests.sh` seeds its own stacks with `server.initialadminpassword`; a server you
started yourself logged a generated password on its first boot:

```sh
./gradlew :smoketest:test -Doie.baseUrl=https://localhost:8443 -Doie.password=...
```

JUnit XML results are written to `ci/test-results/<configuration>/` when using
`ci/runtests.sh`.
