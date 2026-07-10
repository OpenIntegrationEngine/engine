# AGENTS.md — Open Integration Engine (engine)

Guidance for AI coding agents working on **this repository — the OIE engine itself**. OIE is an open-source
fork of Mirth Connect: a **production-critical, healthcare** integration engine (HL7 / FHIR / DICOM) that
runs in hospitals and labs, on a mature codebase with ~15 years of history. Licensed **MPL-2.0**.

> **Writing channel/template JavaScript that runs *on* the engine (transformers, filters, code
> templates)?** That's a different context with its own Rhino/ES5 rules — start from
> **[`AGENTS_USER_EXAMPLE.md`](./AGENTS_USER_EXAMPLE.md)**, not this file.

## Prime directive: be a careful guest, not a rewriter
Optimize for the reviewer's trust and the project's long-term health — **never for volume of change.** A
tiny, correct, well-explained fix tied to an issue is a *success*; a large, plausible-looking, unrequested
change is a *cost* the maintainers must now audit in a healthcare setting. The failure mode this file
prevents: confident, sprawling, style-driven AI changes ("vibeslop") that look fine, pass a glance, and
quietly break behavior, compatibility, or trust. When a change can't be kept small, that's a signal to
**stop and discuss on the issue** — not to proceed. (There is deliberately no orchestration/automation skill
here: the project does not want large multi-file, single-PR agent changes.)

**Green light:** if the change is issue-scoped, small, and test-backed — the thing you were asked to do —
just do it well. You don't need to re-ask permission for what's already agreed on the issue. Escalate only
on the triggers below.

## Before you edit: state your read (and push back)
The most valuable thing you can do here is **refuse to dutifully implement a weak fix.** The moment someone
thinks *"I know how to fix this"* is exactly when a cleaner, safer, more idiomatic solution gets missed.

- **For anything beyond a trivial one-liner, before editing code, first state:** (a) the change you'd make,
  (b) whether you disagree with the requested approach and the **concrete** cost if you do — a *specific*
  broken caller, a named compat/security surface, a symptom-vs-root-cause miss (never a hypothetical), and
  (c) any better alternative with tradeoffs. Don't open code edits in the same turn you receive a non-trivial task.
- **Calibrate to blast radius.** A typo or one-line fix needs no options memo — make it well and say nothing.
  Pushback theater (manufacturing objections on trivial work) wastes the reviewer trust this protects. If you
  have no concrete objection, make the small change and move on.
- You are **advising skeptical maintainers, not obeying** them. They make the final call — take disagreement
  to the issue — but silent compliance with a mediocre or risky fix is a failure. Don't flatter, don't pad.

## Hard rules (non-negotiable)
1. **Issue first — and wait.** Per [`CONTRIBUTING.md`](./CONTRIBUTING.md), open/claim an issue *and get a
   maintainer's triage or reply* before writing non-trivial code. Opening an issue then immediately coding
   the whole change defeats the point. A tiny obvious fix: say so on the issue and keep the diff tiny. No PR
   without a referenced issue.
