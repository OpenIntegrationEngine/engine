<!--
Thanks for contributing! See CONTRIBUTING.md. Keep this PR to one concern; open large or
sweeping changes as an issue first so scope and a verification plan can be agreed.
-->

## What & why
<!-- What does this change do, and why? -->

Closes #<!-- issue number (required; no PR without a referenced issue) -->

## How to verify
<!--
REQUIRED for any behavior change. Give a reviewer a runnable before/after — not just "tests pass."
Replacing the battery isn't the job; starting the car is.

- Exact steps / input to reproduce.
- Observable result BEFORE this change (the bug) vs AFTER (fixed).
- For behavior a unit test can't capture (wire output, a deployed channel, a REST path),
  the exact commands and what you observed.

Docs-only or non-behavioral change? Write "N/A — no behavior change" and why.
-->

## Checklist
- [ ] Scoped to one concern; no drive-by reformatting/renames
- [ ] Tests added/adjusted — a regression test **fails before** the change and **passes after** (line coverage is not proof)
- [ ] `./gradlew build -PdisableSigning=true -Pcoverage=true` passes locally
- [ ] Behavior & compatibility preserved (public API, serialization, DB schema, wire/HL7) — or the change is intentional and called out above
- [ ] No PHI in logs; no new unsafe/reflective deserialization or XXE
