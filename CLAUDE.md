# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

**Anikku** is an Android anime/movie watcher app (min SDK 26, target SDK 36, JVM 17 / Kotlin). It's based on Aniyomi with additional features from Mihon, TachiyomiSY, and Komikku (manga fork). The app includes:
- Anime discovery and playback with configurable mpv-android player
- Local downloading and offline viewing
- Multiple tracker support (MyAnimeList, AniList, Kitsu, Simkl, Shikimori, Bangumi)
- Anime recommendations and metadata editing
- Library management with categories, tagging, and filtering
- Multi-source browsing and feed tabs

**Application ID**: `app.anikku` (debug variant: `app.anikku.dev`)

---

## Build & Development Environment

**Tech Stack**: Jetpack Compose + Material3 • Voyager navigation • SQLDelight database • Injekt DI

**JDK/Gradle**: JVM 17 (compiles to Kotlin JVM 17 target) • Gradle 9.3+ • Android SDK 36 (compileSdk)

### Essential Build Commands

```bash
# Code formatting (required before committing)
./gradlew spotlessApply           # Auto-fix formatting
./gradlew spotlessCheck           # Verify formatting (runs in CI)

# Build variants
./gradlew :app:compileDebugKotlin # Compile-only check (faster than assembleDebug)
./gradlew assembleDebug           # Debug APK (local testing)
./gradlew assemblePreview         # Preview (beta) APK (CI equivalent; use for consistency)
./gradlew assembleRelease         # Release APK

# Testing
./gradlew testReleaseUnitTest # Unit tests for release build
./gradlew test                # All unit tests

# Full CI build with telemetry and updater
./gradlew assemblePreview -Pinclude-telemetry -Penable-updater

# Install to device/emulator
./gradlew installDebug

# SQLDelight database regeneration (after .sq or .sqm schema changes)
./gradlew :data:generateSqlDelightInterface

# Clean build daemon (if OOM occurs)
./gradlew --stop
```

**Build types**: `debug`, `release`, `releaseTest`, `foss`, `preview` (default), `benchmark`

**Gradle `-P` flags**:

| Flag | Effect                                   |
|------|------------------------------------------|
| `include-telemetry` | Include Firebase Analytics + Crashlytics |
| `enable-updater` | Enable In-app update checker             |
| `disable-code-shrink` | Skip ProGuard/R8 minification            |
| `include-dependency-info` | Dependency metadata in APK               |

## Code Marking Convention

All Anikku-specific additions or modifications **must** be surrounded with comment markers:

```kotlin
// ANK -->
// your code here
// ANK <--
```

This is how Anikku-specific changes are tracked relative to the upstream Aniyomi fork. Always apply these markers to any changes you make.

All fork markers used in this codebase:

| Marker | Origin | Notes |
|--------|--------|-------|
| `// ANK` | Anikku-specific | Use for all new Anikku code |
| `// SY` | TachiyomiSY | Keep intact during upstream merges |
| `// KMK` | Komikku | Keep intact during upstream merges |
| `// AY` | Aniyomi | Keep intact during upstream merges |
| `// EXH` | E-Hentai (legacy) | Avoid; use `// ANK` for new code |

---

## Architecture

Multi-module layered architecture (22 modules) with MVVM + MVI patterns.

### Module Layout

| Module | Purpose |
|--------|---------|
| `app/` | UI (Voyager Screens + Compose), DI, workers, build variants |
| `domain/` | Use cases, models, repository interfaces |
| `data/` | SQLDelight database, repository implementations |
| `core:common/` | Network (OkHttp), security, storage, shared utils |
| `core:archive/` | Archive reading utilities |
| `core-metadata/` | Comic-info metadata parsing |
| `source-api/` | Extension `Source` interface + local source |
| `presentation-core/` | Shared Compose components |
| `i18n-ank/` | **Anikku-specific strings** → `AMR` resource class |
| `i18n/` | Mihon strings → `MR` (frozen upstream) |
| `i18n-sy/` | TachiyomiSY strings → `SYMR` (frozen upstream) |
| `i18n-aniyomi/` | Aniyomi strings → `AYMR` (frozen upstream) |
| `i18n-kmk/` | Komikku strings → `KMR` (frozen upstream) |
| `i18n-animiru/` | Animiru strings → `AMMR` (frozen upstream) |
| `presentation-widget/` | Home-screen Glance widget |
| `flagkit/` | Country-flag drawables |
| `telemetry/` | Firebase/Crashlytics (noop unless `-Pinclude-telemetry`) |
| `buildSrc/` | Custom Gradle plugins and build logic |

**Dependency flow**: `app` → `domain` → `source-api`; `data` implements `domain` repos.

**Data flow:** `source-api` extensions → `data` repositories → `domain` use cases → `presentation-core` ViewModels → `app` screens

