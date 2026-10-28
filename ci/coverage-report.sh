#!/usr/bin/env bash
# Merges unit-test and smoke-test JaCoCo data into one report.
#
# <artifacts> holds what the CI build uploads: openintegrationengine.tar.gz
# (the distribution the smoke tests ran), coverage-unit/ (unit-test data,
# jacococli.jar) and coverage-smoke-*/ (one data file per configuration).
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <artifacts> <output>" >&2
    exit 2
fi

artifacts="$(realpath "$1")"
mkdir -p "$2"
output="$(realpath "$2")"
cd "$(dirname "${BASH_SOURCE[0]}")/.."
cli="$artifacts/coverage-unit/jacococli.jar"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
tar xzf "$artifacts/openintegrationengine.tar.gz" -C "$tmp"
dist="$tmp/openintegrationengine"

shopt -s nullglob
java -jar "$cli" merge \
    "$artifacts"/coverage-unit/unit/*.exec \
    "$artifacts"/coverage-smoke-*/*.exec \
    --destfile "$output/merged.exec"

# Only our own jars: third-party jars would add their classes to the report,
# and some of them hold originals of classes we patch in mirth-server.jar.
classfiles=()
for jar in "$dist"/mirth-*-launcher.jar \
    "$dist"/server-lib/mirth-*.jar \
    "$dist"/server-lib/donkey/donkey-*.jar \
    "$dist"/client-lib/mirth-client.jar \
    "$dist"/cli-lib/mirth-cli.jar \
    "$dist"/extensions/*/*.jar; do
    classfiles+=(--classfiles "$jar")
done

sourcefiles=()
for dir in donkey server client command; do
    sourcefiles+=(--sourcefiles "$dir/src/main/java")
done

java -jar "$cli" report "$output/merged.exec" \
    "${classfiles[@]}" "${sourcefiles[@]}" \
    --name "Open Integration Engine (unit + smoke tests)" \
    --xml "$output/coverage.xml" \
    --html "$output/html"
