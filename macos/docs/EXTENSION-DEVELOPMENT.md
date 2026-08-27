# Building Extensions for Anikku macOS

> **Target audience:** Extension developers who want to compile an anime source extension as a **JAR file** that works with the Anikku macOS desktop app.

This guide covers the full workflow: setting up a Gradle project, depending on the macOS stub interfaces, implementing a source, writing the metadata, building the JAR, and installing it into the app.

---

## Table of Contents

1. [Architecture overview](#architecture-overview)
2. [Available stub API surface](#available-stub-api-surface)
3. [Project setup (Gradle)](#project-setup-gradle)
4. [Implementing a source](#implementing-a-source)
5. [Extension metadata (`extension.json`)](#extension-metadata-extensionjson)
6. [Building the JAR](#building-the-jar)
7. [Installing and trusting the extension](#installing-and-trusting-the-extension)
8. [Reference implementation](#reference-implementation)
9. [Android vs macOS differences](#android-vs-macos-differences)
10. [Troubleshooting](#troubleshooting)

---

## Architecture overview

```
┌─────────────────────────────────────────────────────────────────┐
│  Anikku macOS App                                                │
│                                                                  │
│  ┌──────────────────┐   ┌──────────────────────────────────┐   │
│  │ MacOSExtension   │   │  URLClassLoader (parent =        │   │
│  │ Loader           │──▶│  ClassLoader.getSystemClassLoader │   │
│  │                  │   │  )                                │   │
│  │ 1. Read META-INF │   │                                  │   │
│  │    /extension    │   │  ┌────────────────────────────┐  │   │
│  │    .json         │   │  │ Extension JAR              │  │   │
│  │ 2. Compute SHA   │   │  │ ├ META-INF/extension.json  │  │   │
│  │    256 hash      │   │  │ └ com/example/*.class      │  │   │
│  │ 3. Verify trust  │   │  └────────────────────────────┘  │   │
│  │ 4. Load class    │   └──────────────────────────────────┘   │
│  │ 5. Instantiate   │                                          │
│  │    + wrap source │   ┌──────────────────────────────────┐   │
│  └──────────────────┘   │  App classpath (parent CL)       │   │
│                         │  ├ Stub interfaces (Source,      │   │
│                         │  │  CatalogueSource, SAnime…)    │   │
│                         │  ├ Shared deps (OkHttp, RxJava,  │   │
│                         │  │  jsoup, kotlinx-serialization…)│   │
│                         │  └ Kotlin/JVM stdlib             │   │
│                         └──────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

The extension JAR is loaded via a `URLClassLoader` whose **parent** is the system class loader (the app's classpath). This means:

- **Parent classpath** (available to your extension automatically): all the stub interfaces (`Source`, `CatalogueSource`, `SAnime`, `SEpisode`, `Video`, etc.), plus shared dependencies (OkHttp, RxJava, jsoup, kotlinx-serialization, kotlinx-coroutines, Okio).
- **Your JAR** must contain only your source class(es) and any **non-shared** third-party dependencies.

---

## Available stub API surface

The macOS app provides a set of **stub interfaces** that mirror the real `source-api` from the Android app. These stubs live in the package `eu.kanade.tachiyomi` inside the macOS app. You compile your extension **against these stubs** (see [Project setup](#project-setup-gradle)).

### Core interfaces

| Interface | Package | Purpose |
|---|---|---|
| `Source` | `eu.kanade.tachiyomi.source` | Base source marker. Extends `AnimeSource`. |
| `CatalogueSource` | `eu.kanade.tachiyomi.source` | Source with browsing + search. Extends `Source`. **This is what you implement.** |
| `ConfigurableSource` | `eu.kanade.tachiyomi.source` | Source with user preferences. |
| `SourceFactory` | `eu.kanade.tachiyomi.source` | Factory that creates multiple sources at runtime. |
| `AnimeSource` | `eu.kanade.tachiyomi.animesource` | Low-level source interface (browsing omitted). |
| `AnimeSourceFactory` | `eu.kanade.tachiyomi.animesource` | Low-level factory interface. |

### Model classes

All model classes live in `eu.kanade.tachiyomi.animesource.model`:

| Class/Interface | Description | Key fields |
|---|---|---|
| `SAnime` (interface) | An anime entry. | `url`, `title`, `author`, `artist`, `description`, `genre`, `status`, `thumbnail_url`, `update_strategy`, `initialized` |
| `SAnimeImpl` | Default implementation of `SAnime`. Created via `SAnime.create()`. | — |
| `SEpisode` (interface) | An episode entry. | `url`, `name`, `date_upload`, `episode_number`, `scanlator` |
| `SEpisodeImpl` | Default implementation of `SEpisode`. Created via `SEpisode.create()`. | — |
| `Video` (open class) | A video stream. | `videoUrl`, `videoTitle`, `resolution`, `bitrate`, `headers`, `subtitleTracks`, `audioTracks` |
| `AnimePage` (data class) | Paginated result from a source. | `animeList: List<SAnime>`, `hasNextPage: Boolean` |
| `Hoster` (open class) | A video hoster (wrapper around video list). | `hosterUrl`, `hosterName`, `videoList` |
| `AnimeUpdateStrategy` (enum) | How library updates work. | `ALWAYS_UPDATE`, `ONLY_IF_NOT_IN_LIBRARY`, `NEVER_UPDATE` |

### Status constants on `SAnime`

```
SAnime.UNKNOWN           = 0
SAnime.ONGOING           = 1
SAnime.COMPLETED         = 2
SAnime.LICENSED          = 3
SAnime.PUBLISHING_FINISHED = 4
SAnime.CANCELLED         = 5
SAnime.ON_HIATUS         = 6
```

### Shared dependencies available on the app classpath

Your extension JAR must **not** bundle these. They are available from the parent class loader:

| Library | Maven coordinates | Typical version |
|---|---|---|
| OkHttp | `com.squareup.okhttp3:okhttp` | 5.x |
| OkHttp Brotli | `com.squareup.okhttp3:okhttp-brotli` | 5.x |
| OkHttp DoH | `com.squareup.okhttp3:okhttp-dnsoverhttps` | 5.x |
| RxJava | `io.reactivex:rxjava` | 1.3.x |
| jsoup | `org.jsoup:jsoup` | 1.21.x |
| Okio | `com.squareup.okio:okio` | 3.13.x |
| DiskLruCache | `com.jakewharton:disklrucache` | 2.x |
| kotlinx-serialization-json | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.9.x |
| kotlinx-coroutines-core | `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.x |

---

## Project setup (Gradle)

Create a new Kotlin/JVM Gradle project. Your `build.gradle.kts` should look like this:

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
}

repositories {
    mavenCentral()
}

dependencies {
    // You need the stub interfaces to compile against.
    // Simplest approach: declare the same shared-library coordinates
    // so your IDE resolves symbols correctly. Use compileOnly so
    // these classes aren't bundled into your JAR (they come from
    // the app's classpath at runtime).

    // Source stubs — equivalent to the app's eu.kanade.tachiyomi.* package
    // (you can include these as source files or publish them as an artifact)
    compileOnly("com.squareup.okhttp3:okhttp:5.1.0")
    compileOnly("io.reactivex:rxjava:1.3.8")
    compileOnly("org.jsoup:jsoup:1.21.2")
    compileOnly("com.squareup.okio:okio:3.13.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

// Configure the JAR task to include only your extension's classes
tasks.jar {
    archiveBaseName.set("my-extension")
    archiveVersion.set("1.0.0")

    // Ensure META-INF/extension.json is included
    from("src/main/resources") {
        include("META-INF/extension.json")
    }

    manifest {
        attributes(
            "Implementation-Title" to "MyExtension",
            "Implementation-Version" to "1.0.0",
        )
    }
}
```

### Obtaining the stub classes

You have three options to get the stub interfaces into your compilation classpath:

#### Option A: Publish stubs as a Maven artifact (recommended for published extensions)

Publish the `eu.kanade.tachiyomi` package from the macOS project as a library artifact, then depend on it:

```kotlin
dependencies {
    implementation("app.anikku:source-stubs:1.0.0")
}
```

#### Option B: Copy the stub source files directly

Copy the entire `eu/kanade/tachiyomi/` directory tree from `macos/src/main/kotlin/` into your project's `src/main/kotlin/`. This is the simplest approach for local/private extensions.

#### Option C: Reference the macOS project (for multi-module builds)

If your extension lives in the same repository as the macOS app, add a module dependency:

```kotlin
dependencies {
    implementation(project(":macos"))
}
```

### Compiling with the correct Kotlin/JVM target

Anikku macOS uses **JVM toolchain 17** and **Kotlin 2.0.21** (Compose Multiplatform 1.7+). Your extension should target at minimum **JVM 11** (the app supports macOS 12+ which includes JDK 17):

```kotlin
kotlin {
    jvmToolchain(17)
}
```

---

## Implementing a source

Your main source class must implement `CatalogueSource` (or extend a base class that does). Here is the minimum required implementation:

```kotlin
package com.example.animeextension

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.model.AnimePage
import rx.Observable

class MySource : CatalogueSource {

    override val id: Long = 12345L   // Unique ID for this source
    override val name: String = "MySource"
    override val lang: String = "en"

    // ── Required: suspend API ──────────────────────────────────

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        // Fetch and populate full anime details.
        // Return the modified anime object (or create a new SAnime via SAnime.create()).
        // Example:
        return SAnime.create().apply {
            url = anime.url
            title = "Full Title"
            author = "Author Name"
            description = "Description text"
            genre = "Action, Comedy"
            status = SAnime.ONGOING
            thumbnail_url = "https://example.com/thumb.jpg"
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        // Return a list of episodes for the given anime.
        // Example:
        return listOf(
            SEpisode.create().apply {
                url = "/anime/1/episode/1"
                name = "Episode 1"
                episode_number = 1f
                date_upload = System.currentTimeMillis()
            }
        )
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        // Return video URLs for the given episode.
        // Example:
        return listOf(
            Video(
                videoUrl = "https://example.com/video.mp4",
                videoTitle = "1080p",
                resolution = 1080,
                headers = null,
                preferred = true,
            )
        )
    }

    override suspend fun getPopularAnime(page: Int): AnimePage {
        // Return a page of popular anime listings.
        // Example with hardcoded data:
        val anime = SAnime.create().apply {
            url = "/anime/1"
            title = "Popular Anime"
            thumbnail_url = "https://example.com/thumb.jpg"
            status = SAnime.ONGOING
            initialized = true
        }
        return AnimePage(
            animeList = listOf(anime),
            hasNextPage = false,
        )
    }

    override suspend fun getSearchAnime(page: Int, query: String): AnimePage {
        // Return search results for the query.
        // Same return format as getPopularAnime.
        return AnimePage(
            animeList = emptyList(),
            hasNextPage = false,
        )
    }

    // ── Required: RxJava stubs (deprecated but must exist) ───

    @Deprecated("Use suspend API", ReplaceWith("getPopularAnime"))
    override fun fetchPopularAnime(page: Int): Observable<AnimePage> =
        throw UnsupportedOperationException("Use suspend API")

    @Deprecated("Use suspend API", ReplaceWith("getSearchAnime"))
    override fun fetchSearchAnime(page: Int, query: String): Observable<AnimePage> =
        throw UnsupportedOperationException("Use suspend API")
}
```

### Using HTTP networking

The app provides OkHttp on the classpath. You can create an OkHttp client directly in your source:

```kotlin
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class MySource : CatalogueSource {
    private val client = OkHttpClient()

    private fun fetchDocument(url: String): org.jsoup.nodes.Document {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: ""
        return Jsoup.parse(html, url)
    }

    override suspend fun getPopularAnime(page: Int): AnimePage {
        val doc = fetchDocument("https://example.com/popular?page=$page")
        val animeList = doc.select("div.anime-item").map { element ->
            SAnime.create().apply {
                url = element.select("a").attr("href")
                title = element.select("h2").text()
                thumbnail_url = element.select("img").attr("src")
                initialized = true
            }
        }
        return AnimePage(animeList = animeList, hasNextPage = animeList.isNotEmpty())
    }
    // ...
}
```

---

## Extension metadata (`extension.json`)

Place this file at `META-INF/extension.json` inside your JAR:

```json
{
  "name": "Aniyomi: MySource",
  "pkgName": "com.example.animeextension",
  "versionName": "1.0.0",
  "versionCode": 100,
  "libVersion": 14.0,
  "lang": "en",
  "isNsfw": false,
  "isTorrent": false,
  "sourceClass": "com.example.animeextension.MySource",
  "pkgFactory": null,
  "hasReadme": false,
  "hasChangelog": false
}
```

### Field reference

| Field | Required | Type | Description |
|---|---|---|---|
| `name` | ✅ | `string` | Human-readable name. Convention: `"Aniyomi: <name>"` (the app strips the prefix). |
| `pkgName` | ✅ | `string` | Unique Java package name. **Must match the JAR filename** (`<pkgName>.jar`). |
| `versionName` | ✅ | `string` | Version string. The part before the last `.` is the lib version (e.g. `"14.1"` → libVersion `14.0`). |
| `versionCode` | ✅ | `integer` | Monotonically increasing integer. Higher = newer. |
| `libVersion` | ✅ | `float` | Minimum extensions-lib version. Must be between `12.0` and `15.0`. |
| `sourceClass` | ✅ | `string` | Semicolon-delimited list of fully qualified source class names. Classes starting with `.` are resolved relative to `pkgName`. |
| `pkgFactory` | ❌ | `string?` | Fully qualified `SourceFactory` class name. If set, `createSources()` is called to add more sources. |
| `lang` | ❌ | `string` | ISO 639-1 language code. Defaults to `""` (multi-language). |
| `isNsfw` | ❌ | `boolean` | Adult content flag. Defaults to `false`. |
| `isTorrent` | ❌ | `boolean` | Torrent source flag. Defaults to `false`. |
| `hasReadme` | ❌ | `boolean` | Extension includes documentation. Defaults to `false`. |
| `hasChangelog` | ❌ | `boolean` | Extension includes a changelog. Defaults to `false`. |

### Source class resolution

Class names in `sourceClass` can be:

- **Fully qualified:** `com.example.animeextension.MySource`
- **Relative (starts with `.`):** `.SecondarySource` → resolved to `com.example.animeextension.SecondarySource`

### Multiple sources per JAR

You can define multiple sources in a single JAR by separating class names with semicolons:

```json
"sourceClass": "com.example.animeextension.SourceA;com.example.animeextension.SourceB"
```

Or by using a `SourceFactory`:

```json
{
  "sourceClass": "",
  "pkgFactory": "com.example.animeextension.MySourceFactory"
}
```

---

## Building the JAR

### Using Gradle (recommended)

Add this `Jar` task to your `build.gradle.kts`:

```kotlin
tasks.register<Jar>("buildExtensionJar") {
    dependsOn("compileKotlin")
    archiveBaseName.set("my-extension")
    archiveVersion.set("1.0.0")

    // Include compiled extension classes
    from(layout.buildDirectory.dir("classes/kotlin/main")) {
        include("com/example/animeextension/**")
    }

    // Include extension metadata
    from("src/main/resources") {
        include("META-INF/extension.json")
        into("META-INF")
    }

    manifest {
        attributes(
            "Implementation-Title" to "MyExtension",
            "Implementation-Version" to "1.0.0",
        )
    }
}
```

Then build:

```bash
./gradlew buildExtensionJar
```

The JAR is created at `build/libs/my-extension-1.0.0.jar`.

### Verifying the JAR contents

```bash
# List contents
jar tf build/libs/my-extension-1.0.0.jar

# Expected output:
# META-INF/
# META-INF/MANIFEST.MF
# META-INF/extension.json
# com/example/animeextension/MySource.class
```

---

## Installing and trusting the extension

### 1. Copy the JAR to the extensions directory

```bash
cp my-extension-1.0.0.jar ~/Library/Application\ Support/Anikku/extensions/
```

The filename **must** match `pkgName` from your `extension.json`. For `"pkgName": "com.example.animeextension"`, the file must be `com.example.animeextension.jar`.

### 2. Launch (or restart) the app

The app scans the extensions directory on startup.

### 3. Trust the extension

On first load, the extension appears as **Untrusted** in the app. Go to the **Browse** tab → extension list → tap the untrusted extension → confirm **Trust**. The app stores the SHA-256 hash of your JAR in `trusted_extensions.json`.

> **Security note:** Every time the JAR changes (update, rebuild), the hash changes and the extension becomes untrusted again. Users must re-trust each version.

### Trust programmatically (for testing)

```bash
# Compute SHA-256
SHA=$(shasum -a 256 ~/Library/Application\ Support/Anikku/extensions/com.example.animeextension.jar | cut -d' ' -f1)

# Add to trust store
cat > ~/Library/Application\ Support/Anikku/data/trust/new_entry.json << EOF
[
  {"pkgName": "com.example.animeextension", "versionCode": 100, "signatureHash": "$SHA"}
]
EOF
```

See [`EXTENSION-METADATA.md`](./EXTENSION-METADATA.md) for the full trust store format.

---

## Reference implementation

The macOS project includes a complete, working test extension at:

| File | Purpose |
|---|---|
| `macos/src/main/kotlin/app/anikku/macos/testextension/TestAnimeSource.kt` | Production-quality example implementing `CatalogueSource` |
| `macos/src/main/resources/test-extension/extension.json` | Metadata for the test extension |
| `macos/build.gradle.kts` (the `buildTestExtensionJar` task) | Gradle task that builds the JAR |

### TestAnimeSource highlights

```kotlin
class TestAnimeSource : CatalogueSource {

    override val id: Long = 999001L
    override val name: String = "TestSource"
    override val lang: String = "en"

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return SAnime.create().apply {
            url = anime.url
            title = "Big Buck Bunny"
            author = "Blender Foundation"
            artist = "Blender Foundation"
            description = "A large and lovable rabbit deals with three tiny bullies..."
            genre = "Animation, Short, Comedy"
            status = SAnime.COMPLETED
            thumbnail_url = testThumbnail
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return listOf(
            SEpisode.create().apply {
                url = "/test/episode/1"
                name = "Big Buck Bunny"
                episode_number = 1f
                date_upload = 1700000000000L
            }
        )
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return listOf(
            Video(
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                videoTitle = "1080p",
                resolution = 1080,
                preferred = true,
            )
        )
    }
    // ...
}
```

To build it:

```bash
cd /path/to/anikku
./macos/gradlew -p macos buildTestExtensionJar
cp macos/build/libs/test-extension-1.0.0.jar ~/Library/Application\ Support/Anikku/extensions/
```

---

## Android vs macOS differences

### What to change when porting an Android extension

| Android (APK) | macOS (JAR) |
|---|---|
| Extends `AnimeHttpSource` (full source-api base class) | Implements `CatalogueSource` (stub interface) directly |
| `AndroidManifest.xml` with meta-data | `META-INF/extension.json` |
| `PathClassLoader` (Android) | `URLClassLoader` (JVM) |
| `android.*` imports (Bitmap, Uri, Context) | Must be removed or replaced with JVM equivalents |
| APK signing (certificate-based) | SHA-256 hash of JAR file |
| Installed via PackageManager + APK install | Copied as a JAR file to extensions directory |
| `BaseUrl` / `getBaseUrl()` pattern | You manage URLs directly |

### Common compilation issues when porting

**1. `AnimeHttpSource` base class not found**

The full source-api's `AnimeHttpSource` is not available on macOS. Instead of extending it, implement `CatalogueSource` directly and manage HTTP yourself with OkHttp + jsoup:

```kotlin
// Android (NOT available on macOS):
class MySource : AnimeHttpSource() { ... }

// macOS:
class MySource : CatalogueSource {
    private val client = OkHttpClient()
    // ...
}
```

**2. `android.os.Bundle` or `android.content.Context`**

These Android SDK classes do not exist on the JVM. Replace with:

| Android | macOS |
|---|---|
| `Uri.parse(...)` | `java.net.URI(...)` |
| `Base64.encodeToString(...)` | `java.util.Base64.getEncoder().encodeToString(...)` |
| `android.graphics.Bitmap` | Remove or replace with byte array |
| `Context.getString(...)` | Hardcode strings or use resource bundles |
| `System.currentTimeMillis()` | Same (available on JVM) |

**3. `kotlinx.serialization`**

Use `kotlinx.serialization` from the app classpath. **Do not bundle** `kotlinx-serialization-json` in your JAR.

**4. Coroutines**

Use `kotlinx.coroutines` from the app classpath (version 1.10.x). **Do not bundle** coroutines in your JAR.

**5. API changes in the intermediate stub layer**

The macOS stubs are designed to match the `commonMain` source set of the real source-api as closely as possible. However, some methods from the full Android `AnimeHttpSource` are not present in the stubs:

| Method | In stub? | Workaround |
|---|---|---|
| `getAnimeDetails(SAnime)` | ✅ | Implement directly |
| `getEpisodeList(SAnime)` | ✅ | Implement directly |
| `getVideoList(SEpisode)` | ✅ | Implement directly |
| `getPopularAnime(Int)` | ✅ | Implement directly |
| `getSearchAnime(Int, String)` | ✅ | Implement directly |
| `fetchAnimeDetails(SAnime)` | ✅ | Deprecated but required stub |
| `fetchEpisodeList(SAnime)` | ✅ | Deprecated but required stub |
| `fetchVideoList(SEpisode)` | ✅ | Deprecated but required stub |
| `getHosterList(SEpisode)` | ❌ | Not supported on macOS |
| `getVideoList(Hoster)` | ❌ | Not supported on macOS |

---

## Troubleshooting

### "No META-INF/extension.json in <jar>"

The JAR is missing the metadata file. Verify with `jar tf your-extension.jar | grep extension.json`.

### "Lib version X outside supported range [12, 15]"

Your `libVersion` (parsed from `versionName`) is too old or too new. Use a version string like `"14.0"` or `"15.1"`.

### "ClassNotFoundException" when loading source class

- The class name in `extension.json` (`sourceClass`) doesn't match the fully qualified class name in the JAR.
- The JAR wasn't rebuilt after code changes.
- Use `jar tf your-extension.jar` to verify the class path inside the JAR.

### "Cannot infer type for type parameter T" on `SAnime.create()`

This happens when the stub interfaces are not on the compilation classpath. Make sure your project has the stubs available either as source files, a published artifact, or a module dependency.

### Extension appears as "Untrusted" after rebuild

Every JAR build produces a different SHA-256 hash. You must re-trust the extension each time you rebuild. This is by design — it prevents tampering.

### Links

- [`EXTENSION-METADATA.md`](./EXTENSION-METADATA.md) — Full extension metadata field reference and migration guide
- [`macos/src/main/kotlin/app/anikku/macos/testextension/TestAnimeSource.kt`](../src/main/kotlin/app/anikku/macos/testextension/TestAnimeSource.kt) — Working example implementation
- [`macos/src/main/kotlin/eu/kanade/tachiyomi/`](../src/main/kotlin/eu/kanade/tachiyomi/) — Complete stub interfaces and models
- [`macos/src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionLoader.kt`](../src/main/kotlin/app/anikku/macos/platform/extension/MacOSExtensionLoader.kt) — Extension loader source code