**Navigation:** Voyager screens/tabs. Screen classes live in `app/src/main/java/.../ui/`.

**DI:** Injekt (lightweight custom container). Dependencies are registered at app startup and retrieved via `Injekt.get<T>()` or by implementing `Injekt.inject<T>()` delegation.

**Database:** SQLDelight with migrations in `data/src/main/sqldelight/`. Schema changes require a new migration file.

**Concurrency:** Kotlin coroutines + Flow for new code. RxJava remains in the `source-api` layer (extension compatibility).

## Key Technologies

- **UI:** Jetpack Compose + Material 3, Coil 3 for image loading
- **Video:** mpv-android, FFmpeg-kit
- **Network:** OkHttp 5 with DNS-over-HTTPS, Brotli support
- **Serialization:** Kotlinx Serialization (JSON/Protobuf)
- **Scripting:** QuickJS (JavaScript engine for source extensions)
- **Database encryption:** SQLCipher

### Key Source Structure

- **Activities** (non-Voyager): `MainActivity` (shell), `PlayerActivity` + `PlayerViewModel`, `WebViewActivity`, OAuth login, `DeepLinkActivity`
- **UI Screens** (Voyager): `app/src/main/java/eu/kanade/presentation/` & `app/src/main/java/eu/kanade/tachiyomi/ui/`
- **Domain use cases**: `domain/src/main/java/tachiyomi/domain/*/interactor/` (one class per operation; no `*Interactor` suffix)
- **Database**: SQLDelight in `data/src/main/sqldelight/tachiyomi/`
- **Preferences**: `eu.kanade.domain.*.service.*Preferences` classes

---

## Internationalization (Strings)

**Critical rule**: Never add Anikku strings to non-`i18n-ank/` modules. Weblate manages translations; never edit non-base locale files.

| String Type | Module | Resource Class | Base Folder |
|------------|--------|----------------|-------------|
| **Anikku-only** | `i18n-ank/` | **`AMR`** | `i18n-ank/src/commonMain/moko-resources/base/` |
| Mihon (frozen) | `i18n/` | `MR` | `i18n/src/commonMain/moko-resources/base/` |
| SY (frozen) | `i18n-sy/` | `SYMR` | `i18n-sy/src/commonMain/moko-resources/base/` |
| Aniyomi (frozen) | `i18n-aniyomi/` | `AYMR` | `i18n-aniyomi/src/commonMain/moko-resources/base/` |
| Komikku (frozen) | `i18n-kmk/` | `KMR` | `i18n-kmk/src/commonMain/moko-resources/base/` |
| Animiru (frozen) | `i18n-animiru/` | `AMMR` | `i18n-animiru/src/commonMain/moko-resources/base/` |

**Self-check**: `git diff` must not show `<string>` or `<plurals>` additions in non-`base` locales.

---

## Architecture & Design Patterns

### Dependency Injection

Uses **`uy.kohesive.injekt`** (not Hilt):
- Register in `AppModule.kt`, `DomainModule.kt`, `KMKDomainModule.kt`, `SYDomainModule.kt`
- Use `addSingleton` / `addSingletonFactory` for registration
- In class fields: `injectLazy<T>()`
- In functions: `Injekt.get<T>()`

### UI & Navigation

**Voyager** is the primary navigation framework:
- Screens defined in `app/src/main/java/eu/kanade/presentation/` and `app/src/main/java/eu/kanade/tachiyomi/ui/`
- State via `rememberScreenModel { … }`; models extend `StateScreenModel<State>`
- Use `screenModelScope` for lifecycle-bound coroutines
- `launchIO` / `withIOContext` (from `tachiyomi.core.common.util.lang`) run on GlobalScope — prefer `screenModelScope` for UI lifecycle-bound work

### Domain / Data Layer

- One interactor class per operation: verb-based names (e.g., `GetTracksPerManga`, `HideCategory`) under `domain/…/interactor/`
- Wire in `eu.kanade.domain.DomainModule.kt` + `KMKDomainModule`, `SYDomainModule`
- `SAnime` / `SEpisode` are source-layer interfaces; `Anime` / `Episode` are domain models (never use source types in domain/UI)

### Database Migrations

Schema changes in SQLDelight:
1. Add `.sqm` migration file
2. Update `.sq` query
3. Update `*RepositoryImpl` mapper
4. Run `./gradlew :data:generateSqlDelightInterface`

**App preference migrations**: `app/src/main/java/mihon/core/migration/migrations/`

### Images

**Coil 3** (`coil3.*`); no Glide/Picasso. Configured in `App.kt`.

### Logging

- **Anikku code**: `xLogE()` / `xLog()` from `exh.log`
- **Mihon code**: `logcat { }` from `tachiyomi.core.common.util.system`

---

## Adding New Anikku Features

