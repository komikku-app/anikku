# Anikku macOS architecture

This document describes the implemented desktop architecture. The original
[rework plan](../../architectural_rework_for_macos.md) remains the requirements
record; the ADRs below record where implementation choices became concrete.

## Runtime flow

```text
Compose UI
  ├─ repositories (library/history/downloads/preferences)
  ├─ extension manager → trusted JVM source JAR → browse/episodes/videos
  ├─ player → libmpv JNA → software Render API → Compose Image
  └─ platform adapters
       ├─ Keychain / LocalAuthentication / Dock / notifications
       ├─ browser + loopback OAuth / Chrome CDP
       ├─ Google Drive / SyncYomi / Discord IPC / Sparkle
       └─ bundled TorrServer → WebTorrent fallback
```

`AnikkuApplication` owns long-lived services and their shutdown order. Compose
owns window, navigation, and screen state. Koin connects these boundaries; JVM
extension binaries receive the small compatibility surface they expect.

## Decision records

- [ADR-0001: Isolated Compose Desktop module](adr/0001-compose-desktop-module.md)
- [ADR-0002: Native libmpv through JNA and the Render API](adr/0002-libmpv-jna-render-api.md)
- [ADR-0003: Trusted JVM extension artifacts](adr/0003-trusted-jvm-extensions.md)
- [ADR-0004: Atomic files and Keychain-only secrets](adr/0004-storage-and-secrets.md)
- [ADR-0005: System browser, loopback OAuth, and targeted Chrome CDP](adr/0005-web-and-oauth.md)
- [ADR-0006: Bundled native helpers with verified fallbacks](adr/0006-native-helpers.md)

The detailed player ABI, threading, and ownership contract is in
[MPV-JNA.md](MPV-JNA.md).

## Data ownership

Repositories persist versioned JSON under `~/Library/Application Support/Anikku`
using temporary-file plus atomic-move replacement. Preferences use the same
discipline. The SQLDelight JDBC database exists for shared database contracts,
while the macOS UI repositories remain the source of truth for desktop state.

Credentials never enter those files. Tracker, Google Drive, SyncYomi, proxy,
and app-lock secrets use Keychain. Backups filter credential-shaped preference
keys. Native macOS backups are versioned JSON for lossless round trips; Android
gzip/protobuf `.tachibk` is a migration input format.

## Release boundary

The package contains a Java runtime, libmpv, Sparkle and biometric helpers,
TorrServer, entitlements, notices, and the icon. `verifyPackage` checks those
components, the TorrServer checksum, and bundle signature structure. Developer
ID signing, notarization, and a signed Sparkle enclosure require release-owner
credentials and artifacts and are intentionally separate operations.
