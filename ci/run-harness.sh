#!/usr/bin/env bash
# Entrypoint of the harness container (see Dockerfile smoketest-harness target)
set -euo pipefail

ENGINE_HOME="${ENGINE_HOME:-/opt/engine}"
HARNESS_DIR="${HARNESS_DIR:-/harness}"
RESULTS_DIR="${RESULTS_DIR:-/results}"

# Build our classpath from the harness and engine jars, sorted so that the engine jars are first.
classpath=$(find "$ENGINE_HOME/server-lib" "$ENGINE_HOME/extensions" "$HARNESS_DIR" \
    -name '*.jar' -type f | sort | paste -sd: -)

results="$RESULTS_DIR/$OIE_CONFIGURATION"
mkdir -p "$results"

# Lock engine to junit-jupiter to prevent false-pass results.
status=0
java \
    -Doie.baseUrl="$OIE_BASE_URL" \
    -Doie.configuration="$OIE_CONFIGURATION" \
    -Doie.password="$OIE_PASSWORD" \
    ${OIE_HARNESS_OPTS:-} \
    -cp "$classpath" \
    org.junit.platform.console.ConsoleLauncher execute \
    --select-package=org.openintegrationengine.smoketest \
    --include-engine=junit-jupiter \
    --details=tree \
    --disable-ansi-colors \
    --fail-if-no-tests \
    --reports-dir="$results" || status=$?

if ! compgen -G "$results/*.xml" > /dev/null; then
    echo "The harness produced no report in $results" >&2
    exit 1
fi

exit "$status"
