# ADR-0006: Bundled native helpers with verified fallbacks

- Status: Accepted
- Date: 2026-08-02

## Context

Playback, Touch ID, Sparkle, and performant torrent streaming require native
components that macOS/JDK do not provide directly. Silent downloads or
unbounded child processes would make packaging and shutdown unsafe.

## Decision

Bundle libmpv and build the Swift LocalAuthentication/Sparkle bridges during
packaging. Download the architecture-specific pinned TorrServer release over
HTTPS, verify its SHA-256, and stage it executable in the app. Prefer TorrServer
for magnet playback and fall back to a bounded WebTorrent process. Own every
helper's startup, readiness deadline, output drain, and process-tree shutdown.

## Consequences

Installed users need no Homebrew/native setup. Builds need network access for a
first TorrServer/Sparkle provisioning. Native artifacts and licensing notices
increase package size; `verifyPackage` and native integration tests are release
gates.