2. **One concern; minimal diff; bounded in breadth *and* depth.** No drive-by edits (reformatting,
   re-indenting, import reordering, renames in code you aren't otherwise changing). **Breadth:** touch **≤3
   files**; more → stop and discuss. **Depth:** if the fix means rewriting a whole method/class, or a single
   file's diff exceeds ~40–50 changed lines, that's sprawl too — stop and discuss. A big single-file rewrite
   is still slop.
3. **Match the surrounding code.** There's no autoformatter to hide behind — mirror the style/naming/idioms
   of the file. Introduce no new patterns, libraries, frameworks, build tools, or dependencies without
   explicit maintainer approval on the issue.
4. **Preserve behavior & compatibility.** Don't change public APIs, serialization formats, DB
   schema/migrations, wire/protocol/HL7 behavior, extension-point signatures, or config compatibility without
   maintainer sign-off — existing deployments upgrade in place. **Watch the subtle breaks that don't look like
   API changes:** default charset, timezone/locale handling, null-vs-empty, HL7 delimiter/escape/segment
   order, connection-pool defaults, XStream alias maps. For any change under `donkey/` or in a
   serialize/parse path, include a **before/after golden-output test** proving behavior is unchanged (or the
   change is the point and is signed off).
5. **Tests are load-bearing and must bite.** Add/adjust tests for any behavior change; a regression test must
   **fail against the pre-fix code and pass after** — state that you verified this (line coverage is not
   proof). **Never delete, skip, or weaken a test to go green.** The build (below) must pass before a PR.
6. **Security — this engine carries PHI.**
   - **Never log message content, payloads, headers, or anything that could be PHI**; add no new logging of
     message bodies or connector I/O; serialize no new fields into audit/event tables without sign-off.
   - **No reflective/polymorphic deserialization** (XStream, Java `ObjectInputStream`) without an allowlist;
     **XML parsers must disable DTDs/external entities (XXE).** This engine has a documented RCE history in
     exactly these paths — don't reopen it.
   - Be careful with SQL, crypto, auth, and file/network I/O. Suspected vulnerabilities go through
     [`SECURITY.md`](./SECURITY.md), not a silent patch-and-PR.
7. **License headers:** copy the existing header **verbatim** from [`server/license-header.txt`](./server/license-header.txt)
   onto any new file (it reads "Copyright (c) Mirth Corporation …" — do **not** invent your own MPL text).
   Contributions are licensed MPL-2.0.
8. **Don't invent.** Verify classes, methods, and config keys against the actual code before using them; if
   unsure whether something exists or is safe, say so — don't guess.
9. **No slop tells.** No emoji; no "as an AI"; no comments that restate the code or narrate the change; no
   speculative abstractions or "future-proofing" (YAGNI); no dead code; no reflowing of existing comments.

## Build & test (Java 17 + Ant)
- Toolchain is pinned in [`.sdkmanrc`](./.sdkmanrc) — install [SDKMAN](https://sdkman.io/) and run
  `sdk env install` in the repo root (the JavaFX-bundled JDK is required; the `client` GUI imports JavaFX).
- **Build + test** (run this locally; it's also the CI build on pull requests):
  `cd server && ant -f mirth-build.xml -DdisableSigning=true -Dcoverage=true`. CI runs this unsigned +
  coverage build on PRs; on `main` it runs the **signed** build (same target, without those flags). JUnit
  results land under `*/build/test-results/**/*.xml`. **A red build is not reviewable — never open a PR on one.**

## Module map (change the narrowest one that solves the issue)
| Module | What it is | Care level |
|---|---|---|
| `donkey/` | Core message-processing engine (queues, channels, message lifecycle). | **Highest** — the heart; small, golden-tested changes only. |
| `server/` | Server runtime, connectors, REST APIs, plugins, DAO/DB. | High — public API & compatibility surface. |
| `client/` | Swing **Administrator** GUI (pulls in JavaFX). | Medium. |
| `command/` | Mirth CLI client. | Medium. |
| `generator/` | Code generation used by the build. | Medium — build-affecting. |
| `tools/` | Assets/support files. | Low. |

## Contribution flow
Follow [`CONTRIBUTING.md`](./CONTRIBUTING.md) (issue → fork → `feature/<name>` branch → **draft** PR to
`main`, marked "Ready for review" only when truly done). Beyond what it documents: reviewers are
`@openintegrationengine/maintainers` (see `.github/CODEOWNERS`); project governance lives in the
`OpenIntegrationEngine/governance` repo.

## Enforcement note
This file is **advisory** context, not an enforced gate. The load-bearing rules should be machine-checked in
CI / the PR template rather than left to good faith — a linked-issue requirement, a license-header check, a
"no new dependency / build-file change without a label" diff check, rejecting whitespace-only or
import-order-only hunks, and an emoji/AI-tell grep. If those checks aren't wired yet, adding them is a good
first contribution.

> **Maintaining this file:** keep it lean — if a rule doesn't change agent behavior on most tasks, cut it;
> push sometimes-needed detail to a linked file. It works only if contributors actually read it.
