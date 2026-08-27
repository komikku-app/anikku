# Anikku macOS Extension Repository

Pre-converted anime extension JARs for [Anikku macOS](https://github.com/komikku-app/anikku).

## How it works

Anikku on Android installs extensions as APK files. On macOS, the app loads **JAR files** via `URLClassLoader`. These JARs are produced by converting the Android APK's DEX bytecode directly to JVM bytecode using `dex2jar`.

This repo hosts those pre-converted JARs so you can install them from within the app — just like the Android extension repos.

## Adding this repo to the app

1. Open **Anikku → Browse → Extensions**
2. Go to the **Repos** tab
3. Add this repo URL in the custom field:

   ```
   https://raw.githubusercontent.com/komikku-app/anikku-extensions/main/
   ```

4. Tap **Fetch** to load available extensions
5. Go to the **Available** tab and install the extensions you want

## Available extensions

| Extension | Package | Source |
|-----------|---------|--------|
| AllAnime | `eu.kanade.tachiyomi.animeextension.en.allanime` | salmanbappi ✅ |
| AnimePahe | `eu.kanade.tachiyomi.animeextension.en.animepahe` | salmanbappi ✅ |
| AnimeSogo | `eu.kanade.tachiyomi.animeextension.en.animesogo` | salmanbappi ✅ |
| AniNeko | `eu.kanade.tachiyomi.animeextension.en.anineko` | salmanbappi ✅ |

> **Note:** `gogoanime` and `nineanime` are not available in the community anime extension repos.
> - `gogoanime` was not found in either salmanbappi or keiyoushi repos
> - `nineanime` exists in keiyoushi but as a **manga** extension (Tachiyomi manga API) — it won't work with the Anikku macOS anime app

## Building from source

If you prefer to build extensions yourself from the original Android APKs:

```bash
# Convert a single extension
brew install dex2jar
cd Anikku/macos
./scripts/convert-keiyoushi-extension.sh --pkg allanime

# The JAR will be installed to ~/Library/Application Support/Anikku/extensions/
```

## How the conversion works

1. **Download** the Android APK from the community extension repo
2. **Convert** DEX → JVM bytecode using `d2j-dex2jar` (direct bytecode conversion, no decompilation)
3. **Extract** metadata from `AndroidManifest.xml` (package name, version)
4. **Package** with `META-INF/extension.json` matching the Anikku extension format

The resulting JAR references Android API classes at the bytecode level. The `MacOSExtensionLoader` handles these gracefully by catching `NoClassDefFoundError` at runtime — the extension's business logic (HTTP calls, HTML parsing) executes normally.

## Auto-build pipeline

This repo includes a GitHub Actions workflow (`.github/workflows/build-extensions.yml`) that automatically rebuilds extensions whenever the source repos update. The workflow:

1. Runs weekly or on-demand
2. Fetches the latest APKs from community repos
3. Converts them with dex2jar
4. Updates `index.min.json`
5. Commits any updated JARs back to the repo

## Index format

The repo root contains `index.min.json` — a JSON array where each entry has:

```json
{
  "name": "Aniyomi: AllAnime",
  "pkg": "eu.kanade.tachiyomi.animeextension.en.allanime",
  "apk": "eu.kanade.tachiyomi.animeextension.en.allanime.jar",
  "lang": "en",
  "code": 1661000,
  "version": "16.61.0",
  "nsfw": 0,
  "torrent": 0,
  "sources": []
}
```

JAR files are hosted in the `apk/` directory alongside the index.