1. **Create domain interactor**: `domain/src/main/java/tachiyomi/domain/*/interactor/MyFeature.kt`
2. **Register DI**: Add `addFactory { MyFeature(get()) }` in `DomainModule.kt`
3. **Create UI**: ScreenModel + Screen under `app/src/main/java/eu/kanade/tachiyomi/ui/`
4. **Add strings**: Place in `i18n-ank/src/commonMain/moko-resources/base/strings.xml` using `AMR` resource class
5. **Wrap code**: Surround new/edited lines with `// ANK -->` / `// ANK <--` markers (see [Code Marking Convention](#code-marking-convention)).

Reference: `domain/src/main/java/tachiyomi/domain/category/interactor/HideCategory.kt` + `DomainModule.kt:133`.

---

## Testing

**Framework**: JUnit 5 + Kotest + Mockk

**Test locations**:
- Unit tests live in `<module>/src/test/java/`
- Unit tests mostly in: `domain/src/test/` and `app/src/test/`
- Macro benchmarks: `macrobenchmark/` (CI-only)

**Running tests**:
```bash
./gradlew testReleaseUnitTest          # All release unit tests
./gradlew test                          # All tests
./gradlew :domain:testReleaseUnitTest --tests "*.ClassName" # Run a single test class
```

No broad UI test suite exists; focus on domain layer and critical logic tests.

---

## Code Style & Linting

**Formatter**: Spotless + ktlint

**Linting rules**:
- Kotlin: ktlint with trimTrailingWhitespace, endWithNewline
- XML: trimTrailingWhitespace, endWithNewline (excludes `**/build/**`, non-base i18n locales)

**Always run before commit**:
```bash
./gradlew spotlessApply   # Fix issues
./gradlew spotlessCheck   # Verify (CI gate — must pass)
```

Never skip `spotlessCheck`; if it fails, run `spotlessApply` and retry.

---

## Git Workflow

- **Branch naming**: Use `<type>/<short-description>` (e.g., `feat/anime-recommendations`)
- **Commits**: OK on feature branches; **never commit/push to `master` or `main` unless explicitly asked**
- **Before push**: Confirm current branch is not `master`/`main` (`git branch --show-current`)
- **Code markers**: New Anikku-specific code inside `// ANK` blocks; preserve upstream `// SY`, `// KMK`, `// AY` during merges

---

## CI/CD

**Primary workflows** (`.github/workflows/`):
- `build_pull_request.yml` – PR validation: spotlessCheck → assemblePreview → testReleaseUnitTest
- `build_push.yml` – Push to main: full build + signing
- `build_release.yml` – Release builds

**PR check gates**:
1. Dependency review
2. Gradle wrapper validation
3. Code format check (`spotlessCheck`)
4. Build (`assemblePreview`)
5. Unit tests (`testReleaseUnitTest`)
6. APK signing (if authorized fork)

---

## Version Catalogs

Dependencies managed via version catalogs in `gradle/`:
- `libs.versions.toml` – Core dependencies
- `kotlinx.versions.toml` – Kotlinx libraries
- `androidx.versions.toml` – AndroidX libraries
- `compose.versions.toml` – Compose libraries
- `sy.versions.toml` – TachiyomiSY-specific
- `aniyomi.versions.toml` – Aniyomi-specific

---

## Key Files

- `App.kt` – Injekt bootstrap, logging setup, Coil initialization
- `MainActivity.kt` – Voyager navigation host
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` – Core DI setup
- `app/src/main/java/eu/kanade/domain/DomainModule.kt` – Domain interactor registration

---

## Contributing Guidelines

See `CONTRIBUTING.md` for prerequisites and contribution process. Key rules are covered inline above: code marking in [Code Marking Convention](#code-marking-convention), linting in [Code Style & Linting](#code-style--linting), and strings in [Internationalization](#internationalization-strings). Translations go through Weblate — never edit non-base locale files directly.

---

## Notes for Multi-Source Development

This codebase merges features from multiple sources:
- **Aniyomi** (upstream): Video player, tracker support, episodes
- **Mihon** (upstream): Manga reader foundation (adapted for anime)
- **TachiyomiSY** (upstream): Recommendations, metadata editing, advanced features
- **Komikku** (fork peer): Advanced library features, UI patterns

**Merge strategy**: Keep fork markers intact; preserve upstream `// SY`, `// KMK`, `// AY` blocks when rebasing or cherry-picking. New Anikku-specific code uses `// ANK` markers.

---

## Common Issues & Solutions

**Gradle OOM**: Run `./gradlew --stop` to kill daemon.

**Spotless failures**: Run `spotlessApply` first, then `spotlessCheck`. See [Code Style & Linting](#code-style--linting).

**SQLDelight errors**: See [Database Migrations](#database-migrations) — run `./gradlew :data:generateSqlDelightInterface` after schema changes.

**i18n mistakes**: Anikku strings must use `AMR` + `i18n-ank/…/base/`. Non-base locales are Weblate-managed.
