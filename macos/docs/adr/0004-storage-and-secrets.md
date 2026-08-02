# ADR-0004: Atomic files and Keychain-only secrets

- Status: Accepted
- Date: 2026-08-02

## Context

Desktop state must survive interruption and malformed input. OAuth tokens,
passwords, and app-lock material must not leak into preference files, backups,
or logs.

## Decision

Persist repositories and preferences as versioned JSON with synchronized
mutation, temporary files, fsync/close, and atomic replacement. Preserve
malformed originals for diagnosis. Store credentials only in macOS Keychain;
fail visibly if secure storage is unavailable and never fall back to plaintext.
Use LocalAuthentication for Touch ID with a salted PBKDF2 PIN record as fallback.

Use `.anikku_backup.json` for lossless macOS backup/restore. Accept Android
gzip/protobuf `.tachibk` as migration input and map supported library/category/
history/progress/custom metadata into desktop repositories.

## Consequences

Crashes cannot normally leave partially written JSON, and secure-store outages
do not silently weaken protection. macOS backups preserve desktop-only fields;
Android imports intentionally exclude credentials and platform preferences.
