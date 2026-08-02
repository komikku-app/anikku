# Migrating Android Anime Extensions to macOS JAR Format

> **Target audience:** Extension developers porting their Android Aniyomi/Komikku/Mihon extensions to the Anikku macOS desktop app.
>
> **Format:** Markdown
> **Applies to:** Anikku macOS v1.0.0+

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture differences](#2-architecture-differences)
3. [Step-by-step migration checklist](#3-step-by-step-migration-checklist)
4. [Creating the extension.json metadata](#4-creating-the-extensionjson-metadata)
5. [Replacing Android APIs with JVM equivalents](#5-replacing-android-apis-with-jvm-equivalents)
6. [Building your extension JAR](#6-building-your-extension-jar)
7. [Testing your extension locally](#7-testing-your-extension-locally)
8. [Source API reference](#8-source-api-reference)
9. [Troubleshooting](#9-troubleshooting)
10. [Appendix: Sample extension](#10-appendix-sample-extension)

---

## 1. Overview

### What changed?

On Android, extensions are **APK files** — Android application packages containing DEX bytecode and a `.pkg` extension in the filename. On macOS, extensions are **JAR files** — standard Java archives containing JVM bytecode and a `META-INF/extension.json` metadata file.

| Aspect | Android (APK) | macOS (JAR) |
|---|---|---|
| File format | `.apk` (ZIP with DEX) | `.jar` (ZIP with JVM `.class`) |
| Filename convention | `pkgName.pkg` | `pkgName.jar` (must match `pkgName` field exactly) |
| Class loading | `PathClassLoader` (Android) | `URLClassLoader` (JVM) |
| Metadata | `AndroidManifest.xml` + `<meta-data>` tags | `META-INF/extension.json` |
| Dependencies | Bundled in APK | Bundled in JAR or placed in `extensions/libs/` |
| Security | APK signing certificates | SHA-256 hash trust store |
| Runtime | Android Runtime (ART) | JVM (OpenJDK 17+) |
| Distribution | App stores / direct APK download | JAR files from repo index |

### Quick reference: AndroidManifest.xml → extension.json

| Android `<meta-data>` attribute | `extension.json` field |
|---|---|
| `tachiyomi.animeextension.class` | `sourceClass` |
| `tachiyomi.animeextension.factory` | `pkgFactory` |
| `tachiyomi.animeextension.nsfw` (value `1`) | `isNsfw: true` |
| `tachiyomi.animeextension.torrent` (value `1`) | `isTorrent: true` |
| `tachiyomi.animeextension.hasReadme` (value `"true"`) | `hasReadme: true` |
| `tachiyomi.animeextension.hasChangelog` (value `"true"`) | `hasChangelog: true` |
| `android:versionName` / `android:versionCode` | `versionName` / `versionCode` |
| `android:label` | `name` |
| `android:usesCleartextTraffic` | _(not needed — macOS has no cleartext restriction)_ |

---

## 2. Architecture differences

### 2.1 Class loading: PathClassLoader → URLClassLoader

**Android** uses `dalvik.system.PathClassLoader` to load DEX bytecode from APK files. The `PackageManager` resolves metadata from `AndroidManifest.xml`.

**macOS** uses `java.net.URLClassLoader` with the system class loader as parent. The JAR is enumerated as a URL, and the loader reads `META-INF/extension.json` from inside the JAR.

Key implications:

- **Your extension must compile to JVM bytecode** (`.class`), not DEX bytecode (`.dex`). This means all Kotlin/Java source must be compiled with `-jvm-target 17` (or compatible).
- **No `PackageManager` API is available.** Use the `extension.json` metadata instead.
- **Extension JARs cannot contain Android framework classes.** Any `android.*` imports must be replaced with JVM equivalents (see section 5).

### 2.2 Security: APK signing → SHA-256 trust model

**Android** verifies extensions by their APK signing certificate. Extensions signed by the same key can auto-update.

**macOS** computes a SHA-256 hash of the entire JAR file. The hash is recorded in a local trust store (`trusted_extensions.json`). On subsequent loads, the hash is recomputed and compared:

- If the hash matches → extension loads.
- If the JAR has changed (update, corruption, tampering) → extension reverts to **Untrusted** status.
- The user must **explicitly trust** each new version.

There is no centralized signing authority — trust is per-user, per-hash. This means:

- Each extension update requires the user to re-trust.
- The trust store is stored at `~/Library/Application Support/Anikku/data/trust/trusted_extensions.json`.

### 2.3 Networking: No `usesCleartextTraffic` flag

On Android, extensions targeting cleartext HTTP URLs need `android:usesCleartextTraffic="true"` in `AndroidManifest.xml`. On macOS, there is no such restriction — HTTP and HTTPS both work out of the box.

### 2.4 Storage: No Context APIs

On Android, extensions access files via `Context.getFilesDir()`, `Context.getCacheDir()`, etc. On macOS, these don't exist. The app's data directory is `~/Library/Application Support/Anikku/`. Extensions should not write files directly to this directory; instead, they should use whatever storage is passed to them.

> **Rule of thumb:** If your extension stores state (cookies, tokens, cached HTML), use the `okhttp.CookieJar` and in-memory caches. The macOS app manages its own persistent storage for library entries, history, and downloads.

---

## 3. Step-by-step migration checklist

### Phase 1: Project setup

- [ ] **Clone your extension repository** to a development machine with JDK 17 installed.
- [ ] **Verify your build tooling:** You need Kotlin 1.9.x+ and Gradle 7.x+ (or Maven). The macOS app uses Kotlin 2.0.x and compiles against JDK 17.
- [ ] **Remove Android-specific build plugins:** Remove `com.android.application` or `com.android.library` plugins. Replace with `kotlin("jvm")`.
- [ ] **Set JVM target:** Add `kotlin.jvmToolchain(17)` or `kotlin { jvmToolchain(17) }`.

### Phase 2: Code adaptation

- [ ] **Replace all `android.*` imports** with JVM equivalents (see section 5).
- [ ] **Replace `AndroidManifest.xml` metadata** with `extension.json` (see section 4).
- [ ] **Audit deprecated API usage:** Ensure you're using the `suspend` API, not the deprecated `Observable`-based API. The macOS app supports both, but the suspend API is preferred.
- [ ] **Review filter/UI code:** `ConfigurableSource.setupPreferenceScreen()` uses Android `SharedPreferences`. On macOS, this works if you use `getSourcePreferences()`, but the `PreferenceScreen` expect class may have limited rendering. Prefer to define source behavior via `getFilterList()` instead.

### Phase 3: Build & package

- [ ] **Compile to JVM bytecode:** `./gradlew jar` or equivalent.
- [ ] **Include `META-INF/extension.json`** in the JAR.
- [ ] **Verify your JAR structure:**
  ```
  my-extension.jar
  ├── META-INF/
  │   └── extension.json
  ├── com/
  │   └── example/
  │       └── animeextension/
  │           ├── MySource.class
  │           └── MyOtherSource.class
  ├── some-library.jar  (embedded inside — not recommended)
  └── ...
  ```
- [ ] **Rename JAR to match `pkgName`:** e.g., `com.example.animeextension.jar`.

### Phase 4: Local testing

- [ ] **Place the JAR** in `~/Library/Application Support/Anikku/extensions/`.
- [ ] **Launch the macOS app.**
- [ ] **Navigate to Browse → Extensions** and find your extension listed (may appear as Untrusted).
- [ ] **Trust the extension** via the UI.
- [ ] **Test all source operations:** Browse popular, search, view anime details, fetch episodes, fetch video URLs.
- [ ] **Test playback:** Select an episode and verify video loading in the mpv player.

### Phase 5: Distribution

- [ ] **Host your JAR** on a web server or CDN.
- [ ] **Submit an `index.min.json` entry** to your repo's extension index (see [Extension repository format](#extension-repository-format)).
- [ ] **Ensure the JAR URL is stable** and publicly accessible.

---

## 4. Creating the extension.json metadata

The `extension.json` file is the heart of your macOS extension. It sits at `META-INF/extension.json` inside your JAR and replaces the metadata that `AndroidManifest.xml` provided on Android.

### Template

```json
{
  "name": "Aniyomi: My Extension",
  "pkgName": "com.example.animeextension",
  "versionName": "1.2.3",
  "versionCode": 123,
  "libVersion": 15.0,
  "lang": "en",
  "isNsfw": false,
  "isTorrent": false,
  "sourceClass": "com.example.animeextension.MySource;.MyOtherSource",
  "pkgFactory": null,
  "hasReadme": false,
  "hasChangelog": false
}
```

### Field reference

#### Required fields

| Field | Type | Description |
|---|---|---|
| `name` | `string` | Human-readable name. Convention: `"Aniyomi: My Extension Name"`. The app strips the `"Aniyomi: "` prefix for display. |
| `pkgName` | `string` | Unique reverse-domain package name. **Must match the JAR filename** (minus `.jar`). E.g., `pkgName: "com.example.foo"` → file must be `com.example.foo.jar`. |
| `versionName` | `string` | SemVer-like version string. The integer part before the last `.` determines `libVersion`. Example: `"15.4"` → libVersion = `15.0`. |
| `versionCode` | `integer` | Monotonically increasing version number. Higher = newer. Used for update detection. |
| `libVersion` | `float` | Minimum extensions-lib version required. Must be between `12.0` and `15.0` (inclusive). Currently supported range. |
| `sourceClass` | `string` | Semicolon-delimited list of fully qualified source class names. See [Source class resolution](#42-source-class-resolution) below. |

#### Optional fields

| Field | Type | Default | Description |
|---|---|---|---|
| `lang` | `string` | `""` | ISO 639-1 language code (`"en"`, `"ja"`, `"fr"`, etc.). Leave empty for multi-language sources. |
| `isNsfw` | `boolean` | `false` | Adult content flag. Hidden unless user enables NSFW in Preferences. |
| `isTorrent` | `boolean` | `false` | Torrent source flag. Enables torrent-specific UI features. |
| `pkgFactory` | `string` or `null` | `null` | Optional `SourceFactory` class. See [Factory pattern](#43-pkgfactory-vs-sourceclass). |
| `hasReadme` | `boolean` | `false` | Extension includes README/doc. Shown in extension details. |
| `hasChangelog` | `boolean` | `false` | Extension includes changelog. Shown in extension update UI. |

### 4.2 Source class resolution

Each entry in `sourceClass` must implement exactly **one** of these interfaces:

| Interface | Full Package | Behavior |
|---|---|---|
| `Source` | `eu.kanade.tachiyomi.source.Source` | Loaded directly. Each class = one source. |
| `CatalogueSource` | `eu.kanade.tachiyomi.source.CatalogueSource` | Loaded directly. Adds browse/search/latest capabilities. |
| `SourceFactory` | `eu.kanade.tachiyomi.source.SourceFactory` | `createSources()` is called; can return multiple sources. |
| `AnimeSource` | `eu.kanade.tachiyomi.animesource.AnimeSource` | Wrapped in a `Source` adapter automatically. |

#### Short class name resolution

If a class name starts with `.`, it is resolved relative to `pkgName`:

```json
{
  "pkgName": "com.example.extension",
  "sourceClass": ".MySource;.MyOtherSource"
}
```

Resolves to:
- `com.example.extension.MySource`
- `com.example.extension.MyOtherSource`

### 4.3 `pkgFactory` vs `sourceClass`

**Option A: Direct class listing (`sourceClass`)**

Best for extensions with a fixed set of sources. Each class is instantiated via its no-arg constructor and registered.

```json
{
  "sourceClass": "com.example.extension.SourceA;com.example.extension.SourceB",
  "pkgFactory": null
}
```

**Option B: Factory pattern (`pkgFactory`)**

Best when source creation requires runtime logic (configuration, dynamic enabling). The factory class implements `SourceFactory` and returns a list of sources.

```json
{
  "sourceClass": "",
  "pkgFactory": "com.example.extension.MyFactory"
}
```

```kotlin
class MyFactory : SourceFactory {
    override fun createSources(): List<Source> {
        return listOf(
            SourceA(lang = "en"),
            SourceB(lang = "ja"),
            SourceC(lang = "en", requiresLogin = true),
        )
    }
}
```

> **Note:** You can use both — sources from `sourceClass` are loaded first, then the factory's `createSources()` adds more.

### 4.4 Lib version compatibility

| libVersion | Android Aniyomi | macOS Anikku |
|---|---|---|
| < 12.0 | ❌ Too old | ❌ Rejected |
| 12.0 – 15.0 | ✅ Supported | ✅ Supported |
| > 15.0 | ⚠️ Future | ❌ Rejected |

The lib version is parsed from the integer part before the last `.` in `versionName`:

```kotlin
fun extractLibVersion(versionName: String): Double {
    return versionName.substringBeforeLast('.').toDouble()
}
```

Examples:
| `versionName` | `libVersion` |
|---|---|
| `"15.4"` | `15.0` |
| `"14.2"` | `14.0` |
| `"12.0"` | `12.0` |
| `"1.2.3"` | `1.0` (below minimum — rejected) |

---

## 5. Replacing Android APIs with JVM equivalents

This is the most time-consuming part of the migration. Below is a comprehensive mapping of common Android APIs to their JVM replacements.

### 5.1 Core Android → JVM

| Android API | JVM Replacement | Notes |
|---|---|---|
| `android.content.Context` | — | Remove entirely. Injekt DI may still work, but macOS does not set up the Injekt Android module. |
| `android.content.SharedPreferences` | `java.util.prefs.Preferences` or custom JSON store | The macOS app provides `MacOSPreferenceStore` internally. Extensions should use `getSourcePreferences()` from `ConfigurableSource` when possible. |
| `android.os.AsyncTask` | `kotlinx.coroutines` (suspend/launch/async) | The source API now uses `suspend` functions directly. No need for AsyncTask. |
| `android.os.Handler` / `android.os.Looper` | `kotlinx.coroutines.Dispatchers.Main` / `Dispatchers.Default` | |
| `android.util.Log` | `kotlin-logging` (SLF4J) — `io.github.oshai.kotlinlogging.KotlinLogging` | |
| `android.text.Html` | `org.jsoup.Jsoup.parse(html).text()` | jsoup is available on the shared classpath. |
| `android.text.TextUtils` | Kotlin stdlib — `isEmpty()`, `isNullOrEmpty()`, `isBlank()`, `trim()` | |
| `android.os.Build` / `Build.VERSION` | `System.getProperty("java.version")` | |
| `android.net.Uri` | `okhttp3.HttpUrl` or `java.net.URI` / `java.net.URL` | OkHttp is on the shared classpath. |
| `android.util.Base64` | `java.util.Base64` | |
| `android.graphics.Bitmap` / `BitmapFactory` | `java.awt.image.BufferedImage` / `javax.imageio.ImageIO` | Only needed if your extension does image processing. Most extensions don't touch images. |
| `android.opengl.GLES20` | — | Not used by extensions. |
| `java.io.File` (same on both) | `java.io.File` ✅ | Works identically. |

### 5.2 Networking

| Android API | JVM Replacement | Notes |
|---|---|---|
| `HttpURLConnection` with `HttpsURLConnection` | `okhttp3.OkHttpClient` (preferred) or `java.net.HttpURLConnection` | OkHttp is on the shared classpath. Download only — don't bundle your own OkHttp. |
| `okhttp3.OkHttpClient` | `okhttp3.OkHttpClient` ✅ | OkHttp 5.x is on the shared classpath. **Do not bundle** your own OkHttp — use the app's version. |
| `okhttp3.Request.Builder()` | Same ✅ | |
| `okhttp3.Response` | Same ✅ | |
| Cloudflare bypass (CookieJar) | Same — `okhttp3.CookieJar` ✅ | Available on shared classpath. |

### 5.3 HTML Parsing

| API | Available? |
|---|---|
| `org.jsoup.Jsoup` | ✅ Shared classpath (jsoup 1.21.x) |
| `org.jsoup.nodes.Document` | ✅ |
| `org.jsoup.select.Elements` | ✅ |

**No changes needed** if your extension already uses jsoup — it's on the shared classpath.

### 5.4 JSON

| API | Available? |
|---|---|
| `kotlinx.serialization.json.Json` | ✅ Shared classpath (1.9.x) |
| `org.json.JSONObject` / `org.json.JSONArray` | ✅ Bundled in app |
| `com.google.gson.Gson` | ❌ Not bundled. Include in your JAR or use kotlinx.serialization. |

### 5.5 What to bundle in your JAR (vs. what to leave out)

#### Do NOT bundle (provided by the macOS app):

```
com.squareup.okhttp3:okhttp          (5.x)
com.squareup.okhttp3:okhttp-brotli   (5.x)
com.squareup.okhttp3:okhttp-dnsoverhttps (5.x)
com.squareup.okio:okio               (3.13.x)
io.reactivex:rxjava                  (1.3.x)  — if you use the deprecated Observable API
org.jsoup:jsoup                      (1.21.x)
com.jakewharton:disklrucache         (2.x)
org.jetbrains.kotlinx:kotlinx-serialization-json (1.9.x)
org.jetbrains.kotlinx:kotlinx-coroutines-core (1.10.x)
org.jetbrains.kotlin:kotlin-stdlib   (2.0.x)
```

#### Bundle in your JAR:

- **Third-party libraries** not listed above (e.g., Gson if you must use it, custom XML parsers)
- **Your compiled extension classes** (`.class` files)

> **Alternative for large dependencies:** Place shared dependency JARs in `~/Library/Application Support/Anikku/extensions/libs/`. The loader adds all JARs from this directory to the classpath. This is useful if multiple extensions share a dependency.

---

## 6. Building your extension JAR

### 6.1 Gradle: Converting an Android project to JVM

Before (Android):

```kotlin
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = 34
    namespace = "com.example.animeextension"
    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    implementation("eu.kanade.tachiyomi:source-api:14.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
}
```

After (JVM):

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // Source API (from parent project's build output)
    implementation(files("libs/source-api-jvm.jar"))
    implementation(files("libs/common-jvm.jar"))

    // Provided by macOS app — use compileOnly to avoid bundling
    compileOnly("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    compileOnly("org.jsoup:jsoup:1.17.2")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    compileOnly("io.reactivex:rxjava:1.3.8")

    // Bundle only what's actually needed at runtime
    // implementation("com.google.code.gson:gson:2.10.1")  // if needed
}

// Package extension JAR
tasks.register<Jar>("extensionJar") {
    dependsOn("compileKotlin")
    archiveBaseName.set("com.example.animeextension")
    archiveVersion.set("")

    from(layout.buildDirectory.dir("classes/kotlin/main"))

    // Include META-INF/extension.json
    from("src/main/resources/META-INF") {
        into("META-INF")
    }

    // Bundle additional runtime dependencies
    from(configurations.runtimeClasspath.get().map { zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}
```

### 6.2 Building the JAR

```bash
# Build your extension
./gradlew extensionJar

# The JAR is at: build/libs/com.example.animeextension.jar

# Verify the contents
jar tf build/libs/com.example.animeextension.jar
# Should show:
#   META-INF/
#   META-INF/MANIFEST.MF
#   META-INF/extension.json
#   com/example/animeextension/MySource.class
#   ...

# Copy to the app's extensions directory
cp build/libs/com.example.animeextension.jar \
   ~/Library/Application\ Support/Anikku/extensions/
```

### 6.3 Alternative: Using `jar` command directly

If you're not using Gradle:

```bash
# Compile your Kotlin code to JVM bytecode
kotlinc -cp source-api-jvm.jar:common-jvm.jar:okhttp.jar:jsoup.jar \
    -d classes \
    src/com/example/animeextension/*.kt

# Create META-INF/extension.json alongside your class files
mkdir -p classes/META-INF
cat > classes/META-INF/extension.json << EOF
{
  "name": "Aniyomi: My Extension",
  "pkgName": "com.example.animeextension",
  "versionName": "15.1",
  "versionCode": 151,
  "libVersion": 15.0,
  "lang": "en",
  "isNsfw": false,
  "sourceClass": "com.example.animeextension.MySource"
}
EOF

# Package the JAR
cd classes
jar cf com.example.animeextension.jar META-INF/extension.json com/
```

### 6.4 Building from the Aniyomi source tree

If you're developing within the Aniyomi ecosystem:

```bash
# From the root project, rebuild source-api and common as JVM JARs
./gradlew :source-api:jvmJar :core:common:jvmJar

# Copy to macos/libs/ with stable names
cp source-api/build/libs/source-api-jvm-*.jar macos/libs/source-api-jvm.jar
cp core/common/build/libs/common-jvm-*.jar macos/libs/common-jvm.jar
```

---

## 7. Testing your extension locally

### 7.1 Directory structure

```
~/Library/Application Support/Anikku/
├── extensions/
│   ├── com.example.animeextension.jar   ← Your extension JAR
│   └── libs/                            ← Shared dependency JARs (optional)
│       ├── gson-2.10.1.jar
│       └── my-custom-lib-1.0.jar
├── data/
│   └── trust/
│       └── trusted_extensions.json       ← Trust store (auto-managed)
├── downloads/
├── backups/
├── logs/
└── covers/
```

### 7.2 First load flow

1. Copy your JAR to the `extensions/` directory.
2. Launch the macOS app.
3. Navigate to **Browse → Extensions**.
4. Your extension appears with an **Untrusted** badge.
5. Click **Trust** on your extension.
6. The extension's sources now appear in the Browse tab.

### 7.3 Reloading after changes

After modifying and rebuilding your extension:

```bash
# Kill the app
killall Anikku

# Replace the JAR
cp build/libs/com.example.animeextension.jar \
   ~/Library/Application\ Support/Anikku/extensions/

# Re-launch (from Gradle)
cd macos && ./gradlew run
```

> **Important:** Since the JAR hash changes, the extension will revert to **Untrusted** after every update. You must trust it again. This is by design — it prevents a compromised update from loading automatically.

### 7.4 Test source using the app

The macOS app includes a `TestAnimeSource` (class: `app.anikku.macos.testextension.TestAnimeSource`) that returns hardcoded sample data with a real playable video URL (Big Buck Bunny). Use this as a reference for API compliance.

To build the test extension:

```bash
cd macos
./gradlew buildTestExtensionJar
cp build/libs/test-extension-1.0.0.jar \
   ~/Library/Application\ Support/Anikku/extensions/
```

The test extension's metadata is at `macos/src/main/resources/test-extension/extension.json` — a minimal working example.

---

## 8. Source API reference

### 8.1 Interface hierarchy

```
AnimeSource (eu.kanade.tachiyomi.animesource.AnimeSource)
├── AnimeCatalogueSource (adds browse/search/latest)
│   ├── AnimeHttpSource (adds HTTP client management)
│   └── AnimeParsedHttpSource (adds HTML parsing helpers)
├── ConfigurableAnimeSource (adds per-source preferences)
└── UnmeteredSource (marker for self-hosted sources)

Typealiases (for convenience):
  Source           = AnimeSource
  CatalogueSource  = AnimeCatalogueSource
  SourceFactory    = AnimeSourceFactory
  HttpSource       = AnimeHttpSource
```

### 8.2 Required methods

| Method | Return type | Description |
|---|---|---|
| `getAnimeDetails(SAnime)` | `SAnime` | Fetch full anime details. |
| `getEpisodeList(SAnime)` | `List<SEpisode>` | Fetch episode list. |
| `getVideoList(SEpisode)` | `List<Video>` | Fetch video URLs (direct stream links). |

### 8.3 CatalogueSource additional methods

| Method | Return type | Description |
|---|---|---|
| `getPopularAnime(page: Int)` | `AnimesPage` | Browse popular anime. |
| `getSearchAnime(page, query, filters)` | `AnimesPage` | Search anime. |
| `getLatestUpdates(page: Int)` | `AnimesPage` | Latest updates feed. |
| `getFilterList()` | `AnimeFilterList` | Available search filters. |

### 8.4 Data models

| Model | Key fields |
|---|---|
| `SAnime` | `url`, `title`, `author`, `artist`, `description`, `genre`, `status`, `thumbnail_url` |
| `SEpisode` | `url`, `name`, `episode_number`, `date_upload`, `scanlator` |
| `Video` | `videoUrl`, `videoTitle`, `resolution`, `headers`, `preferred` |
| `AnimesPage` | `animes: List<SAnime>`, `hasNextPage: Boolean` |

### 8.5 Status constants

```kotlin
SAnime.UNKNOWN       = 0
SAnime.ONGOING       = 1
SAnime.COMPLETED     = 2
SAnime.LICENSED      = 3
SAnime.AIRING        = 4  // AniYomi extension
SAnime.NOT_YET       = 5  // AniYomi extension
SAnime.CANCELLED     = 6  // AniYomi extension
SAnime.HIATUS        = 7  // AniYomi extension
```

### 8.6 Multipart/form-data uploads

If your extension needs to upload data (e.g., for token exchange or API calls), use OkHttp's `MultipartBody`:

```kotlin
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

val body = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart("client_id", clientId)
    .addFormDataPart("code", authCode)
    .addFormDataPart("grant_type", "authorization_code")
    .build()
```

### 8.7 WebView / JavaScript requirements

If your Android extension uses a `WebView` to bypass JavaScript challenges (e.g., Cloudflare):

❌ **Not supported on macOS.** The `android.webkit.WebView` API does not exist.

**Alternatives:**
- Use OkHttp's cookie jar to persist Cloudflare clearance cookies.
- Pre-compute the challenge in a browser outside the app and hardcode the cookie.
- If the website requires JS execution, you may need to use a headless browser like Jsoup with cookie forwarding (limited) or Puppeteer/Playwright as an external service.

---

## 9. Troubleshooting

### 9.1 Extension appears as "Untrusted"

This is expected on first load and after every JAR update. Trust it via the UI.

If it stays Untrusted even after clicking **Trust**:
1. Check the logs at `~/Library/Application Support/Anikku/logs/anikku.log`.
2. Verify the JAR filename matches `pkgName`. E.g., if `pkgName = "com.example.foo"`, the file must be `com.example.foo.jar`.
3. Verify the JAR contains `META-INF/extension.json` — `jar tf myextension.jar | grep extension.json`

### 9.2 Extension loads but no sources appear

1. Check the `sourceClass` field — ensure class names are correct and classes exist in the JAR.
2. Verify each source class has a **public no-arg constructor**.
3. If using `SourceFactory`, verify the factory's `createSources()` returns a non-empty list.
4. Check for `ClassNotFoundException` in the logs.

### 9.3 "No source classes defined" error

Your `sourceClass` field is empty or contains only whitespace/semicolons. Set at least one source class:

```json
"sourceClass": "com.example.animeextension.MySource"
```

### 9.4 "Lib version outside supported range" error

The `libVersion` (parsed from `versionName`) must be between 12.0 and 15.0:

```json
// ✅ Valid
"versionName": "15.4",  // libVersion = 15.0
"versionName": "14.0",  // libVersion = 14.0
"versionName": "12.1",  // libVersion = 12.0

// ❌ Invalid
"versionName": "11.0",  // libVersion = 11.0 (below minimum)
"versionName": "16.0",  // libVersion = 16.0 (above maximum)
```

### 9.5 Network errors

- **SSL/TSL issues:** The app trusts the standard system CA certificates. If you need a custom CA, you may need to configure an `OkHttpClient` with a custom `SSLSocketFactory`.
- **Connection refused / timeout:** The app has no built-in proxy support. Extensions must manage their own proxy configuration if needed.
- **Cloudflare / WAF blocks:** OkHttp is generally accepted, but aggressive WAFs may block non-browser user agents. Try setting a more realistic `User-Agent` header:

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .build()
        chain.proceed(request)
    }
    .build()
```

### 9.6 ClassNotFoundException / NoClassDefFoundError

1. Check if the missing class is one the macOS app already provides (see [shared dependencies](#do-not-bundle-provided-by-the-macos-app)).
2. If your extension bundles a library that conflicts with a shared one, remove your bundled version.
3. If you need a library not on the shared classpath, either bundle it in your JAR or place it in `extensions/libs/`.

### 9.7 Extension works on Android but not macOS (no error)

Common silent differences:
- **`android.os.Build` references** — code paths that check `Build.VERSION` may crash silently. Replace with JVM alternatives.
- **`Resource` / `R` references** — no Android resource system exists. Hardcode strings.
- **`Parcelable` / `Serializable`** — not used in the source API. Only use data classes.
- **`Context.getDrawable()`** — not available. The app handles image loading (cover art, thumbnails) via Coil/OkHttp.

---

## 10. Appendix: Sample extension

### 10.1 Minimal working extension

**`META-INF/extension.json`:**

```json
{
  "name": "Aniyomi: MyExtension",
  "pkgName": "com.example.minext",
  "versionName": "15.0",
  "versionCode": 150,
  "libVersion": 15.0,
  "lang": "en",
  "isNsfw": false,
  "sourceClass": ".MyMiniSource"
}
```

**`src/com/example/minext/MyMiniSource.kt`:**

```kotlin
package com.example.minext

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.animesource.model.*

class MyMiniSource : CatalogueSource {

    override val id: Long = 999000L
    override val name: String = "MiniSource"
    override val lang: String = "en"
    override val supportsLatest: Boolean = false

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return SAnime.create().apply {
            url = anime.url
            title = anime.title
            description = "A sample anime description."
            status = SAnime.ONGOING
            thumbnail_url = ""
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return listOf(SEpisode.create().apply {
            url = "/episode/1"
            name = "Episode 1"
            episode_number = 1f
        })
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return listOf(Video(
            videoUrl = "https://example.com/stream.mp4",
            videoTitle = "720p",
            resolution = 720,
            headers = null,
            preferred = true,
        ))
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        return AnimesPage(emptyList(), false)
    }

    override suspend fun getSearchAnime(
        page: Int, query: String, filters: AnimeFilterList
    ): AnimesPage {
        return AnimesPage(emptyList(), false)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        return AnimesPage(emptyList(), false)
    }
}
```

**Build and deploy:**

```bash
# Compile
kotlinc -cp <paths-to-shared-jars> \
    -d classes \
    src/com/example/minext/MyMiniSource.kt

# Add metadata
mkdir -p classes/META-INF
cp src/META-INF/extension.json classes/META-INF/

# Package
cd classes
jar cf com.example.minext.jar META-INF/extension.json com/
cp com.example.minext.jar ~/Library/Application\ Support/Anikku/extensions/
```

### 10.2 Extension repository format

To make your extension installable from within the app, you need an `index.min.json` file hosted on a web server. The app fetches this to discover available extensions.

```json
[
  {
    "name": "Aniyomi: My Extension",
    "pkg": "com.example.animeextension",
    "apk": "com.example.animeextension.jar",
    "lang": "en",
    "code": 151,
    "version": "15.1",
    "nsfw": 0,
    "sources": [
      {"id": 123456, "lang": "en", "name": "MySource", "baseUrl": "https://example.com"}
    ]
  }
]
```

| Field | Description |
|---|---|
| `name` | Display name |
| `pkg` | Package name (must match JAR filename minus `.jar`) |
| `apk` | JAR filename to download |
| `lang` | Language code |
| `code` | Version code (integer, for update comparison) |
| `version` | Version string (parsed for lib version) |
| `nsfw` | 0 or 1 |
| `sources` | Optional list of sources with their IDs |

The JAR is downloaded from `{repoBaseUrl}/apk/{apk}`.

---

## Quick migration card

```text
┌─────────────────────────────────────────────────────────────────┐
│                  ANDROID APK → macOS JAR                        │
│                                                                 │
│  1. Remove Android plugins → Add kotlin("jvm")                  │
│  2. Set JVM target 17                                           │
│  3. Replace android.* imports → JVM equivalents                 │
│  4. Create META-INF/extension.json                              │
│  5. Build with: ./gradlew jar                                   │
│  6. Rename JAR: pkgName.jar                                     │
│  7. Copy to ~/Library/Application Support/Anikku/extensions/    │
│  8. Launch app → Trust extension → Done!                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## See also

- [`EXTENSION-METADATA.md`](./EXTENSION-METADATA.md) — Full extension metadata field reference
- [`macos/src/main/kotlin/app/anikku/macos/platform/extension/`](../src/main/kotlin/app/anikku/macos/platform/extension/) — macOS extension loader source code
- [`macos/src/main/resources/test-extension/extension.json`](../src/main/resources/test-extension/extension.json) — Minimal working example metadata
- [`macos/src/main/kotlin/app/anikku/macos/testextension/TestAnimeSource.kt`](../src/main/kotlin/app/anikku/macos/testextension/TestAnimeSource.kt) — Reference source implementation
- [Aniyomi Extension Guide (Android)](https://aniyomi.org/docs/guides/contribute) — Original Android extension development docs
- [`docs/samples/META-INF/extension.json`](./samples/META-INF/extension.json) — Ready-to-use metadata template with comments
