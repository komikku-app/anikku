# Changelog — Anikku macOS Port

All notable changes to the macOS port of Anikku are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [1.0.5] — 2026-08-04

### History filter/sort + subtitle matching

- **History filters**: search box (title / episode) plus a Sort menu —
  Recently viewed, Oldest first, Title A→Z, Title Z→A, Episode number.
  Result count updates live; empty-search shows "no matches".
- **Season-aware subtitle matching tightened**: the episode matcher now
  understands Netflix-style continuation numbering — S2E1 (app reports "1")
  also matches files named "13" when Season 1 had 12 episodes (offset derived
  from AniList). It also stops false positives: "Season 2" markers and years
  like "(2025)" are no longer treated as episode numbers.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.0.4] — 2026-08-04

### Crash fixes

- Fixed History crash: `Key "-1730808111" was already used` when scrolling.
  Episode IDs are URL hashes that can collide across anime; the list now keys
  on the unique `(animeId, episodeId)` pair.
- Fixed "layouts are not part of the same hierarchy" crash when opening video
  settings (Subtitles) during playback. The root-level `SelectionContainer`
  made every `Text` selectable, including inside popups and the player's
  animated panels; a press across layout roots crashed Compose's selection
  manager. Copy actions already use explicit clipboard buttons, so the
  container was removed.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.0.3] — 2026-08-04

### Season-aware subtitle matching

- AniList resolution is now season-aware: the player picks the season whose
  episode range covers the requested episode (e.g. "Solo Leveling" episode 13
  resolves to Season 2, not Season 1). Falls back to the top title match for
  airing seasons and unknown episode counts.
- Removed the hardcoded `season_number=1` from OpenSubtitles searches, which
  would always match season 1 for multi-season shows.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.0.2] — 2026-08-04

### Subtitle credentials + resume fix

- Baked the developer's free Jimaku + OpenSubtitles credentials into the app
  (`gradle.properties` → generated `SubtitleDefaults.kt`) so automatic English
  subtitles work out of the box with zero end-user configuration.
- **Fixed resume playback**: the player now saves the watch position every 5s
  during playback (previously only on screen dispose, which app quit / force-quit
  could skip) and seeks back to the saved position when reopening an episode.
  Pause at 10:00, exit, reopen → resumes at 10:00.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.0.1] — 2026-08-03

### Maintenance release

