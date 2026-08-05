# Anikku – AI Agent Guide

Anikku is an Android anime/movie watcher (min SDK 26, target SDK 36, JVM 17 / Kotlin). Stack: Jetpack Compose + Material3, Voyager navigation, SQLDelight, Injekt DI. `applicationId`: `app.anikku`.

---

## Mandatory rules for AI agents

**Read before every change.** These rules override shortcuts (e.g. copying nearby `MR` imports or only running `compileDebugKotlin`).

### Git

| Rule | Required behavior |
|------|-------------------|
| Branch | Create a **feature branch** (`git checkout -b <type>/<short-description>`). |
| Commit | OK on a feature branch. **Never** commit/push to `master`/`main` unless explicitly asked. |

Before `git push`: confirm current branch is not `master`/`main` (`git branch --show-current`).

### Internationalization (strings)

| String kind | Module | Resource class | Base folder |
|-------------|--------|----------------|-------------|
| Anikku-only | `i18n-ank/` | **`AMR`** | `i18n-ank/src/commonMain/moko-resources/base/` |
| Mihon-only (frozen upstream) | `i18n/` | **`MR`** | `i18n/src/commonMain/moko-resources/base/` |
| TachiyomiSY-only (frozen upstream) | `i18n-sy/` | **`SYMR`** | `i18n-sy/src/commonMain/moko-resources/base/` |
| Aniyomi-only (frozen upstream) | `i18n-aniyomi/` | **`AYMR`** | `i18n-aniyomi/src/commonMain/moko-resources/base/` |
| Komikku-only (frozen upstream) | `i18n-kmk/` | **`KMR`** | `i18n-kmk/src/commonMain/moko-resources/base/` |
| Animiru-only (frozen upstream) | `i18n-animiru/` | **`AMMR`** | `i18n-animiru/src/commonMain/moko-resources/base/` |

**Rules:** Never add Anikku strings to `i18n/`, `i18n-sy/`, or other non-`i18n-ank/` modules. Never edit non-`base` locale files (Weblate owns translations). Inside `// ANK` blocks → use `AMR` + `i18n-ank`. **Self-check:** `git diff` must not add `<string>` or `<plurals>` under non-`base` locales in any `i18n*/src/`.

### Build verification (run in order, after every Kotlin/XML edit)

```bash
./gradlew spotlessApply    # fix formatting
./gradlew spotlessCheck    # must pass (CI gate)
./gradlew assembleDebug    # or :app:compileDebugKotlin for compile-only
```

Never skip `spotlessCheck`. If it fails, run `spotlessApply` and retry.

---

## Module layout

| Module | Purpose |
|--------|---------|
| `app/` | UI (`eu.kanade.*`, `exh/`, `mihon/`), DI, workers, build variants |
| `domain/` | Use cases in `…/interactor/`, models, repo interfaces |
| `data/` | SQLDelight DB, `*RepositoryImpl` |
| `core:common/` | Network (OkHttp), security, storage, shared utils |
| `core:archive/` | Archive reading |
| `core-metadata/` | Comic-info metadata parsing |
| `source-api/` / `source-local/` | Extension `Source` API + local source |
| `presentation-core/` | Shared Compose components |
| `presentation-widget/` | Home-screen Glance widget |
| `i18n/` | Mihon strings → `MR` (frozen upstream) |
| `i18n-ank/` | Anikku strings → `AMR` |
| `i18n-aniyomi/` | Aniyomi strings → `AYMR` (frozen upstream) |
| `i18n-kmk/` | Komikku strings → `KMR` (frozen upstream) |
| `i18n-sy/` | TachiyomiSY strings → `SYMR` (frozen upstream) |
| `i18n-animiru/` | Animiru strings → `AMMR` (frozen upstream) |
| `flagkit/` | Country-flag drawables |
| `telemetry/` | Firebase/Crashlytics (noop unless `-Pinclude-telemetry`) |

Dependency flow: `app` → `domain` → `source-api`; `data` implements `domain` repos.

Version catalogs: `gradle/libs.versions.toml`, `kotlinx.versions.toml`, `androidx.versions.toml`, `compose.versions.toml`, `sy.versions.toml`, `aniyomi.versions.toml`.

---

## Architecture

**DI** – `uy.kohesive.injekt` (not Hilt). Register in `AppModule.kt`, `DomainModule.kt`, `KMKDomainModule.kt`, `SYDomainModule.kt` via `addSingleton`/`addSingletonFactory`. `injectLazy<T>()` for class-field delegates; `Injekt.get<T>()` for imperative resolution inside functions.

