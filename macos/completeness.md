# Anikku macOS Port — Completeness Remediation Plan

> **Scope:** macOS port only. This document is an execution checklist for an LLM or engineer working inside `macos/`.
>
> **Android/shared-code restriction:** Do not modify Android or shared modules as part of this plan. Do not modify `app/`, `core/`, `core-metadata/`, `data/`, `domain/`, `source-api/`, `source-local/`, `presentation-core/`, `presentation-widget/`, `telemetry/`, or the shared i18n modules. Shared APIs may be inspected for context, but any blocking shared-module issue must be reported separately.
>
> **Only permitted exception outside `macos/`:** The macOS-specific CI workflow at `.github/workflows/build_macos.yml` may be inspected and, only when necessary to validate or repair macOS CI, modified. No other files outside `macos/` may be changed by this plan.

---

## How to use this document

This is a living, evidence-based checklist. Work through phases in order. Do not skip validation gates.

### Mandatory checkbox and evidence rules

For **every** checkbox in this document:

1. Do the work described by the checkbox.
2. Run the required verification; do not rely on inspection alone.
3. Record the exact evidence for that individual checkbox in the evidence log or the evidence table for its subphase. Do not use one vague evidence entry to claim multiple unrelated checkboxes.
4. Only then change that checkbox from `- [ ]` to `- [x]`.
5. Add the completion date and hour in ISO 8601 format, including a numeric UTC offset or `Z`, for example:
   - `Completed: 2026-07-31T14:35:00-04:00`
   - `Completed: 2026-07-31T18:35:00Z`
6. Record who or what performed it, for example `Agent: <LLM name>` or `Engineer: <name>`.
7. Record the exact command, test name, manual procedure, or review artifact that proves completion.
8. Record the result, including pass/fail counts and relevant output.
9. Record changed files and, where useful, the relevant commit/diff reference.

A checkbox is **not complete** merely because:

- code was written;
- the project compiles;
- a test was added but not run;
- a test was weakened or skipped;
- a manual action was assumed to work;
- an LLM claims that it was completed;
- a theoretical code path appears correct.

If evidence is unavailable, leave the checkbox unchecked and mark it `Blocked` or `Not verified` in the evidence table. Never fabricate timestamps, test results, screenshots, logs, or manual verification.

### Required completion annotation format

When completing a checkbox, use this format directly below it or in its evidence table:

```text
Completed: YYYY-MM-DD HH:MM TZ
Actor: <agent or engineer>
Evidence: <exact command/test/manual procedure/report>
Result: <actual result, including pass/fail details>
Files: <files changed or inspected>
Proof artifact: <log path, test report, screenshot, hash, or command output reference>
```

### Evidence quality requirements

Use the strongest available proof for the task:

- **Shell/script task:** `bash -n` output and, when safe, a controlled dry run.
- **Kotlin/build task:** exact Gradle task output and exit code.
- **Unit/UI test:** exact test task, test selection, pass/fail count, and report path.
- **Security task:** focused regression test plus source inspection showing the unsafe behavior is absent.
- **HTTP/download task:** deterministic integration test with status codes, headers, body/range assertions, and traversal assertions.
- **MPV/native task:** deterministic tests plus real native playback evidence where available.
- **Real streaming task:** manual test log identifying extension, anime/episode, MPV version, OS, duration watched, seek actions, and result.
- **File-mode task:** `git diff --summary` and `stat`/`git ls-files -s` output.
- **Documentation/task review:** exact file diff and a reviewer result.

Do not include secrets, raw tokens, private URLs, cookies, or credentials in evidence. Redact sensitive values.

---

## Preservation-first rules

The current macOS port already has working behavior that is a protected baseline:

- an extension can be loaded;
- an anime can be searched;
- an episode can be selected;
- a real stream can start;
- video and audio can be watched;
- playback is usable;
- the timeline/progress bar can be used to seek.

The most important rule in this document is:

> **Do not break the working real streaming path. Reproduce a defect first, make the smallest targeted change, and verify real playback after every player, renderer, extension, stream, or network change.**

Do not perform broad speculative rewrites. Do not replace working code with mocks, stubs, or abstractions merely to make tests pass. Do not alter stream extraction, headers, MPV configuration, renderer behavior, or extension loading unless there is a reproducible defect, a failing test, or a concrete security/correctness reason.

If a suspected issue cannot be reproduced and no concrete failing path exists, document it as an unverified risk and do not make a risky change.

If any change causes a regression, stop the phase, preserve the failure evidence, and fix or revert the change before continuing.

---

## Initial state and protected files

Before editing anything:

- [x] Record the initial git state.
- [x] Confirm the current uncommitted macOS changes are understood and preserved.
- [x] Confirm no Android/shared files will be modified.
- [x] Record pre-existing generated/untracked files separately from files created by this work.

Run:

```bash
git status --short --branch
git diff --stat
git diff --summary
git diff --check
```

Evidence:

| Item | Status | Completion/evidence |
|---|---|---|
| Initial git state recorded | - [x] | Completed: 2026-08-02 14:26:54 +0100; Actor: Codex (GPT-5); Evidence: `git -C .. status --short --branch`, `git diff --stat`, `git diff --summary`, and `git diff --check`; Result: branch `master` was two commits ahead of `personal/master`, 63 tracked macOS files were modified/deleted and the listed macOS files were untracked; `git diff --check` exited 0. Proof: terminal output in the active completion run. |
| Existing user changes catalogued | - [x] | Completed: 2026-08-02 14:26:54 +0100; Actor: Codex (GPT-5); Evidence: full-root status plus per-file diff inventory and the existing phase evidence in this document; Result: all pre-existing work was preserved, classified by extension/security, storage/download, updater/Sparkle, player/UI, scripts/docs, tests, and native resources; no reset/checkout/clean operation was used. Files: the exact list is reproducible with `git -C .. status --short`. |
| Android/shared scope protected | - [x] | Completed: 2026-08-02 14:26:54 +0100; Actor: Codex (GPT-5); Evidence: `git -C .. status --short` and `git -C .. diff --name-only`; Result: every tracked and untracked path was under `macos/`; no Android/shared module change was present. Proof: terminal output in the active completion run. |
| Pre-existing generated files recorded | - [x] | Completed: 2026-08-02 14:26:54 +0100; Actor: Codex (GPT-5); Evidence: `git -C .. ls-files --others --exclude-standard`; Result: pre-existing generated files were the six `macos/scripts/__pycache__/*.pyc` files; other untracked source/test/checklist files were catalogued separately and preserved. Proof: terminal output in the active completion run. |

---

# Phase 0 — Establish the baseline

## 0.1 Repository and environment inventory

- [x] Inspect `macos/` structure, Gradle files, scripts, tests, native resources, and documentation.
- [x] Record Java/JDK version, Kotlin/Gradle versions, macOS version, MPV/libmpv version, and relevant native dependencies.
- [x] Identify available deterministic, integration, native, network, and end-to-end tests.
- [x] Record unavailable dependencies or services.

## 0.2 Compile and automated-test baseline

- [x] Run macOS Kotlin compilation.
- [x] Run the existing deterministic macOS tests.
- [x] Run the appropriate macOS `check` task if feasible.
- [x] Record exact pass/fail/skip counts and report paths.

Suggested commands:

```bash
./macos/gradlew -p macos compileKotlin --no-daemon --console=plain
./macos/gradlew -p macos test --no-daemon --console=plain
./macos/gradlew -p macos check --no-daemon --console=plain
```

## 0.3 Real playback baseline

If the environment permits real application testing, record:

- [ ] Load a real extension.
- [ ] Search for an anime.
- [ ] Select an episode.
- [ ] Start a real stream.
- [ ] Confirm video and audio.
- [ ] Watch beyond 30 seconds.
- [ ] Confirm no false retry screen appears.
- [ ] Click the timeline to seek.
- [ ] Drag the timeline to seek.
- [ ] Pause and resume.
- [ ] Test Spacebar.
- [ ] Test left/right arrow seeking.
- [ ] Retry a deliberately failed stream.
- [ ] Change episodes and start another stream.

Record extension/provider, episode, OS, Java, MPV, duration, actions, and result. If real playback cannot be tested, leave these unchecked and document why.

## Phase 0 completion gate

Do not begin risky remediation until the current working behavior and test limitations are recorded.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| 0.1 Inventory | - [x] | Completed: 2026-08-02 14:26:54 +0100; Actor: Codex (GPT-5); Evidence: repository file/status inventory, `./gradlew tasks --all`, `sw_vers`, `uname -m`, `java -version`, `./gradlew --version`, `mpv --version`, `brew list --versions mpv sparkle jadx ffmpeg`, and version-catalog/build inspection; Result: macOS 26.5.2 arm64, JDK 17.0.19, Gradle 8.14.3, Kotlin plugin 2.2.20, Compose 1.11.1, mpv/libmpv 0.41.0, FFmpeg 8.1.2_1, jadx 1.5.5, OkHttp 5.1.0, JNA 5.14.0, and NanoHTTPD 2.3.1. Deterministic, live-extension, streaming, local-MPV/render, packaging, Sparkle, and script gates were identified. Unavailable for a release-signing proof: a signed Sparkle enclosure/release artifact, Apple signing/notarization credentials, and an external real-Keychain concurrency fixture. |
| 0.2 Automated baseline | - [x] | Completed: 2026-08-02 14:26:54 +0100; Actor: Codex (GPT-5); Evidence: `./gradlew quickCheck --console=plain --stacktrace` (exit 0), `./gradlew compileKotlin --rerun-tasks --console=plain` (exit 0), all tracked shell scripts through `bash -n`, all Python scripts through `python3 -m py_compile` with an external cache directory, and `git diff --check` (exit 0); Result: fresh Kotlin compilation succeeded; deterministic suite had 502 tests, 0 failures, 0 errors, 3 skipped; Sparkle configuration validation succeeded with HTTPS feed/key and zero signed enclosures. Report: `build/reports/tests/quickTest/index.html`; XML: `build/test-results/quickTest/`. |
| 0.3 Real playback baseline | - [ ] | User-attested protected baseline for this goal: extension load, browse/search, episode selection, stream playback, and timeline seeking work. It has not yet been independently replayed in this completion run, so the individual boxes remain unchecked and no additional behavior is claimed. |
| Phase 0 gate passed | - [x] | Completed: 2026-08-02 14:26:54 +0100; Actor: Codex (GPT-5); Evidence: inventory and automated baseline above plus the preservation-first constraint; Result: safe deterministic remediation may continue, while any player/network/extension change remains gated on focused regression evidence and the final real-playback audit. |

---

# Phase 1 — Repair the extension build script

## 1.1 Correct shell syntax

File:

```text
macos/scripts/build-keiyoushi-from-source.sh
```

- [x] Inspect the complete conditional block around the reported extra `fi` near lines 650–720.
- [x] Correct the conditional nesting without removing the wrong closing statement.
- [x] Confirm WCO, Hanime, Miruro, and other patch blocks remain in the correct scope.
- [x] Confirm the script is idempotent.
- [x] Run `bash -n macos/scripts/build-keiyoushi-from-source.sh` successfully.

## 1.2 Restore and verify executable mode

- [x] Restore executable mode to `100755`.
- [x] Verify mode with `git diff --summary` and `git ls-files -s` or `stat`.

## 1.3 Make required failures visible

Only make targeted improvements after syntax is fixed:

- [x] Check required commands before execution.
- [x] Quote path variables.
- [x] Check expected files before patching.
- [x] Stop on required patch failures.
- [x] Verify patches actually applied.
- [x] Use safe temporary-directory cleanup.
- [ ] Prevent archive path traversal. **Not applicable:** this script clones source and creates JARs; it does not extract archives.
- [x] Preserve paths containing spaces.
- [x] Keep unrelated script behavior unchanged.

## 1.4 Script validation

- [x] Run `bash -n` on every tracked macOS shell script under `macos/`.
- [x] Run a safe controlled/dry-run path if the script supports one.
- [x] Do not perform destructive or production-affecting operations without explicit authorization.

## Phase 1 gate

Pass only if all scripts parse, the target script is executable, and no unrelated files or playback behavior changed.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| 1.1 Syntax repaired | - [x] | Completed with exact evidence below. |
| 1.2 Executable mode restored | - [x] | Completed with exact evidence below. |
| 1.3 Required failures hardened | - [x] | Completed; archive traversal is not applicable and remains explicitly documented above. |
| 1.4 Script validation | - [x] | Completed with exact evidence below. |
| Phase 1 gate passed | - [x] | All applicable Phase 1 checks passed; no real build or production-affecting operation was run. |

Phase 1 completion annotations:

```text
Completed: 2026-07-31 13:44:16 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Inspected `nl -ba scripts/build-keiyoushi-from-source.sh | sed -n '620,735p'`; ran `bash -n scripts/build-keiyoushi-from-source.sh`.
Result: The unmatched `fi` at line 702 was identified and removed; the target script parses successfully. WCO, Miruro, SuperStream, and surrounding patch blocks remain in their intended conditional scope. No Hanime block exists in this target script; no Hanime-related file was changed.
Files: `scripts/build-keiyoushi-from-source.sh`
Proof artifact: Terminal inspection and `bash -n` output captured at 2026-07-31 13:44:16 +0100.
```

```text
Completed: 2026-07-31 13:44:16 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `stat -f '%Sp %Mp%Lp %N' scripts/build-keiyoushi-from-source.sh`; `git ls-files -s -- scripts/build-keiyoushi-from-source.sh`.
Result: Filesystem and index mode are both executable `100755` (`-rwxr-xr-x`).
Files: `scripts/build-keiyoushi-from-source.sh`
Proof artifact: `git ls-files -s` output showing mode `100755`.
```

```text
Completed: 2026-07-31 13:44:16 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Target-script diff review; preflight command validation; quoted path handling review; required Miruro/WCO source existence and post-patch marker checks; safe fixed-path cleanup guard.
Result: Required tools are checked after argument parsing, missing option values fail visibly, required Miruro/WCO patch scripts and expected source files are checked, patch markers are verified, and cleanup refuses unexpected recursive-delete paths. Archive traversal is not applicable because this script performs no archive extraction.
Files: `scripts/build-keiyoushi-from-source.sh`, `scripts/patch-miruro-sources.py`
Proof artifact: Final target diff and review output captured at 2026-07-31 13:44:16 +0100.
```

```text
Completed: 2026-07-31 13:44:16 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Controlled temporary-fixture test: ran `patch-wco-video-extraction.py` twice and `patch-miruro-sources.py` twice, comparing `shasum -a 256` after each run; ran `python3 -m py_compile scripts/patch-miruro-sources.py scripts/patch-wco-video-extraction.py`.
Result: WCO hashes matched (`5bb2661b65f8e51ccf8cc2f7d9779bb0bca4da9ba1888d7f3fc99eb892f587ec` both runs); Miruro hashes matched (`930f946fd4bbd7b192e2064ab5af93c2e35301895de96c0f62dabd5c22186ad4` both runs); both patchers are idempotent and Python compilation exited 0.
Files: `scripts/build-keiyoushi-from-source.sh`, `scripts/patch-miruro-sources.py`, `scripts/patch-wco-video-extraction.py`
Proof artifact: Controlled-fixture terminal output and patcher output captured at 2026-07-31 13:44:16 +0100.
```

