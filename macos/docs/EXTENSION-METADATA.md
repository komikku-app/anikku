# Anikku macOS Extension Metadata

> **Target audience:** Extension developers porting their Android Aniyomi extensions to the macOS desktop app.

## Overview

The macOS Anikku app loads extensions as **JAR files** placed in `~/Library/Application Support/Anikku/extensions/`. Each JAR must contain a `META-INF/extension.json` file that describes the extension, its sources, and version metadata.

This replaces the Android model, where extensions are APK files with metadata in `AndroidManifest.xml`.

---

## Quick Start

### 1. Create the metadata file

Copy [`docs/samples/META-INF/extension.json`](./samples/META-INF/extension.json) and replace all values with your extension's details.

### 2. Add it to your JAR

```bash
# During build
jar cf my-extension.jar META-INF/extension.json com/example/animeextension/*.class

# Or add to an existing JAR
jar uf my-extension.jar META-INF/extension.json
```

### 3. Install the extension

Place the JAR in the extensions directory:

```bash
cp my-extension.jar ~/Library/Application\ Support/Anikku/extensions/com.example.animeextension.jar
```

The JAR filename **must** match the `pkgName` field (e.g., `com.example.animeextension.jar`).

### 4. Trust the extension

On first load, the extension will appear as **Untrusted** in the app. The user must explicitly trust it via the UI. This is a security measure — the extension's SHA-256 hash is verified on every subsequent load.

---

## Field Reference

### Required Fields

| Field | Type | Description |
|---|---|---|
| `name` | `string` | Human-readable extension name. Convention: `"Aniyomi: My Extension Name"` |
| `pkgName` | `string` | Unique package name (reverse domain). Must match the JAR filename. |
| `versionName` | `string` | Version string like `"1.2.3"`. The integer part before the last `.` is the **libVersion** (e.g., `"15.4"` → `15.0`). |
| `versionCode` | `integer` | Monotonically increasing version number. Higher = newer. |
| `libVersion` | `float` | Minimum library version required. Must be between `12.0` and `15.0` (inclusive). |
| `sourceClass` | `string` | Semicolon-delimited list of fully qualified source class names. |

### Optional Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `lang` | `string` | `""` | ISO 639-1 language code (`"en"`, `"ja"`, etc.). Multi-lang sources should leave empty. |
| `isNsfw` | `boolean` | `false` | Adult content flag. Hidden unless user enables NSFW in Preferences. |
| `isTorrent` | `boolean` | `false` | Torrent source flag. Enables torrent-specific UI features. |
| `pkgFactory` | `string` | `null` | Optional `SourceFactory` class. Its `createSources()` method is called at load time. |
| `hasReadme` | `boolean` | `false` | Extension includes documentation. |
| `hasChangelog` | `boolean` | `false` | Extension includes a changelog. |

---

## Source Class Resolution

The `sourceClass` field specifies which Java/Kotlin classes to instantiate as anime sources. Multiple classes are separated by semicolons:

```json
"sourceClass": "com.example.extension.MySource;com.example.extension.MyOtherSource"
```

### Supported class types

Each class must implement exactly **one** of these interfaces:

| Interface | Package | Behavior |
|---|---|---|
| `Source` | `eu.kanade.tachiyomi.source` | Loaded directly. Each class = one source. |
| `SourceFactory` | `eu.kanade.tachiyomi.source` | `createSources()` is called. Can return multiple sources. |
| `AnimeSource` | `eu.kanade.tachiyomi.animesource` | Wrapped in a `Source` adapter automatically. |

### Short class name resolution

If a class name starts with `.`, it is resolved relative to `pkgName`:

```json
"pkgName": "com.example.extension",
"sourceClass": ".MySource;.MyOtherSource"
```

Resolves to:
- `com.example.extension.MySource`
- `com.example.extension.MyOtherSource`

---

## Usage: `pkgFactory` vs `sourceClass`

There are two ways to register sources:

### Option A: Direct class listing (`sourceClass`)

Best for extensions with a fixed set of sources. Each class in `sourceClass` is instantiated and registered as a source.

```json
{
  "sourceClass": "com.example.extension.MySource;com.example.extension.MyOtherSource",
  "pkgFactory": null
}
```

### Option B: Factory pattern (`pkgFactory`)

Best when source creation requires runtime logic. The factory class must implement `SourceFactory`.

```json
{
  "sourceClass": "",
  "pkgFactory": "com.example.extension.MySourceFactory"
}
```

> **Note:** You can use both. Sources from `sourceClass` are loaded first, then the factory's `createSources()` adds more.

---

## Lib Version Compatibility

| Lib Version | Aniyomi Version (Android) | macOS Support |
|---|---|---|
| 12 – 15 | ✅ Supported | ✅ Supported |
| < 12 | ❌ Too old | ❌ Rejected |
| > 15 | ❌ Too new | ❌ Rejected |

The lib version is parsed from the integer part before the last `.` in `versionName`.

Examples:
- `"15.4"` → libVersion = `15.0`
- `"14.1"` → libVersion = `14.0`
- `"12.0"` → libVersion = `12.0`

---

## Migration from Android

### AndroidManifest.xml → extension.json