**UI & navigation** – Voyager: `Screen` in `eu.kanade.tachiyomi.ui.*`, composables in `eu.kanade.presentation.*`. State via `rememberScreenModel { … }`; models extend `StateScreenModel<State>`. Use `screenModelScope` for lifecycle-bound coroutines. `launchIO`/`withIOContext` (from `tachiyomi.core.common.util.lang`) run on **GlobalScope** — prefer `screenModelScope` when the work should be tied to the model's lifecycle.

**Activities (not Voyager)** – `MainActivity` (shell), `PlayerActivity` + `PlayerViewModel`, `WebViewActivity`, `UnlockActivity`, OAuth login activities, `DeepLinkActivity`. Player: `PlayerActivity.newIntent(context, animeId, episodeId)`. Web: `WebViewScreen` (Voyager) or `WebViewActivity.newIntent(...)`.

**Domain / data** – One class per operation under `domain/…/interactor/` (verb names, no `*Interactor` suffix). Wire repos in `eu.kanade.domain.DomainModule.kt` (+ `KMKDomainModule`, `SYDomainModule`).

**Source API types** – `SAnime`/`SEpisode` are source-layer interfaces (`source-api`); `Anime`/`Episode` are domain models. Never use source types in domain or UI layers.

**Database** – SQLDelight in `data/src/main/sqldelight/tachiyomi/`. Schema change workflow: add `.sqm` migration → update `.sq` query → update `*RepositoryImpl` mapper → run `./gradlew :data:generateSqlDelightInterface`.

**Images** – Coil 3 (`coil3.*`). No Glide/Picasso.

**App preference migrations** – `app/src/main/java/mihon/core/migration/migrations/`.

---

## Anikku-specific

- Strings: always `AMR` + `i18n-ank/…/base/`. See i18n table above.
- Anikku code/DI: search `// ANK` (e.g. `ANKDomainModule`, `HideCategory`).
- Prefs: `eu.kanade.domain.*.service.*Preferences`.
- Fork markers to preserve: `// KMK`, `// AY`, `// SY`, `// EXH` (legacy — E-Hentai; prefer `// ANK` for new Anikku-only code).

**New Anikku feature workflow:**
1. Domain interactor in `domain/…/interactor/MyFeature.kt`
2. Register: `addFactory { MyFeature(get()) }` in `DomainModule.kt`
3. `ScreenModel` + `Screen` under `app/…/ui/`
4. Strings in `i18n-ank/src/commonMain/moko-resources/base/strings.xml` using `AMR`

Reference: `domain/src/main/java/tachiyomi/domain/category/interactor/HideCategory.kt` + `DomainModule.kt:133`.

---

## Build & CI

Build types: `debug`, `release`, `releaseTest`, `foss`, `preview` (CI default), `benchmark`.

Gradle `-P` flags:

| Flag | Effect |
|------|--------|
| `include-telemetry` | Firebase Analytics + Crashlytics |
| `enable-updater` | In-app update checker |
| `disable-code-shrink` | Skip R8 minification |
| `include-dependency-info` | Dependency metadata in APK |

Use `assembleDebug` for local verification; `assemblePreview` for CI-equivalent output.

```bash
./gradlew assemblePreview                                        # main CI/dev APK
./gradlew assemblePreview -Pinclude-telemetry -Penable-updater  # full CI build
./gradlew testReleaseUnitTest                                    # CI unit tests
./gradlew installDebug                                           # device install
./gradlew :data:generateSqlDelightInterface                      # after .sq/.sqm changes
```

---

## Conventions

- **Logging** – `xLogE()` / `xLog()` from `exh.log` for Anikku code; `logcat { }` from `tachiyomi.core.common.util.system` for Mihon code.
- **Formatting** – Spotless + ktlint. Always run `spotlessApply` then `spotlessCheck`.
- **Fork edits** – New Anikku features inside `// ANK` islands; keep upstream `// SY` / `// EXH` blocks intact when merging.

---

## Key files

- `App.kt` – Injekt bootstrap, logging setup
- `MainActivity.kt` – Voyager host
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` – core DI
- `app/src/main/java/eu/kanade/domain/DomainModule.kt` – domain interactors

---

## Tests

Unit tests: `domain/src/test/`, `app/src/test/`. Framework: JUnit 5 + Kotest + Mockk. No broad UI test suite.

---

## Cursor Cloud specific instructions

### Environment

Android SDK installed into `/opt/android-sdk`. JDK 21 pre-installed (compiles to JVM target 17). Export before running Gradle in a fresh shell:

```bash
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

### Gotchas

- First Gradle build downloads ~1 GB of dependencies; subsequent builds use cache.
- `local.properties` is `.gitignore`d — recreate if missing.
- No emulator/device on Cloud VM; use `assembleDebug` for verification.
- `google-services.json` absent; builds without `-Pinclude-telemetry` succeed.
- OOM: kill daemon with `./gradlew --stop`.
