# Anikku macOS completion report

Completed: 2026-08-02 (Europe/Dublin)

Actor: Codex (GPT-5)

Classification: **Development preview**

## Outcome

The macOS application builds, passes its deterministic and full local test
suites, loads the installed extension fleet, searches and resolves episodes,
starts real streams through libmpv, renders visible frames, seeks, and packages
as a verified Apple-silicon DMG. The protected Android/shared modules were not
changed.

This is not classified as production-ready or a release candidate. A final
manual GUI session confirming audible playback beyond 30 seconds, retry and
episode switching is still outstanding. Developer ID signing, notarization,
and a Sparkle-signed enclosure also require owner credentials and release
infrastructure that were not supplied.

## Issues fixed

- Removed JVM-global trust-all TLS behavior and retained normal certificate and
  hostname verification.
- Hardened extension metadata/signature/package validation, duplicate source-ID
  rejection, trust state, atomic updates/rollback, classloader cleanup, and
  first-time untrusted-install preservation.
- Made the installed-fleet compatibility test enforce its 30-second stage
  deadline even when a third-party source blocks inside synchronous work.
- Hardened the local media server's path/range/method/route/lifecycle behavior
  and the download manager's path containment, atomic completion, retry,
  cancellation, deduplication, and shutdown behavior.
- Added atomic, malformed-safe persistence for preferences, repositories,
  backup state, downloads, and tracker tokens; retained Keychain-only token
  storage without silent plaintext fallback.
- Corrected libmpv ABI/render constants and stabilized event snapshots,
  callbacks, native memory ownership, resize/disposal races, repeated player
  lifecycle, stale end events, native seeking, and Compose transport controls.
- Fixed torrent startup's ineffective timeout: child output is drained on a
  daemon thread, the deadline is enforced independently, and the process tree
  is terminated on timeout or shutdown.
- Restricted updater traffic and release links to HTTPS, added semantic version
  comparison/bounded retry, prevented unverified fallback installation, and
  made Sparkle native shutdown/feed-string ownership deterministic.
- Corrected unsupported Dock, Discord, biometric, documentation, packaging, and
  updater claims instead of presenting simulated or unavailable behavior as
  working.
- Updated macOS CI to use deterministic plus controlled native/integration
  gates and to retain HTML/XML test reports even when a job fails.

## Verification evidence

Environment:

- macOS 26.5.2 (25F84), Apple Silicon/aarch64
- OpenJDK 17.0.19, Gradle 8.14.3
- mpv/libmpv 0.41.0
- 53 installed JVM extension JARs

Successful checks:

- `./gradlew quickCheck`: 511 tests, 0 failures, 0 errors, 3 documented
  skips; Sparkle configuration valid with an HTTPS feed and zero active signed
  enclosures.
- `./gradlew check`: 542 tests across 58 suites, 0 failures, 0 errors, 3
  documented skips; exit 0 in 13m33s.
- Installed-fleet compatibility: 53/53 extensions loaded, 32 returned browse
  data, 28 returned episodes, and 14 returned video URLs; exit 0 in 9m41s.
  Individual remote-provider HTTP/DNS/TLS/timeouts remain visible in the report
  and are not represented as application successes.
- Real stream coverage: both extension-to-libmpv workflows passed, including
  search to episode to streaming playback. HLS and DASH passed. Nyaa magnet
  startup passed with a bounded no-peer outcome and no remaining webtorrent
  process.
- Native MPV coverage passed for local playback/position advance, visible
  production rendering, exact seek, resize, concurrent render/dispose,
  callback disposal, and repeated renderer/player initialization and shutdown.
- Compose player coverage passed for click/drag seeking, Space, arrow seek,
  volume keys, disabled/live/unknown-duration states, and accessibility labels.
- Every tracked macOS shell script passed `bash -n`; every tracked macOS Python
  script passed `py_compile` with an external cache prefix. Six pre-existing
  untracked `.pyc` files under `macos/scripts/__pycache__` were removed.
- `buildSparkleHelper` and `validateSparkleConfiguration` passed; the rebuilt
  helper is bundled. Actual enclosure signature verification is blocked by the
  absent matching private key and signed release artifact.
- `packageDmg -Pcompose.desktop.packaging.checkJdkVendor=false`,
  `verifyPackage`, and `hdiutil verify` passed. The bundle contains its runtime,
  universal libmpv library, Sparkle framework/helper, HTTPS feed keys, and icon;
  its ad-hoc bundle signature is structurally valid.
- Packaged-app smoke: the `.app` loaded 53 extensions with zero untrusted,
  initialized Sparkle, initialized the application, and exited 0 via a normal
  macOS quit. No Anikku/webtorrent process remained.

Artifact:

```text
build/compose/binaries/main/dmg/Anikku-1.0.0.dmg
size: 182902240 bytes
SHA-256: f09d7f7f06154f83fc9afa51c68995fbcc28c41ee4fc416304520cb67e13c1c0
```

Expected/diagnostic failures encountered and resolved:

- The first plain `packageDmg` invocation rejected the Homebrew JDK vendor.
  The documented Compose vendor-check override produced the verified local
  package; CI uses Temurin 17.
- The first full check exposed metadata-mismatched repository fixtures and a
  valid first-time untrusted-install rollback bug. Matching fixtures and the
  production install result were fixed and focused tests passed.
- A subsequent check exposed ineffective coroutine timeouts around blocking
  third-party extension calls. A timed daemon-backed harness completed the full
  53-extension sweep.
- The next check reached a silent webtorrent child and exposed the production
  blocking-read timeout bug. The production lifecycle fix, two deterministic
  regressions, a real focused torrent run, quickCheck, and the final full check
  all passed afterward.