- Added an `android.webkit.URLUtil` stub so more Keiyoushi extensions (e.g. StreamingCommunity) compile and load.
- Extended the extension build pipeline: okruextractor overload fix, per-extractor retry, cached-classpath fix, and support for newer `src/all` extensions (strips unsupported source-api overrides).
- Added new sources to the installed fleet: animexin, lmanime, subsplease, rouvideo, chineseanime, animeonsen, mkissa (allanime successor), animetsu, streamingcommunity.
- Added `scripts/refresh-extensions.sh` for repeatable fleet refresh + reporting.
- Fixed Sparkle auto-update startup: `SUPublicEDKey` now embeds the raw 32-byte
  Ed25519 key (not the DER/SPKI form Sparkle can't decode), which was causing
  "Unable to Check For Updates — the update checker failed to start correctly".
- Enabled Sparkle's `automaticallyChecksForUpdates` so silent background checks
  run on their own schedule instead of a manual `checkForUpdatesInBackground`.
- App version string now comes from `gradle.properties` (generated `AppInfo.kt`)
  instead of a hardcoded `1.0.0`, so internal version reporting matches the bundle.
- **Automatic English subtitles**: when a source serves no subtitle track, Anikku
  resolves the anime via AniList and fetches the exact episode's subs from Jimaku
  (anime-native database), cached per episode. OpenSubtitles.com is the fallback:
  the player's subtitle panel has a "Search OpenSubtitles…" entry that lists
  candidates English-first for the user to pick. Provider keys are baked in from
  `gradle.properties` (free tiers) and can be overridden in Settings (Keychain).
- **Smart offset**: manual subtitle-delay adjustments are remembered per anime and
  auto-applied to subsequent episodes.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

---

## [Unreleased] — 2026-08-02

### Completed desktop architecture

- Completed and live-tested the real extension browse/search → details →
  episodes → video resolution → libmpv play/seek flow, including playback past
  30 seconds, deliberate failure/retry, and switching to a second episode on
  the same player.
- Added versioned startup migrations, secure SyncYomi synchronization, full
  Google Drive desktop OAuth/backup restore, native Discord Desktop IPC,
  LocalAuthentication/Touch ID, actionable Dock controls, and Keychain-only
  proxy credentials.
- Added Android gzip/protobuf `.tachibk` migration import for library,
  categories, history/progress, and custom anime metadata while retaining the
  lossless versioned macOS JSON format.
- Added ADRs and a detailed libmpv JNA ABI/threading/ownership reference.

### Reliability and security

- Removed JVM-global insecure TLS behavior and retained normal certificate and
  hostname verification.
- Hardened extension package/signature validation, duplicate-source handling,
  atomic replacement/rollback, trust state, and classloader cleanup.
- Added atomic persistence and malformed-data recovery across preferences,
  repositories, downloads, backups, and tracker tokens.
- Hardened the local media server against traversal, unauthorized methods,
  invalid ranges, stale routes, and shutdown races.

### Playback and controls

- Corrected libmpv ABI/render parameters and made renderer callbacks, native
  memory, resize/disposal, and repeated player lifecycle safe.
- Added authoritative playback-state handling, stale end-event rejection,
  exact local seek behavior, and click/drag/keyboard transport tests.
- Revalidated real extension-to-libmpv playback plus HTTP, HLS, DASH, and
  magnet/torrent stream paths.
- Enabled safe hardware decoding with `hwdec=auto-copy-safe`, bounded image
  caches, adaptive lazy library rendering, and high-bitrate playback/seek/heap
  regression coverage.
- Removed the old no-libmpv simulated watch-progress path; playback now fails
  visibly instead of reporting progress without rendered media.

### Native torrent streaming

- Pinned TorrServer `MatriX.141.1` for Apple Silicon and Intel, with HTTPS
  download, architecture-specific SHA-256 verification, executable packaging,
  localhost API integration, and a native launch/API regression task.
- Select the largest recognized video file from torrent metadata and retain a
  bounded WebTorrent fallback when the native helper cannot prepare a stream.

### Updates, packaging, and validation

- Restricted update checks and release links to authenticated HTTPS, added
  semantic version ordering and bounded retries, and made fallback behavior
  informational rather than an unverified installer.
- Added deterministic Sparkle helper shutdown and fixed its feed-string
  lifetime; active signed-enclosure verification remains pending owner signing
  credentials and a release artifact.
- Produced and structurally verified an unsigned Apple-silicon DMG containing
  the runtime, libmpv, Sparkle framework/helper, and application icon.
- Made the live extension fleet test enforce its documented per-stage timeout,
  preventing a blocking third-party provider from hanging Gradle.

This remains a development preview only because Developer ID signing,
notarization, and an active Sparkle-signed release enclosure require release
owner credentials/artifacts. Those release operations were not fabricated.

## [1.0.0-beta.2] — 2026-07-29

### Extension System — Major Compatibility Milestone

- **58 extension JARs pre-installed** (up from 20), 33 verified end-to-end
- **Extension compatibility test** exercises all 58 extensions through Load → Browse → Episodes → Video URL stages with Chrome CDP Cloudflare bypass
- **Core module fix**: Preferences.kt context!! null-safety — unblocked extensions depending on core utilities
- **ACTUAL_PKG detection fix**: Ported dot-sort algorithm from single-build script to batch script — fixes extensions picking up subpackage names (e.g., `.dto` instead of base package)

### Chrome CDP Cloudflare Bypass

- **ChromeCDPClient**: Auto-launches headless Chrome with anti-detection (hidden `navigator.webdriver`, real User-Agent, hidden `navigator.plugins`)
- **Referer pass-through**: CDP `Page.navigate` now accepts and forwards `Referer` header — critical for WCO embed domains
- **CloudflareInterceptor**: Detects hard WAF blocks vs JS challenges, 3-attempt retry with cookie caching
- **FallbackDns**: Included in shared-libs.jar for CDP bypass at runtime
- **CDP diagnostics**: ExtensionCompatibilityTest captures CDP debug logs, WAF detection summaries

### WCO Video Extraction — Fixed

- **Root cause found**: WCO embed pages serve a 10-second announcement interstitial (not the video player)
- **Fix**: URL rewrite `index.php` → `video-js.php` bypasses the countdown and fetches the actual player page
- **Content-based extraction**: Domain-agnostic `iframeParse` with 3 fallback paths (getJSON/ajax API, universal m3u8, atob/base64 obfuscation)
- **Error-resilient debug dump**: HTML dumps to `/tmp/wco-iframe-dump-*.html` even when HTTP/CDP fails
- **Result**: All 6 WCO extensions (wcoanimedub, wcoanimesub, wcoforever, wcofun, wcostream, wcotv) now return 16 video URLs each

### Build Pipeline Improvements

- **Batch build**: Auto-discovers and compiles extractors individually (3-pass) — avoids single broken extractor blocking all others
- **lib-multisrc themes**: Compiled individually instead of as one blob — fixes multisrc extensions producing 1-9KB JARs
- **Extractor webview patches**: Non-null `onPageFinished`/`shouldInterceptRequest` overrides for JVM compatibility
- **Extension-specific patches**: superstream (CloudflareInterceptor injection), miruro (JVM stubs), animekhor (vidhideextractor), animenosub (autoUnpacker), luciferdonghua (okruextractor)
- **Python patching system**: `patch-wco-video-extraction.py`, `patch-miruro-sources.py`, `patch-hanime-source.py`, `patch-kissanime-source.py`

### JVM Compatibility Fixes

- `android.net.Uri.decode()` — Lenient URL decoding
- `android.util.LruCache` — Added import for CinebyExtractor
- `OkruExtractor` — Explicit lambda params for `extractFromDash` overload resolution
- `AniDB.kt` — `sortVideos` override with preserved `setDefaultValue`
- `DopeFlix.kt` — `MutableSet<String>` via `.toMutableSet()`
- `keiyoushi-utils Json.kt` — `parseAs` uses `body.string()` instead of `body.source()` for JVM
- `NextJs.kt` — Non-exhaustive `when` fix
- `Preference.kt` stub — Added `setDefaultValue` + `setEnabled`

### Other

- CDP Cloudflare bypass enabled for video CDN domains (fxpy7.watching.onl, etc.)
- `shared-libs.jar` rebuilt with ChromeCDPClient referer + FallbackDns + CloudflareInterceptor
- Backup & Restore panel with create/timeline/restore UI
- Menu bar actions wired (undo/redo, file operations)
- Extension test updated from 45 to 52 (now 58) extensions

---

## [1.0.0-beta.1] — 2026-07-11

### Phase 12 — Documentation & Final Polish

- Created `macos/CHANGELOG.md` documenting full development history
- Updated `macos/README.md` with honest status table (Phase 10 marked ❌ Not started)
- Updated project root `README.md` with macOS port section
- Updated `macos/INSTALL.md` with references to CHANGELOG and architecture plan
- All `macos/docs/` guides reviewed and updated
- Staged and committed all outstanding changes (both modified and untracked files)

### Phase 10 — Packaging (Not Started)

- App icon `.icns` exists at `macos/src/main/resources/icons/`
- `entitlements.plist` created with proper Hardened Runtime permissions
- Sparkle appcast template exists at `macos/src/main/resources/Sparkle/`
- Sparkle Ed25519 public key exists at `macos/src/main/resources/Sparkle/`
- `libmpv.2.dylib` bundled at `macos/src/main/resources/dist/`
- jpackage DMG/PKG packaging NOT yet configured in `build.gradle.kts`
- Code signing and notarization NOT yet set up

### Phase 11 — Testing

- 40+ test files created across all subsystems (UI, platform, player, auth, etc.)
- Extension loading integration tests (SampleExtension, AllAnime, Keiyoushi)
- End-to-end flow tests NOT yet run (Browse → Search → Play)

### Phase 9 — macOS Native Integration

- **Menu bar**: Full macOS menu bar (File, Edit, View, Playback, Window, Help) with
  ⌘ keyboard shortcuts — `MacOSMenuBarFactory.kt`
- **Keyboard shortcuts**: Global shortcuts handler for app-wide hotkeys —
  `GlobalKeyboardShortcuts.kt`
- **File picker**: AWT-based file/directory picker — `MacOSFilePicker.kt`
- **PiP**: Picture-in-Picture support via secondary always-on-top window —
  `MacOSPipHandler.kt`
- **Dock**: Badge count and dock menu (Play/Pause, Next Episode) —
  `MacOSDockManager.kt`
- **Full screen**: macOS fullscreen toggle — `MacOSFullScreen.kt`
- **Share**: macOS native share via clipboard — `MacOSShareUtil.kt`
- **Dark mode**: Automatic detection via `isSystemInDarkTheme()`
- **Touch Bar**: Not implemented

### Phase 8 — WebView Replacement

- Replaced Android WebView with system browser launch via
  `java.awt.Desktop.browse(URI)` — `BrowserLauncher.kt`

### Phase 7 — Advanced Features

- **Tracker sync**: OAuth login flows with local HTTP server for callback handling —
  `TrackerManager.kt`, `TrackerOAuthManager.kt`, `TrackerTokenStore.kt`,
  `OAuthServer.kt`
- **Google Drive sync**: REST API v3 client — `GoogleDriveRestClient.kt`
- **Discord Rich Presence**: WebSocket-based Discord RPC —
  `DiscordRPC.kt`
- **Biometric auth**: Touch ID via `LocalAuthentication.framework` with PIN
  fallback — `MacOSBiometricAuth.kt`
- **Torrent support**: TorrentServer API client — `TorrentServerBridge.kt`
- **Notifications**: macOS Notification Center integration —
  `MacOSNotificationManager.kt`
- **App update checker**: GitHub Releases API — `AppUpdateChecker.kt`
- **Crash reporting**: Local crash log with uncaught exception handler —
  `CrashReporter.kt`
- **Download manager**: Queue-based download system — `MacOSDownloadManager.kt`
- **UI Action Logger**: Development logging for UI interactions —
  `UIActionLogger.kt`

### Phase 6 — Video Player (mpv via JNA)

- **MPVLib**: Full JNA bindings to libmpv C API — `MPVLib.kt`
  - Core: `create()`, `destroy()`, `initialize()`, `command()`
  - Options: `setOptionString()`, `setPropertyString/Int/Double()`
  - Reads: `getPropertyString/Int/Double/Flag()`
  - Events: `observeProperty()`, `event()`, `waitEvent()`
  - Render API: `mpv_render_context_create()`, `render()`, `free()`
- **MPVEventLoop**: Coroutine-based event processing for mpv property changes
  and events — `MPVEventLoop.kt`
- **MPVSoftwareRenderer**: Offscreen FBO render context pulling RGBA frames for
  Compose rendering — `MPVSoftwareRenderer.kt`
- **MPVVideoSurface**: Compose composable wrapping the software renderer for
  video display — `MPVVideoSurface.kt`
- **PlayerViewModel**: Central player state machine managing:
  - Playback lifecycle (IDLE → LOADING → PLAYING → PAUSED → ENDED → ERROR)
  - Position tracking with periodic updates
  - Volume, speed (0.25x–4.0x), fullscreen
  - Audio/subtitle track selection and delay
  - Video equalizer (brightness, contrast, saturation, gamma)
  - Aspect ratio, rotation, horizontal/vertical flip
  - Screenshot capture
  - mpv initialization with locale-safe configuration
- **Utils**: Time formatting utilities — `Utils.kt`
- **PlayerPreferences**: mpv config file management — `PlayerPreferences.kt`
- **MacOSHttpServer**: NanoHTTPd-based local HTTP server for streaming video to
  mpv — `MacOSHttpServer.kt`
- **FFmpegBridge**: FFmpeg binary wrapper for screenshot/transcoding —
  `FFmpegBridge.kt`

### Phase 5 — Screen-by-Screen UI

- **Library screen**: Grid/list view with category filtering, search, sort —
  `LibraryTab.kt`
- **Updates screen**: Grouped updates by date, mark all seen, refresh —
  `UpdatesTab.kt`
- **History screen**: Chronological history list with clear and resume —
  `HistoryTab.kt`
- **Browse/Sources screen**: Source list with language icons, search, extension
  management — `BrowseTab.kt`, `SourceBrowseScreen.kt`, `ExtensionsScreen.kt`
- **Anime Detail screen**: Cover image, info header, episode list with seen/
  bookmark/download/play — `AnimeDetailScreen.kt`
- **Player screen**: Full player UI with controls, settings panels, keyboard
  shortcuts — `PlayerScreen.kt`, `PlayerControls.kt`, `PlayerSettings.kt`
- **Settings screen**: 15 sub-screens (Appearance, Library, Downloads, Player
  Advanced, Data & Storage, Security, Tracking, Connections, etc.) —
  `SettingsScreen.kt`, `SettingsState.kt`
- **Tracker settings**: MAL/AniList/Kitsu login panels — `TrackerSettingsPanel.kt`
- **Download queue**: LazyColumn-based queue with download management —
  `DownloadQueueScreen.kt`
- **Stats screen**: Anime watching statistics — `StatsScreen.kt`
- **Onboarding**: First-launch setup flow — `OnboardingScreen.kt`
- **About dialog**: Version info, update check, credits — `AboutDialog.kt`

### Phase 4 — UI Framework & Navigation

- **Theme system**: Material 3 with 20 color schemes:
  Base, Cottoncandy, Cloudflare, Doom, GreenApple, Lavender, Matrix,
  MidnightDusk, Mocha, Monet, Nord, Sapphire, Strawberry, Tachiyomi, Tako,
  TealTurqoise, TidalWave, YinYang, Yotsuba, CustomColorScheme
- **Voyager navigation**: Tab navigator with per-tab inner navigators preventing
  ClassCastException on tab switch when non-Tab screens are on stack
- **NavigationRail**: Desktop left sidebar with 5 tabs (Library, Updates,
  History, Browse, More)
- **AnimatedTabFade**: Fade transition when switching tabs
- **Components**: Scrollbar, VerticalFastScroller, SettingsItems, AdaptiveSheet,
  AnimeCoverImage, CommonAnimeItem, MacOSToast, VideoQualityBadge,
  PlaybackStateBadge, OfflineBadge, ErrorUi, OfflineCheckmarkAnimation

### Phase 3 — Networking & Source API

- **MacOSNetworkHelper**: OkHttp client with cache, brotli, DoH, logging
  interceptors
- **MacOSCookieJar**: Persistent cookie store via `java.net.CookieManager`
- **MacOSExtensionLoader**: URLClassLoader-based extension loading with:
  - META-INF/extension.json parsing
  - SHA-256 trust verification
  - Shared dependency JAR injection
  - Per-source class instantiation with resilient error handling
- **MacOSExtensionManager**: Extension lifecycle management:
  - Installed extension scanning and loading
  - Trust store management (auto-trust on first launch, persistent
    `trusted_extensions.json`)
  - Source enumeration and deduplication
  - Extension install from repo URLs
  - Load state tracking (`StateFlow<ExtensionState>`)
- **ReflectiveSourceProxy**: Reflection-based source proxy for calling extension
  methods with hoster-based fallback for getVideoList
- **Source API stubs**: Source, CatalogueSource, ConfigurableSource, SAnime,
  SEpisode, Video, AnimesPage, Hoster, AnimeFilterList, etc.
- **DexClassLoader**: APK-to-JAR compatibility layer (d2j-dex2jar-based)
- **Android stubs for extension compatibility**:
  - `android.content.Context` — File-based directory access
  - `android.util.Base64` — Delegates to `java.util.Base64`
  - `android.util.Log` — Delegates to SLF4J
  - `android.os.Bundle`, `android.os.Looper`, `android.os.Handler` —
    JVM-compatible stubs
  - `android.graphics.Bitmap`, `android.graphics.BitmapFactory` —
    Delegates to AWT/Apache Commons
  - `android.net.Uri` — Delegates to `java.net.URI`
  - `android.annotation.SuppressLint` — With `AnnotationTarget.CLASS`
  - `android.text.InputType` — Common constants
  - `org.json.JSONObject`, `org.json.JSONArray` — Full JVM stubs
  - `eu.kanade.tachiyomi.animeextension.BuildConfig` — TMDB_API stub

### Phase 2 — Domain & Data Layer

- **MacOSStorageManager**: Download/backup management with file system
- **MacOSCustomAnimeRepository**: File-backed anime CRUD
- **LibraryRepository**: JSON-backed library persistence
- **HistoryRepository**: JSON-backed watch history
- **DownloadRepository**: Download tracking with state management

### Phase 1 — Core Infrastructure

- **Koin DI**: 3 modules (Platform, Domain, App) replacing Android's Injekt
- **MacOSPreferenceStore**: JSON-file backed preferences
- **MacOSDatabaseDriver**: SQLDelight JDBC SQLite driver
- **MacOSLogger**: SLF4J/Logback logging to file and console
- **AnikkuApplication**: Full app lifecycle (init, focus, blur, shutdown)
- **BackgroundTaskScheduler**: Coroutine-based replacement for Android WorkManager
- **CrashReporter**: Thread.setDefaultUncaughtExceptionHandler + Sentry log
- **Injekt bridge**: Koin delegation for extension compatibility

### Phase 0 — Project Scaffolding

- Created `macos/` directory structure as standalone Compose Desktop Gradle project
- Configured Gradle build with JVM toolchain 17, Compose Multiplatform 1.11.1
- Version catalog (`libs.versions.toml`) with all desktop-compatible deps
- Settings management with JitPack, Maven Central, Google repositories
- Built source-api-jvm.jar and common-jvm.jar from Android KMP modules
- Entry point (`AnikkuApp.kt`) with Window management

---

## [0.9.0] — 2026-07-10

### Build pipeline

- Created `batch-build-keiyoushi-from-source.sh` — builds 51 English extensions
  from yuzono/anime-extensions source as JVM JARs
- Created `patch-hanime-source.py` — removes Chicory/WASM/WebView files,
  patches Hanime.kt to use NativeSignatureProvider exclusively
- Created `patch-kissanime-source.py` — adds hosterListSelector/hosterFromElement
  stubs, fixes putString null-safety
- Created `patch-hoster-stubs.py` — generalized hoster stubs for all extensions
- Created `copy-extension-deps.sh` — copies extension runtime dependencies
- Created `e2e-test.sh` — basic end-to-end test script
- Extension compilation tracking: 10/51 English extensions build from source

### Extension fixes

- Fixed `SuppressLint` annotation for `object` declarations (hanime Base64Helper)
- Added `InputType` constants stub for hanime edit-text preferences
- Added `addEditTextPreference` / `getSwitchPreference` to keiyoushi-utils
- Created `BuildConfig.kt` stub for mapple extension
- Created `JSONObject.kt` / `JSONArray.kt` stubs for miruro extension
- Created `org.json` stubs for extensions needing JSON parsing
- Fixed `PreferenceScreenExt.kt` with both addSwitchPreference and
  getSwitchPreference stubs
- Hosted stubs generalized for all multisrc extensions

## [0.8.0] — 2026-07-09

### Extension system overhaul

- Rebuilt `MacOSExtensionLoader.kt` with proper shared dependency JAR injection
- Fixed class loader hierarchy to properly resolve source-api classes
- Added `findSharedLibsDir()` with multi-path search (dev vs bundled .app)
- Made source loading resilient — individual class failures skip rather than
  fail the entire extension
- Fixed `SourceProxy.kt` with hoster-based getVideoList fallback
- Fixed `MacOSExtensionManager.kt` with auto-trust on first launch
- Fixed `deployBundledExtensions` to compare by pkgName, not filename
- Deployed 20 extension JARs to extensions directory

## [0.7.0] — 2026-07-08

### Player & mpv

- Created `MPVLib.kt` — Complete JNA bindings to libmpv C API
- Created `MPVEventLoop.kt` — Coroutine-based mpv event processing
- Created `MPVSoftwareRenderer.kt` — Offscreen FBO render context
- Created `MPVVideoSurface.kt` — Compose composable for video display
- Created `PlayerViewModel.kt` — Full player state machine (1200+ lines)
- Created `PlayerScreen.kt` — Complete player UI with controls, settings panels
- Created `PlayerControls.kt` — Play/pause, seek, volume, speed controls
- Created `PlayerSettings.kt` — Advanced player settings panels
- Created `Utils.kt` — Time formatting utilities
- Created `PlayerPreferences.kt` — mpv config file management
- Created `MacOSHttpServer.kt` — NanoHTTPd local video streaming server
- Created `FFmpegBridge.kt` — FFmpeg binary wrapper

## [0.6.0] — 2026-07-07

### Screen-by-screen UI porting

- Ported all main screens: Library, Updates, History, Browse, More
- Created `AnimeDetailScreen.kt` with full source API integration
- Created all settings screens (15 sub-screens)
- Created `SourceBrowseScreen.kt` with search/browse
- Created `ExtensionsScreen.kt` with extension management
- Created `DownloadQueueScreen.kt`
- Created `StatsScreen.kt`
- Created `OnboardingScreen.kt`
- Created `AboutDialog.kt`
- Created all UI components

## [0.5.0] — 2026-07-06

### UI framework & navigation

- Set up Voyager tab navigator with per-tab inner navigators
- Created `MainWindow.kt` with desktop NavigationRail sidebar
- Ported 20 Material 3 color schemes
- Created `AnikkuTheme.kt` with dynamic theming
- Created all custom UI components

## [0.4.0] — 2026-07-05

### Network & extension loading

- Created `MacOSNetworkHelper.kt` with OkHttp client
- Created `MacOSCookieJar.kt` with persistent cookie storage
- Created `MacOSExtensionLoader.kt` — URLClassLoader-based loading
- Created `MacOSExtensionManager.kt` — extension lifecycle management
- Created `SourceProxy.kt` — reflection-based source proxy
- Created all Android stubs for extension compatibility

## [0.3.0] — 2026-07-04

### Core infrastructure

- Set up Koin DI (Platform, Domain, App modules)
- Created `MacOSPreferenceStore.kt` (JSON-backed)
- Created `MacOSDatabaseDriver.kt` (SQLDelight JDBC)
- Created `MacOSLogger.kt` (SLF4J/Logback)
- Created `BackgroundTaskScheduler.kt` (WorkManager replacement)
- Created `AnikkuApplication.kt` (app lifecycle)
- Created storage layer (`MacOSStorageProvider`, `MacOSStorageManager`)

## [0.2.0] — 2026-07-03

### Project scaffolding

- Created `macos/` directory as standalone Compose Desktop Gradle project
- Configured build system with version catalog
- Built source-api and common JVM JARs from Android KMP modules
- Set up Compose Multiplatform Desktop plugin
- Created minimal entry point (`AnikkuApp.kt`) with empty window
- Created `macos/settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`

## [0.1.0] — 2026-07-02

### Initial planning

- Created `architectural_rework_for_macos.md` — comprehensive 12-phase port plan
- Analyzed 1,071 Kotlin files across all Android modules
- Catalogued Android import usage across 17 Android packages (~800 import lines)
- Documented architecture decisions (Koin, mpv JNA, Compose Desktop, etc.)
- Created risk register and migration map (Appendix C, D)
- Designed build approach: standalone `macos/` module + shared source references

---

## Upcoming

### Phase 10 — Packaging & Distribution (planned)

- jpackage DMG packaging configuration
- App code signing and notarization
- Sparkle auto-updater wiring
- GitHub Actions CI for macOS builds
- Homebrew formula

### Phase 11 — Testing & Polish (planned)

- Run full end-to-end test: Browse → Search → Anime Detail → Play
- Fix runtime extension loading errors
- Fix mpv video rendering bugs
- Performance optimization for large libraries
- Memory profiling during extended playback

---

*For the full architecture plan and migration guide, see
[architectural_rework_for_macos.md](../architectural_rework_for_macos.md).*