| Android (APK) | macOS (JAR) |
|---|---|
| `<meta-data android:name="tachiyomi.animeextension.class" android:value="x;y"/>` | `"sourceClass": "x;y"` |
| `<meta-data android:name="tachiyomi.animeextension.factory" android:value="z"/>` | `"pkgFactory": "z"` |
| `<meta-data android:name="tachiyomi.animeextension.nsfw" android:value="1"/>` | `"isNsfw": true` |
| `<meta-data android:name="tachiyomi.animeextension.torrent" android:value="1"/>` | `"isTorrent": true` |
| `<meta-data android:name="tachiyomi.animeextension.hasReadme" android:value="true"/>` | `"hasReadme": true` |
| `<meta-data android:name="tachiyomi.animeextension.hasChangelog" android:value="true"/>` | `"hasChangelog": true` |
| `<uses-feature android:name="tachiyomi.animeextension"/>` | _(automatic — any JAR with extension.json is an extension)_ |
| APK signing | SHA-256 hash of JAR file |
| `android:versionName` / `android:versionCode` | `versionName` / `versionCode` |
| `android:label` | `name` |

### Key differences for Android extension developers

1. **No Android SDK classes**: macOS extensions run on the JVM, not Android. Do not use `android.*` classes. Use JVM equivalents:
   - `java.net.HttpURLConnection` or OkHttp for networking
   - `java.util.Base64` for Base64
   - `java.io.File` for file I/O

2. **No PackageManager**: The macOS app uses `java.net.URLClassLoader` instead of Android's `PathClassLoader`. All classes your extension needs must be bundled in the JAR or placed in the `libs/` subdirectory.

3. **Trust model**: Instead of APK signing certificates, trust is based on the SHA-256 hash of the entire JAR file. Users must explicitly trust each extension version.

4. **No APK install flow**: Extensions are installed by copying JAR files to the extensions directory — no install broadcasts, no PackageManager hooks.

---

## Directory Structure

```
~/Library/Application Support/Anikku/
├── data/
│   └── trust/
│       └── trusted_extensions.json   ← Trust store (SHA-256 hashes)
├── extensions/                        ← Extension JARs go here
│   ├── com.example.myextension.jar
│   └── libs/                          ← Shared dependency JARs
│       ├── gson-2.10.1.jar
│       └── ...
├── downloads/
├── backups/
├── logs/
└── covers/
```

### Dependencies

If your extension JAR depends on external libraries, place them in the `extensions/libs/` directory. The loader automatically adds all JARs from this directory to the classpath.

> **Important**: Do not include libraries already on the app's classpath (OkHttp, RxJava, jsoup, kotlinx.serialization, etc.). See [shared dependencies](#shared-dependencies) below.

---

## Shared Dependencies

The macOS app provides these libraries on the main classpath. Your extension JAR should **NOT** bundle them:

| Library | Version | Group:Artifact |
|---|---|---|
| OkHttp | 5.x | `com.squareup.okhttp3:okhttp` |
| OkHttp Brotli | 5.x | `com.squareup.okhttp3:okhttp-brotli` |
| OkHttp DoH | 5.x | `com.squareup.okhttp3:okhttp-dnsoverhttps` |
| RxJava | 1.3.x | `io.reactivex:rxjava` |
| jsoup | 1.21.x | `org.jsoup:jsoup` |
| Okio | 3.13.x | `com.squareup.okio:okio` |
| DiskLruCache | 2.x | `com.jakewharton:disklrucache` |
| kotlinx-serialization | 1.9.x | `org.jetbrains.kotlinx:kotlinx-serialization-json` |
| kotlinx-coroutines | 1.10.x | `org.jetbrains.kotlinx:kotlinx-coroutines-core` |

---

## Security & Trust Model

Every extension JAR is hashed (SHA-256) on first load. The hash is stored in `trusted_extensions.json` when the user explicitly trusts the extension.

- **Untrusted extensions** appear in the UI with a warning. Their sources are not loaded.
- **Trusted extensions** load normally. If the JAR file changes (update, tampering), the hash no longer matches and the extension reverts to untrusted.
- Users must **explicitly trust** each new version after an update.

There is no centralized signing authority — trust is per-user, per-hash.

---

## Example: Complete extension.json

```json
{
  "name": "Aniyomi: ExampleSource",
  "pkgName": "com.example.animeextension",
  "versionName": "15.1",
  "versionCode": 151,
  "libVersion": 15.0,
  "lang": "en",
  "isNsfw": false,
  "isTorrent": false,
  "sourceClass": "com.example.animeextension.ExampleSource;.SecondarySource",
  "pkgFactory": null,
  "hasReadme": true,
  "hasChangelog": true
}
```

In this example, `sourceClass` resolves to:
1. `com.example.animeextension.ExampleSource` (fully qualified)
2. `com.example.animeextension.SecondarySource` (relative — starts with `.`)

---

## See Also

- [Phase 3.3 Implementation](../src/main/kotlin/app/anikku/macos/platform/extension/) — Extension loader and manager source code
- [Aniyomi Extension Guide (Android)](https://aniyomi.org/docs/guides/contribute) — Original Android extension development guide
- [Sample template](./samples/META-INF/extension.json) — Ready-to-use metadata template