- Intermediate compilation failures while introducing the two timeout helpers
  were corrected before commits; final production/test compilation is green.

## Remaining boundaries and known risks

- No manual GUI acceptance run confirmed audible real-stream playback beyond
  30 seconds, false-retry absence for that duration, episode switching, or a
  deliberately failed-stream retry. Automated native/live/Compose evidence is
  strong but is not relabeled as manual evidence.
- The DMG is Apple-silicon and not Developer ID signed or notarized. Intel
  packaging has not been produced or verified.
- The repository has a valid Sparkle public key and secure feed structure but
  no active signed enclosure; no signature was fabricated.
- Native Discord IPC, LocalAuthentication/Touch ID, actionable Dock menu
  dispatch, and the custom shared-anime API remain explicitly unavailable or
  partial.
- Real concurrent Keychain CLI access and a complete native-dialog/focus/action
  audit remain unverified.
- Third-party providers change independently. Fleet figures above are a dated
  observation, not a permanent compatibility guarantee.

## Scope and exact files

No Android or shared-module source file changed. The only file outside
`macos/` is the macOS-specific workflow `.github/workflows/build_macos.yml`.
The implementation commit sequence before this final report is:

```text
7da441fbd docs(macos): record completion checklist and verified baseline
e336405d9 fix(macos): harden extension lifecycle and live streaming gates
2857ff574 fix(macos): stabilize native playback and controls
9623b3291 fix(macos): harden lifecycle storage and updates
93362d036 fix(macos): preserve untrusted extension installs
78977ce6e test(macos): enforce extension stage timeouts
eebba6b79 fix(macos): bound torrent stream startup
```

The final documentation, icon attributes/asset, and macOS CI/report update are
committed together with this file, avoiding a self-referential hash claim.

Exact changed-file categories:

- Build/release/CI/docs: `.github/workflows/build_macos.yml`,
  `macos/.gitattributes`, `macos/build.gradle.kts`, `macos/gradle/libs.versions.toml`,
  `macos/BUILDING.md`, `macos/CHANGELOG.md`, `macos/INSTALL.md`,
  `macos/README.md`, `macos/completeness.md`, `macos/COMPLETION-REPORT.md`,
  `macos/src/main/resources/icons/app.icns`,
  `macos/src/main/resources/Sparkle/appcast.xml`,
  `macos/src/main/resources/dist/Frameworks/libSparkleHelper.dylib`, and
  `macos/src/main/swift/SparkleHelper.swift`.
- Scripts: `macos/scripts/batch-build-keiyoushi-from-source.sh`,
  `build-keiyoushi-from-source.sh`, `build-sparkle-helper.sh`,
  `generate-appcast.sh`, and `patch-miruro-sources.py`.
- Production Kotlin: `AnikkuApp.kt`, `AnikkuApplication.kt`,
  `MacOSDockManager.kt`, `TrackerManager.kt`, `TrackerTokenStore.kt`,
  `MacOSBackupManager.kt`, `DownloadRepository.kt`, `HistoryRepository.kt`,
  `LibraryRepository.kt`, `MacOSCustomAnimeRepository.kt`, `DiscordRPC.kt`,
  `MacOSDownloadManager.kt`, `MacOSExtensionLoader.kt`,
  `MacOSExtensionManager.kt`, `MacOSHttpServer.kt`, `InsecureSSLHelper.kt`
  (deleted), `MacOSPreferenceStore.kt`, `MacOSBiometricAuth.kt`,
  `MacOSKeychain.kt`, `MacOSAtomicFile.kt`, `MacOSStorageManager.kt`,
  `MacOSStorageProvider.kt`, `AppUpdateChecker.kt`, `SparkleUpdater.kt`,
  `MagnetStreamer.kt`, `MPVEventLoop.kt`, `MPVLib.kt`,
  `MPVSoftwareRenderer.kt`, `MPVVideoSurface.kt`, `PlayerViewModel.kt`,
  `MacOSMenuBar.kt`, `MainWindow.kt`, `AboutDialog.kt`,
  `OnboardingScreen.kt`, `PlayerControls.kt`, `PlayerScreen.kt`,
  `PlayerSettings.kt`, `TrackerDetailScreen.kt`, `TrackerListScreen.kt`,
  `SettingsScreen.kt`, `SettingsState.kt`, `TrackerSettingsPanel.kt`, and the
  macOS compatibility model `eu/kanade/tachiyomi/extension/model/Extension.kt`.
- Tests: `Phase7StorageLifecycleTest.kt`, `MacOSDownloadManagerTest.kt`,
  `ExtensionCompatibilityTest.kt`, `ExtensionRepoIntegrationTest.kt`,
  `ExtensionSecurityTest.kt`, `MacOSHttpServerTest.kt`,
  `SecureTlsDefaultsTest.kt`, `AppUpdateCheckerTest.kt`, `MPVAbiTest.kt`,
  `MPVEventLoopTest.kt`, `MagnetStreamerTest.kt`, `MPVPlaybackTest.kt`,
  `MPVRenderExperiment.kt`, `StreamingEndToEndTest.kt`,
  `MacOSMenuBarFactoryTest.kt`, `OnboardingScreenTest.kt`,
  `PlayerScreenTest.kt`, `PlayerTransportControlsRobotTest.kt`, and
  `SettingsStateTest.kt`.

Streaming extraction, source contracts, and Android/shared behavior were left
unchanged unless a verified macOS compatibility or lifecycle defect required a
targeted macOS fix. No push, release upload, signing, notarization, or deployment
was performed.