```text
Completed: 2026-07-31 13:44:16 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `for file in scripts/*.sh; do bash -n "$file"; done`; `bash scripts/build-keiyoushi-from-source.sh --help`; `bash scripts/build-keiyoushi-from-source.sh --pkg`; `git diff --check`.
Result: 10/10 shell scripts passed syntax validation; help exited 0; missing `--pkg` value exited 1 with an explicit error; diff check exited 0. No real build, clone, download, installation, or production-affecting operation was run.
Files: All 10 `scripts/*.sh` files inspected; target and patch scripts validated.
Proof artifact: Final validation output captured at 2026-07-31 13:44:16 +0100.
```

Evidence log entries for Phase 1:

| ID | Phase/subphase | Status | Completed date/time with timezone | Actor | Exact evidence/command/test | Result | Files changed | Proof artifact |
|---|---|---|---|---|---|---|---|---|
| P1-1 | 1.1 conditional inspection | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | `nl -ba scripts/build-keiyoushi-from-source.sh | sed -n '620,735p'` | Extra `fi` at line 702 identified; patch scopes inspected. | `scripts/build-keiyoushi-from-source.sh` | Terminal inspection output. |
| P1-2 | 1.1 syntax repair | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | `bash -n scripts/build-keiyoushi-from-source.sh` | Exit 0 after removing the unmatched `fi`. | `scripts/build-keiyoushi-from-source.sh` | `bash -n` output. |
| P1-3 | 1.1 patch-scope review | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | Target diff and conditional-scope inspection | WCO, Miruro, SuperStream, and adjacent blocks remain scoped; Hanime is not present in this target. | `scripts/build-keiyoushi-from-source.sh` | Review output. |
| P1-4 | 1.1 idempotence | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | Controlled fixture; each patcher run twice; SHA-256 comparison | WCO and Miruro hashes matched on repeated runs. | `scripts/build-keiyoushi-from-source.sh`, `scripts/patch-miruro-sources.py`, `scripts/patch-wco-video-extraction.py` | Fixture output and hashes. |
| P1-5 | 1.1 target parse | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | `bash -n scripts/build-keiyoushi-from-source.sh` | Exit 0. | `scripts/build-keiyoushi-from-source.sh` | `bash -n` output. |
| P1-6 | 1.2 executable mode | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | `stat -f '%Sp %Mp%Lp %N' ...`; `git ls-files -s -- ...` | Filesystem and index mode `100755`. | `scripts/build-keiyoushi-from-source.sh` | `stat`/index output. |
| P1-7 | 1.2 mode verification | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | `git ls-files -s -- scripts/build-keiyoushi-from-source.sh` | Mode field is `100755`. | `scripts/build-keiyoushi-from-source.sh` | Git index output. |
| P1-8 | 1.3 required commands | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | Target preflight inspection and missing-argument run | Required tool checks occur before build mutation but after `--help`/argument parsing; missing values fail visibly. | `scripts/build-keiyoushi-from-source.sh` | Target diff and command output. |
| P1-9 | 1.3 path quoting | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | Target diff/path-handling review | Targeted filesystem operations use quoted path variables; intentional pattern expansion remains unchanged. | `scripts/build-keiyoushi-from-source.sh` | Review output. |
| P1-10 | 1.3 expected files | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | Required Miruro/WCO script and source checks in target diff | Missing required patch inputs now fail visibly. | `scripts/build-keiyoushi-from-source.sh` | Target diff. |
| P1-11 | 1.3 required patch failures | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | `set -euo pipefail` plus required patch commands and verification checks | Required patch failures propagate or fail post-patch verification. | `scripts/build-keiyoushi-from-source.sh` | Target diff/review. |
| P1-12 | 1.3 patch verification | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | Miruro marker and WCO marker checks | Actual patched source files are checked after patching. | `scripts/build-keiyoushi-from-source.sh` | Target diff/review. |
| P1-13 | 1.3 safe cleanup | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | Cleanup case guard inspection; help/missing-argument runs | Cleanup only permits `/tmp/anikku-source-build`; no build cleanup was exercised. | `scripts/build-keiyoushi-from-source.sh` | Target diff and command output. |
| P1-14 | 1.3 archive traversal | Not applicable | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | Script operation audit | No archive extraction occurs; source is cloned and a JAR is created. | `scripts/build-keiyoushi-from-source.sh` | Script inspection output. |
| P1-15 | 1.3 spaces/unrelated behavior | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | Target diff review | Quoted path handling retained; unrelated playback/Kotlin files were not changed by this work. | Target script and patcher files only | `git status`/diff review. |
| P1-16 | 1.4 shell validation | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | `for file in scripts/*.sh; do bash -n "$file"; done` | 10/10 scripts passed. | `scripts/*.sh` | Final validation output. |
| P1-17 | 1.4 controlled validation | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | Target `--help`, missing-argument path, controlled patcher fixture | Help exit 0; missing argument exit 1; patchers idempotent. | Target and patcher scripts | Final validation output. |
| P1-18 | 1.4 no destructive operation | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | Validation commands only; no real build invocation | No clone, download, install, or production-affecting operation run. | None beyond intended Phase 1 files | Terminal command history/output. |
| P1-19 | Phase 1 gate | Complete | 2026-07-31 13:44:16 +0100 | Buffy (openai/gpt-5.6-luna) | `bash -n` all scripts; mode checks; idempotence fixture; `git diff --check` | Applicable checks passed; later phases remain untouched. | `scripts/build-keiyoushi-from-source.sh`, `scripts/patch-miruro-sources.py`, `completeness.md` | Final review and validation output. |

---

# Phase 2 — Remove global insecure TLS behavior

## 2.1 Usage and behavior audit

File:

```text
macos/src/main/kotlin/app/anikku/macos/platform/network/InsecureSSLHelper.kt
```

- [x] Search every usage of `InsecureSSLHelper`.
- [x] Identify whether the current stream path depends on it.
- [x] Identify all global SSL-context and hostname-verifier mutations.

## 2.2 Secure implementation

- [x] Remove JVM-global trust-all behavior.
- [x] Remove global hostname-verifier bypass.
- [x] Keep JVM default certificate and hostname validation.
- [x] If a legacy extension needs special handling, isolate it to that extension's client and require explicit opt-in. **Not applicable:** no legacy extension-specific TLS exception was present or required after the global helper was removed; no trust-all client was added.
- [x] Do not restore global trust-all behavior to fix a stream failure.

## 2.3 Regression tests and real-stream check

- [x] Test invalid certificate rejection.
- [x] Test hostname mismatch rejection.
- [x] Test no global SSL mutation.
- [x] Test extension-specific client isolation.
- [x] Test secure updater traffic.
- [x] Re-run the protected real-stream workflow.

## Phase 2 gate

Do not proceed until secure defaults are active and the working stream path remains functional or the exact environmental limitation is recorded.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| 2.1 Usage audit | - [x] | Completed with the usage and mutation audit recorded below. |
| 2.2 Global bypass removed | - [x] | Global trust-all helper removed; JVM defaults preserved. |
| 2.3 Security tests and playback regression | - [x] | Focused TLS/updater tests and the protected real-stream test passed. |
| Phase 2 gate passed | - [x] | Secure TLS defaults are active and real extension-to-mpv playback remained functional. |

Phase 2 completion annotations:

```text
Completed: 2026-08-01 01:43:31 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Searched `macos/src/main` and `macos/src/test` for `InsecureSSLHelper`, `SSLContext`, `hostnameVerifier`, `TrustManager`, `X509TrustManager`, `setDefaultSSLContext`, and `setDefaultHostnameVerifier`; inspected `AnikkuApplication.kt`, `MacOSNetworkHelper.kt`, and the updater clients.
Result: No remaining references or global TLS mutations were found. The current stream path does not reference `InsecureSSLHelper`; its prior installation was removed from application startup.
Files: `src/main/kotlin/app/anikku/AnikkuApplication.kt`, `src/main/kotlin/app/anikku/macos/platform/network/InsecureSSLHelper.kt`, `src/main/kotlin/app/anikku/macos/platform/network/MacOSNetworkHelper.kt`, `src/main/kotlin/app/anikku/macos/platform/update/AppUpdateChecker.kt`, `src/main/kotlin/app/anikku/macos/platform/update/SparkleUpdater.kt`.
Proof artifact: macOS-scoped grep audit and source inspection; exit status 1/no matches for the insecure TLS symbol search.
```

```text
Completed: 2026-08-01 01:43:31 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Reviewed the Phase 2 diff and application initialization.
Result: Deleted `InsecureSSLHelper.kt`, removed its startup installation, and left OkHttp/JVM certificate and hostname verification at secure defaults. No extension-specific trust bypass was introduced. The only HTTP URLs identified outside HTTPS are intentional localhost callbacks, local media/torrent services, and Chrome CDP endpoints; updater production default remains `https://api.github.com`.
Files: `src/main/kotlin/app/anikku/AnikkuApplication.kt`, `src/main/kotlin/app/anikku/macos/platform/network/InsecureSSLHelper.kt`, `src/main/kotlin/app/anikku/macos/platform/network/MacOSNetworkHelper.kt`, `src/main/kotlin/app/anikku/macos/platform/update/AppUpdateChecker.kt`.
Proof artifact: Phase 2 diff review and plaintext-HTTP classification review.
```

```text
Completed: 2026-08-01 01:43:31 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `cd macos && ./gradlew --offline --no-daemon --console=plain compileTestKotlin`; `./gradlew --offline --no-daemon --console=plain test --tests 'app.anikku.macos.platform.network.SecureTlsDefaultsTest' --tests 'app.anikku.macos.platform.update.AppUpdateCheckerTest'`; `git diff --check`.
Result: Test compilation exited 0. Ten focused tests passed and zero failed: three TLS isolation/default-validation tests and seven updater tests, including the HTTPS default-endpoint test. Diff check exited 0.
Files: `src/test/kotlin/app/anikku/macos/platform/network/SecureTlsDefaultsTest.kt`, `src/test/kotlin/app/anikku/macos/platform/update/AppUpdateCheckerTest.kt`.
Proof artifact: Gradle focused-test output and macOS-scoped diff-check output captured on 2026-08-01.
```

```text
Completed: 2026-08-01 01:43:31 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `cd macos && ./gradlew --no-daemon --console=plain test --tests 'app.anikku.macos.player.StreamingEndToEndTest.full end-to-end - extension to mpv streaming playback'`.
Result: Exit 0. The real extension-to-mpv streaming playback test passed, confirming that the protected stream path still loads an extension, obtains a real video URL, and starts playback after the TLS hardening.
Files: Existing macOS streaming path; no player or stream-extraction changes were made for Phase 2.
Proof artifact: Gradle `StreamingEndToEndTest` result showing `full end-to-end - extension to mpv streaming playback()` passed.
```

Evidence log entries for Phase 2:

| ID | Phase/subphase | Status | Completed date/time with timezone | Actor | Exact evidence/command/test | Result | Files changed | Proof artifact |
|---|---|---|---|---|---|---|---|---|
| P2-1 | 2.1 usage audit | Complete | 2026-08-01 01:43:31 +0100 | Buffy (openai/gpt-5.6-luna) | macOS-scoped grep for insecure TLS symbols and source inspection | No helper usages or global TLS mutations remain; stream path does not depend on the removed helper. | `src/main/kotlin/app/anikku/AnikkuApplication.kt`, `src/main/kotlin/app/anikku/macos/platform/network/InsecureSSLHelper.kt`, network/updater files inspected | Grep/source-audit output. |
| P2-2 | 2.2 global bypass removal | Complete | 2026-08-01 01:43:31 +0100 | Buffy (openai/gpt-5.6-luna) | Phase 2 diff review | Trust-all `X509TrustManager`, permissive hostname verifier, and JVM-global SSL setters removed; no replacement bypass added. | `src/main/kotlin/app/anikku/AnikkuApplication.kt`, `src/main/kotlin/app/anikku/macos/platform/network/InsecureSSLHelper.kt` | Diff review and no-match audit. |
| P2-3 | 2.2 secure defaults | Complete | 2026-08-01 01:43:31 +0100 | Buffy (openai/gpt-5.6-luna) | Focused TLS tests | Default client rejected an untrusted certificate and isolated client trust did not affect a separately created default client. | `src/test/kotlin/app/anikku/macos/platform/network/SecureTlsDefaultsTest.kt` | 3/3 TLS tests passed. |
| P2-4 | 2.2 legacy isolation | Not applicable/complete | 2026-08-01 01:43:31 +0100 | Buffy (openai/gpt-5.6-luna) | Extension/network audit | No legacy extension-specific TLS exception existed or was needed; no trust-all client was introduced. | macOS network and extension call sites inspected | Audit output. |
| P2-5 | 2.3 invalid certificate rejection | Complete | 2026-08-01 01:43:31 +0100 | Buffy (openai/gpt-5.6-luna) | `SecureTlsDefaultsTest.default client rejects an untrusted certificate` | Passed. | `SecureTlsDefaultsTest.kt` | JUnit/Gradle output. |
| P2-6 | 2.3 hostname mismatch rejection | Complete | 2026-08-01 01:43:31 +0100 | Buffy (openai/gpt-5.6-luna) | `SecureTlsDefaultsTest.default hostname verification rejects a trusted certificate for another host` | Passed. | `SecureTlsDefaultsTest.kt` | JUnit/Gradle output. |
| P2-7 | 2.3 no global mutation | Complete | 2026-08-01 01:43:31 +0100 | Buffy (openai/gpt-5.6-luna) | `SecureTlsDefaultsTest.client local trust does not change secure default client behavior` plus source audit | Passed; isolated trust did not leak globally and no global mutator symbols remain. | `SecureTlsDefaultsTest.kt`, audited production files | JUnit/grep output. |
| P2-8 | 2.3 extension-specific isolation | Complete | 2026-08-01 01:43:31 +0100 | Buffy (openai/gpt-5.6-luna) | Isolated OkHttp client test and extension/network audit | Passed; any client-local TLS configuration remains scoped to that client, with no global bypass. | `SecureTlsDefaultsTest.kt`, macOS extension/network call sites | JUnit/source-audit output. |
| P2-9 | 2.3 updater traffic | Complete | 2026-08-01 01:43:31 +0100 | Buffy (openai/gpt-5.6-luna) | `AppUpdateCheckerTest.default updater endpoint uses HTTPS` | Passed; production default request targets `https://api.github.com`; test uses an interceptor and does not expose credentials or perform a live update. | `src/main/kotlin/app/anikku/macos/platform/update/AppUpdateChecker.kt`, `src/test/kotlin/app/anikku/macos/platform/update/AppUpdateCheckerTest.kt` | JUnit/Gradle output. |
| P2-10 | 2.3 protected real stream | Complete | 2026-08-01 01:43:31 +0100 | Buffy (openai/gpt-5.6-luna) | `StreamingEndToEndTest.full end-to-end - extension to mpv streaming playback` | Exit 0; real extension-to-mpv playback passed. | Existing macOS streaming implementation; no Phase 2 player changes | Gradle test report/output. |
| P2-11 | Phase 2 gate | Complete | 2026-08-01 01:43:31 +0100 | Buffy (openai/gpt-5.6-luna) | All Phase 2 evidence above; focused tests; protected stream test; `git diff --check` | Secure defaults active, focused security/updater tests passed, protected real streaming remained functional, and diff check passed. | Phase 2 files only for this work; pre-existing unrelated worktree changes preserved | Review and validation output. |

---

# Phase 3 — Harden updater and Sparkle behavior

Files:

```text
macos/src/main/kotlin/app/anikku/macos/platform/update/AppUpdateChecker.kt
macos/src/main/kotlin/app/anikku/macos/platform/update/SparkleUpdater.kt
```

## 3.1 Secure update source and verification

- [x] Require authenticated HTTPS.
- [ ] Verify Sparkle feed/signatures. **Blocked: no active signed appcast entry is present in the repository, so an actual artifact signature cannot be verified without fabricating evidence.**
- [x] Prevent automatic installation of unverified fallback artifacts.
- [x] Make unverifiable fallback behavior informational only or reject it.
- [x] Prevent partial downloads from replacing the current app.
- [x] Add bounded timeouts and retries.

## 3.2 Correct version comparison

- [x] Replace lexicographic comparison with semantic comparison.
- [x] Test `1.9.0 < 1.10.0`.
- [x] Test `1.2.10 > 1.2.9`.
- [x] Test prerelease ordering.
- [x] Test malformed versions.

## 3.3 Failure and lifecycle handling

- [x] Distinguish no update from check failure.
- [x] Ensure update failures cannot crash the app.
- [x] Ensure native callbacks are strongly referenced and released safely. **No callback object is used; the native controller is strongly retained until deterministic shutdown.**
- [x] Ensure updater shutdown is deterministic.
- [x] Ensure logs do not expose tokens or sensitive URLs.

## Phase 3 gate

Updater changes must not alter extension loading or playback. The protected extension-to-libmpv workflow was rerun after the final updater/player changes and passed.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| 3.1 Secure update verification | - [ ] Blocked on actual signed appcast artifact verification; all other 3.1 controls are complete. | |
| 3.2 Version comparison | - [x] | Completed with exact evidence below. |
| 3.3 Failure/lifecycle handling | - [x] | Completed with exact evidence below. |
| Phase 3 gate passed | - [x] | Completed: 2026-08-02 (Europe/Dublin); updater tests/configuration/native-helper build and the post-change protected live stream all passed. Release signing remains explicitly blocked without the matching private key and is not fabricated. |

Phase 3 completion annotations:

```text
Completed: 2026-08-02 (Europe/Dublin)
Actor: Codex (GPT-5)
Evidence: `./gradlew buildSparkleHelper validateSparkleConfiguration --console=plain --stacktrace`; full `quickCheck` (509 tests, 0 failures/errors, 3 documented skips); post-change `StreamingEndToEndTest.full end-to-end - extension to mpv streaming playback`.
Result: The Swift helper compiled and the bundled dylib was refreshed; its effective feed is HTTPS-validated, main-thread access is serialized, shutdown releases the controller and cached C string, and repeated feed queries no longer allocate indefinitely. Sparkle structural configuration passed with zero signed enclosures, and protected streaming still passed. A signed enclosure remains unavailable because no matching release private key/artifact was supplied.
Files: updater Kotlin/Swift code, Sparkle build/configuration, updater tests, and this evidence ledger.
Proof artifact: Gradle console output, `build/reports/tests/quickTest/`, and post-change streaming test report.
```

```text
Completed: 2026-08-01 02:21:50 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Source audit of AppUpdateChecker, SparkleUpdater, SparkleHelper.swift, AboutDialog, build.gradle.kts, generate-appcast.sh, and Sparkle/appcast.xml; `./gradlew --offline --no-daemon --console=plain validateSparkleConfiguration`; XML parse of `src/main/resources/Sparkle/appcast.xml`; `bash -n scripts/build-sparkle-helper.sh scripts/generate-appcast.sh`.
Result: Production updater endpoint is restricted to HTTPS api.github.com; release URLs are restricted to HTTPS GitHub hosts; Sparkle feed validation requires HTTPS; fallback update behavior opens only a trusted GitHub release page and never installs or opens a direct unverified artifact; appcast generation uses a temporary `.part` file and atomic rename; bounded OkHttp connect/read/call timeouts and two-attempt retry behavior are present. Configuration validation passed with a valid DER-encoded Ed25519 public key and zero active appcast enclosures. The zero-enclosure state is intentional until a real signed DMG is generated.
Files: `src/main/kotlin/app/anikku/macos/platform/update/AppUpdateChecker.kt`, `src/main/kotlin/app/anikku/macos/platform/update/SparkleUpdater.kt`, `src/main/kotlin/app/anikku/macos/ui/components/AboutDialog.kt`, `src/main/swift/SparkleHelper.swift`, `build.gradle.kts`, `scripts/generate-appcast.sh`, `src/main/resources/Sparkle/appcast.xml`, `src/main/resources/Sparkle/ed25519_pub.pem`.
Proof artifact: Gradle validation exit 0; XML parse exit 0; shell syntax exit 0; no placeholder signature remains in the active appcast because no unsigned release item is shipped.
```

```text
Completed: 2026-08-01 02:21:50 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `./gradlew --offline --no-daemon --console=plain test --tests 'app.anikku.macos.platform.update.AppUpdateCheckerTest'`.
Result: 11 updater tests passed, 0 failed. Coverage includes HTTPS endpoint enforcement, trusted release URL rejection, multi-digit semantic versions, prerelease ordering, build metadata, malformed versions, no-update versus API failure, bounded retry behavior, release-page-only fallback, and browser delegation safety.
Files: `src/test/kotlin/app/anikku/macos/platform/update/AppUpdateCheckerTest.kt`.
Proof artifact: Gradle focused test task exit 0 and test report.
```

```text
Completed: 2026-08-01 02:21:50 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `./gradlew --offline --no-daemon --console=plain compileKotlin`; `./gradlew --offline --no-daemon --console=plain compileTestKotlin`; source inspection of SparkleUpdater, SparkleHelper.swift, and AnikkuApplication.onShutdown().
Result: Production and test compilation exited 0. Sparkle helper loading and initialization failures are caught; the native controller is retained for its lifetime; `sparkle_shutdown` is exposed and called during application shutdown; compatibility handling catches a missing lifecycle symbol. Update results distinguish `NoUpdate`, `Available`, `Failed`, and native-dialog initiation. Sensitive feed URLs and response bodies are not logged.
Files: `src/main/kotlin/app/anikku/macos/platform/update/SparkleUpdater.kt`, `src/main/swift/SparkleHelper.swift`, `src/main/kotlin/app/anikku/macos/AnikkuApplication.kt`, `src/main/kotlin/app/anikku/macos/ui/components/AboutDialog.kt`.
Proof artifact: compile task exit codes 0 and code-review output; no player, renderer, extension-loading, or stream-extraction files were changed for Phase 3.
```

```text
Blocked: 2026-08-01 02:21:50 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: XML parse and `validateSparkleConfiguration` report `ACTIVE_ENCLOSURES=0` / `0 signed appcast enclosure(s)`; `grep` audit found no active `REPLACE_WITH_REAL_SIGNATURE` value.
Result: Structural key/feed/signature validation is implemented and passes, but actual Sparkle artifact signature verification cannot be proven until a real DMG is built, signed with the private key corresponding to `ed25519_pub.pem`, and an active signed appcast item is generated. No signature, artifact length, or release evidence was fabricated.
Files: `src/main/resources/Sparkle/appcast.xml`, `src/main/resources/Sparkle/ed25519_pub.pem`, `build.gradle.kts`, `scripts/generate-appcast.sh`.
Proof artifact: validation output and active-enclosure count.
```

```text
Not verified: 2026-08-01 02:21:50 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Phase 3 validation command set did not include the protected real extension-to-mpv playback workflow.
Result: Compile/tests/configuration/scripts passed, but Phase 3's explicit non-regression gate remains unchecked. Phase 2 already recorded a passing real extension-to-mpv stream test; that prior evidence was not reused as new Phase 3 proof.
Files: No playback files changed for Phase 3.
Proof artifact: Phase 3 evidence review.
```

---

# Phase 4 — Harden extension verification and lifecycle

Files:

```text
macos/src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionLoader.kt
macos/src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionManager.kt
macos/src/main/kotlin/app/anikku/macos/platform/extension/SourceProxy.kt
```

## 4.1 Trust and authenticity

- [x] Confirm `autoTrustAllJars` is disabled by default.
- [x] Keep any development bypass developer-only and visibly warned. **Completed: no automatic/development trust bypass remains.**
- [x] Validate extension metadata.
- [ ] Verify artifact integrity/authenticity against a trusted source. **Blocked: the live repository index inspected for this work publishes no SHA-256 artifact values; local user trust hashes are not trusted-source authenticity evidence.**
- [x] Reject corrupt, incomplete, malformed, mismatched, or unsupported artifacts.
- [x] Preserve the existing explicit trusted-extension workflow.

## 4.2 Archive and path safety

- [x] Reject `../` entries.
- [x] Reject absolute entries.
- [x] Reject symlink escapes.
- [ ] Ensure all extracted files remain inside the extension directory. **Not applicable to the current loader: no archive extraction is performed; install/replacement paths are separately canonicalized and confined to the extensions directory.**
- [x] Prevent overwriting application-owned files.

## 4.3 Classloader and extension lifecycle

- [x] Close old classloaders on reload/removal. **Completed for the tested reload/removal paths.**
- [x] Clean failed-install temporary files. **Completed for the tested failed-replacement path.**
- [x] Prevent stale extension instances. **Completed for the tested failed-replacement path: replacement is loaded before the old loader is released and the prior artifact/state is restored on failure.**
- [x] Handle duplicate IDs and package names. **Completed: duplicate JAR packages are rejected as a complete set before code loading; duplicate source IDs are rejected; the APK conversion path uses the same package/source ownership sets and refuses occupied conversion targets.**
- [x] Isolate failures to the affected extension.
- [x] Refresh sources after install/update/remove/reload. **Completed with a deterministic manager-flow test covering reload, a trusted higher-version installation through the update API, source lookup, and removal.**
- [x] Document that in-process extensions are not a security sandbox.

## 4.4 Tests and protected playback check

- [x] Test valid trusted extension loading.
- [x] Test invalid trust entries (empty/wrong hashes).
- [x] Test malformed JAR/metadata.
- [x] Test path traversal and symlink escape.
- [x] Test reload/removal/classloader cleanup.
- [x] Re-run extension load, search, episode selection, and real streaming. **Completed with installed AniDB: search for `One Piece` returned 28 results, the source resolved an episode/video URL, and libmpv started and advanced the real stream. The broader browse/HLS/DASH/torrent suite also passed.**

## Phase 4 gate

Do not proceed if a trusted existing extension no longer loads or the streaming path regresses.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| 4.1 Trust/authenticity | - [ ] Partial; trusted-source authenticity is blocked because the repository publishes no artifact hashes. | Deterministic trust, metadata, and artifact rejection controls completed below. |
| 4.2 Archive/path safety | - [ ] Partial; archive extraction confinement is not applicable to the current loader. | Traversal, absolute-path, symlink, canonical install-path, and safe replacement controls passed focused tests/source review. |
| 4.3 Lifecycle/isolation | - [x] Complete for the implemented JAR/runtime-APK paths at 2026-08-02 14:44:44 +0100. | Duplicate packages are rejected before loading; duplicate source IDs are rejected; reload/update/removal publish current sources; failure rollback and classloader cleanup remain green. |
| 4.4 Tests/playback regression | - [x] Complete at 2026-08-02 14:44:44 +0100. | Focused extension suite passed 8/8; installed-extension live suite passed browse-to-mpv, HLS, DASH, and torrent 4/4; separate search-to-mpv test passed 1/1. |
| Phase 4 gate passed | - [x] Completed: 2026-08-02 14:44:44 +0100; Actor: Codex (GPT-5). | A trusted installed extension loads and the protected browse/search → episode → stream → libmpv path is green. Trusted-source publishing remains a separately documented repository blocker and is not misrepresented as complete. |

Phase 4 completion annotations:

```text
Completed: 2026-08-02 (Europe/Dublin)
Actor: Codex (GPT-5)
Evidence: Full `check` exposed an outdated package-mismatched repository fixture and a first-install lifecycle defect; fixed fixtures now generate metadata-matching JARs; focused `ExtensionRepoIntegrationTest` plus `ExtensionSecurityTest` passed 13/13.
Result: A validated first-time repository install now persists as Untrusted for explicit user approval instead of being rolled back as a load failure. A changed untrusted update to an already trusted artifact still rolls back, preserving the security invariant. Progress, two-package install, APK fallback, live index fetch, install/trust/browse, duplicate rejection, and failed-update rollback all pass.
Files: `MacOSExtensionManager.kt`, `ExtensionRepoIntegrationTest.kt`, and this ledger.
Proof artifact: Focused Gradle test output and `build/reports/tests/test/`.
```

```text
Completed: 2026-08-01 03:36:04 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Source audit of MacOSExtensionLoader.kt and MacOSExtensionManager.kt; focused tests `ExtensionSecurityTest`, `ExtensionLoadingTest`, and `SampleExtensionIntegrationTest`.
Result: Automatic trust is not enabled; no development trust bypass remains; extension metadata validates package, identity, versions, and fully-qualified source classes; invalid, malformed, unsupported, incomplete, and metadata-mismatched artifacts are rejected; explicit SHA-256 trust entries remain required for loading.
Files: `src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionLoader.kt`, `src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionManager.kt`, `src/main/kotlin/eu/kanade/tachiyomi/extension/model/Extension.kt`.
Proof artifact: Final source review and focused Gradle test report.
```

```text
Completed: 2026-08-01 03:36:04 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `ExtensionSecurityTest` archive/path tests and source audit of `validateArchive`, `requireSafeArtifactFile`, `safeExtensionFile`, `requireSafeExtensionPath`, and replacement code.
Result: `../` traversal, absolute paths, archive Unix symlink entries, and filesystem symlink artifacts are rejected. The current loader performs no archive extraction, so extracted-file confinement is not applicable; install/replacement paths are confined to the extensions directory. Occupied APK/dependency targets are rejected; replacement uses validation before swap and rollback-safe temporary backups.
Files: `src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionLoader.kt`, `src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionManager.kt`, `src/test/kotlin/app/anikku/macos/platform/extension/ExtensionSecurityTest.kt`.
Proof artifact: Focused archive/path tests passed; `git diff --check` exited 0.
```

```text
Completed: 2026-08-01 03:36:04 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `cd macos && ./gradlew --offline --no-daemon --console=plain compileKotlin compileTestKotlin test --tests 'app.anikku.macos.platform.extension.ExtensionSecurityTest' --tests 'app.anikku.macos.platform.extension.ExtensionLoadingTest' --tests 'app.anikku.macos.platform.extension.SampleExtensionIntegrationTest'`.
Result: Exit 0; BUILD SUCCESSFUL. 17 tests passed, 0 failed, 0 skipped: 6 `SampleExtensionIntegrationTest`, 6 `ExtensionSecurityTest` (including failed-replacement preservation), and 5 `ExtensionLoadingTest`. Only pre-existing/general test deprecation warnings were reported; no Phase 4 production compile failure occurred.
Files: `src/test/kotlin/app/anikku/macos/platform/extension/ExtensionSecurityTest.kt`, `src/test/kotlin/app/anikku/macos/platform/extension/ExtensionLoadingTest.kt`, `src/test/kotlin/app/anikku/macos/platform/extension/SampleExtensionIntegrationTest.kt`.
Proof artifact: Gradle test report at `macos/build/reports/tests/test/index.html` and XML reports under `macos/build/test-results/test/`.
```

```text
Completed: 2026-08-01 03:36:04 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `ExtensionSecurityTest` failed-replacement test, manager removal test, loader classloader swap/close paths, and explicit trust/revoke source review.
Result: A valid trusted extension loads; empty/wrong trust entries leave artifacts untrusted; failed replacement preserves the original JAR and installed manager state; temporary replacement files are cleaned; removal closes the classloader and removes the artifact/state; in-process extension code is explicitly documented as running with JVM privileges rather than in a security sandbox.
Files: `src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionLoader.kt`, `src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionManager.kt`, `src/test/kotlin/app/anikku/macos/platform/extension/ExtensionSecurityTest.kt`.
Proof artifact: 17/17 focused tests passed and final code review.
```

```text
Scope note: The repository contained unrelated pre-existing macOS worktree changes in updater, player, UI, scripts, and TLS files. This Phase 4 continuation modified only the extension-loader/manager/model/test files and the Phase 4 section of `completeness.md`; later checklist phases were not edited.
```

```text
Blocked: 2026-08-01 03:36:04 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Inspection of the live repository index showed no published SHA-256 artifact field/value for downloaded extension artifacts.
Result: Optional published-hash verification is implemented when an index supplies `sha256`, but authenticity against the current trusted repository cannot be proven. No artifact signature or hash evidence was fabricated.
Files: `src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionManager.kt`, `src/main/kotlin/eu/kanade/tachiyomi/extension/model/Extension.kt`.
Proof artifact: Repository-index inspection recorded during Phase 4 validation.
```

```text
Not verified: 2026-08-01 03:36:04 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `StreamingEndToEndTest.full end-to-end - extension to mpv streaming playback` was run with a 600-second limit and timed out.
Result: Deterministic extension loading/security tests passed, but the protected real extension-to-MPV playback workflow was not demonstrated in this continuation. The Phase 4 gate remains unchecked.
Files: No player, renderer, or stream-extraction files were changed for Phase 4.
Proof artifact: Timed-out Gradle streaming-test execution; no playback success was claimed.
```

```text
Completed: 2026-08-02 14:44:44 +0100
Actor: Codex (GPT-5)
Evidence: `./gradlew quickTest --tests 'app.anikku.macos.platform.extension.ExtensionSecurityTest' --console=plain --stacktrace`; `./gradlew test --tests 'app.anikku.macos.player.StreamingEndToEndTest' --console=plain --stacktrace`; and the focused `StreamingEndToEndTest.search anime to episode and mpv streaming playback` method.
Result: Focused lifecycle/security tests passed 8/8. Live installed-extension tests passed 4/4 in 3m05s: browse-to-mpv, HLS, DASH, and Nyaa magnet/torrent streaming. The new hard-asserting search test passed 1/1: AniDB search for `One Piece` returned 28 results and libmpv loaded a 1415.8-second stream and advanced playback. Duplicate package artifacts were all rejected before code loading, and reload/update/removal emitted current source instances. The batch source builder's inherited Miruro fallback that returned no videos was removed and its executable mode restored; malformed patch output now fails visibly.
Files: `src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionLoader.kt`, `src/test/kotlin/app/anikku/macos/platform/extension/ExtensionSecurityTest.kt`, `src/test/kotlin/app/anikku/macos/player/StreamingEndToEndTest.kt`, `scripts/batch-build-keiyoushi-from-source.sh`.
Proof artifact: `build/reports/tests/quickTest/index.html`, `build/reports/tests/test/index.html`, and XML reports under `build/test-results/`; live output recorded `Search-to-stream verified: One Piece via AniDB`.
```

---

# Phase 5 — Implement and secure the local HTTP media server

File:

```text
macos/src/main/kotlin/app/anikku/macos/platform/media/MacOSHttpServer.kt
```

## 5.1 Route and path handling

- [x] Replace hardcoded `/download/` placeholder behavior with a real implementation or disable the route. **Completed: the unsupported download-ID route is explicitly disabled; `getStreamUrl(downloadId)` returns `null` and `/download/` returns `404` rather than advertising a non-functional placeholder.**
- [x] Restrict access to the configured media root.
- [x] Canonicalize and validate paths.
- [x] Reject traversal, absolute paths, directories, and missing files.
- [x] Ensure intentional localhost binding.

## 5.2 HTTP behavior

- [x] Implement `GET`.
- [x] Implement `HEAD`.
- [x] Return correct `Content-Type`.
- [x] Return correct `Content-Length`.
- [x] Return `Accept-Ranges: bytes`.
- [x] Implement `206 Partial Content`.
- [x] Implement `416 Range Not Satisfiable`.
- [x] Support explicit, open-ended, and suffix ranges.
- [x] Avoid loading entire videos into memory.
- [x] Close streams and handle shutdown.

## 5.3 Deterministic integration tests

- [x] Test complete-file response.
- [x] Test HEAD.
- [x] Test `bytes=0-99`.
- [x] Test `bytes=100-`.
- [x] Test `bytes=-100`.
- [x] Test invalid/reversed/out-of-range requests.
- [x] Test missing file.
- [x] Test traversal and absolute path.
- [x] Test server shutdown.

## Phase 5 gate

The local server must work correctly without changing the remote stream extraction path.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| 5.1 Route/path handling | - [x] | Loopback-only server, canonical media-root confinement, safe URL encoding, and explicit disabled download-ID behavior verified. |
| 5.2 HTTP/range behavior | - [x] | GET/HEAD, media headers, zero-length files, single byte ranges, 206/416 responses, streaming bodies, and shutdown verified. |
| 5.3 Integration tests | - [x] | 14/14 deterministic HTTP integration tests passed. |
| Phase 5 gate passed | - [x] | Local serving works without changes to remote stream extraction; Phase 6 and later sections remain untouched. |

Phase 5 completion annotations:

```text
Completed: 2026-08-01 11:14:27 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Source review of `MacOSHttpServer.kt`; `MacOSHttpServerTest` integration tests; `git diff --check`; scoped diff review.
Result: The server binds explicitly to `127.0.0.1`; file URLs encode the filename as one path segment; candidate paths are canonicalized and confined below the configured media root; traversal, absolute paths, directories, missing files, and symlink escapes are rejected. The unsupported `/download/` placeholder is disabled rather than advertised.
Files: `src/main/kotlin/app/anikku/macos/platform/media/MacOSHttpServer.kt`, `src/test/kotlin/app/anikku/macos/platform/media/MacOSHttpServerTest.kt`.
Proof artifact: Focused integration-test report and final scoped diff review.
```

```text
Completed: 2026-08-01 11:14:27 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `cd macos && ./gradlew --offline --no-daemon --console=plain compileKotlin compileTestKotlin test --tests 'app.anikku.macos.platform.media.MacOSHttpServerTest'`.
Result: Exit 0; BUILD SUCCESSFUL. 14 tests passed, 0 failed, 0 skipped. Coverage includes complete GET, HEAD, correct media/content-length/range headers, empty files, explicit `bytes=0-99`, open-ended `bytes=100-`, suffix `bytes=-100`, clamped ranges, invalid/reversed/out-of-range 416 responses, missing files, traversal, absolute paths, symlink escape, method rejection, loopback URL construction, health response, and shutdown.
Files: `src/main/kotlin/app/anikku/macos/platform/media/MacOSHttpServer.kt`, `src/test/kotlin/app/anikku/macos/platform/media/MacOSHttpServerTest.kt`.
Proof artifact: Gradle report under `macos/build/reports/tests/test/` and XML results under `macos/build/test-results/test/`; the build emitted only the existing general Gradle deprecation warning.
```

```text
Completed: 2026-08-01 11:14:27 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: NanoHTTPD response-stream contract review; `MacOSHttpServerTest` shutdown and repeated-stop test; `git diff --check`.
Result: Media bodies use fixed-length file streams rather than loading the video into memory; NanoHTTPD owns and closes the response stream after transmission; repeated stop is safe and the server no longer accepts requests after shutdown. No remote stream extraction, player, renderer, download-manager, or later-phase file was changed.
Files: `src/main/kotlin/app/anikku/macos/platform/media/MacOSHttpServer.kt`, `src/test/kotlin/app/anikku/macos/platform/media/MacOSHttpServerTest.kt`.
Proof artifact: Final code review, 14/14 focused tests, clean diff check, and Phase 6 boundary at `completeness.md` line 689.
```

```text
Scope note: Pre-existing unrelated macOS worktree changes were preserved. This Phase 5 work changed only `platform/media/MacOSHttpServer.kt`, its focused integration test, and this Phase 5 checklist section. Android/shared modules and Phase 6 onward were not modified.
```

---

# Phase 6 — Repair the download lifecycle

File:

```text
macos/src/main/kotlin/app/anikku/macos/platform/download/MacOSDownloadManager.kt
```

## 6.1 Safe file lifecycle

- [x] Compare download paths with storage and HTTP-server paths.
- [x] Use temporary filenames while downloading.
- [x] Atomically rename only after successful completion.
- [x] Remove or clearly mark failed/cancelled partial files.
- [x] Sanitize filenames and prevent traversal.
- [x] Prevent concurrent overwrite.
- [x] Close response bodies and streams.
- [x] Make cancellation stop the network request.

## 6.2 Tests

- [x] Successful download.
- [x] HTTP failure.
- [x] Interrupted connection.
- [x] Cancellation.
- [x] Retry.
- [x] Duplicate download.
- [x] Concurrent download.
- [x] Invalid filename.
- [x] Failure cleanup.
- [x] HTTP-server compatibility.

## Phase 6 gate

Completed downloads remain playable, and partial downloads are never incorrectly served as complete files.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| 6.1 File lifecycle | - [x] | Completed with the source audit and regression suite recorded below. |
| 6.2 Download tests | - [x] | Completed with the fresh focused Gradle run recorded below. |
| Phase 6 gate passed | - [x] | Completed: successful files are atomically finalized and local-server playable; failed, cancelled, interrupted, paused, retried, and shutdown attempts do not expose partial files. |

Phase 6 completion annotations:

```text
Completed: 2026-08-01 13:00:58 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Source review of `MacOSDownloadManager.kt`, `DownloadRepository.kt`, and the local HTTP-server contract in `MacOSHttpServer.kt`; focused test `successful download atomically completes and is compatible with local HTTP server()`.
Result: The manager writes below the storage provider's `downloads/videos` directory, which is the directory consumed by the local HTTP server. Completed entries persist the finalized path and are served only when the path is a regular file inside that managed root. The integration test reloaded the repository entry and fetched the completed bytes through `MacOSHttpServer` with HTTP 200.
Files: `src/main/kotlin/app/anikku/macos/platform/download/MacOSDownloadManager.kt`, `src/main/kotlin/app/anikku/macos/platform/data/DownloadRepository.kt`, `src/main/kotlin/app/anikku/macos/platform/media/MacOSHttpServer.kt`, `src/test/kotlin/app/anikku/macos/platform/download/MacOSDownloadManagerTest.kt`.
Proof artifact: `macos/build/test-results/test/TEST-app.anikku.macos.platform.download.MacOSDownloadManagerTest.xml`; `/tmp/anikku-phase6-gradle.log`.
```

```text
Completed: 2026-08-01 13:00:58 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Source review of `executeDownload()`, `atomicComplete()`, `temporaryFiles`, and the successful-download, interrupted-response, cancellation, pause/resume, retry, and shutdown tests.
Result: Each attempt uses a unique hidden `.part` filename. The temp file is moved to the final filename only after a successful response-body read; `ATOMIC_MOVE` is used when supported, with a non-atomic fallback only when the filesystem does not support atomic moves. Failed, interrupted, cancelled, paused, retried, and shutdown attempts remove known partial files and mark or remove the repository entry without presenting the partial as completed.
Files: `src/main/kotlin/app/anikku/macos/platform/download/MacOSDownloadManager.kt`, `src/test/kotlin/app/anikku/macos/platform/download/MacOSDownloadManagerTest.kt`.
Proof artifact: Fresh focused Gradle report with 12/12 tests passed; no `.part` artifacts remained in the tested scenarios.
```

```text
Completed: 2026-08-01 13:00:58 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Source review of `sanitizeFileName()`, `requireSafeDownloadFile()`, `safeManagedDownloadFile()`, `isManagedDownloadPath()`, unique entry-ID suffixes, and test `unsafe filename characters are sanitized and remain inside videos directory()` plus test `persisted outside-root path is not served or deleted()`.
Result: Control characters, separators, reserved filename characters, whitespace, leading/trailing dots, and overlong names are sanitized; canonical managed-root checks reject traversal and persisted outside-root paths. Symlinks, directories, missing files, and non-regular files are not accepted as playable or cleanup targets. Final names include the entry ID, and each attempt's token makes temporary names unique, preventing concurrent overwrite.
Files: `src/main/kotlin/app/anikku/macos/platform/download/MacOSDownloadManager.kt`, `src/test/kotlin/app/anikku/macos/platform/download/MacOSDownloadManagerTest.kt`.
Proof artifact: Focused test report; 12/12 tests passed, including the filename/path-safety and outside-root regression tests.
```

```text
Completed: 2026-08-01 13:00:58 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Source review of nested `call.execute().use`, response-body `byteStream().use`, output-stream `use`, `activeCalls` cancellation/removal, `attemptTokens`, and `close()`; focused cancellation, interruption, and shutdown tests.
Result: Response bodies and input/output streams are closed using structured `use` scopes. Cancellation calls `Call.cancel()`, cancels the worker job, invalidates the attempt token, cleans partial files, and prevents stale completion. Shutdown cancels active calls/jobs, cleans artifacts, marks still-active entries as `ERROR`, rejects new enqueue operations, and does not rely on unsafe synchronous coroutine self-joining.
Files: `src/main/kotlin/app/anikku/macos/platform/download/MacOSDownloadManager.kt`, `src/test/kotlin/app/anikku/macos/platform/download/MacOSDownloadManagerTest.kt`.
Proof artifact: Fresh JUnit XML and `/tmp/anikku-phase6-gradle.log`; cancellation, interruption, pause/resume, and shutdown tests passed.
```

```text
Completed: 2026-08-01 13:00:58 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `cd macos && ./gradlew --offline --no-daemon --console=plain --rerun-tasks compileKotlin compileTestKotlin test --tests 'app.anikku.macos.platform.download.MacOSDownloadManagerTest'`.
Result: Exit 0; BUILD SUCCESSFUL; tasks executed rather than retrieved from cache (`10 actionable tasks: 10 executed`). The focused JUnit suite ran 12 tests with 0 failures, 0 errors, and 0 skipped. The report contains successful download, HTTP failure, interrupted response, cancellation, retry, pause/resume, duplicate, concurrent, unsafe filename, stale cleanup, persisted outside-root, and shutdown coverage.
Files: `src/main/kotlin/app/anikku/macos/platform/download/MacOSDownloadManager.kt`, `src/main/kotlin/app/anikku/macos/platform/data/DownloadRepository.kt`, `src/test/kotlin/app/anikku/macos/platform/download/MacOSDownloadManagerTest.kt`.
Proof artifact: `/tmp/anikku-phase6-gradle.log`; `macos/build/test-results/test/TEST-app.anikku.macos.platform.download.MacOSDownloadManagerTest.xml`; Gradle HTML report under `macos/build/reports/tests/test/`.
```

Evidence log entries for Phase 6:

| ID | Phase/subphase | Status | Completed date/time with timezone | Actor | Exact evidence/command/test | Result | Files changed or inspected | Proof artifact |
|---|---|---|---|---|---|---|---|---|
| P6-1 | 6.1 storage/HTTP path alignment | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | Source audit; successful local HTTP compatibility test | Final files use `downloads/videos`, the same managed directory consumed by local serving; completed file fetched with HTTP 200. | Download manager, repository, HTTP server, focused test | JUnit XML; Gradle log |
| P6-2 | 6.1 temporary filenames | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `executeDownload()`/`temporaryFiles` audit; success/failure/cancellation tests | Unique hidden `.part` files are used for every attempt and are absent after tested terminal paths. | Download manager; focused test | JUnit XML |
| P6-3 | 6.1 atomic completion | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `atomicComplete()` audit; successful download test | Final path is published only after `ATOMIC_MOVE` when supported and successful completion. | Download manager; focused test | JUnit XML |
| P6-4 | 6.1 partial-file cleanup | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | HTTP failure/interrupted/cancellation/pause/shutdown tests | Partial files are removed; failed entries become `ERROR`, while explicit cancel/remove and pause preserve their caller-selected state. | Download manager; focused test | JUnit XML |
| P6-5 | 6.1 filename/path safety | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | Sanitization and managed-root source audit; unsafe filename and outside-root regression tests | Traversal/separators/control characters are neutralized; persisted outside-root files are neither served nor deleted. | Download manager; focused test | JUnit XML |
| P6-6 | 6.1 concurrent-overwrite protection | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | Duplicate/concurrent tests; entry-ID/token source audit | Duplicate enqueue returns the existing active entry; distinct episodes use distinct final/temp paths and both complete. | Download manager; focused test | JUnit XML |
| P6-7 | 6.1 response/stream closure | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | Nested OkHttp/stream `use` audit; interruption/cancellation tests | Response body, input stream, and output stream are closed deterministically. | Download manager; focused test | JUnit XML |
| P6-8 | 6.1 network cancellation | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `Call.cancel()`/job cancellation/token audit; cancellation and shutdown tests | Active network calls are cancelled, stale attempts cannot complete, and partial outputs are cleaned. | Download manager; focused test | JUnit XML |
| P6-9 | 6.2 successful download | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `successful download atomically completes and is compatible with local HTTP server()` | Passed; complete bytes persisted and served through the local HTTP server. | Focused test; download manager | JUnit XML |
| P6-10 | 6.2 HTTP failure | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `HTTP failure marks entry error and cleans partial files()` | Passed; HTTP 503 produces `ERROR` and no partial artifact. | Focused test; download manager | JUnit XML |
| P6-11 | 6.2 interrupted connection | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `interrupted response marks entry error and removes partial output()` | Passed; disconnected response produces `ERROR` and cleanup. | Focused test; download manager | JUnit XML |
| P6-12 | 6.2 cancellation | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `cancellation stops the request and removes partial files()` | Passed; request observed, cancellation removed the entry and partial files. | Focused test; download manager | JUnit XML |
| P6-13 | 6.2 retry | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `retry after HTTP failure creates a complete file and clears error state()` | Passed; first HTTP failure was followed by a completed retry. | Focused test; download manager | JUnit XML |
| P6-14 | 6.2 duplicate download | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `duplicate enqueue returns existing active entry and does not overwrite()` | Passed; one repository entry and one server request. | Focused test; download manager | JUnit XML |
| P6-15 | 6.2 concurrent download | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `concurrent episodes use distinct final and temporary paths()` | Passed; both episodes completed with different paths. | Focused test; download manager | JUnit XML |
| P6-16 | 6.2 invalid filename | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `unsafe filename characters are sanitized and remain inside videos directory()` | Passed; sanitized file remained directly under the managed videos directory. | Focused test; download manager | JUnit XML |
| P6-17 | 6.2 failure cleanup | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `failed retry cleanup removes stale partial file before retry()` | Passed; stale partial state was absent before and after retry completion. | Focused test; download manager | JUnit XML |
| P6-18 | 6.2 HTTP-server compatibility | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | Successful download test's `MacOSHttpServer` GET assertion | Passed; local server returned 200 and the exact payload. | Focused test; HTTP server | JUnit XML |
| P6-19 | 6.2 persisted-path security regression | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | `persisted outside-root path is not served or deleted()` | Passed; outside-root file remained intact and was not exposed as a local download. | Focused test; download manager | JUnit XML |
| P6-20 | Phase 6 gate | Complete | 2026-08-01 13:00:58 +0100 | Buffy (openai/gpt-5.6-luna) | Fresh focused Gradle run plus source/reviewer audit | 12/12 passed; 0 failures, 0 errors, 0 skipped. Completed files were locally playable; partial files were not exposed as complete files. Phase 7 and later checklist content was left unchanged. | Phase 6 implementation/tests and this Phase 6 section only | `/tmp/anikku-phase6-gradle.log`; JUnit XML; final `git diff --check` |

Scope note: This Phase 6 continuation changed only the download manager/repository implementation, its focused tests, and this Phase 6 section of `completeness.md`. Phase 7 and later checklist sections were not edited; unrelated pre-existing macOS worktree changes were preserved.

---

# Phase 7 — Storage, keychain, and application lifecycle

Files:

```text
macos/src/main/kotlin/app/anikku/macos/platform/storage/MacOSStorageManager.kt
macos/src/main/kotlin/app/anikku/macos/platform/storage/MacOSStorageProvider.kt
macos/src/main/kotlin/app/anikku/macos/platform/storage/MacOSAtomicFile.kt
macos/src/main/kotlin/app/anikku/macos/platform/data/MacOSCustomAnimeRepository.kt
macos/src/main/kotlin/app/anikku/macos/platform/data/LibraryRepository.kt
macos/src/main/kotlin/app/anikku/macos/platform/data/HistoryRepository.kt
macos/src/main/kotlin/app/anikku/macos/platform/data/DownloadRepository.kt
macos/src/main/kotlin/app/anikku/macos/platform/preference/MacOSPreferenceStore.kt
macos/src/main/kotlin/app/anikku/macos/platform/security/MacOSKeychain.kt
macos/src/main/kotlin/app/anikku/macos/platform/auth/TrackerTokenStore.kt
macos/src/main/kotlin/app/anikku/macos/platform/auth/TrackerManager.kt
macos/src/main/kotlin/app/anikku/macos/AnikkuApplication.kt
macos/src/main/kotlin/app/anikku/macos/AnikkuApp.kt
macos/src/main/kotlin/app/anikku/macos/ui/settings/TrackerSettingsPanel.kt
macos/src/main/kotlin/app/anikku/macos/ui/screens/tracker/TrackerDetailScreen.kt
macos/src/main/kotlin/app/anikku/macos/ui/screens/tracker/TrackerListScreen.kt
macos/src/test/kotlin/app/anikku/macos/platform/Phase7StorageLifecycleTest.kt
```

## 7.1 Persistence safety

- [x] Create directories before writes.
- [x] Use atomic writes. **`ATOMIC_MOVE` is used where supported; the documented filesystem fallback uses replacement move when atomic moves are unavailable.**
- [x] Handle malformed JSON without destroying valid state.
- [x] Serialize concurrent writes.
- [x] Clean temporary files.
- [x] Canonicalize paths.
- [x] Sanitize filenames.
- [x] Make failures visible.
- [x] Test version-0 backward-compatible migration and permission-denied persistence failures. **Completed with deterministic fixtures; the permission test injects `AccessDeniedException` at the persistence boundary and does not claim OS permission-bit enforcement.**

## 7.2 Keychain and tokens

- [x] Never log raw tokens.
- [x] Distinguish missing token from keychain failure.
- [x] Clear tokens on logout.
- [x] Avoid silent plaintext fallback.
- [x] Handle malformed stored data.
- [ ] Test unavailable keychain and concurrent access. **Partial: unavailable-keychain behavior is tested; concurrent access to the real macOS Keychain CLI is not verified.**

## 7.3 Startup and shutdown

- [x] Initialize managers once. **Source-audited: application-owned managers are initialized once during `AnikkuApplication` construction.**
- [x] Close HTTP servers. **Verified by the existing deterministic HTTP-server shutdown test and `PlayerScreen` disposal path.**
- [x] Close extension classloaders. **Verified by the existing focused extension lifecycle/security suite and `MacOSExtensionManager.close()`.**
- [ ] Stop MPV. **Source-audited in `PlayerViewModel.shutdown()`; an integrated application shutdown run was not performed in this Phase 7 validation.**
- [x] Cancel coroutines.
- [x] Release updater callbacks. **Not applicable: no callback object is retained; Sparkle native resources are released by deterministic shutdown.**
- [x] Close Discord RPC.
- [x] Handle downloads predictably.
- [ ] Test repeated startup/shutdown. **Not verified: constructing the full application mutates user-scoped state and loads extensions; no safe repeated startup/shutdown integration fixture was available.**

## Phase 7 gate

State survives restart, credentials remain protected, and shutdown does not leak or corrupt resources.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| 7.1 Persistence safety | - [x] | Deterministic persistence regressions, version-0 backward-compatible import/default migration, and injected permission-denied failure coverage passed. See P7-1 through P7-10. |
| 7.2 Keychain/tokens | - [ ] Partial: secure storage and token regressions passed; real concurrent Keychain CLI access remains unverified. | See P7-11 through P7-16. |
| 7.3 Lifecycle | - [ ] Partial: deterministic component cleanup and source-audited application shutdown passed; integrated MPV/application restart coverage remains unverified. | See P7-17 through P7-25. |
| Phase 7 gate passed | - [ ] Not verified: repeated full application startup/shutdown and integrated lifecycle proof were not safely run. | No gate completion is claimed. |

Phase 7 completion annotations:

```text
Completed: 2026-08-01 13:41:40 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `cd macos && ./gradlew --offline --no-daemon --console=plain compileKotlin compileTestKotlin test --tests 'app.anikku.macos.platform.Phase7StorageLifecycleTest' --tests 'app.anikku.macos.platform.preference.MacOSPreferenceStoreTest' --tests 'app.anikku.macos.platform.auth.TrackerOAuthManagerTest' --tests 'app.anikku.macos.platform.discord.DiscordRPCTest' --tests 'app.anikku.macos.platform.download.MacOSDownloadManagerTest'`.
Result: `BUILD SUCCESSFUL`; production and test compilation passed; 55 tests passed, 0 failed, 0 skipped. The Phase 7 suite covers directory creation, malformed-state preservation, concurrent preference writes, visible persistence failures, temporary-file cleanup, custom-anime reload, unavailable/malformed/failing keychain behavior, secure token round-trip/logout, failed logout retention, scheduler cancellation, and storage watcher closure. The existing selected suites additionally cover tracker OAuth, Discord lifecycle, download cleanup/shutdown, and preference persistence.
Files: All Phase 7 implementation files and `src/test/kotlin/app/anikku/macos/platform/Phase7StorageLifecycleTest.kt`.
Proof artifact: Gradle reports under `macos/build/reports/tests/test/` and `macos/build/test-results/test/`; final validation output recorded at 2026-08-01 13:41:40 +0100.
```

```text
Completed: 2026-08-01 13:41:40 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `git diff --check`; scoped diff review; source audit of `MacOSAtomicFile`, `MacOSPreferenceStore`, `MacOSCustomAnimeRepository`, `LibraryRepository`, `HistoryRepository`, and `DownloadRepository`.
Result: Persistence writes create parent directories, use unique temporary files, replace targets through the atomic-move path when supported, remove temporary files in `finally`, preserve malformed JSON beside the original, serialize mutations, canonicalize managed paths, sanitize download filenames, roll back in-memory preference mutations when persistence fails, and expose write errors. The non-atomic filesystem fallback is documented above; no strict atomicity claim is made for filesystems that reject `ATOMIC_MOVE`.
Files: `src/main/kotlin/app/anikku/macos/platform/storage/MacOSAtomicFile.kt`, `src/main/kotlin/app/anikku/macos/platform/preference/MacOSPreferenceStore.kt`, `src/main/kotlin/app/anikku/macos/platform/data/MacOSCustomAnimeRepository.kt`, `src/main/kotlin/app/anikku/macos/platform/data/LibraryRepository.kt`, `src/main/kotlin/app/anikku/macos/platform/data/HistoryRepository.kt`, `src/main/kotlin/app/anikku/macos/platform/data/DownloadRepository.kt`.
Proof artifact: `git diff --check` passed; final scoped diff review; focused JUnit report.
```

```text
Completed: 2026-08-01 13:41:40 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `Phase7StorageLifecycleTest` persistence/keychain tests and source audit of `MacOSKeychain` and `TrackerTokenStore`.
Result: Token values are never written to logs or plaintext preferences when a secure store is supplied; keychain commands do not receive the secret as an argv value, drain stdout/stderr concurrently, and serialize access. Missing stored data returns no token while unavailable/failed keychain access records a storage error; malformed token blobs are rejected safely. Successful logout removes secure data and metadata; failed secure deletion retains metadata and reports failure. No silent plaintext fallback occurs.
Files: `src/main/kotlin/app/anikku/macos/platform/security/MacOSKeychain.kt`, `src/main/kotlin/app/anikku/macos/platform/auth/TrackerTokenStore.kt`, `src/main/kotlin/app/anikku/macos/platform/auth/TrackerManager.kt`, tracker UI callers, `src/test/kotlin/app/anikku/macos/platform/Phase7StorageLifecycleTest.kt`.
Proof artifact: 7 keychain/token regression tests passed within the 55-test Gradle run; no raw token values were included in logs or evidence.
```

```text
Completed: 2026-08-01 13:41:40 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Source audit of `AnikkuApplication.onShutdown()`, `AnikkuApp` window focus/disposal handling, `BackgroundTaskScheduler`, `SparkleUpdater`, `DiscordRPC`, `MacOSDownloadManager`, `MacOSHttpServer`, `MacOSExtensionManager`, and `PlayerViewModel`; selected existing lifecycle tests; `Phase7StorageLifecycleTest`.
Result: Shutdown is idempotent, cancels scheduled application work, closes the lazily-created download manager when present, closes extension classloaders, shuts down Sparkle/Chrome/Discord/notifications, closes storage watchers, cancels the application scope, and stops Koin best-effort. Window focus listeners call app focus/blur hooks and are removed on disposal. HTTP-server, extension, Discord, download, scheduler, and storage cleanup paths are covered by deterministic tests or source audit. Full repeated `AnikkuApplication` startup/shutdown and integrated MPV shutdown remain explicitly not verified, so the Phase 7 gate remains open.
Files: `src/main/kotlin/app/anikku/macos/AnikkuApplication.kt`, `src/main/kotlin/app/anikku/macos/AnikkuApp.kt`, `src/main/kotlin/app/anikku/macos/platform/BackgroundTaskScheduler.kt`, `src/main/kotlin/app/anikku/macos/platform/update/SparkleUpdater.kt`, `src/main/kotlin/app/anikku/macos/platform/discord/DiscordRPC.kt`, `src/main/kotlin/app/anikku/macos/platform/download/MacOSDownloadManager.kt`, `src/main/kotlin/app/anikku/macos/platform/media/MacOSHttpServer.kt`, `src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionManager.kt`, `src/main/kotlin/app/anikku/macos/player/PlayerViewModel.kt`.
Proof artifact: 55/55 selected tests passed; `git diff --check` passed; integrated startup/shutdown was not claimed.
```

Evidence log entries for Phase 7:

| ID | Phase/subphase | Status | Completed date/time with timezone | Actor | Exact evidence/command/test | Result | Files changed or inspected | Proof artifact |
|---|---|---|---|---|---|---|---|---|
| P7-1 | 7.1 directory creation | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `Phase7StorageLifecycleTest.storage manager creates required directories and closes its watcher` | Required storage directories were created and verified as directories. | Storage provider/manager and Phase 7 test | Gradle JUnit report |
| P7-2 | 7.1 atomic/temp persistence | Complete with fallback documented | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | Source audit plus persistence-failure/temp-file test | Unique temp files are cleaned; `ATOMIC_MOVE` is attempted and the unsupported-filesystem fallback is explicitly documented. | `MacOSAtomicFile.kt`, preference/custom/data repositories, Phase 7 test | Source review and JUnit report |
| P7-3 | 7.1 malformed JSON preservation | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `Phase7StorageLifecycleTest.malformed preferences are preserved and valid state is not destroyed`; custom anime malformed-state test | Malformed files are preserved as `.corrupt-*`; valid subsequent state persists and reloads. | Preference/custom anime repositories and Phase 7 test | Gradle JUnit report |
| P7-4 | 7.1 concurrent writes | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `Phase7StorageLifecycleTest.concurrent preference writes remain valid and survive restart` | 64 concurrent preference writes reloaded with the expected values. | `MacOSPreferenceStore.kt`, Phase 7 test | Gradle JUnit report |
| P7-5 | 7.1 temporary cleanup | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `Phase7StorageLifecycleTest.persistence failure is visible and does not leave a temporary file` | Controlled write failure was visible and no `.tmp` file remained. | `MacOSAtomicFile.kt`, `MacOSPreferenceStore.kt`, Phase 7 test | Gradle JUnit report |
| P7-6 | 7.1 canonical paths | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | Source audit of canonical target and managed download path checks | Persistence targets and managed file paths are canonicalized before use. | Atomic writer, storage/data/download files | Source review |
| P7-7 | 7.1 filename safety | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | Existing download filename-safety regression plus source audit | User-derived download names are sanitized and confined to the managed directory; fixed JSON filenames are not user-derived. | Download manager and data repositories | Existing JUnit report/source review |
| P7-8 | 7.1 visible failures | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `Phase7StorageLifecycleTest.persistence failure is visible and does not leave a temporary file` | Write failure propagated and `lastPersistenceError` was populated. | Preference store and Phase 7 test | Gradle JUnit report |
| P7-9 | 7.1 version-0 backward-compatible import/default migration | Complete | 2026-08-01 13:56:10 +0100 | Buffy (openai/gpt-5.6-luna) | `Phase7StorageLifecycleTest.legacy backup format migrates with defaults and preserves data` | Explicit `version: 0` backup imported successfully; legacy library and preference data survived and omitted newer fields received serializer defaults. This proves backward-compatible import/default migration, not an unimplemented schema transformation. | `platform/backup/MacOSBackupManager.kt`, `platform/data/LibraryRepository.kt`, `Phase7StorageLifecycleTest.kt` | Gradle focused report: 29/29 passed |
| P7-10 | 7.1 deterministic permission-denied persistence coverage | Complete | 2026-08-01 13:56:10 +0100 | Buffy (openai/gpt-5.6-luna) | `Phase7StorageLifecycleTest.permission-denied persistence failure is deterministic and visible` | Injected `AccessDeniedException` propagated to the caller, was recorded by `lastPersistenceError()`, and rolled back the in-memory mutation. This is deterministic persistence-boundary coverage; OS permission-bit denial remains unclaimed because it is environment-dependent. | `platform/preference/MacOSPreferenceStore.kt`, `Phase7StorageLifecycleTest.kt` | Gradle focused report: 29/29 passed |
| P7-11 | 7.2 raw-token logging | Complete by source audit | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | Source audit of keychain/token logger calls | Only token lengths/identifiers are logged; raw token values are not emitted. | Keychain/token store/manager | Source review |
| P7-12 | 7.2 missing vs failure | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | Secure token round-trip/logout plus unavailable-keychain and malformed-keychain tests | Missing token returns null; unavailable/failing secure storage records `lastStorageError` and does not masquerade as a valid missing-token success. | Keychain/token store and Phase 7 test | Gradle JUnit report |
| P7-13 | 7.2 logout clearing | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `Phase7StorageLifecycleTest.keychain token round trip and logout clear secure and metadata state` | Secure token and metadata were cleared successfully. | Token store and Phase 7 test | Gradle JUnit report |
| P7-14 | 7.2 no plaintext fallback | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `Phase7StorageLifecycleTest.keychain failure is distinct from missing token and never falls back to plaintext` | Unavailable secure storage rejected save and wrote no token to preferences. | Token store and Phase 7 test | Gradle JUnit report |
| P7-15 | 7.2 malformed stored data | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `Phase7StorageLifecycleTest.malformed keychain token data is treated as unavailable` | Malformed token blob returned null without exposing token data. | Token store and Phase 7 test | Gradle JUnit report |
| P7-16 | 7.2 unavailable/concurrent keychain | Partial/not verified | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | Unavailable behavior test and synchronized implementation audit; no real concurrent Keychain CLI fixture | Unavailable behavior passed; real concurrent CLI access remains unverified, so the checkbox remains unchecked. | Keychain/token store and Phase 7 test | Gradle JUnit report/source review |
| P7-17 | 7.3 manager initialization | Complete by source audit | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `AnikkuApplication` initialization-order/source audit | Managers are application-owned and initialized once per application instance. | `AnikkuApplication.kt` | Source review |
| P7-18 | 7.3 HTTP server closure | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | Existing `MacOSHttpServerTest` shutdown/repeated-stop test and `PlayerScreen` disposal audit | Repeated stop is safe; player disposal closes the server. | HTTP server, PlayerScreen, existing test | Existing JUnit report/source review |
| P7-19 | 7.3 extension classloader closure | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | Existing extension lifecycle/security suite and manager close audit | Classloaders close on removal/reload/manager shutdown in tested paths. | Extension loader/manager and existing tests | Existing JUnit report/source review |
| P7-20 | 7.3 MPV stop | Not verified in integrated Phase 7 run | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `PlayerViewModel.shutdown()` source audit; no full application shutdown fixture | Cleanup code exists, but integrated Phase 7 proof was not run; checkbox remains unchecked. | PlayerViewModel/MPV files | Source review/evidence limitation |
| P7-21 | 7.3 coroutine cancellation | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `Phase7StorageLifecycleTest.background tasks cancel predictably and one shot tasks complete`; app shutdown source audit | Scheduled work stopped predictably and application scope cancellation is wired. | Scheduler/Application and Phase 7 test | Gradle JUnit report/source review |
| P7-22 | 7.3 updater callbacks | Complete/not applicable | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | Sparkle updater/native helper source audit | No callback object is retained; updater native resources have deterministic shutdown. | SparkleUpdater/Swift helper/Application | Source review |
| P7-23 | 7.3 Discord closure | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `DiscordRPCTest` plus `AnikkuApplication.onShutdown()` audit | Discord stop is idempotently invoked during application shutdown; focused Discord tests passed. | DiscordRPC/Application and existing test | Gradle JUnit report/source review |
| P7-24 | 7.3 download handling | Complete | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | `MacOSDownloadManagerTest` selected in final Gradle command | Download success/failure/cancellation/retry/concurrency/shutdown behavior passed in the existing 12-test manager suite. | Download manager/repository and existing test | Gradle JUnit report |
| P7-25 | 7.3 repeated startup/shutdown | Not verified | 2026-08-01 13:41:40 +0100 | Buffy (openai/gpt-5.6-luna) | No safe repeated full-application integration fixture was run. | Left unchecked; no integrated lifecycle result claimed. | Application/lifecycle files inspected | Evidence review |
| P7-26 | Phase 7 gate | Not verified | 2026-08-01 13:56:10 +0100 | Buffy (openai/gpt-5.6-luna) | Final evidence review; `compileKotlin compileTestKotlin test --tests 'app.anikku.macos.platform.Phase7StorageLifecycleTest' --tests 'app.anikku.macos.platform.preference.MacOSPreferenceStoreTest'` | Version-0 backward-compatible import/default migration and deterministic persistence-boundary permission failure are now verified; real concurrent Keychain access, integrated MPV shutdown, and repeated full startup/shutdown remain unverified. Phase 7 gate remains open. | Phase 7 files and tests | Gradle report: 29 passed, 0 failed, 0 skipped; `git diff --check` passed |

Scope note: Only macOS files were modified for this Phase 7 implementation. Android/shared modules and Phase 8 onward were not modified by this work.

---

# Phase 8 — MPV and renderer verification

Files:

```text
macos/src/main/kotlin/app/anikku/macos/player/PlayerViewModel.kt
macos/src/main/kotlin/app/anikku/macos/player/MPVEventLoop.kt
macos/src/main/kotlin/app/anikku/macos/player/MPVLib.kt
macos/src/main/kotlin/app/anikku/macos/player/MPVSoftwareRenderer.kt
```

> This phase has the highest regression risk. Do not perform a speculative rewrite.

## 8.1 ABI and event correctness

- [x] Verify MPV event IDs against the linked version.
- [x] Verify event struct layout, pointer size, and alignment.
- [x] Handle null/unknown events safely.
- [x] Decode end-file reasons accurately.
- [ ] Distinguish buffering, idle, shutdown, reconfiguration, property changes, and fatal failure. **Not fully verified:** source handling exists, but no deterministic event-loop state-transition matrix was run.
- [x] Ensure event-loop exceptions do not silently stop playback. Injected native-link failure is emitted, polling resumes, a later property event is delivered, and shutdown terminates the loop.

## 8.2 Render parameters and native memory

- [x] Verify render constants against the exact MPV headers/version.
- [x] Test render parameter layout and invalid terminator.
- [x] Keep native `Memory` objects strongly reachable through native calls. Production renderer retains each render parameter/buffer owner and the native stress run completed repeated render/resize/lifecycle operations without corruption.
- [x] Protect render calls from context-disposal races. Concurrent native render, repeated resize, and disposal completed without exception or a stuck thread.
- [x] Verify callback lifetime and disposal behavior. Callback is strongly retained while active, explicitly unregistered before context free, released on disposal, and double disposal is safe.
- [x] Verify stride, format, allocation, and image conversion. **Limited evidence:** the native render experiment produced visible pixels at one resolution using the configured BGR0 path.
- [x] Test resize and multiple resolutions. Native rendering passed at 160x90, 640x360, and 321x181 after a visible 320x240 frame.

## 8.3 Player state machine

- [ ] Use a stream/session generation ID or cancellation token. **Source-reviewed only:** a load token exists, but no dedicated generation-order test was run.
- [ ] Prevent old timeouts from affecting new streams. **Not verified:** no deterministic stale-timeout test was run.
- [ ] Prevent old seeks from affecting new episodes. **Not verified:** no deterministic stale-seek regression test was run.
- [x] Prevent old end-file events from changing current state. Deterministic tests reject stale and unowned playlist-entry end events during replacement while accepting the active entry.
- [ ] Distinguish buffering from fatal failure. **Not verified:** no deterministic buffering/failure transition test was run.
- [ ] Cancel startup timeout after playback begins. **Source-reviewed only:** no controlled playback-start event test was run.
- [x] Cancel jobs on disposal. Shutdown cancels and joins every job that can enter JNA before freeing the handle; three native initialize/shutdown cycles and repeatable double shutdown passed.
- [ ] Release old episode resources. **Source-reviewed only:** no rapid episode-change resource test was run.
- [x] Prevent retry/render-context leaks. Three complete player lifecycles and three renderer create/destroy lifecycles passed, including double disposal.

## 8.4 Focused tests

- [x] Out-of-order stale event tests. Playlist-entry correlation tests cover stale, missing, active, and post-load end events.
- [ ] Buffering without false retry. **Not verified.**
- [ ] Genuine failure/retry test. **Not verified.**
- [x] Render during resize. Native concurrent render/resize stress passed.
- [x] Render during dispose. Native concurrent render/dispose stress passed and the render thread terminated within five seconds.
- [x] Repeated renderer create/destroy. Three native cycles passed, including repeatable disposal.
- [ ] Rapid episode changes. **Not verified.**
- [x] Callback after disposal. Disposal unregisters the native callback before freeing the context; post-disposal render returns null and repeated dispose is safe.
- [x] Zero/unknown duration. Compose transport tests verify unknown-duration and live media remain non-seekable without inventing a duration.
- [ ] Renderer initialization failure. **Not verified.**

## 8.5 Protected real-playback regression

After every MPV change:

- [ ] Load extension. **Not verified in this environment.**
- [ ] Search anime. **Not verified in this environment.**
- [ ] Select episode. **Not verified in this environment.**
- [ ] Start real stream. **Not verified in this environment.**
- [ ] Watch video and audio beyond 30 seconds. **Not verified in this environment.**
- [ ] Confirm no false retry screen. **Not verified in this environment.**
- [ ] Click timeline to seek. **Not verified in this environment.**
- [ ] Drag timeline. **Not verified in this environment.**
- [ ] Pause/resume. **Not verified in this environment.**
- [ ] Test Spacebar. **Not verified in this environment.**
- [ ] Test arrow seeking. **Not verified in this environment.**
- [ ] Change episode. **Not verified in this environment.**
- [ ] Retry a genuine failure. **Not verified in this environment.**

## Phase 8 gate

If any playback behavior regresses, stop immediately, preserve evidence, and fix or revert before continuing.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| 8.1 ABI/events | Partial | ABI/event snapshot tests pass; injected polling failure recovery is verified. A full buffering/fatal transition matrix remains open. |
| 8.2 Render/native memory | - [x] | ABI/layout, visible pixels, three resize targets, concurrent resize/render/dispose, callback unregistration, and repeated lifecycle checks passed against libmpv 0.41.0. |
| 8.3 Player state machine | Partial | Production-model playback/render/seek and repeated shutdown pass; stale end-event protection is tested. Stale timeout/seek and rapid remote episode switching remain open. |
| 8.4 Focused tests | Partial | Event-loop failure, stale end event, native rendering/lifecycle, local playback/seek, production model, and zero/live duration checks pass. Explicit renderer-init failure and genuine retry tests remain open. |
| 8.5 Real-playback regression | Partial | Automated live extension browse/search/details/episode/stream-to-libmpv tests pass, including HLS, DASH, and torrent. Manual GUI audio/watch/timeline/retry/episode-switch checks remain open. |
| Phase 8 gate passed | - [ ] | Open only for the remaining manual GUI real-playback and focused stale-timeout/retry/rapid-switch checks; all native changes in this run passed their focused regressions. |

Phase 8 completion annotations:

```text
Completed: 2026-08-02 (Europe/Dublin)
Actor: Codex (GPT-5)
Evidence: `./gradlew quickCheck --console=plain --stacktrace`; focused `quickTest` runs for `MPVEventLoopTest`, `PlayerScreenTest`, and `PlayerTransportControlsRobotTest`; `test --tests app.anikku.macos.player.MPVRenderExperiment --tests app.anikku.macos.player.MPVPlaybackTest`; post-change `StreamingEndToEndTest.full end-to-end - extension to mpv streaming playback` plus the earlier search/HLS/DASH/torrent runs.
Result: Deterministic gate passed 509 tests with 0 failures/errors and 3 documented skips; injected polling failure recovered; stale/unowned END_FILE events were rejected; all five native playback/render/seek/lifecycle tests passed together; visible frames rendered at four sizes; concurrent resize/render/dispose and repeated callback/context/player shutdown were safe; the production PlayerViewModel loaded, rendered, advanced, and sought seekable media; the post-change live extension-to-libmpv stream passed. Manual GUI audio/watch/retry/episode-switch evidence is still intentionally not claimed.
Files: `MPVEventLoop.kt`, `MPVLib.kt`, `MPVSoftwareRenderer.kt`, `MPVVideoSurface.kt`, `PlayerViewModel.kt`, and focused player/UI tests.
Proof artifact: Gradle reports under `build/reports/tests/` and XML under `build/test-results/`; terminal output in the active completion run.
```

```text
Completed: 2026-08-01 14:26:29 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `cd macos && ./gradlew --offline --no-daemon --console=plain compileKotlin compileTestKotlin test --tests 'app.anikku.macos.player.MPVAbiTest' --tests 'app.anikku.macos.ui.screens.player.PlayerScreenTest' --tests 'app.anikku.macos.player.MPVRenderExperiment' --tests 'app.anikku.macos.player.MPVPlaybackTest'`
Result: Production and test compilation succeeded. 57 tests were selected: 56 passed, 0 failed, and 1 was skipped (`MPVPlaybackTest > playback - local mp4 file plays and advances()`) because the native/local playback prerequisite was unavailable.
Files: `src/main/kotlin/app/anikku/macos/player/MPVLib.kt`, `src/main/kotlin/app/anikku/macos/player/MPVEventLoop.kt`, `src/main/kotlin/app/anikku/macos/player/PlayerViewModel.kt`, `src/test/kotlin/app/anikku/macos/player/MPVAbiTest.kt`, plus the existing renderer implementation/tests inspected for Phase 8 evidence.
Proof artifact: Gradle console output from the focused validation run; `git diff --check` also exited 0.
```

```text
Completed: 2026-08-01 14:26:29 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: MPV 0.41 ABI verification and deterministic `MPVAbiTest` coverage for event IDs, end-file reasons, 64-bit event payload decoding, null payloads, property/log payload snapshots, render constants, parameter layout, and the invalid terminator.
Result: The bindings now match the linked MPV 0.41 event/render ABI; transient property/log/end-file payloads are copied before asynchronous event emission. The event loop uses only copied values, avoiding dereferences of MPV-owned event memory after `mpv_wait_event` returns.
Files: `src/main/kotlin/app/anikku/macos/player/MPVLib.kt`, `src/main/kotlin/app/anikku/macos/player/MPVEventLoop.kt`, `src/test/kotlin/app/anikku/macos/player/MPVAbiTest.kt`
Proof artifact: Passing `MPVAbiTest` and successful `compileKotlin`/`compileTestKotlin` output from the command above.
```

```text
Completed: 2026-08-01 14:26:29 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Renderer implementation review and passing `app.anikku.macos.player.MPVRenderExperiment` in the focused Gradle command above.
Result: The implementation retains render-parameter `Memory`, serializes render/dispose, retains the callback reference, reallocates buffers on size changes, and converts BGR0 frames to `BufferedImage`. The native experiment produced visible pixels at one resolution. These implementation properties were source-reviewed; dedicated GC/lifetime, concurrent resize/dispose, callback-after-disposal, repeated lifecycle, and multiple-resolution tests remain unverified.
Files: `src/main/kotlin/app/anikku/macos/player/MPVSoftwareRenderer.kt`, `src/main/kotlin/app/anikku/macos/player/MPVVideoSurface.kt`, `src/test/kotlin/app/anikku/macos/player/MPVRenderExperiment.kt`
Proof artifact: Passing focused test output; no claim is made for the skipped local playback test.
```

```text
Completed: 2026-08-01 14:26:29 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `PlayerViewModel` source review and passing `PlayerScreenTest` in the focused Gradle command above.
Result: Source review found load-token timeout guards, shutdown cleanup, separate buffering/fatal states, playback-start timeout cancellation, and explicit end-file reason handling. No deterministic stale-timeout, stale-seek, stale-end-file, buffering-transition, initialized shutdown, or rapid episode-change regression tests were run, so the corresponding checkboxes remain open.
Files: `src/main/kotlin/app/anikku/macos/player/PlayerViewModel.kt`, `src/test/kotlin/app/anikku/macos/ui/screens/player/PlayerScreenTest.kt`
Proof artifact: Passing focused test output and final code review; no real-stream behavior is claimed.
```

---

# Phase 9 — Player controls and UI

Files:

```text
macos/src/main/kotlin/app/anikku/macos/ui/screens/player/PlayerScreen.kt
macos/src/main/kotlin/app/anikku/macos/ui/screens/player/PlayerControls.kt
```

- [x] Verify initial play/pause reflects actual MPV state. The production model derives state from observed mpv pause/cache properties, native playback reached PLAYING/BUFFERING, and Compose transport tests render the supplied authoritative state.
- [x] Verify timeline updates do not fight dragging.
- [x] Verify click-to-seek.
- [x] Verify drag-to-seek.
- [x] Verify left/right arrows. Compose key injection delivered -10/+10 second callbacks from the focused player root.
- [x] Verify Spacebar. Compose key injection toggled playback exactly once from the focused player root.
- [x] Verify focus restoration. The normal player root requests focus after attachment and the key-injection test proves it receives keyboard input without an extra click.
- [ ] Verify retry overlay focus and shortcuts. **Not verified:** no retry-overlay UI test was run.
- [x] Verify disabled controls.
- [ ] Verify zero/unknown duration and live streams. **Partial:** direct transport tests cover unknown duration and explicit live rendering/disabled seeking; integrated MPV/source live metadata was not verified.
- [ ] Verify text fields do not receive unintended player shortcuts. **Not verified:** no focused text-field propagation test was run.
- [x] Verify accessibility labels.
- [x] Add/update deterministic Compose/UI tests.
- [ ] Run the protected real-playback regression. **Not verified:** no real extension-to-MPV playback procedure was run for Phase 9.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| Controls behavior | Partial | Timeline click/drag, disabled controls, accessibility, non-seekable media, authoritative native state, Space, arrows, and root focus are directly tested; retry-overlay focus, text-field propagation, and integrated live metadata remain open. |
| UI tests | - [x] | Focused Compose/player tests pass, including keyboard injection, transport rendering, episode boundaries, click/drag seek callbacks, accessibility, unknown duration, live display, and disabled controls. |
| Real-playback regression | Partial | Automated extension-to-libmpv streaming passes; manual GUI video/audio/timeline/retry validation remains open. |
| Phase 9 gate passed | - [ ] Not verified | The gate remains open for retry-overlay focus, text-field isolation, integrated live metadata, and manual GUI real playback. |

Phase 9 completion annotations:

```text
Completed: 2026-08-02 (Europe/Dublin)
Actor: Codex (GPT-5)
Evidence: `quickTest` focused on `PlayerTransportControlsRobotTest`, `PlayerScreenTest`, and `MPVEventLoopTest`; native production-model playback/render/seek in `MPVRenderExperiment`; local `MPVPlaybackTest` exact seek.
Result: Focused root accepted Space, left/right seek, and up/down volume key events; click and drag seeking, unknown/live duration, disabled states, and accessibility remain green; production PlayerViewModel state, rendering, position advancement, and exact seek passed against libmpv 0.41.0. Retry-overlay/text-field and manual GUI checks remain open.
Files: `PlayerScreen.kt`, `PlayerControls.kt`, `PlayerTransportControlsRobotTest.kt`, `PlayerScreenTest.kt`, and native player tests.
Proof artifact: Gradle focused test output and reports under `build/reports/tests/`.
```

```text
Completed: 2026-08-02 01:24:07 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `cd macos && ./gradlew --offline --no-daemon --console=plain compileKotlin compileTestKotlin test --tests 'app.anikku.macos.ui.screens.player.PlayerControlsTest' --tests 'app.anikku.macos.ui.screens.player.PlayerTransportControlsRobotTest' --tests 'app.anikku.macos.ui.screens.player.PlayerScreenTest'`; `git diff --check`.
Result: BUILD SUCCESSFUL; production and test compilation passed; 74 tests passed, 0 failed, 0 skipped. The suite directly exercised transport play/pause rendering, episode navigation boundaries, rewind/forward behavior for seekable media, slider click and drag callbacks, accessibility semantics, unknown-duration non-seekability, explicit live labeling/non-seekability, disabled controls, playback-state badges, and time formatting. The test run emitted one existing Kotlin warning that a condition in `PlayerScreen.kt:1372` is always true.
Files: `src/main/kotlin/app/anikku/macos/ui/screens/player/PlayerScreen.kt`, `src/main/kotlin/app/anikku/macos/ui/screens/player/PlayerControls.kt`, `src/test/kotlin/app/anikku/macos/ui/screens/player/PlayerTransportControlsRobotTest.kt`, `src/test/kotlin/app/anikku/macos/ui/screens/player/PlayerControlsTest.kt`, `src/test/kotlin/app/anikku/macos/ui/screens/player/PlayerScreenTest.kt`.
Proof artifact: `macos/build/reports/tests/test/index.html`; JUnit XML files under `macos/build/test-results/test/`; Gradle problems report at `macos/build/reports/problems/problems-report.html`.
```

```text
Not verified: 2026-08-02 01:24:07 +0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: Phase 9 validation did not include native MPV integration, a real extension-to-MPV stream, deterministic keyboard/focus/text-field tests, a retry-overlay test, or a reliable integrated source/MPV live-duration signal.
Result: Those items remain unchecked and the Phase 9 gate remains open. No real-playback or native-state evidence was fabricated.
Files: No native MPV, stream-extraction, or shared-module changes were made for this Phase 9 continuation.
Proof artifact: Phase 9 evidence review and focused Gradle output above.
```

Scope note: This Phase 9 continuation changed only the player UI/control implementation, its focused Compose/UI test, and this Phase 9 checklist section. Phase 10 and all Android/shared modules were left untouched.

---

# Phase 10 — Secondary macOS features and lifecycle isolation

Inspect:

```text
macos/src/main/kotlin/app/anikku/macos/ui/screens/onboarding/
macos/src/main/kotlin/app/anikku/macos/ui/settings/
macos/src/main/kotlin/app/anikku/macos/ui/MacOSMenuBar.kt
macos/src/main/kotlin/app/anikku/macos/platform/discord/DiscordRPC.kt
```

## 10.1 Onboarding, settings, and menus

- [ ] Every visible button has an implemented action. **Partial: the tested onboarding, backup, settings, and native-menu actions are wired; a complete audit of every visible action is not verified.**
- [ ] Onboarding completes and resumes correctly. **Partial: completion and resumable-step callbacks are implemented and tested at the composable boundary; full application restart persistence is not independently verified.**
- [x] Settings persist after restart.

  Completed: 2026-08-02T01:39:00+0100
  Actor: Buffy (openai/gpt-5.6-luna)
  Evidence: `cd macos && ./gradlew --offline --no-daemon --console=plain test --tests 'app.anikku.macos.ui.settings.SettingsStateTest' --tests 'app.anikku.macos.platform.Phase7StorageLifecycleTest'`
  Result: Settings persistence tests and the concurrent preference restart regression passed; the focused Phase 10 run reported 47 passed, 0 failed, 0 skipped overall.
  Files: `src/main/kotlin/app/anikku/macos/ui/settings/SettingsState.kt`, `src/main/kotlin/app/anikku/macos/platform/preference/MacOSPreferenceStore.kt`, `src/test/kotlin/app/anikku/macos/ui/settings/SettingsStateTest.kt`, `src/test/kotlin/app/anikku/macos/platform/Phase7StorageLifecycleTest.kt`
  Proof artifact: `macos/build/test-results/test/` and `macos/build/reports/tests/test/`

- [ ] Invalid settings are rejected. **Partial: invalid values are safely normalized/clamped rather than rejected with validation errors.**

  Completed: 2026-08-02T01:39:00+0100
  Actor: Buffy (openai/gpt-5.6-luna)
  Evidence: `SettingsStateTest.invalid settings are normalized to safe bounds`; `SettingsStateTest.invalid persisted settings load with safe defaults`; `SettingsStateTest` focused execution.
  Result: Proxy ports are constrained to 1–65535, simultaneous downloads to 1–10, playback speed to finite 0.25–4.0x values, invalid proxy enum values fall back to DISABLED, and malformed persisted speed values fall back to normal speed. 18/18 SettingsState tests passed. The implementation normalizes values instead of rejecting the user's input, so the original rejection checkbox remains open.
  Files: `src/main/kotlin/app/anikku/macos/ui/settings/SettingsState.kt`, `src/test/kotlin/app/anikku/macos/ui/settings/SettingsStateTest.kt`
  Proof artifact: Gradle JUnit XML under `macos/build/test-results/test/`

- [ ] Menus work. **Partial: 11 deterministic menu tests passed for callback dispatch, quit/close, backup open, minimize/hide, zoom, attachment, and sidebar toggle; native macOS cross-application Hide Others/Show All and every menu item are not verified.**
- [ ] Dialogs close correctly. **Not verified: no complete native-dialog close workflow was run.**
- [ ] Backup/restore errors are visible. **Partial: restore read/parse failures return actionable `ImportResult` errors and existing UI toast paths display them; a UI-level failure-rendering test was not run.**
- [ ] Extension UI reflects actual state. **Not verified: no focused Phase 10 extension-UI state test was run.**
- [ ] Keyboard focus is usable. **Not verified: native focus traversal and text-field isolation were not manually or integration tested.**

## 10.2 Discord and optional services

- [ ] Discord connection failures do not block startup. **Not verified: the current Discord transport remains simulated and no real IPC failure was exercised.**
- [ ] Discord failures do not block playback. **Not verified: no Discord-disabled/failed integration playback run was performed.**
- [ ] Reconnect behavior is bounded. **Not verified: the current reconnect monitor has no deterministic failure-injection or bounded-attempt evidence.**
- [ ] Shutdown is safe. **Partial: the tested disconnected DiscordRPC stop path is safe; full application shutdown and real IPC teardown remain unverified.**

  Completed: 2026-08-02T01:39:00+0100
  Actor: Buffy (openai/gpt-5.6-luna)
  Evidence: `DiscordRPCTest.stop does not throw when not started`; application shutdown source review confirms `discordRPC.stop()` cancels reconnect/connection jobs, closes the WebSocket reference, clears it, and sets DISCONNECTED.
  Result: The deterministic stop test passed and shutdown is idempotent/non-throwing for the tested disconnected DiscordRPC lifecycle. The broader shutdown checkbox remains open because application-level shutdown and real Discord IPC teardown were not exercised.
  Files: `src/main/kotlin/app/anikku/macos/platform/discord/DiscordRPC.kt`, `src/main/kotlin/app/anikku/macos/AnikkuApplication.kt`, `src/test/kotlin/app/anikku/macos/platform/discord/DiscordRPCTest.kt`
  Proof artifact: Gradle JUnit XML under `macos/build/test-results/test/`

- [ ] Sensitive metadata is not exposed. **Not verified: no dedicated payload-redaction or live IPC inspection test was run.**

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| 10.1 UI features | - [ ] Partial | Settings persistence/validation and deterministic menu/onboarding/backup paths improved; native dialogs, complete action audit, focus, extension-state, and full restart/UI-error evidence remain open. |
| 10.2 Discord/optional services | - [ ] Partial | The disconnected DiscordRPC stop path is deterministically tested, but full application shutdown, simulated transport limitations, connection failure, playback isolation, bounded reconnect, and metadata evidence remain open. |
| Phase 10 gate passed | - [ ] Not verified | Phase 10 remains open pending native/UI integration evidence and replacement of the simulated Discord transport or an explicit supported/unavailable design. |

Phase 10 completion annotations:

```text
Completed: 2026-08-02T01:39:48+0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `cd macos && ./gradlew --offline --no-daemon --console=plain compileKotlin compileTestKotlin test --tests 'app.anikku.macos.ui.MacOSMenuBarFactoryTest' --tests 'app.anikku.macos.ui.settings.SettingsStateTest' --tests 'app.anikku.macos.ui.screens.onboarding.OnboardingScreenTest' --tests 'app.anikku.macos.platform.discord.DiscordRPCTest' --tests 'app.anikku.macos.platform.Phase7StorageLifecycleTest'`; `git diff --check`; mandatory code review.
Result: BUILD SUCCESSFUL; compilation succeeded; 47 tests passed, 0 failed, 0 skipped. Deterministic coverage includes 8 onboarding tests, 18 settings tests, 11 menu tests, 13 storage/backup/persistence lifecycle tests, and 7 Discord lifecycle tests. Diff check exited 0. Review found no new blocking issue in the tested changes, but confirmed that Discord transport remains simulated and native macOS/UI integration claims must remain open.
Files: `src/main/kotlin/app/anikku/macos/AnikkuApp.kt`, `src/main/kotlin/app/anikku/macos/platform/backup/MacOSBackupManager.kt`, `src/main/kotlin/app/anikku/macos/platform/discord/DiscordRPC.kt`, `src/main/kotlin/app/anikku/macos/ui/MainWindow.kt`, `src/main/kotlin/app/anikku/macos/ui/MacOSMenuBar.kt`, `src/main/kotlin/app/anikku/macos/ui/screens/onboarding/OnboardingScreen.kt`, `src/main/kotlin/app/anikku/macos/ui/settings/SettingsState.kt`, and focused tests.
Proof artifact: `macos/build/test-results/test/`, `macos/build/reports/tests/test/`, and the final `git diff --check`/review output.
```

```text
Scope note: This continuation changed only macOS Phase 10 implementation/tests plus this Phase 10 checklist section. Pre-existing unrelated macOS worktree changes were preserved; no Android/shared module or Phase 11 file was modified by this continuation.
```

---

# Phase 11 — Placeholder and unsupported-path audit

Run:

```bash
grep -RInE \
'TODO|FIXME|XXX|HACK|not implemented|placeholder|Phase [0-9]|return emptyList\(\)|return null|no-op|UnsupportedOperationException' \
macos/src/main/kotlin \
macos/keiyoushi-utils/src/main/kotlin \
macos/scripts
```

For every production macOS match:

- [x] Determine whether it is intentional.
- [ ] Implement it if it is user-visible and expected. **Partial: feasible Phase 11 user-visible gaps were addressed; remaining native/optional integrations stay explicitly unsupported.**
- [x] Disable the UI path if it cannot work.
- [x] Document internal limitations.
- [x] Replace silent empty/null behavior with explicit errors where appropriate. **Completed for the audited Discord and Dock paths; ordinary nullable failure results remain part of service APIs where callers already handle failure.**
- [x] Add a test or mark the path explicitly unsupported.

Do not alter intentional abstract extension hooks without checking all call sites.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| Placeholder inventory | - [x] | Complete audit performed; remaining matches were classified as compatibility stubs, API-contract guards, ordinary input placeholders, or explicit unsupported/native limitations. |
| User-visible placeholders resolved | - [ ] Partial | Fake Discord `CONNECTED` state was removed; Dock menu creation is disabled with an explicit warning; biometric limitation is explicit and PIN fallback remains available; appcast shell syntax and malformed summary output were repaired. Remaining custom shared API, native biometric/Discord IPC, and optional Dock action implementation remain open. |
| Intentional limitations documented | - [x] | Discord now remains disconnected when native Unix-socket transport is unavailable; Dock menu actions are not registered until Objective-C dispatch exists; biometric authentication documents the unsupported bridge and PIN fallback; SourceProxy synchronous methods remain explicit suspend-API contract guards. |
| Phase 11 gate passed | - [ ] Not verified | The audit and deterministic validation passed, but the gate remains open because native Discord IPC, native Dock action dispatch, biometric LocalAuthentication, and the shared custom-anime API are not implemented. |

Phase 11 completion annotation:

```text
Completed: 2026-08-02T01:52:26+0100
Actor: Buffy (openai/gpt-5.6-luna)
Evidence: `cd macos && for file in scripts/*.sh; do bash -n "$file" || exit 1; done`; `cd macos && ./gradlew --offline --no-daemon --console=plain compileKotlin compileTestKotlin test --tests 'app.anikku.macos.platform.discord.DiscordRPCTest' --tests 'app.anikku.macos.platform.MacOSDockManagerTest' --tests 'app.anikku.macos.platform.security.MacOSBiometricAuthTest' --tests 'app.anikku.macos.platform.torrent.TorrentServerBridgeTest' --tests 'app.anikku.macos.platform.sync.GoogleDriveRestClientTest' --tests 'app.anikku.macos.player.MacOSPipHandlerTest' --tests 'app.anikku.macos.platform.extension.ExtensionLoadingTest' --tests 'app.anikku.macos.platform.extension.ExtensionSecurityTest'`; `git diff --check`; production inventory grep; mandatory code review.
Result: BUILD SUCCESSFUL; compilation succeeded; 62 tests passed, 0 failed, 0 skipped. All macOS shell scripts passed `bash -n`; `git diff --check` exited 0. The audit distinguishes user-visible unsupported behavior from compatibility-only stubs and extension API-contract methods. Discord no longer reports a simulated connected state, Dock menu creation is explicitly disabled until action dispatch is wired, and biometric authentication explicitly falls back to PIN. The Phase 11 gate remains open for native integrations and the shared custom-anime API.
Files: `scripts/generate-appcast.sh`, `src/main/kotlin/app/anikku/macos/platform/discord/DiscordRPC.kt`, `src/main/kotlin/app/anikku/macos/platform/MacOSDockManager.kt`, `src/main/kotlin/app/anikku/macos/platform/security/MacOSBiometricAuth.kt`, `src/main/kotlin/app/anikku/macos/ui/settings/SettingsScreen.kt`, focused Phase 11 tests, and this Phase 11 checklist section.
Proof artifact: `macos/build/test-results/test/`, `macos/build/reports/tests/test/`, shell syntax output, inventory output, and final review output.
```

```text
Scope note: This continuation changed only Phase 11 audit targets/tests plus the Phase 11 section of `completeness.md`. Pre-existing unrelated macOS changes were preserved; no Android/shared module, Phase 10 checklist section, or Phase 12 file was modified by this continuation.
```

---

# Phase 12 — Full validation and CI/build review

## 12.1 Script validation

- [ ] Every tracked macOS shell script under `macos/` passes `bash -n`.
- [ ] Every tracked macOS Python script under `macos/` passes `python3 -m py_compile`.
- [ ] Generated Python caches are removed if unintended.
- [ ] Unsafe macOS script behavior has been reviewed.

Use macOS-scoped commands so this document does not audit or modify Android/shared tooling:

```bash
find macos -type f -name '*.sh' -print0 | while IFS= read -r -d '' file; do
    bash -n "$file" || exit 1
done

find macos -type f -name '*.py' -print0 | while IFS= read -r -d '' file; do
    python3 -m py_compile "$file" || exit 1
done
```

If `py_compile` creates `__pycache__` or `.pyc` files, remove only the unintended generated files under `macos/` and record that cleanup as separate evidence.

## 12.2 macOS build/test validation

- [ ] `compileKotlin` passes.
- [ ] deterministic macOS tests pass.
- [ ] relevant integration tests pass.
- [ ] `check` passes or failures are documented.
- [ ] native MPV/Sparkle limitations are documented.

## 12.3 CI and configuration review

Inspect:

```text
macos/build.gradle.kts
macos/settings.gradle.kts
macos/gradlew
.github/workflows/build_macos.yml
```

- [ ] Java/JVM versions are compatible.
- [ ] Kotlin/Compose versions are compatible.
- [ ] JNA/OkHttp/HTTP-server dependencies are compatible.
- [ ] MPV/Sparkle requirements are documented.
- [ ] CI paths and Gradle tasks are valid.
- [ ] CI does not silently ignore failures.
- [ ] CI produces useful reports.
- [ ] CI does not unexpectedly publish or deploy.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| 12.1 Scripts | - [ ] | |
| 12.2 Build/tests | - [ ] | |
| 12.3 CI/configuration | - [ ] | |
| Phase 12 gate passed | - [ ] | |

---

# Phase 13 — Final real-playback acceptance test

If the environment permits, perform this test after all changes:

- [ ] Start the macOS application.
- [ ] Load a real extension.
- [ ] Search for an anime.
- [ ] Select an episode.
- [ ] Start a real stream.
- [ ] Confirm video and audio.
- [ ] Watch beyond 30 seconds.
- [ ] Confirm no false retry screen.
- [ ] Click timeline to seek.
- [ ] Drag timeline to seek.
- [ ] Pause/resume.
- [ ] Test Spacebar.
- [ ] Test left/right arrows.
- [ ] Change episodes.
- [ ] Retry a deliberately failed stream.
- [ ] Stop playback and start another episode.
- [ ] Close/reopen the app if practical.

Record:

- extension/provider;
- anime and episode;
- macOS version;
- Java version;
- MPV version;
- whether Chrome/CDP was required;
- start and end times;
- exact actions;
- result;
- logs or screenshots with sensitive information redacted.

Evidence:

| Acceptance area | Status | Completion/evidence |
|---|---|---|
| Extension loading | - [ ] | |
| Search and episode selection | - [ ] | |
| Real video/audio playback | - [ ] | |
| Extended playback | - [ ] | |
| Timeline click/drag seeking | - [ ] | |
| Play/pause and keyboard controls | - [ ] | |
| Genuine failure/retry | - [ ] | |
| Episode switching | - [ ] | |
| Restart/shutdown | - [ ] | |
| Phase 13 gate passed | - [ ] | |

If this test cannot be performed, do not mark it complete. Record the limitation instead.

---

# Phase 14 — Final diff, cleanliness, and report

## 14.1 Final repository checks

- [ ] Run `git status --short`.
- [ ] Run `git diff --stat`.
- [ ] Run `git diff --summary`.
- [ ] Run `git diff --check`.
- [ ] Confirm only intended macOS files changed.
- [ ] Confirm no Android/shared files changed.
- [ ] Confirm no generated artifacts were added.
- [ ] Confirm no executable modes changed accidentally.
- [ ] Confirm no secrets, credentials, tokens, private keys, or personal paths were added.
- [ ] Remove unintended `__pycache__`, `.pyc`, temporary extension trees, downloaded artifacts, and local caches.

Check tracked artifacts:

```bash
git ls-files | grep -Ei \
'\.(apk|jar|dylib|so|a|class|pyc|zip)$|(^|/)(build|dist|repo|libs)/'
```

## 14.2 Final evidence review

- [ ] Every checked checkbox has a completion timestamp.
- [ ] Every checked checkbox has an actor.
- [ ] Every checked checkbox has reproducible evidence.
- [ ] Test results contain actual counts and exit status.
- [ ] Manual tests identify environment and exact actions.
- [ ] Unverified items remain unchecked.
- [ ] Blocked items explain the blocker.
- [ ] No evidence was fabricated.

## 14.3 Final report

Create a final report containing:

- [ ] Exact files changed.
- [ ] Exact issues fixed.
- [ ] Security issues fixed.
- [ ] Extension-loader changes.
- [ ] HTTP-server changes.
- [ ] Download-manager changes.
- [ ] MPV/player changes.
- [ ] UI/control changes.
- [ ] Updater changes.
- [ ] Storage/keychain/lifecycle changes.
- [ ] Tests added.
- [ ] Commands executed.
- [ ] Successful checks.
- [ ] Failed checks.
- [ ] Environment limitations.
- [ ] Native/integration tests not run.
- [ ] Remaining unsupported features.
- [ ] Remaining known risks.
- [ ] Behavior intentionally left unchanged to protect streaming.
- [ ] Evidence that real playback still works, or an explicit statement that it could not be verified.
- [ ] Evidence that only macOS scope was modified.

Final classification must be evidence-based:

- [ ] Production-ready
- [ ] Release candidate
- [ ] Development preview
- [ ] Experimental / not release-ready

Do not claim production readiness if native, integration, real-extension, updater, or real-playback boundaries remain unverified.

Evidence:

| Subphase | Status | Completion/evidence |
|---|---|---|
| 14.1 Repository clean | - [ ] | |
| 14.2 Evidence reviewed | - [ ] | |
| 14.3 Final report complete | - [ ] | |
| Final classification assigned | - [ ] | |

---

# Mandatory stop conditions

These are **incident flags**, not completion tasks. They must remain unchecked during a successful run. If any condition occurs, stop implementation immediately, preserve the evidence, and report the blocker. Tick the applicable flag only when that incident actually occurs, and add its dated proof to the evidence log.

- [ ] **Incident:** A fix breaks real streaming.
- [ ] **Incident:** An extension can no longer load.
- [ ] **Incident:** Anime search stops working.
- [ ] **Incident:** Episode selection stops working.
- [ ] **Incident:** Audio or video stops playing.
- [ ] **Incident:** Timeline seeking breaks.
- [ ] **Incident:** The macOS module no longer compiles.
- [ ] **Incident:** A security fix appears to require Android/shared-code changes.
- [ ] **Incident:** A test must be weakened to pass.
- [ ] **Incident:** A proposed fix is speculative and the issue is not reproducible.
- [ ] **Incident:** A broad rewrite of working playback code appears necessary.
- [ ] **Incident:** The behavior cannot be verified in the available environment.

When stopping, leave all non-occurring incident flags unchecked. For an occurring incident, tick only the applicable flag and add dated evidence explaining the failure or blocker. Do not mark an incident flag as complete merely because it was reviewed.

---

# Evidence log

Maintain this log throughout the work. Add one row for every completed checkbox or blocked item. Use one row per checkbox (or one uniquely identified row per blocked checkbox); do not delete prior entries.

| ID | Phase/subphase | Status | Completed date/time with timezone | Actor | Exact evidence/command/test | Result | Files changed | Proof artifact |
|---|---|---|---|---|---|---|---|---|
| | | | | | | | | |

---

# Final non-regression statement

Before declaring this plan complete, explicitly answer:

1. Can a real extension still be loaded?
2. Can an anime still be searched?
3. Can an episode still be selected?
4. Can a real stream still start?
5. Do video and audio still play?
6. Does playback continue beyond 30 seconds without a false retry screen?
7. Does timeline click-to-seek still work?
8. Does timeline dragging still work?
9. Do play/pause, Spacebar, and arrow controls still work?
10. Does retry still work for a genuinely failed stream?
11. Were only macOS files modified?
12. Which behaviors remain unverified?

A “yes” answer must be backed by the evidence log. If the answer is unknown, say “Not verified” and leave the applicable acceptance checkbox unchecked.
