# Anikku macOS completion report

Completed: 2026-08-02 (Europe/Dublin)

Classification: **implementation complete; ad-hoc-signed preview**

## Outcome

The macOS port and the desktop architecture described in
`../architectural_rework_for_macos.md` are implemented. The application builds,
loads the installed extension fleet, completes browse → anime → episode → stream
→ native libmpv playback, renders frames, seeks, retries a failed stream, and
switches episodes without replacing the player. It packages as a verified
Apple-silicon DMG whose mounted application launches and exits normally.

No Android or shared-module source was changed. No push, release upload,
Developer ID signing, notarization, or deployment was performed.

## Implemented architecture

- A standalone Compose Desktop application with macOS menus, Dock actions,
  notifications, file opening, deep links, keyboard shortcuts, onboarding,
  crash reporting, and versioned startup migrations.
- Trusted JVM extension discovery, signature and metadata checks, isolated
  classloaders, atomic install/update rollback, installed-fleet compatibility
  reporting, normal TLS validation, system-browser authentication, and targeted
  Chrome DevTools fallback for providers that require browser challenges.
- A real libmpv JNA player and software Render API surface with HLS, DASH,
  request headers, subtitles, audio and video track selection, exact seeking,
  retry, episode switching, progress persistence, and bounded shutdown. There is
  no simulated playback or fabricated completion path.
- Native torrent streaming through a pinned, checksum-verified, bundled
  TorrServer binary, plus bounded legacy WebTorrent compatibility and process
  cleanup.
- Atomic local repositories for library, categories, history, progress,
  downloads, settings, and custom domains; lossless native backups and Android
  gzip/protobuf `.tachibk` import.
- Keychain-only tracker, proxy, Discord, Google Drive, and SyncYomi secrets;
  native LocalAuthentication/Touch ID; Discord IPC; Google Drive OAuth and
  resumable upload; SyncYomi synchronization; and actionable Dock integration.
- Sparkle framework integration with HTTPS feed validation, a public Ed25519
  key, native helper lifecycle, semantic version handling, and safe update
  checks. An active signed enclosure is deliberately absent until a real release
  artifact and the matching private key are available.
- Performance controls for paged and filtered libraries, bounded image caches,
  high-bitrate playback, renderer reuse, and deterministic native cleanup.
- Architecture, build, install, migration, libmpv/JNA, contribution, and ADR
  documentation matching the implemented code.

## Verification evidence

Environment:

- macOS 26.5.2 (25F84), Apple Silicon/arm64
- OpenJDK 17.0.19, Gradle 8.14.3, Kotlin 2.0.21
- mpv/libmpv 0.41.0
- 53 installed JVM extension JARs

Successful checks:

- `./gradlew quickCheck --no-daemon`: build, deterministic tests, Sparkle
  configuration, and TorrServer configuration passed after the final packaging
  change.
- `./gradlew check --no-daemon`: 599 tests across 72 suites, 0 failures,
  0 errors, and 4 documented skips; completed in 15m52s.
- Installed-fleet run: 53/53 extensions loaded, 30 returned popular browse data,
  27 returned episodes, and 13 returned video URLs in 634 seconds. These are
  dated third-party-provider observations, not permanent availability claims.
- Live playback: direct extension-to-mpv and search-to-episode flows passed;
  HLS and DASH passed; video and audio tracks were detected; playback advanced
  31.4 seconds; exact seek moved 32.2s → 42.2s; a genuine failed stream emitted
  `END_FILE(error)`; retry recovered; and a second episode started on the same
  player handle.
- Native renderer: all four production MPV render experiments passed, including
  visible pixels, real play/render/seek, repeated initialization, and repeated
  renderer disposal. High-bitrate playback and heap-budget coverage passed.
- Torrent coverage: Nyaa magnet discovery and bounded no-peer handling passed
  without a lingering process. `./gradlew nativeTorrServerTest --no-daemon`
  separately launched the bundled TorrServer on loopback and exercised its
  current JSON API.
- Every tracked shell script passed `bash -n`; every tracked Python script passed
  `py_compile` with its cache outside the repository.
- `packageDmg`, `verifyPackage`, and `hdiutil verify` passed. Verification covers
  Info.plist metadata, the bundled runtime and launcher, libmpv, Sparkle,
  Touch ID helper, the pinned MatriX.141.1 arm64 TorrServer checksum and execute
  bit, and structural bundle signing.
- Mounted-DMG smoke passed: native helper permissions were executable, deep
  signature verification passed, the packaged app remained healthy after
  launch, and normal application quit terminated it cleanly.
- Local links in all 20 tracked macOS Markdown documents passed validation.

Expected skips in the full check are three Keiyoushi APK-only fixtures that are
not provisioned locally and the separately gated native TorrServer integration,
which passed when invoked explicitly.

## Artifact

```text
build/compose/binaries/main/dmg/Anikku-1.0.0.dmg
size: 214170148 bytes
SHA-256: ad883de029e21d7108a95a7341151933b9f9582c5d5622b21a2312f0bc96279f
architecture: Apple Silicon/arm64
signature: ad-hoc/development structural signature
```

## Remaining external release operations

- Sign the application and DMG with the owner's Developer ID certificate and
  submit them to Apple for notarization and stapling.
- Generate a Sparkle Ed25519 signature for the final hosted artifact with the
  owner's private key, publish an HTTPS enclosure, and validate the production
  appcast end to end.
- Produce and verify a separate Intel package if Intel distribution is desired;
  the delivered artifact is arm64.
- Re-run live extension compatibility before each release because remote source
  behavior, anti-bot challenges, DNS, TLS, and torrent seeding change outside
  this repository.

These items require owner credentials, hosted release infrastructure, another
target architecture, or mutable third-party services; they are not missing
application implementations.
