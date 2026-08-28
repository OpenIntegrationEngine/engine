#!/usr/bin/env bash
# Runs one smoke-test configuration using existing server and harness images.
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <configuration.compose.yml> <server-image>" >&2
    exit 2
fi

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ ! -f "$1" ]]; then
    echo "No such configuration: $1" >&2
    exit 2
fi
export OIE_IMAGE="$2"
export OIE_CONFIGURATION="$(basename "$1" .compose.yml)"
# The engine generates a random admin password on first boot, so the stack and the
# harness have to agree on one up front. See ci/harness.compose.yml.
export OIE_ADMIN_PASSWORD="${OIE_ADMIN_PASSWORD:-ci-smoke-admin}"
export WORKSPACE="$PWD"
export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"
mkdir -p "$WORKSPACE/ci/test-results"

compose=(docker compose -f "$1" -f ci/harness.compose.yml -p "oie-ci-${OIE_CONFIGURATION//[^a-z0-9-]/-}-$$")

cleanup() {
    "${compose[@]}" down -v --remove-orphans || true
}
trap cleanup EXIT

echo "==> $OIE_CONFIGURATION: starting stack (image $OIE_IMAGE)"
if ! "${compose[@]}" up -d --wait oie; then
    "${compose[@]}" logs --no-color oie >&2 || true
    exit 1
fi

echo "==> $OIE_CONFIGURATION: running the harness"
"${compose[@]}" run --rm harness