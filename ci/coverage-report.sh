#!/usr/bin/env bash
# Merges unit-test and smoke-test JaCoCo data into build/coverage/report,
# which `./gradlew sonar` picks up. Expects, from the same commit:
#   ./gradlew build stageCoverage -Pcoverage=true    (server/setup, build/coverage)
#   JACOCO_AGENT=build/coverage/jacocoagent.jar ci/runtests.sh <configuration>
#                                                    (ci/coverage/*.exec)
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
cli=build/coverage/jacococli.jar
dist=server/setup
output=build/coverage/report
mkdir -p "$output"

shopt -s nullglob
unit=(build/coverage/unit/*.exec)
smoke=(ci/coverage/*.exec)
echo "Merging ${#unit[@]} unit-test and ${#smoke[@]} smoke-test data files"
if [[ ${#unit[@]} -eq 0 || ${#smoke[@]} -eq 0 ]]; then
    echo "Missing unit-test or smoke-test coverage data" >&2
    exit 1
fi
java -jar "$cli" merge "${unit[@]}" "${smoke[@]}" --destfile "$output/merged.exec"

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
if [[ ${#classfiles[@]} -eq 0 ]]; then
    echo "No jars found in the distribution" >&2
    exit 1
fi

sourcefiles=()
for dir in donkey server client command; do
    sourcefiles+=(--sourcefiles "$dir/src/main/java")
done

java -jar "$cli" report "$output/merged.exec" \
    "${classfiles[@]}" "${sourcefiles[@]}" \
    --name "Open Integration Engine (unit + smoke tests)" \
    --xml "$output/coverage.xml" \
    --html "$output/html"
