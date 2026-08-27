# Anikku macOS Port — Comprehensive Architectural Rework Plan

> **Source of Truth** for remaking the Anikku Android app into a native macOS desktop application.
>
> **Date:** July 7, 2026  
> **Source:** `github.com/komikku-app/anikku` (1,071 Kotlin files, 15+ Gradle modules)  
> **Target:** macOS desktop app via Compose Multiplatform + JVM  
> **Strategy:** Same repo, separate `macos/` top-level directory alongside existing Android modules

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Architecture Decisions](#architecture-decisions)
3. [Phase 0: Project Scaffolding & Build System](#phase-0-project-scaffolding--build-system)
4. [Phase 1: Core Infrastructure Layer](#phase-1-core-infrastructure-layer)
5. [Phase 2: Domain & Data Layer Porting](#phase-2-domain--data-layer-porting)
6. [Phase 3: Networking & Source API](#phase-3-networking--source-api)
7. [Phase 4: UI Framework & Navigation](#phase-4-ui-framework--navigation)
8. [Phase 5: Screen-by-Screen UI Porting](#phase-5-screen-by-screen-ui-porting)
9. [Phase 6: Video Player — mpv Integration](#phase-6-video-player--mpv-integration)
    - [Phase 6.3b: VLCJ Fallback — Feasibility Study](#phase-63b-vlcj-fallback--feasibility-study)
10. [Phase 7: Advanced Features Porting](#phase-7-advanced-features-porting)
11. [Phase 8: WebView Replacement](#phase-8-webview-replacement)
12. [Phase 9: macOS Native Integration](#phase-9-macos-native-integration)
13. [Phase 10: Packaging & Distribution](#phase-10-packaging--distribution)
14. [Phase 11: Testing & Polish](#phase-11-testing--polish)
15. [Phase 12: Documentation & Final Cleanup](#phase-12-documentation--final-cleanup)
16. [Appendix A: Full Dependency Map](#appendix-a-full-dependency-map)
17. [Appendix B: File-by-File Migration Guide](#appendix-b-file-by-file-migration-guide)
18. [Appendix C: Risk Register](#appendix-c-risk-register)
19. [Appendix D: Complete Android Import Migration Map](#appendix-d-complete-android-import-migration-map)
    - [D.1 Import Inventory by Package](#d1-import-inventory-by-package)
    - [D.2 File-by-File Migration Map — By Module](#d2-file-by-file-migration-map--by-module)
    - [D.3 Framework & Dependency Replacement Reference](#d3-framework--dependency-replacement-reference)
    - [D.4 Summary Statistics](#d4-summary-statistics)

---

## Executive Summary

### What We're Building

A native macOS `.app` bundle that runs the Anikku anime watching application, preserving **every feature that has a macOS equivalent**. The app will use Compose Multiplatform for Desktop for the UI layer, share the existing domain/data Kotlin logic virtually unchanged, and replace Android-specific subsystems (mpv player, WebView, storage, biometrics, etc.) with macOS-native equivalents.

### Key Metrics

| Metric | Value |
|---|---|
| Source files to touch | ~1,071 `.kt` files |
| Files reusable as-is (PASS_THROUGH) | ~400 (37%) — zero Android imports |
| Files needing import changes only (COMPOSE_UPDATE) | ~300 (28%) — `androidx.compose` → `org.jetbrains.compose` |
| Files needing partial rewrites (STUB / REPLACE_LIB) | ~241 (23%) — some Android imports alongside portable code |
| Files needing full rewrite (REWRITE / DROP) | ~130 (12%) — Activities, Services, Views, BroadcastReceivers |
| Files to create new | ~100 (macOS native wrappers, build config, JNA bindings) |
| **Android import lines across all files** | **~800 lines** across **17 Android packages** |
| **Estimated total effort with LLM** | **3–6 weeks** (~156-268 hours) |
| **Target first build** | End of Phase 0 (build compiles, empty window) |
| **Target feature-complete build** | End of Phase 8 |

### Phased Delivery Approach

Each phase produces a **compilable, runnable** state. No phase should be left in a broken build state.

---

## Architecture Decisions

### AD-01: Repo Strategy — Same Repo, Separate `macos/` Directory

**Decision:** Create a `macos/` directory at the repository root containing the entire macOS project as a standalone Compose Multiplatform Desktop project, sharing source code via relative path references to the existing Android modules' source directories.

**Rationale:**
- **Visibility:** Everything lives in one repo. You can diff `app/src/` against `macos/src/` to see what's ported and what's missing.
- **Independence:** The macOS project has its own Gradle build, its own dependencies, and its own source sets. It does not interfere with the Android build.
- **Shared source:** Domain, data, and source-api Kotlin files are referenced directly (not copied), so fixes propagate both ways.
- **Git-friendly:** One commit can update shared logic for both platforms.

**Structure:**
```
anikku/
├── app/                    # Android app (untouched)
├── domain/                 # Shared domain logic (untouched)
├── data/                   # Shared data logic (untouched)
├── source-api/             # Shared source API (untouched)
├── source-local/           # Shared local source (untouched)
├── core/                   # Shared core modules (untouched)
├── presentation-core/      # Shared presentation utilities (untouched)
├── core-metadata/          # Shared metadata (untouched)
├── flagkit/                # Shared flag kit (untouched)
├── i18n/                   # Shared i18n (untouched)
├── i18n-kmk/               # Shared i18n (untouched)
├── i18n-ank/               # Shared i18n (untouched)
├── i18n-sy/                # Shared i18n (untouched)
├── telemetry/              # Telemetry (partially shared)
│
└── macos/                  # *** NEW: macOS Compose Desktop project ***
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradle.properties
    ├── gradle/
    │   └── libs.versions.toml
    └── src/
        └── main/
            ├── kotlin/
            │   └── app/anikku/macos/
            │       ├── AnikkuApp.kt          # Entry point
            │       ├── MainWindow.kt          # Window management
            │       ├── di/                    # macOS-specific DI modules
            │       ├── platform/              # Platform abstractions
            │       │   ├── StorageProvider.kt
            │       │   ├── FilePicker.kt
            │       │   ├── NotificationManager.kt
            │       │   └── ...
            │       ├── player/                # macOS mpv integration
            │       │   ├── MacOSMPVPlayer.kt
            │       │   ├── MPVLib.kt          # JNA bindings
            │       │   └── ...
            │       ├── ui/                    # macOS Compose UI
            │       │   ├── theme/
            │       │   ├── screens/
            │       │   ├── components/
            │       │   └── navigation/
            │       └── ...
            └── resources/
                ├── icons/
                ├── icns/
                └── ...
```

### AD-02: Build System — Simplified Gradle Leveraging Existing KMP Structure

**Decision:** Use a Compose Multiplatform Desktop Gradle project that adds `desktopMain` source sets to existing KMP modules (where `commonMain`/`androidMain` already exist), and creates a new `macos/` top-level module for platform-specific UI and entry point code.

**IMPORTANT DISCOVERY:** The project already uses Kotlin Multiplatform `expect`/`actual` declarations! `source-local/` has `commonMain` and `androidMain` source sets with `expect class LocalSource`, `expect class LocalCoverManager`, etc. `source-api/` has `expect class PreferenceScreen`. We should extend this pattern with `desktopMain` source sets rather than creating standalone replacement classes.

**Rationale:**
- The project isn't purely Android — it already has KMP architecture with `expect`/`actual` for platform-specific code.
- Adding `desktopMain` as a source set to existing modules follows the established pattern.
- The `macos/` directory serves as the application entry point module, UI layer, and home for macOS-native implementations of `actual` declarations.
- This avoids duplicating shared logic and keeps the door open for future Windows/Linux (`windowsMain`, `linuxMain`).

**Build approach — leverage existing KMP modules with `desktopMain` sources:**

For modules that already have `commonMain`/`androidMain`:
```
source-local/
├── src/
│   ├── commonMain/    # Already exists — expect declarations
│   ├── androidMain/   # Already exists — actual implementations
│   └── desktopMain/   # *** NEW *** — desktop actual implementations
```

For the macos app entry point (new module):
```kotlin
// macos/build.gradle.kts
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    // Reference shared modules (domain, data, source-api, etc.)
    // These will have their desktopMain sources compiled in
    implementation(project(":domain"))       // Pure Kotlin — already desktop-compatible
    implementation(project(":data"))          // Needs desktopMain for DB driver
    implementation(project(":source-api"))    // Has expect/actual — needs desktopMain
    implementation(project(":source-local"))  // Has expect/actual — needs desktopMain
    implementation(project(":core:common"))   // Needs desktopMain for preferences/storage
    implementation(project(":presentation-core")) // Needs desktopMain for some components
    // etc.
}
```

### AD-03: Video Player — Full libmpv via JNA + Render API

**Decision:** Replace `mpv-android` (`is.xyz.mpv.*`) with a custom JNA wrapper around macOS's native `libmpv`, using `mpv_render_context` for video rendering (NOT the `wid`/window-handle approach). Keep the exact same player UI, settings, and feature set.

**Rationale:**
- The app has 146+ references to mpv across the player subsystem. The player is deeply integrated with custom settings panels, subtitle management, audio track selection, equalizer, etc.
- libmpv is cross-platform and well-maintained. macOS ships with excellent video decode hardware acceleration.
- JNA (Java Native Access) provides a clean, Kotlin-friendly binding to libmpv's C API without requiring NDK/JNI toolchains.
- The existing `MPVLib`, `Utils`, `BaseMPVView` classes from the Android code provide an exact API surface to replicate.
- **CRITICAL:** The classic `wid` property approach (passing a native window handle to mpv) does NOT work on macOS. We MUST use `mpv_render_context_create()` with the Render API, creating a Metal-backed or OpenGL-backed surface (via `CAMetalLayer` or `NSOpenGLView`) and passing it to libmpv. This is more complex but is the only supported path on macOS.

### AD-04: UI Design — Hybrid: Android Layout + macOS Chrome

**Decision:** Keep the internal Compose layouts from the Android app, but wrap them in macOS-native window chrome, native menu bar (File, Edit, View, etc.), macOS-standard keyboard shortcuts (⌘Q, ⌘W, ⌘,, etc.), and proper window management.

**Rationale:**
- Users get a familiar macOS feel (menus, shortcuts, window behavior) without redesigning every screen.
- The Android Compose screens are already well-designed and functional. Recreating them identically but with macOS window dressing is the fastest path.
- macOS users expect ⌘-key shortcuts, a menu bar, and proper window resizing behavior. These are additive changes, not rewrites.

### AD-05: Feature Scope — Maximum Fidelity

**Decision:** Port every feature that has a macOS equivalent. Explicitly:

| Feature | Port Strategy | Phase |
|---|---|---|
| Source browsing + anime info | Direct port | 5 |
| Library management | Direct port | 5 |
| Video playback (mpv) | Replace with JNA libmpv | 6 |
| Subtitle management | Port via mpv | 6 |
| Download manager | Port with file system adapter | 5 |
| Tracker sync (MAL, AniList, etc.) | Direct port (HTTP-based) | 5 |
| Backup/restore | Port with file picker adapter | 5 |
| Extension system | Port with packaging changes | 7 |
| SyncYomi cloud sync | Port HTTP-based sync | 7 |
| Google Drive sync | Replace Android SDK with REST API | 7 |
| Discord Rich Presence | Port WebSocket implementation | 7 |
| Biometric lock | Replace with macOS Touch ID | 7 |
| Torrent support | Port TorrServer | 7 |
| Local HTTP video server | Port NanoHTTPd (already a dependency) | 6 |
| Library auto-update | Replace WorkManager with coroutines | 5 |
| Cast (Google Cast) | **DROP** — no macOS equivalent | — |
| Android TV mode | **DROP** — not applicable | — |
| Shizuku | **DROP** — Android system API | — |
| Firebase/Crashlytics | Replace with Sentry or local logging | 7 |
| App auto-update | Replace with Sparkle framework | 10 |
| Widget (Android widget) | **DROP** — not applicable | — |
| PiP (Picture-in-Picture) | macOS native PiP via AVKit bridge | 9 |

---

## Phase 0: Project Scaffolding & Build System

**Goal:** Create the `macos/` project, configure Gradle, get an empty Compose Desktop window to launch.

### Phase 0.1: Create Project Directory & Gradle Wrapper

**Files to create:**
- `macos/settings.gradle.kts` — Root project settings
- `macos/build.gradle.kts` — Main build configuration
- `macos/gradle.properties` — JVM and Compose properties
- `macos/gradle/libs.versions.toml` — Version catalog (desktop-compatible subset of existing catalogs)

**Key decisions:**
- Kotlin JVM target: 17 (matches macOS JDK availability)
- Compose Multiplatform version: latest stable (check `org.jetbrains.compose` plugin)
- Include only desktop-compatible dependencies from the original catalogs

**Dependencies to carry over (desktop-compatible):**
- `kotlinx-coroutines-core` + `kotlinx-coroutines-swing` (for macOS EDT integration)
- `kotlinx-serialization-json` + `kotlinx-serialization-protobuf`
- `okhttp` (all modules — fully JVM-compatible)
- `okio` (JVM-compatible)
- `jsoup` (JVM-compatible)
- `sqldelight` with `sqlite-3-38-dialect` + `coroutines-extensions-jvm` + native SQLite driver
- `coil3` (Compose Desktop compatible — check for `coil-compose-desktop` artifact)
- `voyager-navigator` + `voyager-screenmodel` + `voyager-tab-navigator` + `voyager-transitions`
- `markdown-core` + `markdown-coil`
- `material-kolor` (for dynamic theming on desktop)
- `haze` (blur effects — check desktop support)
- `swipe` (check desktop support)
- `reorderable` (check desktop support)

**Dependencies to DROP (Android-only):**
- All `androidx.*` libraries (replaced with desktop equivalents or custom implementations)
- `conscrypt-android` (macOS Java already has TLS 1.3)
- `shizuku-api` / `shizuku-provider`
- `leakcanary-plumber`
- `firebase-*`
- `google-play-services-*`
- `android-shortcut-gradle`
- `desugar_jdk_libs`

**Dependencies to REPLACE:**
- `sqldelight-android-driver` → `sqldelight-native-driver` or `sqldelight-sqlite-driver` (JVM)
- `coil-network-okhttp` → same but check artifact name for desktop
- Image decoder (Android-specific) → use Coil's built-in JVM decoder
- Subsampling scale image view → Compose Desktop equivalent or custom

### Phase 0.2: Create Windows & Linux Version Catalogs (Forward Compatibility)

**Rationale:** Although targeting macOS first, structure the build to accept platform-specific dependencies for future Windows/Linux support. Use `expect`/`actual` declarations where needed.

**Files to create:**
- `macos/gradle/macos.versions.toml`
- Placeholder comments for `windows.versions.toml` and `linux.versions.toml`

### Phase 0.3: Configure Compose Desktop Plugin

**Files to modify/create:**
- `macos/build.gradle.kts` — Apply `org.jetbrains.compose` plugin
- Configure `compose.desktop.application` block:
  - `mainClass = "app.anikku.macos.AnikkuAppKt"`
  - Native distributions: `.dmg`, `.pkg` for macOS
  - App icon: `.icns` file
  - JVM args: `-Xmx2G` (video playback needs memory), `-Dapple.awt.application.appearance=system`

### Phase 0.4: Create Minimal Entry Point

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/AnikkuApp.kt`

```kotlin
// Minimal launchable app — validates the build works
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Anikku",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        Text("Anikku macOS — Build Successful!")
    }
}
```

### Phase 0.5: Verify Build — First Compilation

**Actions:**
- Run `./gradlew :macos:compileKotlin`
- Run `./gradlew :macos:run` — should open an empty window
- Fix any dependency resolution errors
- Fix any source directory path issues
- Document any `Unresolved reference` errors from shared source directories (these are expected and will be resolved in later phases)

### Phase 0.6: Git Setup

**Actions:**
- Create `macos/.gitignore` (Gradle build outputs, IDE files, native libs)
- Ensure shared source directories are not duplicated in git
- First commit: "feat(macos): Initial Compose Desktop project scaffold"

---

## Phase 1: Core Infrastructure Layer

**Goal:** Port all foundational infrastructure so that Phase 2+ code can compile.

### Phase 1.1: Dependency Injection — Replace Injekt with Koin

**Problem:** The app uses `injekt` (a custom DI library by `mihonapp`). It relies on `android.app.Application` context injection. It must be replaced or adapted for desktop.

**Decision: Replace Injekt with Koin for the macOS project.**

**Rationale:**
- Injekt is a niche, custom library with questionable JVM Desktop compatibility.
- Koin is a mature, well-maintained Kotlin DI framework with explicit Compose Desktop support.
- The migration is mechanical: `Injekt.get<T>()` → `koinInject<T>()`, `Injekt.importModule()` → `koinApplication { modules(...) }`.

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/di/AppModule.kt`
- `macos/src/main/kotlin/app/anikku/macos/di/DomainModule.kt`
- `macos/src/main/kotlin/app/anikku/macos/di/PlatformModule.kt`

**Migration mapping:**
| Injekt Pattern | Koin Equivalent |
|---|---|
| `Injekt.get<Foo>()` | `getKoin().get<Foo>()` or `koinInject<Foo>()` |
| `Injekt.importModule(AppModule(this))` | `module { includes(appModule) }` |
| `addSingletonFactory { Foo(bar) }` | `single { Foo(get()) }` |
| `addFactory { Foo(bar) }` | `factory { Foo(get()) }` |

**Files affected (search all `import uy.kohesive.injekt` and `Injekt.get`):**
- ~200+ files across `app/`, `data/`, `domain/` source directories
- These files are in the shared source directories — we have two options:
  1. **Stub Injekt:** Create a thin JVM-compatible Injekt implementation that delegates to Koin (minimal changes to shared files)
  2. **Replace everywhere:** Find and replace all Injekt usage with Koin (cleaner but touches many files)

**Decision: Stub Injekt for Phase 1, migrate gradually.** Create `macos/src/main/kotlin/uy/kohesive/injekt/` with wrapper classes that delegate to Koin. This lets shared code compile immediately with zero changes. Phase 2+ can migrate file by file.

### Phase 1.2: Preference/Storage System

**Problem:** The app uses `tachiyomi.core.common.preference.AndroidPreferenceStore` backed by Android `SharedPreferences`, and `tachiyomi.core.common.storage.AndroidStorageFolderProvider` backed by Android's Storage Access Framework.

**Solution: Replace with file-based implementations.**

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/preference/MacOSPreferenceStore.kt`
  - Backed by a JSON file (`~/Library/Application Support/Anikku/preferences.json`)
  - Implement `PreferenceStore` interface from `tachiyomi.core.common.preference`
  - Uses `kotlinx.serialization` for reading/writing

- `macos/src/main/kotlin/app/anikku/macos/platform/storage/MacOSStorageProvider.kt`
  - Base directory: `~/Library/Application Support/Anikku/`
  - Subdirectories: `downloads/`, `backups/`, `extensions/`, `logs/`, `covers/`, `data/`
  - Implement `FolderProvider` interface from `tachiyomi.core.common.storage`

**Files to search and create expect/actual for:**
- `core/common/src/main/kotlin/tachiyomi/core/common/preference/PreferenceStore.kt` — Interface definition (already exists)
- `core/common/src/main/kotlin/tachiyomi/core/common/preference/AndroidPreferenceStore.kt` — Android impl → create `JvmPreferenceStore.kt`
- `core/common/src/main/kotlin/tachiyomi/core/common/storage/FolderProvider.kt` — Interface definition
- `core/common/src/main/kotlin/tachiyomi/core/common/storage/AndroidStorageFolderProvider.kt` → create `MacOSStorageProvider.kt`

### Phase 1.3: Database — SQLDelight Migration

**Problem:** The app uses `app.cash.sqldelight.driver.android.AndroidSqliteDriver` which requires Android Context.

**Solution:** Replace with `app.cash.sqldelight.driver.jdbc.JdbcSqliteDriver` or the native SQLite driver.

**Files to create/modify:**
- `macos/src/main/kotlin/app/anikku/macos/platform/database/MacOSDatabaseDriver.kt`
  - Use `JdbcSqliteDriver` (or `NativeSqliteDriver`) 
  - DB path: `~/Library/Application Support/Anikku/data/anime.db`
  - Create `DatabaseHandler` equivalent that works on JVM

**Shared files to adapt:**
- `data/src/main/java/tachiyomi/data/AndroidDatabaseHandler.kt` — Contains Android-specific `SqlDriver` usage
  - Extract interface `DatabaseHandler` with methods: `getAnimeQueries()`, `getEpisodeQueries()`, etc.
  - Create `MacOSDatabaseHandler` implementation

**SQLDelight `.sq` files:** All SQLDelight query definitions in `data/src/main/sqldelight/` are **100% reusable** — they're pure SQL. No changes needed.

### Phase 1.4: Logging System

**Problem:** The app uses `timber`, XLog with `AndroidPrinter`, and `android.util.Log` via `logcat`.

**Solution:** Replace with SLF4J/Logback or a simple file-based logger.

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/logging/MacOSLogger.kt`
  - Use `kotlin-logging` (SLF4J wrapper) with Logback backend
  - Log file: `~/Library/Application Support/Anikku/logs/anikku.log`
  - Match existing log levels: `EHLogLevel`

**Migration:**
- `Timber.d(...)` → `logger.debug { ... }`
- `logcat(LogPriority.ERROR) { ... }` → `logger.error { ... }`
- `XLog.d(...)` → `logger.debug { ... }`
- Crashlytics → Sentry or local crash log

### Phase 1.5: Application Lifecycle

**Problem:** `App.kt` extends `Application()` and uses `ProcessLifecycleOwner`, `Lifecycle`, Android `BroadcastReceiver`, `WorkManager`, Android notifications, etc.

**Solution:** Create a desktop equivalent of the app lifecycle.

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/AnikkuApplication.kt`
  - Application-level initialization (migrator, modules, image loader, network helper)
  - Use `kotlinx.coroutines` + `CoroutineScope(SupervisorJob() + Dispatchers.Default)` instead of `ProcessLifecycleOwner`
  - Window focus/blur events replace lifecycle `onStart`/`onStop`
  - Discord RPC start/stop on window focus/blur

**What to DROP from App.kt:**
- `PendingIntent`, `Notification`, `BroadcastReceiver` — replace with macOS notification system
- `WorkManager` — replace with coroutines + scheduled tasks
- `IncognitoMode` notification — replace with a menu bar indicator
- `WebView.setDataDirectorySuffix` — not applicable
- `Conscrypt` — macOS JVM already has TLS 1.3
- `WidgetManager` — not applicable
- `TelemetryConfig.init` — replace or drop

### Phase 1.6: Coroutines & Threading

**Setup:**
- Add `kotlinx-coroutines-swing` for macOS Event Dispatch Thread integration
- Main thread = EDT (Swing thread)
- IO thread pool for network/disk operations
- Dedicated scope for mpv event loop

### Phase 1.7: WorkManager Replacement — Concrete Strategy

**Problem:** The Android app uses `androidx.work.WorkManager` with 9+ different `CoroutineWorker` subclasses for background tasks. The plan previously hand-waved this as "replace with coroutines." Here is the concrete replacement for each:

| Android Worker | Purpose | macOS Replacement |
|---|---|---|
| `BackupCreateJob` | Periodic auto-backup | `CoroutineScope` + `delay()` loop; triggered on window focus and on a timer |
| `BackupRestoreJob` | One-shot restore | Direct coroutine launch from UI (user-initiated action) |
| `SyncDataJob` | Periodic SyncYomi/Google Drive sync | `CoroutineScope` + timer loop; also triggered on app start and window focus |
| `LibraryUpdateJob` | Background library scanning | `CoroutineScope` + timer loop; shows progress in status bar |
| `MetadataUpdateJob` | One-shot metadata refresh | Direct coroutine launch from library screen |
| `AppUpdateDownloadJob` | Download + install APK update | macOS: download `.dmg` + open Finder (Phase 10.6) |
| `AppUpdateJob` | Periodic update check | `CoroutineScope` + timer loop (daily check) |
| `DownloadJob` | Background episode download | Direct coroutine launch; queue managed by `DownloadManager` |
| `DelayedTrackingUpdateJob` | Batched tracker updates | `CoroutineScope` + debounce (batch updates, send periodically) |

**Implementation pattern:**
```kotlin
// macos/src/main/kotlin/app/anikku/macos/platform/BackgroundTaskScheduler.kt
class BackgroundTaskScheduler(private val scope: CoroutineScope) {
    fun schedulePeriodic(name: String, intervalMinutes: Long, task: suspend () -> Unit): Job {
        return scope.launch {
            while (isActive) {
                task()
                delay(intervalMinutes.minutes)
            }
        }
    }
    
    fun runOnce(name: String, task: suspend () -> Unit): Job {
        return scope.launch { task() }
    }
}
```

**Key difference from Android:** On macOS, there is no guaranteed background execution when the app is closed. This is acceptable — library updates and sync happen when the app is running. This matches user expectations for a desktop app.

**Files to modify:**
- `BackupCreateJob.kt` — Extract backup creation logic into a pure function, wrap in `BackgroundTaskScheduler`
- `SyncDataJob.kt` — Extract sync logic, wrap in scheduler
- `LibraryUpdateJob.kt` — Extract update logic, wrap in scheduler
- `DownloadJob.kt` — Keep as direct coroutine launch (user-triggered)
- All other Worker classes follow same pattern

---

## Phase 2: Domain & Data Layer Porting

**Goal:** All domain use cases, repositories, and data mappers compile without Android dependencies.

### Phase 2.1: Domain Layer Audit

**Current state:** The domain layer (`domain/src/main/java/tachiyomi/domain/`) has only **2 Android imports** across all files:
1. `android.content.Context` in `StorageManager.kt`
2. `android.os.Build` in `Release.kt`

**Actions:**
- **`StorageManager.kt`:** The `Context` is used to get storage directories. Replace with the `MacOSStorageProvider` created in Phase 1.2.
- **`Release.kt`:** `android.os.Build` is used for version comparison. Replace with `System.getProperty("os.version")` for macOS.

**All other domain files should compile as-is.** This includes:
- All `*Repository` interfaces (~25 files)
- All `*Interactor` / use case classes (~30 files)
- All domain models (Category, Track, Anime, Episode, Source, etc.)
- All extension/use case logic

### Phase 2.2: Data Layer Audit

**Current state:** The data layer (`data/src/main/java/`) has **3 Android imports**:
1. `android.database.sqlite.SQLiteException` in two repository implementations
2. `android.content.Context` in `CustomAnimeRepositoryImpl.kt`

**Actions:**
- **`SQLiteException`:** Replace with `java.sql.SQLException` or the SQLDelight exception equivalent.
- **`Context` in CustomAnimeRepositoryImpl:** Replace with the new storage/preference provider.

**All repository implementations should compile after these changes** since they depend on domain interfaces that are already platform-agnostic.

### Phase 2.3: Core Modules

**`core/common/` — 23 files with `android.content.*` imports (NOT just a few!):**

Full audit of files requiring `android.content.Context` or other Android imports:

1. `network/NetworkHelper.kt` — Uses `Context` for cookie jar. Replace with desktop cookie store.
2. `network/AndroidCookieJar.kt` → `MacOSCookieJar.kt` (using `java.net.CookieManager`)
3. `network/JavaScriptEngine.kt` — Uses `Context` for quickjs-android init
4. `network/interceptor/CloudflareInterceptor.kt` — Uses `Context` (likely for JS engine)
5. `network/interceptor/WebViewInterceptor.kt` — Uses `Context` for WebView
6. `archive/UniFileExtensions.kt` — Uses `Context` for UniFile abstraction
7. `exh/log/EHLogLevel.kt` — Uses `Context` for Android logging
8. `util/system/DeviceUtil.kt` — Uses `Context` for Android device detection
9. `util/system/DensityExtensions.kt` — Uses `Resources` for density (irrelevant on desktop)
10. `util/system/ToastExtensions.kt` — Uses `Context` for Toast → Replace with snackbar/notification
11. `util/system/WebViewUtil.kt` — Uses `Context` + `PackageManager` → Replace or drop
12. `util/storage/DiskUtil.kt` — Uses `Context` for storage paths
13. `util/storage/FFmpegUtils.kt` — Uses `Context` for FFmpeg → Port (Phase 6)
14. `tachiyomi/core/common/i18n/Localize.kt` — Uses `Context` for locale
15. `tachiyomi/core/common/preference/AndroidPreferenceStore.kt` — Uses `Context` + `SharedPreferences`
16. `tachiyomi/core/common/preference/AndroidPreference.kt` — Uses `SharedPreferences`
17. `tachiyomi/core/common/storage/AndroidStorageFolderProvider.kt` — Uses `Context`
18. `tachiyomi/core/common/storage/UniFileTempFileManager.kt` — Uses `Context`
19. `tachiyomi/core/common/util/system/ImageUtil.kt` — Uses `Context`, `Configuration`, `Resources`
20-23. Additional utility files with Android dependencies

**Strategy:** Categorize into:
- **Replace with JVM equivalent** (~12 files): File paths, preferences, cookies, locale
- **Stub or drop** (~6 files): WebView, Toast, Density, DeviceUtil
- **Port to desktop** (~3 files): FFmpeg (Phase 6), logging, Archive
- **Temporarily exclude** (~2 files): TorrentServer (Phase 7), deeply Android-specific utilities

**`core/archive/`:**
- 5 files: `ArchiveReader.kt`, `ArchiveEntry.kt`, `ArchiveInputStream.kt`, `CbzCrypto.kt`, `UniFileExtensions.kt`
- These handle CBZ/comic archive reading. They use `java.io.InputStream` internally, which is JVM-compatible.
- **Should compile as-is** with the UniFile abstraction adapted.

**`core-metadata/`:**
- 2 files: `AnimeDetails.kt`, `EpisodeDetails.kt`
- Pure Kotlin data classes. **Compile as-is.**

### Phase 2.4: source-api Module

**Current state:** `source-api/src/main/java/` — Contains HTTP source abstractions, anime source definitions, video/torrent models, filter definitions.

**Android imports:** Minimal. Mostly pure Kotlin interfaces and data classes.

**Key files:**
- `Source.kt`, `AnimeSource.kt`, `HttpSource.kt`, `ParsedHttpSource.kt`
- `SAnime.kt`, `SEpisode.kt`, `Video.kt`, `FilterList.kt`
- `JsoupExtensions.kt`, `RxExtension.kt`

**Actions:**
- All model classes (`SAnime`, `SEpisode`, `Video`, `FilterList`, `AnimesPage`) — **compile as-is**
- `RxExtension.kt` — Depends on RxJava 1.x. RxJava 1.x works on JVM. Keep for now, migrate to coroutines in Phase 11.
- `JsoupExtensions.kt` — Uses `jsoup` which is JVM-compatible. **Compile as-is.**
- HTTP sources — rely on OkHttp and jsoup. Both work on JVM. **Compile as-is.**

### Phase 2.5: i18n Modules

**Current state:** Multiple i18n modules using Moko Resources:
- `i18n/` — Core i18n
- `i18n-kmk/` — Komikku-specific strings
- `i18n-ank/` — Anikku-specific strings
- `i18n-sy/` — SY (ScanYomi) strings
- `i18n-aniyomi/` — Referenced in `settings.gradle.kts` as `include(":i18n-aniyomi")`

**Moko Resources** is a Kotlin Multiplatform library and works on JVM/Desktop. String resources should be accessible.

**Note on Compose Resources vs Moko Resources:**
- Compose Multiplatform 1.6+ ships its own `compose-resources` system (via `org.jetbrains.compose.resources`).
- The Android app uses Moko Resources (`dev.icerock.moko:resources`).
- These two resource systems can coexist but may have namespace conflicts.
- **Decision:** Keep Moko Resources for the shared modules (domain/data use Moko for string lookups), and use Compose Resources for UI-layer strings where it simplifies the Compose Desktop integration.

**Actions:**
- Verify Moko Resources descriptor compatibility with Compose Desktop
- Include all i18n source directories (including `i18n-aniyomi`)
- Test that `stringResource(MR.strings.app_name)` resolves
- Watch for potential `stringResource()` import conflicts between Moko and Compose Resources

---

## Phase 3: Networking & Source API

**Goal:** All HTTP networking, cookie management, and source extension APIs work on macOS.

### Phase 3.1: OkHttp Client Configuration

**Port from `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/`:**
- `NetworkHelper.kt` → Port the OkHttp client builder
  - `cache` — Use JVM cache directory
  - `cookieJar` — Replace `AndroidCookieJar` with JVM cookie jar
  - `dns` — Use system DNS (no DoH override needed on desktop)
  - All interceptors: `CloudflareInterceptor`, `UserAgentInterceptor`, `RateLimitInterceptor`, `SpecificHostRateLimitInterceptor` — these are pure Kotlin/OkHttp and should **compile as-is**
- `NetworkPreferences.kt` — Pure preference-backed. **Compile as-is.**
- `Requests.kt` — OkHttp helper. **Compile as-is.**
- `ProgressResponseBody.kt` — OkHttp interceptor. **Compile as-is.**
- `DohProviders.kt` — Pure data. **Compile as-is.**
- `JavaScriptEngine.kt` — Uses `quickjs-android` (JNI). Replace with `quickjs-java` JVM variant or drop if not critical (used by Cloudflare bypass). macOS can fall back to a non-JS approach.

### Phase 3.2: Cookie Management

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/network/MacOSCookieJar.kt`
  - Extends OkHttp's `CookieJar`
  - Backed by `java.net.CookieManager` with persistent storage to `~/Library/Application Support/Anikku/data/cookies.json`
  - Match the existing `AndroidCookieJar` API surface

### Phase 3.3: Extension System Adaptation

**Problem:** Android version installs extensions as separate APK packages via `PackageInstaller`/`Shizuku`. This doesn't make sense on desktop.

**Decision:** Extensions on macOS become **JAR files loaded via URLClassLoader at runtime**.

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/extensions/MacOSExtensionManager.kt`
  - Extension storage: `~/Library/Application Support/Anikku/extensions/`
  - Scan directory for `.jar` files
  - Load each JAR with `URLClassLoader`
  - Find `SourceFactory` implementations via `ServiceLoader`
  - Expose loaded sources to the app

**This is one of the trickiest parts.** The Android extension ecosystem expects APK installation. For a v1 macOS port:
1. Pre-bundle popular source extensions as JARs
2. Allow adding extension JARs via file picker
3. Build a GitHub-based extension repo browser similar to the Android version but downloading JARs instead of APKs

---

## Phase 4: UI Framework & Navigation

**Goal:** Set up the Compose Desktop UI foundation — theming, navigation, and core UI components.

### Phase 4.1: Theme System

**Port from `app/src/main/java/eu/kanade/presentation/theme/`:**

The app uses Material 3 with custom theming via `material-kolor` (dynamic color palettes). 

**Actions:**
- Port `Theme.kt`, `ColorScheme.kt`, `Typography.kt`
- `MonetColorScheme.kt` uses `android.app.WallpaperManager` — replace with a color extraction from a user-chosen image or use a default palette
- `material-kolor` should work on Compose Desktop
- `haze` (blur effects) — check Compose Desktop support; may need to degrade gracefully
- Keep the exact same color tokens, typography scale, and spacing

### Phase 4.2: Navigation Setup — Voyager

The app uses **Voyager** (`cafe.adriel.voyager`) for navigation. Voyager supports Compose Desktop.

**Actions:**
- Set up `Navigator(...)` at the top level inside `Window`
- Port the existing tab navigator (Library, Updates, History, Browse, More)
- Port the screen navigation graph
- Keep the same screen hierarchy from the Android app

**NavigationBar → Desktop Side Rail:**

The Android app uses Material 3 `NavigationBar` (bottom tabs). On desktop, we should use a **side navigation rail** (left sidebar with icons + labels). This is more natural on macOS where horizontal space is abundant and vertical space is at a premium. The tab content remains identical; only the navigation container changes.

**Screen hierarchy to replicate:**
```
Main Screen (Tab Navigator)
├── Library Tab
│   └── Library Settings
│       └── Category Screen
├── Updates Tab
│   └── Update Item → Anime Screen → Episode → Player
├── History Tab
│   └── History Item → Anime Screen → Episode → Player
├── Browse Tab
│   ├── Sources List
│   │   ├── Source Browse → Anime Screen
│   │   └── Global Search
│   └── Extensions Screen
└── More Tab
    ├── Settings (Main)
    │   ├── Appearance
    │   ├── Library
    │   ├── Downloads
    │   ├── Player
    │   ├── Data & Storage
    │   ├── Security
    │   ├── Tracking
    │   ├── Connections (Discord)
    │   ├── Advanced
    │   └── About
    ├── Backup/Restore
    └── Updates → Anime Screen → Episode → Player
```

### Phase 4.3: presentation-core Module Adaptation

**Problem:** `presentation-core/src/main/java/` has **4 Android imports**:
1. `android.view.ViewConfiguration` in `Scrollbar.kt` — replace with desktop scroll detection
2. `android.content.res.Configuration.ORIENTATION_LANDSCAPE` in `AdaptiveSheet.kt` — replace with window size check
3. `android.view.MotionEvent` in `SettingsItems.kt` — replace with Compose pointer events
4. `android.view.ViewConfiguration` in `VerticalFastScroller.kt` — replace with desktop touchpad scroll detection

**Other presentation-core files** use `androidx.compose.*` — these need the import path changed from `androidx.compose` to `org.jetbrains.compose` (or the Compose Multiplatform equivalent). The Compose APIs are 70-80% identical.

**Systematic replacement:**
| Android Compose Import | Compose Desktop Equivalent |
|---|---|
| `androidx.compose.foundation.*` | Same (unified) |
| `androidx.compose.material3.*` | Same (unified) |
| `androidx.compose.runtime.*` | Same (unified) |
| `androidx.compose.ui.*` | Same (unified) |
| `androidx.activity.compose.BackHandler` | Compose Desktop BackHandler |
| `androidx.compose.ui.tooling.preview.*` | Drop (no preview in desktop) |
| `androidx.compose.ui.res.painterResource` | Use `org.jetbrains.compose.resources.painterResource` |
| `androidx.compose.ui.res.stringResource` | Use `org.jetbrains.compose.resources.stringResource` |

### Phase 4.4: Custom UI Components

The app has many custom Compose components. Most will port directly since they only use `androidx.compose.*` imports.

**Key components to verify:**
- `AdaptiveSheet.kt` — Works on desktop (uses `windowSizeClass`)
- `LazyList` extensions — Works on desktop
- `WheelPicker.kt` — Works on desktop
- `Scrollbar.kt` — Needs desktop touch adaptation
- `VerticalFastScroller.kt` — Needs desktop touch adaptation
- `SettingsItems.kt` — Various preference composables (Slider, Switch, Text, etc.) — should work

---

## Phase 5: Screen-by-Screen UI Porting

**Goal:** Port every screen from the Android app to Compose Desktop, preserving exact layouts.

### Phase 5.1: Common Screen Infrastructure

Before porting individual screens, establish shared patterns:

**ScreenModel → StateFlow → Composable pattern:**
```kotlin
// Android pattern (Voyager + ScreenModel)
class LibraryScreenModel : ScreenModel {
    val state: StateFlow<LibraryState> = ...
}

// macOS: identical pattern, just replace Android-specific dependencies
```

**Activity → Composable mapping:**
| Android | macOS |
|---|---|
| `MainActivity` | `MainWindow` composable in `Window {}` |
| `PlayerActivity` | Full-screen composable or new `Window` |
| `WebViewActivity` | New `Window` with embedded browser |
| `UnlockActivity` | `Dialog` / modal overlay |
| `CrashActivity` | Error dialog |

### Phase 5.2: Home/Main Screen

**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt` + `HomeScreen.kt`

**Actions:**
- Port the tab-based main screen layout
- Tab bar at the bottom (Library, Updates, History, Browse, More) → On desktop, consider a sidebar navigation (left rail) which is more desktop-appropriate, but keep the option for bottom tabs
- Port search bar
- Port incognito mode toggle
- Port loading/download progress indicators

**Android-specific to remove:**
- `SearchManager`, `AssistContent`, `onNewIntent` → not applicable
- Android  `BackHandler` (use Compose's)
- `PictureInPicture` → replaced in Phase 9
- Splash screen → replace with loading state

### Phase 5.3: Library Screen

**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt`, `LibraryScreenModel.kt`, `LibraryItem.kt`, `LibrarySettingsScreenModel.kt`

**Key features to port:**
- Grid/list view toggle
- Category filtering
- Sort options
- Search within library
- Long-press for context menu (→ right-click on desktop)
- Library settings dialog
- Pull-to-refresh → manual refresh button

**Android-specific removal:**
- `android.app.Application` references in ScreenModel → replace with Koin injection
- Widget update calls → remove

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/ui/screens/library/LibraryScreen.kt`
- `macos/src/main/kotlin/app/anikku/macos/ui/screens/library/LibraryViewModel.kt`

### Phase 5.4: Updates Screen

**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/updates/UpdatesTab.kt`, `UpdatesScreenModel.kt`

**Key features to port:**
- Grouped update items by date
- Mark all as read
- Download all
- Filter by source
- Pull-to-refresh → refresh button

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/ui/screens/updates/UpdatesScreen.kt`

### Phase 5.5: History Screen

**Source:** `app/src/main/java/eu/kanade/presentation/history/`

**Key features to port:**
- Chronological history list
- Clear history
- Resume watching from last position

### Phase 5.6: Browse / Sources Screen

**Source:** `app/src/main/java/eu/kanade/presentation/browse/`, `app/src/main/java/eu/kanade/tachiyomi/ui/browse/`

**Key features to port:**
- Source list with language icons
- Source search/filter
- Extension management (adapted for desktop JAR loading)
- Global search across sources
- Source-specific browsing

**Special attention:** The extension/APK installation flow needs the complete rewrite described in Phase 3.3.

### Phase 5.7: Anime Detail Screen

**Source:** `app/src/main/java/eu/kanade/presentation/anime/AnimeScreen.kt`, `app/src/main/java/eu/kanade/tachiyomi/ui/anime/`

**Key features to port:**
- Cover image + info header
- Episode list
- Track status management
- WebView button (adapted — Phase 8)
- Share button (adapted for macOS sharing)
- Favorite/bookmark toggle

**Android-specific removal:**
- `BackHandler` (Android Activity back) → use Compose's `BackHandler`

### Phase 5.8: Player Screen

**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/player/` (massive — 15+ files)

**This is the most complex screen.** It's deeply tied to `mpv-android` (146+ references) and Android-specific features (PiP, Cast, external intents).

**Port Phase 5.8 only the non-mpv UI shell:**
- Top bar with title, back button
- Control overlay (play/pause, seek bar, skip buttons)
- Episode selector
- Track selector (audio, subtitles)
- Speed selector
- Settings panels (all the bottom sheets)
- **Do NOT wire up mpv yet — that's Phase 6**

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/ui/screens/player/PlayerScreen.kt`
- `macos/src/main/kotlin/app/anikku/macos/ui/screens/player/PlayerControls.kt`
- `macos/src/main/kotlin/app/anikku/macos/ui/screens/player/PlayerSettings.kt`

### Phase 5.9: Settings Screens

**Source:** `app/src/main/java/eu/kanade/presentation/more/settings/`

**Settings screens to port (~15 sub-screens):**
1. Settings main (with search)
2. Appearance (theme, color scheme, AMOLED mode → dark mode on desktop)
3. Library settings
4. Downloads settings
5. Player settings (basic + advanced)
6. Data & Storage
7. Security (lock, biometric — Phase 7)
8. Tracking (MAL, AniList, etc.)
9. Connections (Discord — Phase 7)
10. Advanced settings
11. About screen

**Settings infrastructure:** The `SettingsItems.kt` file in `presentation-core` provides composable preference items (SwitchPreference, SliderPreference, TextPreference, etc.) that should port well.

### Phase 5.10: Download Queue Screen (Complete Rewrite)

**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/download/` (7 files)

**Critical issue:** The download queue uses Android's View system extensively:
- `DownloadQueueScreen.kt` — Voyager Screen wrapper
- `DownloadQueueScreenModel.kt` — State management
- `DownloadAdapter.kt` + `DownloadHolder.kt` + `DownloadItem.kt` — RecyclerView + FlexibleAdapter pattern
- `DownloadHeaderItem.kt` + `DownloadHeaderHolder.kt` — Expandable header pattern with ItemTouchHelper
- Uses Android `viewbinding` (`DownloadItemBinding`, `DownloadHeaderBinding` from XML layouts)

**Solution:** Complete Compose rewrite using `LazyColumn` with sticky headers.

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/ui/screens/downloads/DownloadQueueScreen.kt`
- `macos/src/main/kotlin/app/anikku/macos/ui/screens/downloads/DownloadQueueViewModel.kt`

### Phase 5.11: Stats Screen

**Source:** `app/src/main/java/eu/kanade/presentation/more/stats/` + `eu/kanade/tachiyomi/ui/stats/`

**Status:** Already uses pure Compose (`StatsScreenContent`, `StatsItem`, etc.). Should port with minimal changes.

### Phase 5.12: Onboarding Flow

**Source:** `app/src/main/java/eu/kanade/presentation/more/onboarding/`

**Current state:** Multi-step onboarding (Permissions, Storage, Theme, Guides). Uses Android permission requests, storage selection intents.

**macOS adaptation:**
- Remove storage permission step (macOS has file picker, no SAF)
- Remove notification permission step (macOS notifications don't require runtime permission)
- Remove install apps permission step (no APK installation)
- Keep theme step and guides step
- Add "Choose Library Location" step for selecting download directory

### Phase 5.13: Toast/Messages Replacement

**Problem:** The app uses `android.widget.Toast` in 10+ files for short user messages.

**Solution:** Replace all `Toast.makeText(context, text, duration).show()` with a Compose `Snackbar` or a custom `MacOSToast` composable.

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/ui/MacOSToast.kt`

### Phase 5.14: Keyboard Handling Adaptation

**Problem:** The player's `AniyomiMPVView` observes mpv's software keyboard events and shows/hides Android's on-screen keyboard. The `GestureHandler` uses touch gestures (pinch, swipe, double-tap).

**macOS adaptation:**
- Software keyboard → drop entirely (desktop has physical keyboard)
- Touch gestures → replace with:
  - Mouse clicks: left click = play/pause, double-click = fullscreen
  - Keyboard shortcuts: space = play/pause, ←/→ = seek, ↑/↓ = volume
  - Scroll wheel: volume control when over the player
  - Right-click: context menu with player options
- The mpv `software_keyboard` property can be ignored on desktop

### Phase 5.15: Migrator System

**Source:** `app/src/main/java/mihon/core/migration/`

**Port the migration system** — it's mostly platform-agnostic:
- `Migration.kt`, `Migrator.kt`, `MigrationContext.kt`
- Individual migration classes (library update, sync data, backup, etc.)
- The migration version is stored in preferences (already ported in Phase 1.2)

---

## Phase 6: Video Player — mpv Integration

**Goal:** Get video playback working with full feature parity to the Android mpv player.

### Phase 6.1: libmpv Installation & Distribution

**Problem:** macOS does not ship `libmpv` by default. Users need to install it or we need to bundle it.

**Decision: Bundle libmpv with the app.**

**Options:**
1. **Brew dependency:** Require `brew install mpv` — easiest for development, bad for distribution
2. **Bundle dylib:** Download `libmpv.1.dylib` from mpv's macOS build and include in `.app/Contents/Frameworks/`
3. **SPM/Carthage:** Not applicable (this is JVM, not Swift)

**Recommended: Bundle the dylib.** Download the official macOS mpv build, extract `libmpv.1.dylib`, and include it in the app bundle. Use `jpackage` with `--java-options "-Djava.library.path=\$APPDIR/Contents/Frameworks"`.

**Files to create:**
- `macos/libs/libmpv.1.dylib` — The native library
- `macos/src/main/kotlin/app/anikku/macos/player/MPVNativeLoader.kt` — JNA loader

### Phase 6.2: JNA Bindings to libmpv

**The most technically challenging phase.** We need to replicate the `MPVLib` API that the Android app uses (`is.xyz.mpv.MPVLib`).

**Android API surface to replicate:**

From the code search, the key MPVLib API calls are:
```kotlin
// Core control
MPVLib.create()
MPVLib.destroy()
MPVLib.setOptionString(key, value)
MPVLib.setPropertyString(key, value)
MPVLib.setPropertyInt(key, value)
MPVLib.setPropertyDouble(key, value)
MPVLib.getPropertyInt(key)
MPVLib.getPropertyString(key)
MPVLib.getPropertyDouble(key)
MPVLib.command(arrayOf("loadfile", url))
MPVLib.command(arrayOf("seek", seconds, "absolute"))

// Event system
MPVLib.observeProperty(property, format, replyUserdata)
MPVLib.event(eventId)  // Wait for events
MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED
MPVLib.mpvEventId.MPV_EVENT_SEEK
MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART

// Format constants
MPVLib.mpvFormat.MPV_FORMAT_INT64
MPVLib.mpvFormat.MPV_FORMAT_DOUBLE
MPVLib.mpvFormat.MPV_FORMAT_STRING
MPVLib.mpvFormat.MPV_FORMAT_FLAG
MPVLib.mpvFormat.MPV_FORMAT_NODE
MPVLib.mpvFormat.MPV_FORMAT_NONE

// Logging
MPVLib.mpvLogLevel.MPV_LOG_LEVEL_FATAL
MPVLib.mpvLogLevel.MPV_LOG_LEVEL_ERROR
MPVLib.mpvLogLevel.MPV_LOG_LEVEL_WARN
MPVLib.mpvLogLevel.MPV_LOG_LEVEL_INFO

// Utility
Utils.getTimeString(milliseconds)
Utils.prettyTime(milliseconds)
```

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/player/MPVLib.kt` — JNA interface mapping to `libmpv`
- `macos/src/main/kotlin/app/anikku/macos/player/MPVEventLoop.kt` — Dedicated coroutine for mpv event processing
- `macos/src/main/kotlin/app/anikku/macos/player/MacOSMPVRenderSurface.kt` — Compose component that renders mpv's video output

### Phase 6.3: MPV Render Surface — The Render API Approach

**Critical challenge:** On Android, mpv renders into an `android.view.Surface`. On macOS, we need to render into a Compose Desktop-compatible surface.

**⚠️ IMPORTANT: The `wid` (window ID) approach does NOT work on macOS!**

The classic method of passing a native window handle via the `wid` property was designed for X11/Linux and Windows. On macOS, this conflicts with AppKit's windowing model and the App Sandbox. Do not attempt it.

**Correct approach: Use mpv's Render API (`mpv_render_context`)**

This is the approach used by IINA, the most popular macOS mpv-based player:

```
1. Create a Metal-backed rendering surface (CAMetalLayer or NSOpenGLView)
2. Initialize mpv with `mpv_render_context_create()` passing the Metal device
3. In the render loop, call `mpv_render_context_render()` to draw each frame
4. The app manages resize, focus, and presentation timing
```

**Implementation plan using Compose Desktop:**

```kotlin
// Step 1: Create an AWT Canvas (heavyweight, gets its own NSView on macOS)
// Step 2: Use JNA to access the Canvas's native CALayer
// Step 3: Call mpv_render_context_create() with the Metal device
// Step 4: Wrap the Canvas in a Compose SwingPanel for embedding
// Step 5: Run the mpv render loop on a dedicated coroutine

@Composable
fun MPVVideoSurface(modifier: Modifier, mpvHandle: Long) {
    SwingPanel(
        modifier = modifier,
        factory = {
            val canvas = Canvas()
            // Get the NSView from the Canvas
            val nsView = getNSView(canvas)
            // Initialize mpv render context with the Metal layer
            mpvRenderContextCreate(mpvHandle, getMetalDevice(nsView))
            canvas
        },
    )
    
    LaunchedEffect(mpvHandle) {
        // Render loop: 60fps
        while (isActive) {
            mpvRenderContextRender(mpvHandle)
            delay(16) // ~60fps
        }
    }
}
```

**Alternative approach (simpler, lower performance): Offscreen OpenGL FBO**

Use mpv's `--vo=libmpv` (OpenGL output) to render to an offscreen framebuffer object, then copy the pixel data into a Compose `Image` composable. This avoids AWT interop entirely but has higher CPU/GPU overhead due to the pixel copy per frame.

**Alternative approach (use VLC instead):**

VLCJ (Java bindings for libVLC) has a well-tested `CanvasVideoSurface` that embeds directly into AWT Canvas and works reliably on macOS. VLCJ also has a `CallbackVideoSurface` that provides raw frame buffers for Compose rendering. This is a viable fallback if mpv's Render API proves too complex. However, VLC has a different API surface, requiring more changes to the player subsystem.

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/player/MPVVideoSurface.kt` — Compose wrapper around AWT Canvas + mpv render context
- `macos/src/main/kotlin/app/anikku/macos/player/MPVRenderContext.kt` — JNA bindings for `mpv_render_context_*` functions
- `macos/src/main/kotlin/app/anikku/macos/player/MacOSMetalBridge.kt` — JNA bridge to Metal/CAMetalLayer for surface creation
- `macos/src/main/kotlin/app/anikku/macos/player/MPVRenderLoop.kt` — Coroutine-based render loop

### Phase 6.3b: VLCJ Fallback — Feasibility Study

> **Date:** July 7, 2026  
> **Status:** Research complete. VLCJ is a viable fallback if mpv Render API proves too complex.

#### Executive Summary

**Recommendation:** Keep `libmpv` via JNA + Render API as the **primary path** (best video quality, matches Android codebase). Use **VLCJ as the fallback** with a clear go/no-go decision point after 2 weeks of mpv Render API development. VLCJ sacrifices some video quality and has a GPLv3 license constraint, but eliminates the entire Metal/CAMetalLayer JNA bridging problem and provides a documented, well-maintained Java API.

#### Comparison Matrix

| Criterion | libmpv (JNA + Render API) | VLCJ (Caprica Software) |
|---|---|---|
| **Video quality** | ⭐⭐⭐⭐⭐ Superior shaders, scalers, upscaling, frame interpolation | ⭐⭐⭐⭐ Good — standard VLC quality |
| **Hardware acceleration** | ⭐⭐⭐⭐⭐ videotoolbox, highly configurable | ⭐⭐⭐⭐ videotoolbox — automatic, less configurable |
| **Subtitle rendering** | ⭐⭐⭐⭐⭐ Excellent SSA/ASS, advanced subtitle styling | ⭐⭐⭐⭐ Good — supports ASS/SSA/SRT but less refined |
| **Playback speed** | ⭐⭐⭐⭐⭐ Fluid, excellent pitch correction | ⭐⭐⭐⭐ Standard variable speed |
| **Seeking responsiveness** | ⭐⭐⭐⭐⭐ Instant | ⭐⭐⭐⭐ Good, slightly more latency |
| **API surface match to Android code** | ⭐⭐⭐⭐⭐ Identical `MPVLib.*` pattern (146+ calls map 1:1) | ⭐⭐⭐ Different API — full player subsystem rewrite |
| **Java/Kotlin integration** | ⭐⭐ Manual JNA bindings, sparse JVM docs, no maintained wrapper | ⭐⭐⭐⭐⭐ Well-documented Java library, tutorials, Discord community |
| **macOS rendering reliability** | ⭐⭐ CAMetalLayer bridging via JNA is untested, historical failures | ⭐⭐⭐⭐ `CallbackVideoSurface` works reliably; `CanvasVideoSurface` is unreliable on modern macOS |
| **Thread safety** | ⭐⭐ Render must be on EDT → bottleneck | ⭐⭐⭐⭐ VLCJ handles threading internally |
| **Bundle size** | ⭐⭐⭐⭐⭐ ~30MB (libmpv.dylib only) | ⭐⭐⭐ ~80MB+ (full VLC framework + plugins) |
| **License** | ⭐⭐⭐⭐⭐ LGPLv2.1+ (dynamic linking allowed, proprietary OK) | ⭐⭐⭐ GPLv3 (must open-source or buy commercial license from Caprica) |
| **Memory usage** | ⭐⭐⭐⭐⭐ Lower overhead | ⭐⭐⭐ Higher — inherits VLC framework overhead |
| **Community & docs** | ⭐⭐ Sparse for JVM; Python/C++ references only | ⭐⭐⭐⭐⭐ Excellent Java docs, examples, Discord |
| **Active maintenance** | ⭐⭐⭐⭐⭐ mpv core is very active | ⭐⭐⭐⭐ vlcj 4.x stable, vlcj 5.x for VLC 4.0 in development |

#### Critical Technical Details

**1. Rendering on macOS — the core challenge**

Both approaches face the same fundamental problem: how to get video pixels onto a Compose Desktop surface.

| Approach | mpv Render API | VLCJ |
|---|---|---|
| **CanvasVideoSurface** (AWT `Canvas` embedding) | ❌ N/A (mpv doesn't use AWT surfaces) | ⚠️ Works on paper but unreliable on macOS 12+. The `NSView` embedding of AWT heavyweight components conflicts with modern macOS windowing. Many VLCJ users report it breaks on newer macOS versions. |
| **CallbackVideoSurface** (raw frame buffers) | ❌ N/A | ✅ Reliable. VLCJ calls a `BufferFormatCallback` with raw RGBA pixel buffers. You write these into a `BufferedImage`, then render in Compose via `SwingPanel` or an `AWTImagePainter`. This bypasses native window embedding entirely. |
| **Render API + Metal** (CAMetalLayer JNA bridge) | ✅ The plan — but untested. Requires custom JNA/ObjC bridge to extract `CAMetalLayer` from AWT `Canvas` and pass to `mpv_render_context`. No known working Java examples. | ❌ N/A |

**2. VLCJ CallbackVideoSurface — how it works end-to-end**

```kotlin
// This is the proven VLCJ approach that avoids all native window embedding issues

// Step 1: Create a MediaPlayerFactory with a CallbackVideoSurface
val factory = MediaPlayerFactory(
    "--no-video-title-show",
    "--avcodec-hw=videotoolbox",  // Force hardware decoding
)

// Step 2: Create a CallbackVideoSurface with a BufferFormatCallback
val videoSurface = factory.videoSurfaces().newVideoSurface(
    BufferedImage.TYPE_INT_RGB  // or RGBA for alpha
)

videoSurface.attachVideoSurface() // before playing

// Step 3: Use a Swing timer to continuously read frames into a BufferedImage
val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
videoSurface.render(/* local graphics */)  // or use direct buffer access

// In a timer (30-60fps):
//   1. Get the BufferedImage from the video surface
//   2. Convert to Compose ImageBitmap
//   3. Display in an Image() composable

// This is a frame-copy approach: GPU decode → CPU RAM → Compose GPU texture
// Performance cost: ~5-10% CPU overhead on modern hardware, acceptable at 1080p
```

**3. API Surface Mapping — what changes in the player subsystem**

If we switch to VLCJ, the `PlayerViewModel` and player UI need a full rewrite. Here is the mapping:

| Operation | mpv (Android/JNA) | VLCJ |
|---|---|---|
| Load media | `MPVLib.command(arrayOf("loadfile", url))` | `mediaPlayer.media().play(url)` |
| Play/Pause | `MPVLib.setPropertyString("pause", "yes")` | `mediaPlayer.controls().pause()` / `play()` |
| Seek absolute | `MPVLib.command(arrayOf("seek", "100", "absolute"))` | `mediaPlayer.controls().setTime(100_000L)` (ms) |
| Seek relative | `MPVLib.command(arrayOf("seek", "10"))` | `mediaPlayer.controls().setTime(currentTime + 10_000L)` |
| Get position | `MPVLib.getPropertyDouble("time-pos")` | `mediaPlayer.status().time()` |
| Get duration | `MPVLib.getPropertyDouble("duration")` | `mediaPlayer.status().length()` |
| Set volume | `MPVLib.setPropertyInt("volume", 80)` | `mediaPlayer.audio().setVolume(80)` |
| Set speed | `MPVLib.setPropertyDouble("speed", 1.5)` | `mediaPlayer.controls().setRate(1.5f)` |
| Audio tracks | `MPVLib.getPropertyString("audio-tracks")` → parse | `mediaPlayer.audio().trackDescriptions()` → `List<TrackDescription>` |
| Set audio track | `MPVLib.setPropertyInt("aid", 1)` | `mediaPlayer.audio().setTrack(1)` |
| Subtitle tracks | `MPVLib.getPropertyString("sub-tracks")` → parse | `mediaPlayer.subpictures().trackDescriptions()` |
| Set subtitle | `MPVLib.setPropertyInt("sid", 1)` | `mediaPlayer.subpictures().setTrack(1)` |
| Equalizer | `MPVLib.setPropertyString("af", eq)` (complex) | `Equalizer()` → `mediaPlayer.audio().setEqualizer(eq)` (clean API) |
| Screenshot | `MPVLib.command(arrayOf("screenshot", "video"))` | `mediaPlayer.snapshots().save(File(...))` |
| Chapter | `MPVLib.getPropertyDouble("chapter")` | `mediaPlayer.chapters().descriptions()` |
| Subtitle delay | `MPVLib.setPropertyDouble("sub-delay", 0.5)` | Native API available |
| Audio delay | `MPVLib.setPropertyDouble("audio-delay", 0.5)` | `mediaPlayer.audio().setDelay(500_000L)` (µs) |
| Events/state | `MPVLib.observeProperty()` + poll `event()` | `MediaPlayerEventListener` interface (~30 callback methods) |

**Impact assessment:** The mpv API is a thin wrapper sending string commands. The VLCJ API is object-oriented with typed methods. This means:
- **48 files** in the player subsystem use `MPVLib.*` or `mpvFormat.*` or `mpvLogLevel.*`
- With mpv: 146+ call sites map 1:1 to JNA wrapper (same API surface)
- With VLCJ: every call site needs to be rewritten to the VLCJ equivalent
- **Estimated effort:** mpv JNA = 2 weeks; VLCJ subsystem rewrite = 6 weeks (player subsystem is ~3,500 lines)

**4. Bundling VLC with the macOS app**

VLCJ requires `libvlc.dylib` and all VLC plugins at runtime:

```
Anikku.app/
└── Contents/
    └── Frameworks/
        └── vlc/
            ├── libvlc.dylib
            ├── libvlccore.dylib
            └── plugins/         # ~120 plugin dylibs
                ├── libaccess_*
                ├── libaudio_*
                ├── libcodec_*
                ├── libvideo_*
                └── ...
```

**How to bundle:**
1. Download official VLC macOS `.dmg`
2. Extract `VLC.app/Contents/MacOS/lib/` → our `Frameworks/vlc/`
3. Set JVM flag: `-Djna.library.path=$APPDIR/Contents/Frameworks/vlc`

**Size comparison:**
- libmpv alone: ~30MB
- Full VLC bundle: ~80-100MB (most of the weight is in `plugins/` — codec support, audio filters, etc.)
- This can be trimmed by removing unused plugins (~50MB), but any trimming risks breaking format support

**5. License implications**

| License | mpv (LGPLv2.1+) | VLCJ (GPLv3) |
|---|---|---|
| Distribute closed-source | ✅ Yes (dynamic linking) | ❌ No (must open-source or buy license) |
| Modify and keep private | ✅ Yes | ❌ No |
| Mix with proprietary code | ✅ Yes | ❌ No |
| Commercial license available | N/A | ✅ Yes (contact Caprica Software) |

**⚠️ The GPLv3 constraint is significant.** If Anikku plans to remain open-source (the original is Apache 2.0 / GPL-compatible), this is fine. If there are plans to keep the macOS port proprietary or monetize it, VLCJ requires a commercial license from Caprica Software (cost unknown, contact capricasoftware.co.uk).

mpv's LGPL is more permissive: as long as users can replace `libmpv.dylib` (dynamic linking), the app can be proprietary.

#### Go/No-Go Decision Criteria

**Try mpv Render API first for 2 weeks. Switch to VLCJ if any of these trigger:**

| Trigger | Rationale |
|---|---|
| **Cannot extract `CAMetalLayer` from AWT Canvas via JNA** | This is the fundamental blocker. If JNA/ObjC bridge can't get the layer, Render API cannot work. |
| **mpv render loop causes more than 5% dropped frames at 1080p** | Indicates threading/EDT bottleneck that will degrade user experience. |
| **`mpv_render_context_create` crashes or hangs on macOS 15+** | Apple's Metal changes may break the render API. |
| **JNA mapping of `mpv_render_param` structs causes memory corruption** | The render API uses complex C structs with unions. JNA misalignment can cause hard-to-debug heap corruption. |
| **Cannot get `mpv` and AWT event loops to coexist on a single thread** | Mpv's event loop and AWT's EDT may deadlock. If this happens, the approach is architecturally unsound. |
| **Development exceeds 3 weeks without stable video output** | At this point, VLCJ will be faster to deliver even with the subsystem rewrite. |

**Decision tree:**
```
Start: mpv Render API (JNA + Metal)
  │
  ├─ Week 1: CAN get CAMetalLayer via JNA?
  │   ├─ YES → Continue
  │   └─ NO → IMMEDIATE FALLBACK to VLCJ (CallbackVideoSurface)
  │
  ├─ Week 2: CAN render 30 seconds of video without crash?
  │   ├─ YES → Continue, optimize
  │   └─ NO → FALLBACK to VLCJ
  │
  ├─ Week 3: CAN render 60fps at 1080p with <5% frame drops?
  │   ├─ YES → mpv is viable. Proceed with Phase 6.4-6.7.
  │   └─ NO → FALLBACK to VLCJ
  │
  └─ Week 3+: mpv working → Ship with mpv
```

#### VLCJ Fallback Implementation Plan (if triggered)

If the go/no-go triggers VLCJ, the following plan replaces Phase 6.3-6.5:

**Phase 6F.1: VLCJ Integration** (3 days)
- Add `uk.co.caprica:vlcj:4.8.3` dependency (stable 4.x for VLC 3.x)
- Bundle VLC libraries in app bundle (see bundling above)
- Create `MacOSVLCPlayer.kt` wrapping `MediaPlayerFactory` + `CallbackVideoSurface`

**Phase 6F.2: Player API Rewrite** (1.5 weeks)
- Rewrite `PlayerViewModel` to use VLCJ API instead of `MPVLib`
- Map all 146+ call sites (see mapping table above)
- Replace mpv-specific settings (config files, scripts, input.conf) with VLC equivalents
- Port equalizer, subtitle delay, audio delay to VLCJ API

**Phase 6F.3: CallbackVideoSurface + Compose** (1 week)
- Implement `CallbackVideoSurface` with `BufferFormatCallback` providing RGBA frames
- Create `VLCVideoSurface` composable:
  ```kotlin
  @Composable
  fun VLCVideoSurface(modifier: Modifier, mediaPlayer: MediaPlayer) {
      // Use VLCJ's callback surface to get BufferedImage frames
      // Convert to Compose ImageBitmap at 30-60fps
      // Display in Image() composable
  }
  ```
- Handle resize events (VLCJ can adjust output buffer dimensions)
- Handle Retina display scale factor (2x pixel density)

**Phase 6F.4: Drop Features Unavailable in VLCJ** (1 day)
- mpv user scripts (JavaScript/Lua) — not available in VLC
- mpv input.conf keybindings — replace with Compose keyboard handling
- mpv advanced shader/scaler settings — VLC has fewer options

**Phase 6F.5: Testing** (2 days)
- Test with common anime formats (MKV, MP4, WebM with SSA/ASS subtitles)
- Test hardware acceleration with h.264, h.265/HEVC, AV1
- Test track switching, seeking, playback speed, equalizer
- Memory profiling during extended playback (2+ hours)

**Total VLCJ fallback timeline: ~3 weeks**
**mpv Render API primary timeline: ~5 weeks (assuming it works)**
**VLCJ is actually FASTER to ship but with lower video quality and GPL v3**

#### Final Recommendation

1. **Proceed with mpv Render API as Plan A.** The API surface match to the Android codebase (146+ `MPVLib` call sites) provides enormous leverage. Only the rendering surface integration is risky — everything else ports cleanly.

2. **Budget 2-3 weeks for mpv render surface experimentation.** If `CAMetalLayer` extraction works, the rest is mechanical. If it doesn't work, cut losses immediately.

3. **Don't pre-build VLCJ.** The mpv risks are isolated to one component (render surface). Pre-building VLCJ wastes effort on the 95% chance mpv works. Instead, have the VLCJ integration plan documented (this document) and ready to execute.

4. **If VLCJ is chosen, address GPLv3 immediately.** Consult with project stakeholders about license compatibility. If proprietary distribution is planned, contact Caprica Software for a commercial VLCJ license quote before writing any VLCJ code.

5. **For v1, prioritise shipping with whatever works.** Users care more about watching anime than which decoding library is used. If VLCJ ships in 3 weeks and mpv takes 6, ship VLCJ.

---

### Phase 6.4: Player Model & Controller Port

**Port from Android:**

The player system is massive. Key files:
1. `PlayerViewModel.kt` (~1700 lines) — Central player state management
2. `PlayerActivity.kt` (~1300 lines) — Activity lifecycle + mpv initialization
3. `AniyomiMPVView.kt` — Custom mpv view
4. `PlayerObserver.kt` — mpv event → app state bridge
5. `PlayerControls.kt` — All control composables
6. `GestureHandler.kt` — Touch gestures → keyboard shortcuts
7. `ExternalIntents.kt` — External player launching → drop
8. `PipActions.kt` → macOS PiP (Phase 9)
9. `CastManager.kt` → DROP (no Cast on macOS)

**Porting strategy:**
1. Extract `PlayerViewModel` logic into a pure Kotlin state machine
2. Remove `android.app.Application` dependency
3. Replace all `MPVLib.*` calls with our JNA wrapper (same API surface!)
4. Replace touch gestures with keyboard shortcuts (space=play/pause, arrows=seek, f=fullscreen, etc.)
5. Keep the exact same player settings UI panels

### Phase 6.5: FFmpeg Integration

**Problem:** The app uses `FFmpeg-kit` for media processing (screenshots, format conversion).

**Solution:** Bundle `ffmpeg` binary with the macOS app and invoke via `ProcessBuilder`.

**Files to create/modify:**
- `macos/src/main/kotlin/app/anikku/macos/platform/media/FFmpegBridge.kt`
  - Wrapper around `ProcessBuilder("ffmpeg", ...)`
  - Same API surface as the Android FFmpeg wrapper
- Bundle `ffmpeg` binary in `macos/libs/ffmpeg` (or rely on `brew install ffmpeg`)

### Phase 6.6: Local HTTP Video Server

**Problem:** The Android app runs `LocalHttpServerService` (backed by NanoHTTPd) to stream local video files to the mpv player. This is critical — without it, mpv cannot access downloaded/converted video content.

**Solution:** Port `LocalHttpServerService` to run as a coroutine-based server on macOS.

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/media/MacOSHttpServer.kt`
  - NanoHTTPd is already a dependency and works on JVM
  - Start on a random available port
  - Serve video files from the app's download/cache directories
  - Match the existing API surface (`start()`, `stop()`, `getPort()`)
  - Handle CORS headers for mpv compatibility

**Files to reference:**
- `app/src/main/java/eu/kanade/tachiyomi/util/LocalHttpServerService.kt`

### Phase 6.7: Player Preferences

- `AdvancedPlayerPreferences.kt` → Port directly (preference-backed)
- mpv config file (`mpv.conf`) — Works the same on desktop, just write to app data directory
- mpv input config (`input.conf`) — Works the same
- mpv scripts — Works the same

---

## Phase 7: Advanced Features Porting

### Phase 7.0: RxJava 1.x — Address the Tech Debt

**Critical note:** The app uses **RxJava 1.x** (version `1.3.8`), an unmaintained library from ~2016. While it compiles and runs on JVM, it represents significant technical debt:

- RxJava 1.x is end-of-life and receives no security patches
- It conflicts conceptually with the Kotlin coroutines/Flow used throughout the rest of the codebase
- The `RxCoroutineBridge.kt` file in `core/common` shows there's already bridging code converting between Rx and Coroutines

**Decision for Phase 7:**
- **Keep RxJava 1.x running as-is for v1.** It works on JVM. The bridging code already handles coroutine interop.
- **Flag for Phase 11/12 cleanup:** Replace RxJava observables with Kotlin Flows in source-api and wherever else they appear. This is a mechanical refactor (Observable<T> → Flow<T>) but touches ~50+ files.
- **If RxJava 1.x causes build issues:** The `source-api` module's `RxExtension.kt` can be rewritten to use `Flow` as a targeted fix.

### Phase 7.1: Tracker Sync (MAL, AniList, Kitsu, etc.)

**Status:** Almost entirely HTTP-based OAuth. Should work with minimal changes.

**Actions:**
- Port OAuth login flows: Replace Android `CustomTabs` with desktop browser launch via `java.awt.Desktop.browse(URI)`
- OAuth callback: On Android, the app receives the callback via intent filter. On desktop, use a local HTTP server to receive the callback (redirect URI: `http://localhost:PORT/callback`).
- **Files affected:** `app/src/main/java/eu/kanade/tachiyomi/ui/setting/track/`

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/auth/OAuthServer.kt`
  - Small embedded HTTP server (NanoHTTPd is already a dependency!) to handle OAuth callbacks
  - Listen on a random port, open browser to auth URL, wait for callback

### Phase 7.2: Google Drive Sync

**Problem:** Android uses `com.google.api.services.drive` via Google Play Services. 

**Solution:** Use the Google Drive REST API v3 with OAuth 2.0, which works from any JVM application.

**Actions:**
- Create `GoogleDriveRestClient` using OkHttp + OAuth 2.0
- OAuth flow: Same local HTTP server pattern as trackers
- File operations: REST API for upload/download (already JSON-based)
- **Files to create:**
  - `macos/src/main/kotlin/app/anikku/macos/platform/sync/GoogleDriveRestClient.kt`
  - `macos/src/main/kotlin/app/anikku/macos/platform/sync/MacOSGoogleDriveService.kt`

### Phase 7.3: Discord Rich Presence

**Status:** The app has a **custom WebSocket-based Discord RPC implementation** that doesn't use any Android-specific libraries. It communicates directly with Discord's WebSocket gateway.

**Actions:**
- Port `DiscordWebSocket.kt`, `DiscordRPC.kt`, `DiscordRPCService.kt`, `DiscordRPCModels.kt`
- Replace `android.app.Service` (background service) with a coroutine-based long-running task
- Start/stop on window focus/blur instead of Activity lifecycle
- Keep the exact same Discord activity payload format

**Verdict: This ports remarkably well** — the Discord RPC implementation is already platform-agnostic Kotlin.

### Phase 7.4: Biometric Authentication (Touch ID)

**Problem:** Android uses `androidx.biometric.BiometricPrompt`. macOS uses Touch ID (or Apple Watch unlock).

**Solution:** Use a Swift/Objective-C bridge via JNA to call macOS `LocalAuthentication` framework.

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/security/MacOSBiometricAuth.kt`
  - JNA calls to `LocalAuthentication.framework`
  - `LAContext.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: "Unlock Anikku")`
  - Return success/failure to Kotlin

**Alternative (simpler):** Use a macOS AppleScript bridge:
```applescript
tell application "System Events" to do shell script "biometric_verification_tool"
```
Or skip biometric for v1 and use a simple PIN/password lock within the app.

### Phase 7.5: Torrent Support

**Source:** `core/common/src/main/java/eu/kanade/tachiyomi/torrentServer/` (8 files)

**Actions:**
- Port `TorrentServerPreferences.kt` — preference-backed, works as-is
- Port `TorrentServerApi.kt` — HTTP client to TorrServer, works as-is
- Port all model classes — data classes, compile as-is
- `TorrentServerUtils.kt` — mostly pure Kotlin, verify any Android imports

**The TorrServer binary itself:** The Android app bundles TorrServer as a native binary. For macOS, download the macOS TorrServer binary and bundle it with the app.

### Phase 7.6: Telegram/Notifications

**Problem:** Android uses `NotificationManager`, `NotificationChannel`, `PendingIntent`.

**Solution:** macOS local notifications via `java.awt.TrayIcon` + system notifications, or use `terminal-notifier` (bundled).

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/notification/MacOSNotificationManager.kt`
  - Library updates → macOS notification center
  - Download complete → macOS notification center
  - Incognito mode → menu bar icon indicator

### Phase 7.7: App Update Checker

**Problem:** Android uses GitHub Releases API + Android `DownloadManager` + `PackageInstaller`.

**Solution:** Use **Sparkle** framework (the standard macOS app update framework) or implement a custom updater that downloads the new `.dmg` and prompts the user.

**For v1:** GitHub Releases check + manual download. Show "New version available" notification with a link to the GitHub release page.

### Phase 7.8: Crash Reporting

**Problem:** Android uses Firebase Crashlytics.

**Solution:** Replace with **Sentry** (has JVM SDK) or simple local crash log.

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/logging/CrashReporter.kt`
  - `Thread.setDefaultUncaughtExceptionHandler { thread, throwable -> ... }`
  - Write crash log to `~/Library/Logs/Anikku/crash.log`
  - Optionally upload to Sentry

---

## Phase 8: WebView Replacement

**Goal:** Replace Android `WebView` with a desktop browser component.

### Phase 8.1: WebView Usage Analysis

The app uses WebView in these contexts:
1. **Source websites** — Open manga/anime website in-app for browsing
2. **OAuth login flows** — Already handled in Phase 7 via system browser
3. **Discord login** — Already handled via WebSocket
4. **Extension info pages** — Display extension readme/details
5. **About/credits** — Display HTML content

### Phase 8.2: Replacement Options

| Option | Pros | Cons |
|---|---|---|
| **JavaFX WebView** | Built into JDK (if using JavaFX), full WebKit engine | Heavy, requires JavaFX modules, not great for streaming video |
| **JCEF (Chromium Embedded Framework)** | Full Chromium engine, identical rendering | Complex integration, large binary (100MB+) |
| **System browser (Desktop.browse)** | Zero integration work, uses Safari/Chrome | Opens external window, no in-app browsing |
| **cef-browser Compose** | Compose-native, Chromium-based | May have stability issues |

**Decision: System browser for v1, JCEF for v2.**

For v1 of the macOS port:
- Source browsing → open system browser (`java.awt.Desktop.browse(URI)`)
- Extension info → system browser or HTML renderer
- OAuth → system browser (Phase 7 pattern)
- Drop in-app source WebView completely (it was for browsing source websites within the app)

This is a pragmatic tradeoff: it simplifies the port dramatically and still lets users access source websites.

### Phase 8.3: Implementation

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/web/BrowserLauncher.kt`
  ```kotlin
  object BrowserLauncher {
      fun open(url: String) {
          Desktop.getDesktop().browse(URI(url))
      }
  }
  ```

**Files to modify:**
- All `WebViewActivity` / `WebViewScreen` references → `BrowserLauncher.open(url)`

---

## Phase 9: macOS Native Integration

**Goal:** Make the app feel like a proper macOS citizen, not an Android app running on a Mac.

### Phase 9.1: Native Menu Bar

**Use Compose Desktop's `MenuBar` composable:**

```kotlin
Window(...) {
    MenuBar {
        Menu("File") {
            Item("Open Backup...", onClick = { filePicker.open() })
            Item("Save Backup...", onClick = { saveBackup() })
            Separator()
            Item("Close Window", onClick = { window.close() })
        }
        Menu("Edit") {
            Item("Undo", shortcut = KeyShortcut(Key.Z, ctrl = false, meta = true))
            Item("Redo", shortcut = KeyShortcut(Key.Z, ctrl = false, meta = true, shift = true))
            Separator()
            Item("Cut")
            Item("Copy")
            Item("Paste")
            Item("Select All")
        }
        Menu("View") {
            Item("Library", shortcut = KeyShortcut(Key.Digit1, meta = true))
            Item("Updates", shortcut = KeyShortcut(Key.Digit2, meta = true))
            Item("History", shortcut = KeyShortcut(Key.Digit3, meta = true))
            Item("Browse", shortcut = KeyShortcut(Key.Digit4, meta = true))
            Separator()
            Item("Toggle Full Screen", shortcut = KeyShortcut(Key.F, ctrl = true, meta = true))
            Item("Toggle Sidebar", shortcut = KeyShortcut(Key.S, ctrl = true, meta = true))
        }
        Menu("Playback") {
            Item("Play/Pause", shortcut = KeyShortcut(Key.Spacebar))
            Item("Skip Forward", shortcut = KeyShortcut(Key.Right, meta = true))
            Item("Skip Backward", shortcut = KeyShortcut(Key.Left, meta = true))
            Separator()
            Item("Volume Up", shortcut = KeyShortcut(Key.Up, meta = true))
            Item("Volume Down", shortcut = KeyShortcut(Key.Down, meta = true))
        }
        Menu("Window") {
            Item("Minimize", shortcut = KeyShortcut(Key.M, meta = true))
            Item("Zoom")
            Separator()
            Item("Bring All to Front")
        }
        Menu("Help") {
            Item("Anikku Help")
            Separator()
            Item("Report Issue...")
            Item("About Anikku")
        }
    }
    
    // App content...
}
```

### Phase 9.2: Keyboard Shortcuts

**Global shortcuts:**
- `⌘,` — Settings
- `⌘F` — Search
- `⌘N` — New tab / New source
- `⌘W` — Close window
- `⌘Q` — Quit
- `Space` — Play/Pause (when player is focused)
- `←`/`→` — Seek backward/forward
- `↑`/`↓` — Volume up/down
- `F` or `⌘⌃F` — Fullscreen toggle
- `⌘1`–`⌘4` — Switch tabs

### Phase 9.3: macOS File Picker

**Replace Android `Intent.ACTION_OPEN_DOCUMENT` / SAF with AWT FileDialog or JFileChooser.**

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/storage/MacOSFilePicker.kt`
  - `fun openFile(title: String, extensions: List<String>): File?`
  - `fun saveFile(title: String, defaultName: String, extensions: List<String>): File?`
  - `fun openDirectory(title: String): File?`

### Phase 9.4: Touch Bar Support (Optional)

If we want to go the extra mile:
- Use Java's `com.apple.eawt` APIs or JNA to add Touch Bar items
- Playback controls on Touch Bar when player is active

### Phase 9.5: macOS-native PiP (Picture in Picture)

**Approach:** Use a small undecorated always-on-top window for video playback.

Compose Desktop supports multiple windows. Create a secondary `Window` that:
- Is always on top
- Is resizable (small)
- Shows only the video surface
- Can be moved independently

This replicates the Android PiP experience without needing AVKit integration.

### Phase 9.6: Dock Integration

- App icon in Dock (via `.icns` file bundled in `.app`)
- Dock badge: number of new updates (optional)
- Dock menu: Play/Pause, Next Episode (right-click dock menu)

**Files to create:**
- `macos/src/main/kotlin/app/anikku/macos/platform/DockManager.kt`

### Phase 9.7: Dark Mode Detection

Compose Desktop has `isSystemInDarkTheme()`. Use this to sync with macOS system appearance.

---

## Phase 10: Packaging & Distribution

**Goal:** Produce a distributable macOS `.app` bundle inside a `.dmg`.

### Phase 10.1: App Icon

**Create:** `macos/src/main/resources/icons/app.icns`
- Design an Anikku icon in macOS style (rounded rectangle, 1024x1024 base)
- Generate all required sizes: 16, 32, 64, 128, 256, 512, 1024

### Phase 10.2: Entitlements Configuration

**Create:** `macos/src/main/resources/entitlements.plist`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <!-- Required for network access (source browsing, streaming, tracking) -->
    <key>com.apple.security.network.client</key>
    <true/>
    
    <!-- Required for local HTTP server (video streaming) -->
    <key>com.apple.security.network.server</key>
    <true/>
    
    <!-- Required for file access (downloads, backups, extensions) -->
    <key>com.apple.security.files.user-selected.read-write</key>
    <true/>
    
    <!-- Required for app data directory -->
    <key>com.apple.security.files.downloads.read-write</key>
    <true/>
    
    <!-- Required for hardware-accelerated video decoding -->
    <key>com.apple.security.cs.disable-library-validation</key>
    <true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
    <true/>
    
    <!-- Required for JIT (JVM needs this) -->
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
</dict>
</plist>
```

**IMPORTANT:** The `disable-library-validation` and `allow-unsigned-executable-memory` entitlements are required because JNA loads libmpv.dylib at runtime. Without these, macOS Hardened Runtime will block the dynamic library load. These entitlements are acceptable for notarization; many apps (including Electron-based ones) use them.

### Phase 10.3: jpackage Configuration

**Use `org.jetbrains.compose` plugin's native distribution support:**

```kotlin
compose.desktop {
    application {
        mainClass = "app.anikku.macos.AnikkuAppKt"
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg)
            packageName = "Anikku"
            packageVersion = "1.0.0"
            
            macOS {
                bundleID = "app.anikku.macos"
                iconFile.set(project.file("src/main/resources/icons/app.icns"))
                minimumSystemVersion = "12.0"  // macOS Monterey+
                
                // Signing for distribution
                signing {
                    sign.set(true)
                    identity.set("Developer ID Application: Your Name (TEAMID)")
                }
                
                // Notarization
                notarization {
                    appleID.set("your@email.com")
                    appleIDPassword.set("@keychain:AC_PASSWORD")
                }
                
                // Entitlements
                entitlementsFile.set(project.file("src/main/resources/entitlements.plist"))
            }
        }
    }
}
```

### Phase 10.4: Bundle libmpv and ffmpeg

**Include native libraries in the app bundle:**

```
Anikku.app/
├── Contents/
│   ├── MacOS/
│   │   └── Anikku        # Launcher
│   ├── Resources/
│   │   ├── app.icns
│   │   └── ...
│   ├── Frameworks/
│   │   ├── libmpv.1.dylib
│   │   └── libffmpeg.dylib
│   └── Info.plist
```

**Configure JVM to find native libs:**
```
jpackage --java-options "-Djava.library.path=\$APPDIR/Contents/Frameworks"
```

### Phase 10.5: App Notarization (for distribution)

**For distribution outside the App Store:**
1. Sign with Developer ID certificate
2. Notarize with Apple
3. Staple the notarization ticket

This ensures macOS Gatekeeper doesn't block the app.

### Phase 10.6: Sparkle Auto-Updater

**For update checking (optional but recommended):**
- Integrate Sparkle 2 framework
- Host an `appcast.xml` on GitHub Pages or a simple server
- Sparkle checks for updates and downloads/installs the new `.dmg`

**For v1:** Manual update check via GitHub API → open browser to download page.

---

## Phase 11: Testing & Polish

### Phase 11.1: Unit Tests

**Port existing JUnit 5 + Kotest tests:**
- Domain layer tests — compile as-is (pure Kotlin)
- Data layer tests — adapt DB driver for in-memory SQLite on JVM
- UI tests — replace Android Compose testing with Compose Desktop testing

**Commands:**
```bash
./gradlew :macos:test
```

### Phase 11.2: Integration Testing

- Test end-to-end flows:
  - Launch → browse source → view anime → play episode
  - Login to tracker → sync → verify
  - Backup → close app → restore → verify data
  - Google Drive sync → verify
  - Discord RPC → verify presence

### Phase 11.3: Performance Optimization

**Video playback:**
- Ensure hardware decoding is enabled (videotoolbox on macOS)
- Test with high-bitrate files
- Memory profiling during extended playback

**UI performance:**
- Large library (1000+ entries) scrolling
- Image loading/caching
- Grid/list view transitions

### Phase 11.4: Edge Cases & Error Handling

- No internet connection → graceful error UI
- Corrupted database → repair or recreate
- Missing libmpv → clear error message
- Permission denied for file access → user-friendly error

### Phase 11.5: Migration Path from Android

If users have existing Anikku Android data, provide:
- Backup import from `.tachibk` files (backup format is the same)
- Instructions for copying the database file

---

## Phase 12: Documentation & Final Cleanup

### Phase 12.1: User Documentation

**Files to create:**
- `macos/README.md` — macOS port overview
- `macos/BUILDING.md` — Build instructions
- `macos/INSTALL.md` — Installation guide
- `macos/CHANGELOG.md` — Release notes

### Phase 12.2: Developer Documentation

- Update `CONTRIBUTING.md` with macOS port guidelines
- Architecture decision records (ADRs) for key decisions
- JNA binding documentation for mpv

### Phase 12.3: Code Cleanup

- Remove dead code paths (Android-only features)
- Remove deprecated annotations
- Format all Kotlin files with ktlint
- Remove unused imports

### Phase 12.4: Final Validation

- Full build: `./gradlew :macos:build`
- Run all tests: `./gradlew :macos:test`
- Package distribution: `./gradlew :macos:packageDmg`
- Install on a clean macOS machine and test
- Verify notarization if applicable

---

## Appendix A: Full Dependency Map

### A.1 Dependencies Carried Over (Desktop Compatible)

| Dependency | Android Artifact | macOS Equivalent | Status |
|---|---|---|---|
| Kotlin stdlib | `org.jetbrains.kotlin:kotlin-stdlib` | Same | ✅ |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-core` | Same + `kotlinx-coroutines-swing` | ✅ |
| Serialization | `org.jetbrains.kotlinx:kotlinx-serialization-json` | Same | ✅ |
| OkHttp | `com.squareup.okhttp3:okhttp` | Same | ✅ |
| Okio | `com.squareup.okio:okio` | Same | ✅ |
| jsoup | `org.jsoup:jsoup` | Same | ✅ |
| SQLDelight | Various Android drivers | JVM/Native driver | 🔄 |
| Coil 3 | `io.coil-kt.coil3:coil-compose` | Check for desktop artifact | 🔄 |
| Voyager | `cafe.adriel.voyager:voyager-*` | Same (desktop supported) | ✅ |
| material-kolor | `com.materialkolor:material-kolor` | Check desktop support | 🔄 |
| Moko Resources | `dev.icerock.moko:resources` | Same (multiplatform) | ✅ |
| **RxJava 1.x** | `io.reactivex:rxjava:1.3.8` | Same (JVM) | ⚠️ Unmaintained since ~2016. Works on JVM but flagged for Flow migration in Phase 11. |
| Injekt → Koin | `com.github.mihonapp:injekt` | `io.insert-koin:koin-core` | 🔄 |
| DiskLruCache | `com.jakewharton:disklrucache` | Same (JVM) | ✅ |

### A.2 Dependencies Dropped (Android Only)

| Dependency | Reason |
|---|---|
| All `androidx.*` | Android framework |
| `conscrypt-android` | macOS JVM has TLS 1.3 natively |
| `shizuku-*` | Android system API |
| `firebase-*` | Android SDK only |
| `google-play-services-*` | Android SDK only |
| `android-shortcut-gradle` | Android build plugin |
| `desugar_jdk_libs` | Modern JVM doesn't need desugaring |
| `leakcanary-*` | Android memory leak detection |
| `mpv-android` (`is.xyz.mpv`) | Replaced with JNA libmpv |
| `ffmpeg-kit` | Replaced with bundled ffmpeg binary |
| `cast-*` (Google Cast) | No macOS equivalent |
| `quickjs-android` | Replaced with JVM JS engine or dropped |

### A.3 Dependencies Added (macOS Only)

| Dependency | Purpose |
|---|---|
| `org.jetbrains.compose` | Compose Desktop framework |
| `net.java.dev.jna:jna` | Native library bindings (libmpv, Touch ID) |
| `io.insert-koin:koin-core` | Dependency injection |
| `ch.qos.logback:logback-classic` | Logging |
| `io.github.microutils:kotlin-logging` | Kotlin logging facade |
| `com.google.api-client:google-api-client` | Google Drive REST API |
| `com.google.oauth-client:google-oauth-client-jetty` | Google OAuth |
| `io.sentry:sentry` (optional) | Crash reporting |
| `com.sparkle-project:Sparkle` (optional) | App updates |

---

## Appendix B: File-by-File Migration Guide

### B.1 Files That Compile As-Is (≈400 files — 37%)

These files contain **zero Android imports** and are pure Kotlin:

- All domain models and interfaces in `domain/src/main/java/`
- All data models and mappers in `data/src/main/java/`
- All source API definitions in `source-api/src/main/java/` (except 3 with misplaced Android imports)
- All core metadata in `core-metadata/src/main/java/`
- All i18n resource definitions
- All SQLDelight `.sq` files
- `flagkit/src/main/java/`

### B.2 Files Needing Import Changes Only (≈300 files — 28%)

These files use `androidx.compose.*` imports that need path adjustments only:

- All presentation files in `app/src/main/java/eu/kanade/presentation/` (~120 files)
- All Compose UI components
- All theme/color/typography definitions
- Voyager screen definitions
- `presentation-core/src/main/java/tachiyomi/presentation/core/` (~30 files)
- `app/src/main/java/mihon/feature/` (~8 files)

**Migration:** Mostly automated — see [Appendix D.2.9](#d29-files-using-only-androidxcompose-compose_update--158-files) for the automated script.

### B.3 Files Needing Partial Rewrites (≈241 files — 23%)

Files using `android.app.*`, `android.os.*`, `android.content.*`, `android.net.*`, `android.graphics.*`, `android.util.*`, `android.text.*`, `android.provider.*`, `android.media.*` alongside portable code:

- **`android.content.Context`** — 102 files (largest single impact): Replace with Koin injection + file paths
- **`android.os.Build`** — 56 files: Single stub class `DesktopBuild.SDK_INT = Int.MAX_VALUE`
- **`android.net.Uri`** — 34 files: Replace with `java.net.URI`
- **`android.graphics.*`** — 33 files: Replace with AWT/Compose equivalents
- **`android.view.*`** — 24 files: Drop View system, use Compose
- **`android.widget.*`** — 13 files: Drop widget toolkit, use Compose
- **`android.util.*`** — 12 files: Replace Base64, Log, drop DisplayMetrics/AttributeSet
- **`android.webkit.*`** — 10 files: Drop CookieManager, use system browser
- **`android.provider.*`** — 7 files: Drop Settings/MediaStore lookups
- **`android.text.*`** — 5 files: Replace with `java.time` or `kotlinx-datetime`
- **`android.media.*`** — 4 files: Drop AudioManager, MediaSession
- **`com.google.android.material.*`** widgets — 10 files: Drop Material widgets for Compose

See [Appendix D](#appendix-d-complete-android-import-migration-map) for the complete per-file mapping.

### B.4 Files Needing Complete Rewrites (≈130 files — 12%)

Files that are fundamentally Android constructs:

- All `Activity` subclasses (15 files: `PlayerActivity`, `WebViewActivity`, `UnlockActivity`, `MainActivity`, `DeepLinkActivity`, `CrashActivity`, `BaseActivity`, `BaseOAuthLoginActivity`, `TrackLoginActivity`, `GoogleDriveLoginActivity`, `DiscordLoginActivity`, `ExpandedControlsActivity`, `ExtensionInstallActivity`, `ThemingDelegate`, `SecureActivityDelegate`)
- All `Service` subclasses (7 files: `DiscordRPCService`, `TorrentServerService`, `LocalHttpServerService`, `ExtensionInstallService`, `Installer`, `PackageInstallerInstaller`, `ShizukuInstaller`)
- All `BroadcastReceiver` subclasses (4 files: `NotificationReceiver`, `AppUpdateBroadcast`, `ExtensionInstallReceiver`, `ExtensionInstallReceiver` in another package)
- All Cast-related files (11 files: `CastManager`, `CastOptionsProvider`, `CastSessionListener`, `CastMiniController`, `CastMediaBuilder`, plus 6 files in `ui/player/cast/`)
- All `AndroidManifest.xml` entries
- All XML layout files (`res/layout/*.xml`)
- All `WidgetManager` / widget-related files
- All `PackageInstaller` / APK-related files
- All Shizuku-related files
- All Firebase-related files
- **Download Queue:** `DownloadAdapter`, `DownloadHolder`, `DownloadItem`, `DownloadHeaderHolder`, `DownloadHeaderItem`, `DownloadQueueScreen` (View-based RecyclerView + FlexibleAdapter)
- **View-based widgets:** `MinMaxNumberPicker`, `RevealAnimationView`, `TachiyomiTextInputEditText`, `ReaderPageImageView`
- **View utility files:** `ViewExtensions.kt`, `WindowExtensions.kt`, `MaterialAlertDialogBuilderExtensions.kt`
- **App.kt** — Complete rewrite as `AnikkuApplication.kt`
- **Player subsystem:** `PlayerActivity.kt`, `AniyomiMPVView.kt`, `ExternalIntents.kt`, `PipActions.kt`
- **Manga reader:** `ReaderPageImageView.kt` and related — **deferred to v2**

### B.5 New Files to Create (≈100 files)

Platform-specific implementations for macOS:

- `macos/src/main/kotlin/app/anikku/macos/` — All platform abstractions
- `macos/src/main/kotlin/app/anikku/macos/player/` — mpv JNA bindings (6 files)
- `macos/src/main/kotlin/app/anikku/macos/di/` — Koin modules (3 files)
- `macos/src/main/kotlin/app/anikku/macos/platform/` — Platform adapters (~25 files)
- `macos/src/main/kotlin/app/anikku/macos/ui/` — macOS-specific UI screens (~50 files)
- `macos/src/main/kotlin/uy/kohesive/injekt/` — Injekt stub (3 files)
- Build files: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `libs.versions.toml`
- Resources: `entitlements.plist`, `app.icns`, `Info.plist`

---

## Appendix C: Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **mpv Render API on macOS is too complex** | Medium-High | Critical (no video playback) | Fallback to VLCJ via `CallbackVideoSurface` (well-tested macOS approach). See [Phase 6.3b](#phase-63b-vlcj-fallback--feasibility-study) for detailed feasibility study, API mapping, go/no-go criteria, and 3-week implementation plan. VLCJ has lower video quality and GPLv3 license constraints but eliminates the Metal/CAMetalLayer JNA bridging risk entirely. |
| **Metal/CAMetalLayer JNA bridging fails** | Medium | Critical | Fallback to OpenGL offscreen FBO approach + Compose Image copy. Lower performance but simpler. |
| **Compose Desktop mpv rendering perf is poor** | Medium | High | Use a separate AWT Window for the player (like VLC.app does). Not "integrated" but fully functional. |
| **Extension JAR loading fails** | Medium | High (no sources) | Pre-bundle top 5 source extensions. Fallback: manual browser-based source access. |
| **Koin migration breaks DI** | Low | Medium | Stub Injekt first, migrate incrementally. Each migration tested individually. |
| **Coil 3 Desktop not mature** | Medium | Medium | Fallback to Coil 2 or manual image loading with OkHttp + AWT. |
| **Voyager Desktop navigation bugs** | Low | Medium | Compose Desktop navigation is simpler. Can fallback to manual state-based navigation. |
| **Google Drive REST API rate limits** | Low | Low | Cache tokens, implement retry with backoff. |
| **macOS notarization rejected** | Medium | Medium | Distribute as unsigned DMG with Gatekeeper instructions. |
| **Performance worse than Android** | Medium | Medium | Profile early and often. JVM on desktop is typically faster than Android ART. |
| **Shared source changes break Android build** | Low | High | Use `desktopMain` source sets within existing KMP modules. Android source trees untouched. |
| **RxJava 1.x causes runtime issues on JVM 17+** | Low-Medium | Medium | RxJava 1.3.8 was compiled for older JVMs. May have reflection/access issues with strict module system. Mitigation: `--add-opens` JVM flags, or targeted migration to Flow. |
| **Moko Resources conflicts with Compose Resources** | Low | Medium | Two `stringResource()` functions with different signatures. Mitigation: use fully qualified imports, namespace one system. |
| **i18n-aniyomi module missing from plan** | Low | Low | Module referenced in `settings.gradle.kts` but not analyzed. Include in Phase 2.5 audit. |
| **LocalHttpServer port conflicts** | Low | Low | Port already in use. Mitigation: bind to port 0 (OS-assigned), discover actual port via API. |
| **Download Queue RecyclerView rewrite is complex** | Medium | Medium | The queue screen with expandable headers, swipe-to-cancel, and drag-reorder needs careful Compose rewrite. Mitigation: implement basic list first, add interactions incrementally. |
| **RequerySQLiteOpenHelper not JVM-compatible** | Low-Medium | High | AppModule uses both FrameworkSQLiteOpenHelperFactory and RequerySQLiteOpenHelperFactory. The JVM SQLDelight driver may not need these. Mitigation: use JdbcSqliteDriver directly, test on first compilation. |
| **android.os.Build version checks throughout** | Low | Low | 60+ files use `android.os.Build.VERSION.SDK_INT` for Android API level checks. On desktop these are irrelevant. Mitigation: create a `DesktopBuild` stub with `SDK_INT = Int.MAX_VALUE` so version-gated code paths always take the "modern" path. |
| **MPV software keyboard events** | Low | Low | Player observes mpv software keyboard events for Android on-screen keyboard. On desktop, these events can be safely ignored — hardware keyboard shortcuts replace them. |

---

## Summary of Phase Dependencies

```
Phase 0 (Scaffold)
  └── Phase 1 (Infrastructure)
        ├── Phase 2 (Domain/Data)
        │     └── Phase 3 (Networking)
        │           └── Phase 4 (UI Framework)
        │                 └── Phase 5 (Screens)
        │                       ├── Phase 6 (mpv Player)
        │                       ├── Phase 7 (Advanced Features)
        │                       ├── Phase 8 (WebView)
        │                       └── Phase 9 (macOS Native)
        │                             └── Phase 10 (Packaging)
        │                                   └── Phase 11 (Testing)
        │                                         └── Phase 12 (Docs/Cleanup)
```

Phases 5, 6, 7, 8 can be worked on in parallel once Phase 4 is complete.

---

*End of Architectural Rework Plan. This document is the single source of truth for all macOS port implementation decisions.*

---

## Appendix D: Complete Android Import Migration Map

> **Audit Date:** July 7, 2026  
> **Scope:** Every `import android.*`, `import androidx.*`, and `import com.google.android.*` across 1,071 `.kt` files  
> **Method:** Systematic ripgrep searches across 17 Android package prefixes, compiled into per-file migration strategy

---

### D.1 Import Inventory by Package

#### D.1.1 Summary Table

| Android Package | Unique Files | Total Lines | Migration Category | Desktop Replacement |
|---|---|---|---|---|
| `android.app.*` | 56 | 68 | **REWRITE** / **DROP** | Compose `Window`, coroutine background tasks, macOS-native notifications |
| `android.os.*` | 56 | 83 | **STUB** / **REWRITE** | Stub `Build.VERSION`, replace `Environment` with `java.io.File`, drop `Handler/Looper/IBinder` |
| `android.view.*` | 24 | 37 | **REWRITE** / **REPLACE_LIB** | Compose equivalents (`LazyColumn`, `pointerInput`, keyboard events) |
| `android.widget.*` | 13 | 20 | **REWRITE** / **REPLACE_LIB** | Compose `Snackbar`, standard AWT `EditText`-alike, drop `Toast` |
| `android.content.*` | 102 | 176+ | **STUB** / **REWRITE** | Stub `Context`, replace `SharedPreferences` with file-based store, drop `Intent`/`BroadcastReceiver` |
| `android.database.*` | 2 | 2 | **REPLACE_LIB** | Replace `SQLiteException` with `java.sql.SQLException` |
| `android.webkit.*` | 10 | 18 | **DROP** / **REPLACE_LIB** | System browser via `java.awt.Desktop.browse()`; drop `CookieManager` for `java.net.CookieManager` |
| `androidx.*` (all) | 158+ | 156+ | **COMPOSE_UPDATE** / **DROP** | `org.jetbrains.compose.*` for Compose; drop `work`, `biometric`, `core.content` |
| `android.provider.*` | 7 | 9 | **DROP** / **STUB** | Drop `Settings` lookups, `MediaStore`, `OpenableColumns` |
| `android.net.*` | 34 | 57 | **REPLACE_LIB** | `java.net.URI` instead of `android.net.Uri`; drop `ConnectivityManager`, `WifiManager` |
| `android.graphics.*` | 33 | 54 | **REPLACE_LIB** | `java.awt.image.BufferedImage` + AWT `Color` for Bitmap/Drawable; Compose `ImageBitmap` for rendering |
| `android.animation.*` | 2 | 2 | **DROP** | Compose animation APIs |
| `android.media.*` | 4 | 5 | **DROP** / **REWRITE** | Drop `AudioManager`, `MediaSession`, `PlaybackState` — no desktop equivalent needed |
| `android.text.*` | 5 | 5 | **REPLACE_LIB** | `java.text.DateFormat` for date formatting; `java.awt.Toolkit` for display formatting |
| `android.util.*` | 12 | 15 | **STUB** / **REPLACE_LIB** | `java.util.Base64` for Base64; SLF4J for Log; drop `AttributeSet`, `DisplayMetrics`, `TypedValue` |
| `android.annotation.*` | 3 | 3 | **DROP** | Remove `@SuppressLint` annotations — not needed on desktop |
| `android.system.*` | 1 | 2 | **DROP** | Drop `Os`/`OsConstants` — only used for low-level file ops in archive module |
| `android.security.*` | 1 | 2 | **DROP** / **REWRITE** | Drop `KeyGenParameterSpec`/`KeyProperties` (CBZ crypto). Implement with JVM `javax.crypto` if needed. |
| `com.google.android.material.*` | 10 | 25 | **DROP** | Drop Material Design widgets. Replace dialog builders with Compose `AlertDialog`. |
| `com.google.android.gms.cast.*` | 11 | 63 | **DROP** | Feature dropped entirely — no macOS equivalent for Google Cast. |
| **TOTAL** | **~357 unique files** | **~800 import lines** | | |

#### D.1.2 Migration Categories Defined

| Category | Count (files) | Definition |
|---|---|---|
| **DROP** | ~95 | Feature or class has no macOS equivalent and is not needed. Remove entirely. |
| **REWRITE** | ~75 | The file is fundamentally an Android construct (Activity, Service, View, etc.) and needs a complete rewrite for Compose Desktop. |
| **STUB** | ~60 | Create a thin desktop-compatible stub class with the same API surface (e.g., stub `Context`, stub `Build`). |
| **REPLACE_LIB** | ~85 | Replace with a desktop-compatible library or JVM standard API (e.g., `java.net.URI` for `android.net.Uri`, `java.util.Base64` for `android.util.Base64`). |
| **COMPOSE_UPDATE** | ~158 | Change `androidx.compose.*` → `org.jetbrains.compose.*` imports. Same API, different package. |

---

### D.2 File-by-File Migration Map — By Module

#### D.2.1 `source-api/` Module — 3 Files

These are in `commonMain` but import Android APIs (violating KMP conventions):

| File | Android Imports | Migration |
|---|---|---|
| `animesource/utils/Preferences.kt` | `android.app.Application`, `android.content.Context`, `android.content.SharedPreferences` | **STUB** — Replace with `expect` declaration. The `Application` and `SharedPreferences` are only used for extension preference storage. |
| `animesource/ConfigurableAnimeSource.kt` | `android.app.Application`, `android.content.Context`, `android.content.SharedPreferences` | **STUB** — Same pattern. These should be `expect`/`actual` declarations, not hard Android imports in commonMain. |
| `animesource/model/Video.kt` | `android.net.Uri` | **REPLACE_LIB** — Replace with `java.net.URI` or a simple `String` representing the URI. |

#### D.2.2 `domain/` Module — 2 Files

| File | Android Imports | Migration |
|---|---|---|
| `storage/service/StorageManager.kt` | `android.content.Context` | **STUB** — Replace Context with `MacOSStorageProvider`. |
| `release/model/Release.kt` | `android.os.Build` | **STUB** — Create `DesktopBuild` stub with `SDK_INT = Int.MAX_VALUE`. |

#### D.2.3 `data/` Module — 3 Files

| File | Android Imports | Migration |
|---|---|---|
| `anime/CustomAnimeRepositoryImpl.kt` | `android.content.Context` | **STUB** — Replace with storage provider injection via Koin. |
| `custombutton/CustomButtonRepositoryImpl.kt` | `android.database.sqlite.SQLiteException` | **REPLACE_LIB** — Replace with `java.sql.SQLException`. |
| `mihon/data/repository/ExtensionRepoRepositoryImpl.kt` | `android.database.sqlite.SQLiteException` | **REPLACE_LIB** — Replace with `java.sql.SQLException`. |

#### D.2.4 `core/common/` Module — 23 Files

| File | Key Android Imports | Migration |
|---|---|---|
| `network/NetworkHelper.kt` | `Context` | **STUB** — Replace with desktop context providing cache dir, cookie jar. |
| `network/AndroidCookieJar.kt` | `CookieManager` | **REPLACE_LIB** — Rewrite as `MacOSCookieJar` using `java.net.CookieManager`. |
| `network/JavaScriptEngine.kt` | `Context` | **DROP** — Drop quickjs-android dependency. Cloudflare bypass not needed on desktop. |
| `network/interceptor/CloudflareInterceptor.kt` | `Context`, `WebView`, `WebViewClient`, `WebResourceError`, `WebResourceRequest`, `Toast` | **DROP** — Cloudflare bypass uses a hidden WebView. Drop entirely on desktop. |
| `network/interceptor/WebViewInterceptor.kt` | `Context`, `Build`, `WebSettings`, `WebView`, `Toast` | **DROP** — WebView-based interceptor. Drop on desktop. |
| `network/interceptor/RateLimitInterceptor.kt` | `SystemClock` | **REPLACE_LIB** — Replace `SystemClock.elapsedRealtime()` with `System.nanoTime()`. |
| `exh/log/EHLogLevel.kt` | `Context` | **STUB** — Replace Android log with SLF4J. |
| `exh/log/Logging.kt` | `Log` | **REPLACE_LIB** — Replace `android.util.Log` with SLF4J. |
| `util/system/DeviceUtil.kt` | `ActivityManager`, `Context`, `Build` | **STUB** / **DROP** — Stub device info. Not critical on desktop. |
| `util/system/DensityExtensions.kt` | `Resources` | **DROP** — Density concepts are Android-only. Use fixed desktop scale factor. |
| `util/system/ToastExtensions.kt` | `Context`, `Toast` | **REPLACE_LIB** — Replace with Compose `Snackbar` or `MacOSToast`. |
| `util/system/WebViewUtil.kt` | `Context`, `PackageManager`, `CookieManager`, `WebSettings`, `WebView` | **DROP** — WebView utilities. Drop entirely. |
| `util/storage/DiskUtil.kt` | `Context`, `MediaScannerConnection`, `Uri`, `Environment`, `StatFs` | **REPLACE_LIB** — Replace with `java.io.File` for disk operations. |
| `util/storage/FFmpegUtils.kt` | `Context`, `Uri` | **STUB** — Replace with `ProcessBuilder("ffmpeg", ...)` on desktop. |
| `tachiyomi/core/common/i18n/Localize.kt` | `Context` | **STUB** — Replace with `java.util.Locale` for locale detection. |
| `tachiyomi/core/common/preference/AndroidPreferenceStore.kt` | `Context`, `SharedPreferences` | **REWRITE** — Replace with file-based JSON preferences. |
| `tachiyomi/core/common/preference/AndroidPreference.kt` | `SharedPreferences`, `SharedPreferences.Editor` | **REWRITE** — Replace with file-based preference implementation. |
| `tachiyomi/core/common/storage/AndroidStorageFolderProvider.kt` | `Context`, `Environment` | **REWRITE** — Replace with `~/Library/Application Support/Anikku/` paths. |
| `tachiyomi/core/common/storage/UniFileTempFileManager.kt` | `Context`, `Build`, `FileUtils` | **REWRITE** — Replace with `java.io.File.createTempFile()`. |
| `tachiyomi/core/common/util/system/ImageUtil.kt` | `Context`, `Configuration`, `Resources`, `Bitmap`, `BitmapFactory`, `BitmapRegionDecoder`, `Canvas`, `Color`, `Matrix`, `Rect`, `ColorDrawable`, `Drawable`, `GradientDrawable`, `Build` | **REPLACE_LIB** — Replace with `java.awt.image.BufferedImage` + AWT image operations. This is a large file with 14 Android graphics imports. |

#### D.2.5 `core/archive/` Module — 4 Files

| File | Key Android Imports | Migration |
|---|---|---|
| `ArchiveReader.kt` | `ParcelFileDescriptor`, `Os`, `OsConstants` | **REPLACE_LIB** — Replace `ParcelFileDescriptor` with `java.io.RandomAccessFile`. Drop `Os`/`OsConstants`. |
| `UniFileExtensions.kt` | `Context`, `ParcelFileDescriptor` | **STUB** — Replace Context. Drop `ParcelFileDescriptor` for `java.io.File`. |
| `CbzCrypto.kt` | `KeyGenParameterSpec`, `KeyProperties`, `Base64` | **DROP** / **REPLACE_LIB** — Replace `Base64` with `java.util.Base64`. Drop Android keystore crypto; use JVM `javax.crypto` if CBZ encryption is needed. |

#### D.2.6 `presentation-core/` Module — 4 Files

| File | Key Android Imports | Migration |
|---|---|---|
| `components/VerticalFastScroller.kt` | `ViewConfiguration` | **REPLACE_LIB** — Replace with desktop scrollbar component. |
| `components/SettingsItems.kt` | `MotionEvent` | **REPLACE_LIB** — Replace with Compose `pointerInput`. |
| `components/AdaptiveSheet.kt` | `Configuration.ORIENTATION_LANDSCAPE` | **REPLACE_LIB** — Replace with window size check. |
| `util/Scrollbar.kt` | `ViewConfiguration` | **REPLACE_LIB** — Replace with desktop scroll detection. |

Plus ~154 presentation-core files using `androidx.compose.*` → **COMPOSE_UPDATE** (change imports to `org.jetbrains.compose.*`).

#### D.2.7 `app/src/main/java/` — Activity & Service Files (Complete Rewrites)

These are the ~75 files that are fundamentally Android constructs needing full rewrites:

**Activities (REWRITE → Compose `Window` + `Screen`):**
| File | Migration |
|---|---|
| `ui/main/MainActivity.kt` | **REWRITE** → `MainWindow.kt` composable |
| `ui/player/PlayerActivity.kt` | **REWRITE** → `PlayerScreen.kt` + `PlayerController.kt` |
| `ui/webview/WebViewActivity.kt` | **DROP** — Use system browser |
| `ui/security/UnlockActivity.kt` | **REWRITE** → `LockDialog.kt` composable |
| `ui/deeplink/DeepLinkActivity.kt` | **DROP** — No Android deep links on desktop |
| `ui/base/activity/BaseActivity.kt` | **DROP** — No Activity base class needed |
| `ui/base/delegate/ThemingDelegate.kt` | **DROP** — Compose theming is different |
| `ui/base/delegate/SecureActivityDelegate.kt` | **DROP** — Security handled differently |
| `ui/setting/track/BaseOAuthLoginActivity.kt` | **REWRITE** → OAuth via desktop browser |
| `ui/setting/track/TrackLoginActivity.kt` | **REWRITE** → OAuth via desktop browser |
| `ui/setting/track/GoogleDriveLoginActivity.kt` | **REWRITE** → OAuth via desktop browser |
| `ui/setting/connections/DiscordLoginActivity.kt` | **REWRITE** → OAuth via desktop browser |
| `crash/CrashActivity.kt` | **REWRITE** → Error dialog composable |
| `ui/player/cast/ExpandedControlsActivity.kt` | **DROP** (Cast) |
| `extension/util/ExtensionInstallActivity.kt` | **DROP** — Extensions loaded as JARs |

**Services (REWRITE → Coroutine Background Tasks):**
| File | Migration |
|---|---|
| `data/connections/discord/DiscordRPCService.kt` | **REWRITE** — Coroutine-based RPC with window focus lifecycle |
| `data/torrentServer/service/TorrentServerService.kt` | **REWRITE** — Coroutine-based TorrServer launcher |
| `util/LocalHttpServerService.kt` | **REWRITE** — Coroutine-based NanoHTTPd server |
| `extension/util/ExtensionInstallService.kt` | **DROP** — No APK installation |
| `extension/installer/Installer.kt` | **DROP** — No APK installation |
| `extension/installer/PackageInstallerInstaller.kt` | **DROP** — No APK installation |
| `extension/installer/ShizukuInstaller.kt` | **DROP** (Shizuku) |

**BroadcastReceivers (DROP):**
| File | Migration |
|---|---|
| `data/notification/NotificationReceiver.kt` | **DROP** — No Android broadcast receivers |
| `data/updater/AppUpdateBroadcast.kt` | **DROP** — No Android broadcast receivers |
| `extension/util/ExtensionInstallReceiver.kt` | **DROP** — No Android broadcast receivers |

**Cast & Google Services (DROP):**
| File | Migration |
|---|---|
| `ui/player/CastManager.kt` | **DROP** |
| `ui/player/cast/*` (6 files) | **DROP** |
| `ui/player/PipActions.kt` | **DROP** (replaced with Compose mini window) |
| `extension/installer/ShizukuInstaller.kt` | **DROP** |

#### D.2.8 `app/src/main/java/` — Key Partial Rewrites

| File | Android Import | Migration |
|---|---|---|
| `App.kt` | `Application`, `PendingIntent`, `BroadcastReceiver`, `Context`, `Intent`, `IntentFilter`, `Build`, `Looper`, `WebView` | **REWRITE** → `AnikkuApplication.kt` with Koin + CoroutineScope |
| `ui/player/PlayerViewModel.kt` | `Application`, `InputMethodManager`, `Settings`, `Uri`, `DisplayMetrics`, `AudioManager` | **REWRITE** with extracted pure-Kotlin state machine |
| `ui/player/AniyomiMPVView.kt` | `Context`, `Build`, `Environment`, `KeyCharacterMap`, `KeyEvent`, `AttributeSet` | **REWRITE** → `MacOSMPVView` composable |
| `ui/player/PlayerObserver.kt` | `Toast` | **REPLACE_LIB** → Snackbar |
| `ui/player/ExternalIntents.kt` | `Application`, `Uri`, `Build`, `Bundle` | **DROP** — No external intents on desktop |
| `ui/player/PlayerUtils.kt` | `Uri`, `ParcelFileDescriptor`, `OpenableColumns` | **REPLACE_LIB** → `java.io.File` |
| `ui/anime/EditAnimeDialog.kt` | `LayoutInflater`, `ArrayAdapter`, Material widgets | **REWRITE** → Compose dialog with `LazyColumn` |
| `ui/reader/viewer/ReaderPageImageView.kt` | `GestureDetector`, `MotionEvent`, `View`, `Bitmap`/`Drawable` tree, `AttributeSet`, `FrameLayout` | **DROP** — Manga reader is Android-only; not porting for v1 |
| `ui/download/DownloadQueueScreen.kt` | `LayoutInflater` | **REWRITE** → Compose `LazyColumn` |
| `ui/download/DownloadAdapter.kt` | `MenuItem` | **REWRITE** → Compose `LazyColumn` |
| `ui/download/DownloadItem.kt` | `View` | **REWRITE** → Compose composable |
| `ui/download/DownloadHolder.kt` | `View` | **REWRITE** → Compose composable |
| `ui/download/DownloadHeaderItem.kt` | `View` | **REWRITE** → Compose composable |
| `ui/download/DownloadHeaderHolder.kt` | `View` | **REWRITE** → Compose composable |
| `ui/download/DownloadQueueScreenModel.kt` | `MenuItem` | **REPLACE_LIB** → Drop MenuItem usage |
| `widget/MinMaxNumberPicker.kt` | `Context`, `ViewGroup`, `InputType`, `AttributeSet`, `EditText`, `NumberPicker` | **REWRITE** → Compose custom number picker |
| `widget/RevealAnimationView.kt` | `Context`, `View`, `ViewAnimationUtils`, `Animator`, `AttributeSet` | **DROP** — Compose has its own animation system |
| `widget/TachiyomiTextInputEditText.kt` | `Context`, `AttributeSet`, `EditText`, Material `TextInputEditText` | **REWRITE** → Compose `OutlinedTextField` |
| `widget/materialdialogs/MaterialAlertDialogBuilderExtensions.kt` | `LayoutInflater`, `InputMethodManager`, `TextView`, Material `MaterialAlertDialogBuilder` | **DROP** — Use Compose `AlertDialog` |

#### D.2.9 Files Using Only `androidx.compose.*` (COMPOSE_UPDATE — ~158 Files)

These files only need package name changes and preview annotation removal. They are spread across:

- `app/src/main/java/eu/kanade/presentation/` — All screens and components (~120 files)
- `presentation-core/src/main/java/tachiyomi/presentation/core/` — Core components (~30 files)
- `app/src/main/java/mihon/feature/` — Feature modules (~8 files)

**Automated migration script:**
```bash
# Step 1: Replace androidx.compose imports with Compose Desktop equivalents
find anikku -name "*.kt" -path "*/macos/*" -exec sed -i '' \
  -e 's/import androidx\.compose\./import org.jetbrains.compose./g' \
  -e 's/import androidx\.activity\.compose\.BackHandler/import androidx.compose.ui.input.key.onPreviewKeyEvent/g' \
  {} \;

# Step 2: Remove preview annotations (not supported on desktop)
find anikku -name "*.kt" -path "*/macos/*" -exec sed -i '' \
  -e '/import androidx\.compose\.ui\.tooling\.preview\./d' \
  -e 's/@PreviewLightDark/\/\/ @Preview removed for desktop/g' \
  -e 's/@Preview/\/\/ @Preview removed for desktop/g' \
  {} \;

# Step 3: Replace resource imports
find anikku -name "*.kt" -path "*/macos/*" -exec sed -i '' \
  -e 's/import androidx\.compose\.ui\.res\.painterResource/import org.jetbrains.compose.resources.painterResource/g' \
  -e 's/import androidx\.compose\.ui\.res\.stringResource/import org.jetbrains.compose.resources.stringResource/g' \
  {} \;
```

#### D.2.10 Other Android-Specific Packages

| Package | Files | Migration |
|---|---|---|
| `android.net.Uri` | 34 files | **REPLACE_LIB** — Replace with `java.net.URI`. Used extensively for file paths, tracker API URLs, backup URIs. |
| `android.graphics.Bitmap` | 15 files | **REPLACE_LIB** — Replace with `java.awt.image.BufferedImage` or Compose `ImageBitmap`. Used in image loading, cover caching, notifications, widgets. |
| `android.graphics.drawable.Drawable` | 10 files | **REPLACE_LIB** — Replace with Compose `Painter` or AWT `BufferedImage`. |
| `android.graphics.Color` | 10 files | **REPLACE_LIB** — Replace with `java.awt.Color` or Compose `Color`. Used for tracker logos and theme colors. |
| `android.util.Base64` | 2 files | **REPLACE_LIB** — Replace with `java.util.Base64`. |
| `android.util.Log` | 4 files | **REPLACE_LIB** — Replace with SLF4J. |
| `android.util.DisplayMetrics` | 3 files | **DROP** — Use `java.awt.Toolkit.getDefaultToolkit().screenResolution` or ignore. |
| `android.util.AttributeSet` | 5 files | **DROP** — Compose doesn't use XML attribute sets. |
| `android.text.format.DateUtils` | 2 files | **REPLACE_LIB** — Replace with `java.time` or `kotlinx-datetime`. |
| `android.text.format.Formatter` | 2 files | **REPLACE_LIB** — Replace with manual formatting. |
| `android.provider.Settings` | 5 files | **DROP** — No system settings lookups needed on desktop. |
| `android.media.AudioManager` | 2 files | **DROP** — Volume controlled by mpv/VLC, not system audio. |
| `com.google.android.material.color.utilities.*` (Monet) | 3 files | **REPLACE_LIB** — `material-kolor` library works on Compose Desktop. Keep for dynamic theming. |
| `com.google.android.material.*` widgets | 7 files | **DROP** — Replace with Compose equivalents. |
| `com.google.android.gms.cast.*` | 11 files, 63 lines | **DROP** — Feature dropped entirely. |

---

### D.3 Framework & Dependency Replacement Reference

#### D.3.1 Android Framework → JVM/Compose Desktop

| Android API | Desktop Replacement | Notes |
|---|---|---|
| `SharedPreferences` | JSON file + `kotlinx.serialization` | Simpler and more debuggable |
| `Context` | Koin injection + file paths | The biggest lift — ~102 files use `Context` |
| `androidx.work.WorkManager` | `BackgroundTaskScheduler` (coroutines) | See Phase 1.7 |
| `androidx.lifecycle.ProcessLifecycleOwner` | Window focus/blur events | Coroutine `SupervisorJob` scope |
| `androidx.biometric.BiometricPrompt` | `LocalAuthentication.framework` via JNA | See Phase 7.4 |
| `NotificationManager` / `NotificationChannel` | macOS Notification Center via `java.awt.TrayIcon` | See Phase 7.6 |
| `DownloadManager` (system) | OkHttp download to app directory | Already partially used in app |
| `PackageInstaller` / `PackageManager` | `URLClassLoader` + JAR files | See Phase 3.3 |
| `Intent` / `IntentFilter` / `BroadcastReceiver` | Compose event bus / coroutine channels | No need for Android's intent system |
| `ParcelFileDescriptor` | `java.io.RandomAccessFile` or `java.nio.channels.FileChannel` | For archive reading |
| `PictureInPictureParams` | Compose secondary `Window` (always-on-top, undecorated) | See Phase 9.5 |
| `WebView` | `java.awt.Desktop.browse(URI)` | See Phase 8 |
| `Toast` | Compose `Snackbar` or custom composable | See Phase 5.13 |

#### D.3.2 Android → JVM Type Mapping

| Android Type | JVM/Desktop Type | Migration Effort |
|---|---|---|
| `android.net.Uri` | `java.net.URI` or `String` | Low — mechanical replacement in 34 files |
| `android.graphics.Bitmap` | `java.awt.image.BufferedImage` / Compose `ImageBitmap` | Medium — 15 files need adapter wrappers |
| `android.graphics.Color` | `java.awt.Color` / Compose `Color` | Low — 10 files, mechanical |
| `android.graphics.drawable.Drawable` | Compose `Painter` | Medium — 10 files, needs wrapper |
| `android.graphics.Rect` | `java.awt.Rectangle` | Low — 4 files |
| `android.os.Environment` | `java.io.File` + known paths | Low |
| `android.os.Build` | Stub class with `SDK_INT = Int.MAX_VALUE` | Low — 60+ files, but only need version checks to pass |
| `android.os.Handler` / `Looper` | `kotlinx.coroutines.Dispatchers.Main` | Low — 3 files |
| `android.os.SystemClock` | `System.nanoTime()` | Trivial — 1 file |
| `android.os.StatFs` | `java.io.File.getFreeSpace()` | Trivial — 1 file |
| `android.os.ParcelFileDescriptor` | `java.io.File` / `FileInputStream` | Low — 3 files in archive module |
| `android.os.Bundle` | `Map<String, Any>` or drop | Low — used in Activity plumbing that gets rewritten |
| `android.database.sqlite.SQLiteException` | `java.sql.SQLException` | Trivial — 2 files |
| `android.util.Base64` | `java.util.Base64` | Trivial — 2 files |
| `android.util.Log` | SLF4J / `kotlin-logging` | Trivial — 4 files |
| `android.widget.Toast` | Compose `Snackbar` | Low — 10 files, but each call site is 1 line |

---

### D.4 Summary Statistics

#### D.4.1 By Migration Strategy

| Strategy | File Count | % of 1,071 | Effort Level |
|---|---|---|---|
| **PASS_THROUGH** (no Android imports) | ~400 | 37% | Zero — compile as-is |
| **COMPOSE_UPDATE** (`androidx.compose` → `org.jetbrains.compose`) | ~300 | 28% | Low — mostly automated |
| **PARTIAL REWRITE** (some Android imports, some portable) | ~241 | 23% | Medium — file-by-file adaptation |
| **COMPLETE REWRITE** (Fundamentally Android) | ~130 | 12% | High — ground-up rebuild |
| **TOTAL** | **~1,071** | **100%** | |

#### D.4.2 By Android Import Category (most impactful)

| Android Import | Files Affected | Best Replacement Strategy |
|---|---|---|
| `android.content.Context` | **102** | Stub `Context` for paths/prefs; replace with Koin injection for all business logic |
| `android.os.Build` / `Build.VERSION` | **56** | One stub: `object DesktopBuild { const val SDK_INT = Int.MAX_VALUE }` |
| `android.net.Uri` | **34** | Replace with `java.net.URI` — mechanical find/replace |
| `androidx.compose.*` | **158+** | Replace with `org.jetbrains.compose.*` — mostly automated |
| `android.app.Application` | **30** | Replace with Koin injection |
| `android.view.*` | **24** | Drop View system; use Compose |
| `android.graphics.*` | **33** | Replace with AWT/Compose equivalents |
| `com.google.android.gms.cast.*` | **11** | Drop entire feature |
| `android.widget.*` | **13** | Drop widget toolkit; use Compose |

#### D.4.3 Estimated Effort by Category

| Category | Files | Est. Hours | Est. Days | Notes |
|---|---|---|---|---|
| PASS_THROUGH | ~400 | 0 | 0 | Compile and verify |
| COMPOSE_UPDATE | ~300 | 4-8 | 0.5-1 | Mostly automated scripts + manual verification |
| STUB creation (Context, Build, etc.) | ~60 | 8-16 | 1-2 | Write stub classes once, then minimal per-file changes |
| REPLACE_LIB (URI, Bitmap, Base64, etc.) | ~85 | 16-32 | 2-4 | Mechanical replacements, some adapter wrappers |
| Player subsystem rewrite | ~48 | 40-60 | 5-8 | Most complex subsystem (mpv + UI) |
| Download queue rewrite | ~7 | 8-12 | 1-1.5 | RecyclerView → LazyColumn |
| Remaining COMPLETE REWRITEs | ~75 | 40-60 | 5-8 | Activities, Services, View widgets |
| New file creation | ~100 | 40-80 | 5-10 | Platform abstractions, JNA bindings, UI |
| **TOTAL** | **~1,171** | **156-268** | **~20-35** | |

**Realistic timeline with LLM assistance:** 3-6 weeks (matching the original estimate).

---

*End of Appendix D — Complete Android Import Migration Map.*
