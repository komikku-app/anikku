#!/usr/bin/env bash
#
# batch-build-keiyoushi-from-source.sh
# ======================================
# Batch-builds all yuzono/anime-extensions from source as JVM JARs.
#
# This is the RECOMMENDED approach for making extensions available on macOS.
# Unlike APK→dex2jar conversion, this compiles original Kotlin sources against
# source-api-jvm.jar producing clean JVM bytecode with zero Android references.
#
# Usage:
#   ./batch-build-keiyoushi-from-source.sh
#
# Options:
#   --lang <code>     Build only extensions for this language (default: en)
#   --limit <N>       Build at most N extensions (for testing)
#   --repo <url>      Git repo URL (default: https://github.com/yuzono/anime-extensions.git)
#   --keep-temp       Keep temporary files for debugging
#   --skip-index      Skip generating repo index
#   --help            Show this help
#
# Requirements:
#   - JDK 17+ with kotlinc (brew install kotlin)
#   - git, curl, python3
#   - Anikku source-api JARs (built by Gradle task rebuildSourceApiJars)
#
# Output:
#   - Extension JARs in ~/Library/Application Support/Anikku/extensions/
#   - Repo index in ~/Library/Application Support/Anikku/extensions/macos-source-repo-index.json
#   - Trust store in ~/Library/Application Support/Anikku/data/trust/trusted_extensions.json

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EXTENSIONS_DIR="${HOME}/Library/Application Support/Anikku/extensions"
TEMP_DIR="/tmp/anikku-batch-source-build"
GIT_CLONE_DIR="${TEMP_DIR}/extensions-source"
OUTPUT_DIR="${TEMP_DIR}/output"
BUILD_LOG="${TEMP_DIR}/build-log.txt"

# Default repo: yuzono/anime-extensions (actual anime extension sources)
REPO_URL="${REPO_URL:-https://github.com/yuzono/anime-extensions.git}"

LANG="en"
LIMIT=""
KEEP_TEMP=false
SKIP_INDEX=false

log() { echo "[*] $*"; }
err() { echo "[!] $*" >&2; }

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Batch-build all yuzono/anime-extensions from source as JVM JARs.

Options:
  --lang <code>     Build only extensions for this language (default: en)
  --limit <N>       Build at most N extensions (for testing)
  --repo <url>      Git repo URL
  --keep-temp       Keep temporary files for debugging
  --skip-index      Skip generating repo index
  --help            Show this help
EOF
    exit 0
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --lang) LANG="$2"; shift 2 ;;
        --limit) LIMIT="$2"; shift 2 ;;
        --repo) REPO_URL="$2"; shift 2 ;;
        --keep-temp) KEEP_TEMP=true; shift ;;
        --skip-index) SKIP_INDEX=true; shift ;;
        --help|-h) usage ;;
        *) err "Unknown option: $1"; usage ;;
    esac
done

# ---------------------------------------------------------------------------
# Prerequisites
# ---------------------------------------------------------------------------
log "Prerequisites..."
log "  Extensions dir: ${EXTENSIONS_DIR}"

SOURCE_API_JAR="${PROJECT_DIR}/libs/source-api-jvm.jar"
COMMON_JVM_JAR="${PROJECT_DIR}/libs/common-jvm.jar"

if [ ! -f "$SOURCE_API_JAR" ] || [ ! -f "$COMMON_JVM_JAR" ]; then
    err "source-api JARs not found at ${PROJECT_DIR}/libs/"
    err "Build them first: cd ${PROJECT_DIR} && ./gradlew rebuildSourceApiJars"
    exit 1
fi
log "  source-api: ${SOURCE_API_JAR}"
log "  common-jvm: ${COMMON_JVM_JAR}"

if ! command -v kotlinc &>/dev/null; then
    err "kotlinc not found. Install: brew install kotlin"
    exit 1
fi
log "  kotlinc: $(which kotlinc)"

# Auto-detect JAVA_HOME
JAVA_HOME=""
for candidate in \
    "${JAVA_HOME}" \
    "/opt/homebrew/opt/openjdk@17" \
    "/opt/homebrew/opt/openjdk@21" \
    "/opt/homebrew/opt/openjdk" \
    "/usr/local/opt/openjdk@17" \
    "/usr/local/opt/openjdk" \
    "$(/usr/libexec/java_home 2>/dev/null || true)"; do
    if [ -n "$candidate" ] && [ -f "${candidate}/bin/jar" ]; then
        JAVA_HOME="$candidate"
        break
    fi
done
if [ -z "$JAVA_HOME" ]; then
    err "JDK 17+ not found. Install: brew install openjdk@17"
    exit 1
fi
JAR_CMD="${JAVA_HOME}/bin/jar"
log "  jar: ${JAR_CMD}"

# ---------------------------------------------------------------------------
# Step 1: Clone the extensions source repo
# ---------------------------------------------------------------------------
log ""
log "╔══════════════════════════════════════════════════════════════╗"
log "║  BATCH SOURCE BUILD for language: ${LANG}"
log "╚══════════════════════════════════════════════════════════════╝"
log ""

mkdir -p "${TEMP_DIR}" "${OUTPUT_DIR}"

log "Step 1: Cloning extension sources from ${REPO_URL}..."
if [ -d "${GIT_CLONE_DIR}" ]; then
    log "  Updating existing clone..."
    cd "${GIT_CLONE_DIR}"
    git pull --depth 1 2>&1 | tail -2 || true
else
    git clone --depth 1 --single-branch "${REPO_URL}" "${GIT_CLONE_DIR}" 2>&1 | tail -2
fi

cd "${GIT_CLONE_DIR}"
log "  Repo: $(git remote get-url origin 2>/dev/null || echo 'unknown')"
log "  Commit: $(git rev-parse HEAD 2>/dev/null || echo 'unknown')"

# ---------------------------------------------------------------------------
# Step 1b: Apply source patches (workarounds for JVM compilation)
# ---------------------------------------------------------------------------
#
# Patch 1: parallelMapNotNull overload ambiguity
#   Kotlin compiler generates a synthetic non-inline overload for inline suspend
#   fns with generic parameters, causing ambiguity between:
#     - suspend fun <A, B> Iterable<A>.parallelMapNotNull(...): List<B>
#     - suspend fun <T, R : Any> Iterable<T>.parallelMapNotNull(...): List<R>
#   Fix: remove 'inline' from the suspend version so no synthetic overload is generated.
CORE_COROUTINES="${GIT_CLONE_DIR}/core/src/main/kotlin/keiyoushi/utils/Coroutines.kt"
if [ -f "$CORE_COROUTINES" ] && grep -q 'suspend inline fun.*parallelMapNotNull' "$CORE_COROUTINES" 2>/dev/null; then
    # Kotlin compiler generates a synthetic non-inline overload for inline suspend
    # functions with generic type params, causing overload resolution ambiguity.
    # Patching: remove 'inline' from parallelMapNotNull so no synthetic overload.
    # Also remove 'crossinline' from its lambda param (invalid for non-inline fns).
    # NOTE: 'crossinline' appears as `(crossinline` (no space after paren) so we
    # need to match `(crossinline` not ` crossinline `.
    # IMPORTANT: use a specific pattern to ONLY match the parallelMapNotNull function
    # definition, NOT parallelMapNotNullBlocking and NOT the call parallelMapNotNull(f).
    # The pattern `parallelMapNotNull(crossinline` matches only the definition line.
    sed -i '' '/parallelMapNotNull(crossinline/s/ inline / /' "$CORE_COROUTINES"
    sed -i '' '/parallelMapNotNull(crossinline/s/(crossinline /(/' "$CORE_COROUTINES"
    # Patch parallelMapNotNull: remove inline+crossinline, add B : Any bound
    # The Kotlin/JVM compiler generates a synthetic overload with 'R : Any' bound,
    # causing ambiguity. Adding 'B : Any' matches the synthetic overload's constraint.
    sed -i '' '/parallelMapNotNull(crossinline/s/ inline / /' "$CORE_COROUTINES"
    sed -i '' '/parallelMapNotNull(crossinline/s/(crossinline /(/' "$CORE_COROUTINES"
    sed -i '' '/parallelMapNotNull(f:/s/<A, B>/<A, B : Any>/' "$CORE_COROUTINES"
    log "  Patched: parallelMapNotNull — removed inline+crossinline, added B : Any"

    # Patch parallelMapNotNullBlocking: also remove inline+crossinline since it
    # CALLS parallelMapNotNull (now non-inline). A crossinline parameter cannot
    # be passed to a non-inline function — the compiler requires the parameter
    # to be used within an inline context.
    # NOTE: 'inline' appears at START of line (no leading space), so we need
    # TWO patterns: one for ' inline ' (mid-line) and one for '^inline ' (line-start)
    sed -i '' '/parallelMapNotNullBlocking(crossinline/s/ inline / /' "$CORE_COROUTINES"
    sed -i '' '/parallelMapNotNullBlocking(crossinline/s/^inline //' "$CORE_COROUTINES"
    sed -i '' '/parallelMapNotNullBlocking(crossinline/s/(crossinline /(/' "$CORE_COROUTINES"
    log "  Patched: parallelMapNotNullBlocking — removed inline+crossinline"

    # Patch parallelCatchingFlatMap: also remove inline+crossinline.
    # Same root cause: Kotlin/JVM synthetic overload for inline suspend functions
    # with generic parameters causes type inference failures when non-suspend
    # function references (like ::extractVideo) are passed as arguments.
    # Removing inline+crossinline eliminates the synthetic overload entirely.
    sed -i '' '/parallelCatchingFlatMap(crossinline/s/ inline / /' "$CORE_COROUTINES"
    sed -i '' '/parallelCatchingFlatMap(crossinline/s/(crossinline /(/' "$CORE_COROUTINES"
    log "  Patched: parallelCatchingFlatMap — removed inline+crossinline"

    # Patch parallelCatchingFlatMapBlocking: remove inline+crossinline too.
    # It CALLS parallelCatchingFlatMap (now non-inline) and passes its
    # crossinline parameter — illegal for non-inline functions.
    sed -i '' '/parallelCatchingFlatMapBlocking(crossinline/s/ inline / /' "$CORE_COROUTINES"
    sed -i '' '/parallelCatchingFlatMapBlocking(crossinline/s/^inline //' "$CORE_COROUTINES"
    sed -i '' '/parallelCatchingFlatMapBlocking(crossinline/s/(crossinline /(/' "$CORE_COROUTINES"
    log "  Patched: parallelCatchingFlatMapBlocking — removed inline+crossinline"

    # Patch 4: entryValues null safety in DataLifeEngine.kt
    # The entryValues property in macOS Preference stub was changed from nullable
    # (Array<String>?) to non-null (Array<String>), but the multisrc source files
    # still access it without safe-call. The fix is a no-op at runtime since the
    # stub always returns a non-null array, but the compiler needs the explicit !!.
    DATA_LIFE_ENGINE="${GIT_CLONE_DIR}/lib-multisrc/datalifeengine/src/eu/kanade/tachiyomi/multisrc/datalifeengine/DataLifeEngine.kt"
    # entryValues null safety: the Kotlin compiler infers the result of
    # `entryValues!![index]!! as String` as `String?` when the array property
    # is declared on a class (ListPreference) and accessed through its getter
    # from within a closure/lambda. This appears to be a Kotlin compiler edge
    # case with Array element types and lambda captures.
    # Fix: add `!!` on the `entry` variable where it's used in putString.
    # NOTE: The error is actually about `key` being `String?`, not `entry`!
    # ListPreference.key is `var key: String? = null` in the Preference stubs,
    # while SharedPreferences.Editor.putString expects `String` (non-null).
    # Fix: add `!!` on BOTH key and entry at the putString call site.
    if [ -f "$DATA_LIFE_ENGINE" ] && grep -q 'putString(key' "$DATA_LIFE_ENGINE" 2>/dev/null; then
        sed -i '' 's/putString(key, entry)/putString(key!!, entry!!)/g' "$DATA_LIFE_ENGINE"
        log "  Patched: DataLifeEngine.kt — key!! and entry!! in putString"
    fi

    # Patch 5: entryValues null safety in DooPlay.kt
    DOO_PLAY="${GIT_CLONE_DIR}/lib-multisrc/dooplay/src/eu/kanade/tachiyomi/multisrc/dooplay/DooPlay.kt"
    if [ -f "$DOO_PLAY" ] && grep -q 'putString(key' "$DOO_PLAY" 2>/dev/null; then
        sed -i '' 's/putString(key, entry)/putString(key!!, entry!!)/g' "$DOO_PLAY"
        log "  Patched: DooPlay.kt — key!! and entry!! in putString"
    fi

    # Patch 6: DopeFlix MutableSet property delegate type mismatch
    # getStringSet() returns Set<String> but the property delegate expects MutableSet<String>.
    # Fix: add .toMutableSet() to convert the return value.
    DOPE_FLIX="${GIT_CLONE_DIR}/lib-multisrc/dopeflix/src/eu/kanade/tachiyomi/multisrc/dopeflix/DopeFlix.kt"
    # NOTE: line ends with '!! }' not just '!!' — the sed pattern captures '!! }'
    if [ -f "$DOPE_FLIX" ] && grep -q 'hosterNames.toSet())!!' "$DOPE_FLIX" 2>/dev/null; then
        sed -i '' '/hosterNames.toSet())!!/s/!! }/!!.toMutableSet() }/' "$DOPE_FLIX"
        log "  Patched: DopeFlix.kt — MutableSet<String> via .toMutableSet()"
    fi

else
    log "  Core Coroutines.kt already patched or not found — skipping"
fi

# ---------------------------------------------------------------------------
# Patch hanime: remove Chicory/WASM/WebView signature providers
# ---------------------------------------------------------------------------
#
# The hanime extension has 4 files that depend on unavailable JVM dependencies:
# - ChicoryGlue.kt (requires com.dylibso.chicory WASM runtime)
# - ChicorySignatureProvider.kt (requires Chicory)
# - HanimeWasmBinary.kt (fetches WASM binary — no Android deps but only used by Chicory)
# - WebViewSignatureProvider.kt (requires Android WebView)
#
# The extension also has NativeSignatureProvider.kt which computes SHA-256 directly
# in pure JVM without any external dependencies. We patch the source to:
# 1. Remove the 4 problematic files
# 2. Replace all WebView/WASM/Chicory references with NativeSignatureProvider

HANIME_DIR="${GIT_CLONE_DIR}/src/en/hanime/src/eu/kanade/tachiyomi/animeextension/en/hanime"
if [ -d "$HANIME_DIR" ]; then
    log "  Patching hanime — removing Chicory/WASM/WebView dependencies..."

    # Remove problematic files
    rm -f "$HANIME_DIR/ChicoryGlue.kt" 2>/dev/null || true
    rm -f "$HANIME_DIR/ChicorySignatureProvider.kt" 2>/dev/null || true
    rm -f "$HANIME_DIR/HanimeWasmBinary.kt" 2>/dev/null || true
    rm -f "$HANIME_DIR/WebViewSignatureProvider.kt" 2>/dev/null || true

    # Patch Hanime.kt to replace all non-native signature provider references
    # Use the standalone Python script (avoids shell escaping issues)
    PATCH_SCRIPT="${SCRIPT_DIR}/patch-hanime-source.py"
    if [ -f "$PATCH_SCRIPT" ]; then
        python3 "$PATCH_SCRIPT" "$HANIME_DIR" 2>&1 | sed 's/^/  /'
        log "  Hanime patching complete"
    else
        log "  WARNING: patch-hanime-source.py not found at $PATCH_SCRIPT — skipping"
    fi
fi

# ---------------------------------------------------------------------------
# Patch kissanime: add missing abstract members for ParsedAnimeHttpSource
# ---------------------------------------------------------------------------
#
# The kissanime extension extends ParsedAnimeHttpSource but doesn't implement
# the hosterListSelector() and hosterFromElement() abstract methods because it
# overrides getVideoList() directly instead of using the hoster flow.
#
# We need to add stub implementations of these two methods.

KISSANIME_FILE="${GIT_CLONE_DIR}/src/en/kissanime/src/eu/kanade/tachiyomi/animeextension/en/kissanime/KissAnime.kt"
if [ -f "$KISSANIME_FILE" ]; then
    log "  Patching kissanime — adding abstract member stubs..."
    
    # Use standalone Python script (avoids shell escaping issues)
    PATCH_SCRIPT="${SCRIPT_DIR}/patch-kissanime-source.py"
    KISSANIME_SRC_DIR="${GIT_CLONE_DIR}/src/en/kissanime/src/eu/kanade/tachiyomi/animeextension/en/kissanime"
    if [ -f "$PATCH_SCRIPT" ] && [ -d "$KISSANIME_SRC_DIR" ]; then
        python3 "$PATCH_SCRIPT" "$KISSANIME_SRC_DIR" 2>&1 | sed 's/^/  /'
        log "  Kissanime patching complete"
    else
        log "  WARNING: patch-kissanime-source.py not found or dir missing — skipping"
    fi
fi

# ---------------------------------------------------------------------------
# Step 2: Find all extensions for the target language
# ---------------------------------------------------------------------------
log ""
log "Step 2: Finding extensions in src/${LANG}/..."

EXT_DIRS=()
while IFS= read -r dir; do
    [ -n "$dir" ] && EXT_DIRS+=("$dir")
done < <(find "src/${LANG}" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort)

TOTAL_COUNT=${#EXT_DIRS[@]}
log "  Found ${TOTAL_COUNT} extension(s) in src/${LANG}/"

if [ "$TOTAL_COUNT" -eq 0 ]; then
    err "No extensions found in src/${LANG}/!"
    err "Available languages: $(ls src/ 2>/dev/null | tr '\n' ' ')"
    exit 1
fi

# Apply limit
if [ -n "$LIMIT" ] && [ "$LIMIT" -gt 0 ] 2>/dev/null; then
    EXT_DIRS=("${EXT_DIRS[@]:0:$LIMIT}")
    log "  Limited to ${LIMIT} extension(s)"
fi

# ---------------------------------------------------------------------------
# Step 2b: Build classpath (shared across all compilations)
# ---------------------------------------------------------------------------
log ""
log "Step 2b: Building shared classpath..."

CLASSPATH="${SOURCE_API_JAR}:${COMMON_JVM_JAR}"
GRADLE_CACHE="${HOME}/.gradle/caches/modules-2/files-2.1"

add_to_cp() {
    local item="$1"
    [ -z "$item" ] && return
    [ ! -e "$item" ] && return
    if echo "$CLASSPATH" | tr ':' '\n' | grep -Fxq "$item"; then
        return
    fi
    CLASSPATH="${CLASSPATH}:${item}"
}

# Prepend to classpath so this entry is found FIRST before others
prepend_to_cp() {
    local item="$1"
    [ -z "$item" ] && return
    [ ! -e "$item" ] && return
    if echo "$CLASSPATH" | tr ':' '\n' | grep -Fxq "$item"; then
        return
    fi
    CLASSPATH="${item}:${CLASSPATH}"
}

find_dep() {
    local group="$1"
    local artifact="$2"
    local found
    found=$(find "$GRADLE_CACHE" -path "*/${group}/${artifact}/*" -name "${artifact}*.jar" ! -name '*sources*' ! -name '*javadoc*' 2>/dev/null | sort -V | tail -1)
    if [ -n "$found" ] && [ -f "$found" ]; then
        add_to_cp "$found"
        return 0
    fi
    return 1
}

# Add macos/libs/ JARs
for j in "${PROJECT_DIR}"/libs/*.jar; do
    [ -f "$j" ] && add_to_cp "$j"
done

# Add compiled macOS module classes (provides Android stubs: android.*, androidx.*)
# These are compiled by the Gradle build and found in the build output directory.
# They provide android.util.Base64, android.os.Handler, android.webkit.WebView, etc.
# which yuzono anime extensions reference.
MACOS_CLASSES_DIR="${PROJECT_DIR}/build/classes/kotlin/main"
if [ -d "$MACOS_CLASSES_DIR" ]; then
    prepend_to_cp "$MACOS_CLASSES_DIR"
    log "  Android stubs: ${MACOS_CLASSES_DIR} ✓ (prepended)"
else
    log "  WARNING: macOS build classes not found at ${MACOS_CLASSES_DIR}"
    log "  Run './gradlew :compileKotlin' first to build the module"
fi

# Kotlin stdlib from brew
KOTLIN_LIB=$(brew --prefix kotlin 2>/dev/null || echo "/opt/homebrew/opt/kotlin")
if [ -d "$KOTLIN_LIB/libexec/lib" ]; then
    for j in "$KOTLIN_LIB"/libexec/lib/*.jar; do
        add_to_cp "$j"
    done
fi

# Common extension deps
find_dep "org.jetbrains.kotlinx" "kotlinx-coroutines-core-jvm" || true
find_dep "org.jetbrains.kotlinx" "kotlinx-serialization-json-jvm" || true
find_dep "org.jetbrains.kotlinx" "kotlinx-serialization-core-jvm" || true
find_dep "org.jetbrains.kotlinx" "kotlinx-serialization-protobuf-jvm" || true

# aniyomi-extensions-lib (source-api stubs + extractor interfaces)
ANIYOMI_LIB_JAR="${PROJECT_DIR}/libs/aniyomi-extensions-lib.jar"
if [ -f "$ANIYOMI_LIB_JAR" ]; then
    add_to_cp "$ANIYOMI_LIB_JAR"
    log "  aniyomi-extensions-lib: $(basename $ANIYOMI_LIB_JAR) ✓"
fi
find_dep "com.squareup.okhttp3" "okhttp-jvm" || find_dep "com.squareup.okhttp3" "okhttp" || true
find_dep "com.squareup.okio" "okio-jvm" || true
find_dep "org.jsoup" "jsoup" || true
find_dep "io.reactivex" "rxjava" || true
find_dep "com.github.mihonapp" "injekt" || true
# Original injekt API (uy.kohesive.injekt) — needed by core/keiyoushi.utils
find_dep "uy.kohesive.injekt" "injekt-api" || true
find_dep "uy.kohesive.injekt" "injekt-core" || true
find_dep "com.fasterxml.jackson.core" "jackson-core" || true
find_dep "com.fasterxml.jackson.core" "jackson-databind" || true
find_dep "com.google.code.gson" "gson" || true
find_dep "org.jetbrains" "kotlin-reflect" || true
find_dep "com.squareup.okhttp3" "okhttp-brotli" || true
find_dep "app.cash.quickjs" "quickjs-jvm" || true

# Kotlinx-serialization compiler plugin (required for @Serializable .serializer() methods)
SERIALIZATION_PLUGIN="${KOTLIN_LIB}/libexec/lib/kotlinx-serialization-compiler-plugin.jar"
if [ ! -f "$SERIALIZATION_PLUGIN" ]; then
    # Fallback: search more paths
    SERIALIZATION_PLUGIN=$(find "$KOTLIN_LIB" -name 'kotlinx-serialization-compiler-plugin*.jar' 2>/dev/null | head -1)
fi
KOTLINC_OPTS=""
if [ -f "$SERIALIZATION_PLUGIN" ]; then
    KOTLINC_OPTS="-Xplugin=$SERIALIZATION_PLUGIN"
    log "  serialization plugin: $(basename $SERIALIZATION_PLUGIN) ✓"
else
    log "  WARNING: kotlinx-serialization plugin not found"
fi

log "  Classpath: $(echo "$CLASSPATH" | tr ':' '\n' | wc -l) entries"

# ---------------------------------------------------------------------------
# Step 2d: Apply universal source patches for JVM compilation
# ---------------------------------------------------------------------------
log ""
log "Step 2d: Applying universal source patches for JVM compilation..."
PATCH_SCRIPT="${SCRIPT_DIR}/patch-all-source-issues.py"
if [ -f "$PATCH_SCRIPT" ]; then
    python3 "$PATCH_SCRIPT" "${GIT_CLONE_DIR}" 2>&1 || log "  WARNING: patch-all-source-issues.py returned non-zero — continuing"
else
    log "  WARNING: patch-all-source-issues.py not found at ${PATCH_SCRIPT}"
fi

# Patch: add import getListPreference to Miruro.kt (replacing the commented-out line)
MIRURO_FILE="${GIT_CLONE_DIR}/src/en/miruro/src/eu/kanade/tachiyomi/animeextension/en/miruro/Miruro.kt"
if [ -f "$MIRURO_FILE" ]; then
    # Replace the full commented line with a clean import (removing trailing comment text)
    # The original line is: // import keiyoushi.utils.getListPreference — inlined below
    sed -i '' '/^\/\/ import keiyoushi\.utils\.getListPreference /c\
import keiyoushi.utils.getListPreference' "$MIRURO_FILE" 2>/dev/null || true
    log "  Patched: Miruro.kt — replaced commented import with clean import"
fi

# Patch: remove problematic extractor dependencies from MiruroExtractor (m3u8server,
# megacloudextractor, omniembedextractor, rapidcloudextractor — all have unresolved
# external dependencies like nanohttpd or WebViewResolver stub issues)
MIRURO_PATCH="${SCRIPT_DIR}/patch-miruro-sources.py"
if [ -f "$MIRURO_PATCH" ]; then
    python3 "$MIRURO_PATCH" "${GIT_CLONE_DIR}" 2>&1 | sed 's/^/  /'
else
    log "  WARNING: patch-miruro-sources.py not found — cannot patch miruro"
fi

# Patch: add import android.util.LruCache to CinebyExtractor.kt
CINEBY_FILE="${GIT_CLONE_DIR}/src/en/cineby/src/eu/kanade/tachiyomi/animeextension/en/cineby/CinebyExtractor.kt"
if [ -f "$CINEBY_FILE" ]; then
    # Check if the import is already present
    if ! grep -q 'import android.util.LruCache' "$CINEBY_FILE" 2>/dev/null; then
        sed -i '' '/^import android.os.Build$/a\
import android.util.LruCache' "$CINEBY_FILE" 2>/dev/null || true
        log "  Patched: CinebyExtractor.kt — added import android.util.LruCache"
    else
        log "  CinebyExtractor.kt already has LruCache import — skipping"
    fi
fi

# Patch: CloudflareInterceptor overrides onPageFinished with nullable params
# (WebView?, String?) but our WebViewClient stub has non-null (WebView, String).
# Strip the nullability markers so the override matches.
CF_INTERCEPTOR="${GIT_CLONE_DIR}/lib/cloudflareinterceptor/src/aniyomi/lib/cloudflareinterceptor/CloudflareInterceptor.kt"
if [ -f "$CF_INTERCEPTOR" ]; then
    sed -i '' 's/override fun onPageFinished(view: WebView?, pageUrl: String?)/override fun onPageFinished(view: WebView, pageUrl: String)/' "$CF_INTERCEPTOR" 2>/dev/null || true
    log "  Patched: CloudflareInterceptor.kt — non-null onPageFinished params"
fi

# ---------------------------------------------------------------------------
# Patch anidb: fix extension function hiding supertype member
# ---------------------------------------------------------------------------
ANIDB_SRC=$(find "${GIT_CLONE_DIR}" -path "*/en/anidb/*" -name "AniDB.kt" 2>/dev/null | head -1)
if [ -n "$ANIDB_SRC" ] && [ -f "$ANIDB_SRC" ]; then
    # 1. sortVideos extension function conflicts with supertype member.
    #    Remove 'private' so it can be marked 'override' (private+override invalid)
    sed -i '' 's/private fun List<Video>.sortVideos/override fun List<Video>.sortVideos/' "$ANIDB_SRC" 2>/dev/null || true
    # 1b. Remove 'override' from disableRelatedAnimesBySearch (not in our JVM stubs)
    sed -i '' 's/override val disableRelatedAnimesBySearch/val disableRelatedAnimesBySearch/' "$ANIDB_SRC" 2>/dev/null || true
    # 2. Remove setDefaultValue() calls on SwitchPreferenceCompat (not in JVM stubs)
    sed -i '' '/setDefaultValue(/d' "$ANIDB_SRC" 2>/dev/null || true
    log "  Patched: AniDB.kt — sortVideos override + setDefaultValue removal"
fi

# ---------------------------------------------------------------------------
# Patch miruro: remove AniLib import only (don't delete whole lines)
# ---------------------------------------------------------------------------
MIRURO_EXT_SRC=$(find "${GIT_CLONE_DIR}" -path "*/en/miruro/*" -name "Miruro.kt" 2>/dev/null | head -1)
if [ -n "$MIRURO_EXT_SRC" ] && [ -f "$MIRURO_EXT_SRC" ]; then
    # Only delete the import line — let the compiler skip unreachable AniLib code
    sed -i '' '/^import aniyomi\.lib\.anilib\.AniLib$/d' "$MIRURO_EXT_SRC" 2>/dev/null || true
    log "  Patched: Miruro.kt — removed AniLib import"
fi

# ---------------------------------------------------------------------------
# Step 3: Compile each extension
# ---------------------------------------------------------------------------
log ""
log "Step 3: Building extensions..."
log ""

# ---------------------------------------------------------------------------
# Step 2c: Compile shared library modules (lib-*, common, core)
# ---------------------------------------------------------------------------
log ""
log "Step 2c: Compiling shared library modules..."

SHARED_LIBS_DIR="${TEMP_DIR}/shared-libs-classes"

# Step 2c-i: Compile keiyoushi-utils FIRST (pure JVM port, provides keiyoushi.utils.*)
# Must compile before lib/extractors since several extractors import keiyoushi.utils.*
KEIYOUSHI_UTILS_DIR="${PROJECT_DIR}/keiyoushi-utils/src/main/kotlin"
if [ -d "$KEIYOUSHI_UTILS_DIR" ]; then
    UTILS_NAME="keiyoushi-utils"
    UTILS_CLASSES="${SHARED_LIBS_DIR}/${UTILS_NAME}"
    # Only recompile if no cached classes
    if [ -d "$UTILS_CLASSES" ]; then
        cached_classes=$(find "$UTILS_CLASSES" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        [ "$cached_classes" -gt 0 ] && compiled_ok=true || { rm -rf "$UTILS_CLASSES"; compiled_ok=false; }
    else
        compiled_ok=false
    fi

    if [ "$compiled_ok" != true ]; then
        find "$KEIYOUSHI_UTILS_DIR" -name "*.kt" > "${TEMP_DIR}/${UTILS_NAME}-sources.txt" 2>/dev/null || true
        utils_src_count=$(wc -l < "${TEMP_DIR}/${UTILS_NAME}-sources.txt" 2>/dev/null || echo 0)
        if [ "$utils_src_count" -gt 0 ]; then
            log "  Compiling: ${UTILS_NAME} (${utils_src_count} files, pure JVM port)..."
            mkdir -p "$UTILS_CLASSES"
            set +e
            kotlinc -cp "${CLASSPATH}" -d "$UTILS_CLASSES" -jvm-target 17 ${KOTLINC_OPTS} @"${TEMP_DIR}/${UTILS_NAME}-sources.txt" 2>"${TEMP_DIR}/${UTILS_NAME}-compile.log"
            utils_exit=$?
            set -e
            utils_class_count=$(find "$UTILS_CLASSES" -name "*.class" 2>/dev/null | wc -l)
            if [ "$utils_class_count" -gt 0 ]; then
                prepend_to_cp "$UTILS_CLASSES"
                log "    -> ${utils_class_count} classes ✓"
            else
                log "    -> FAILED: $(cat "${TEMP_DIR}/${UTILS_NAME}-compile.log" 2>/dev/null | head -3)"
            fi
        fi
    else
        prepend_to_cp "$UTILS_CLASSES"
        cached_count=$(find "$UTILS_CLASSES" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        log "  ${UTILS_NAME} already compiled (${cached_count} cached classes) ✓"
    fi
fi

# Step 2c-ii: Compile lib/*/ extractor modules (aniyomi.lib.* package)
# These are the actual extractor implementations (DoodExtractor, StreamWishExtractor, PlaylistUtils, etc.)
# that extensions and lib-multisrc depend on. Must compile AFTER keiyoushi-utils.
EXTRACTORS_DIR="${GIT_CLONE_DIR}/lib"
EXTRACTORS_OUT="${SHARED_LIBS_DIR}/lib-extractors"
if [ -d "$EXTRACTORS_DIR" ]; then
    if [ -d "$EXTRACTORS_OUT" ]; then
        cached_classes=$(find "$EXTRACTORS_OUT" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        [ "$cached_classes" -gt 0 ] && extractors_compiled=true || { rm -rf "$EXTRACTORS_OUT"; extractors_compiled=false; }
    else
        extractors_compiled=false
    fi

    if [ "${extractors_compiled:-false}" != true ]; then
        # WHITELIST approach: only compile extractors needed by our target extensions.
        # This avoids whack-a-mole with dozens of unrelated extractors that have
        # missing Android stubs (WebView, JSpecify, etc.).
        #
        # Extensions needed by the 4 blocked extensions (allanime, anikage, animekhor, animenosub):
        #   doodextractor, filemoonextractor, gogostreamextractor, mp4uploadextractor,
        #   okruextractor, playlistutils, streamwishextractor, vidhideextractor, vidmolyextractor
        #
        # Add more here as additional extensions are enabled.
        # WHITELIST: extractors needed by target standalone extensions.
        # Each entry maps to a directory under lib/ in the cloned repo.
        #
        # Core extractors (needed by allanime, animetake, anikage, etc.):
        #   doodextractor, filemoonextractor, gogostreamextractor, mp4uploadextractor,
        #   okruextractor, playlistutils
        #
        # Added for kissanime:
        #   dailymotionextractor (kissanime)
        #   youruploadextractor (kissanime)
        # WHITELIST: extractors known to compile on JVM.
        # Each entry maps to a directory under lib/ in the cloned repo.
        # NOTE: gogostreamextractor has WebView/crypto deps but compiles.
        # MixDrop, Dailymotion, Rumble, GoogleDrive, Chillx are new additions.
        # Whenever a new extension type is enabled, add its extractors here.
        # WHITELIST: extractors known to compile on JVM.
        # NOTE: chillxextractor requires Android WebView, textinterceptor requires
        # Android Layout/Html APIs — excluded from whitelist.
        # Add new extractors incrementally and verify they compile independently.
        EXTRACTOR_WHITELIST=(
            "burstcloudextractor"
            "chillxextractor"
            "cloudflareinterceptor"
            "cryptoaes"
            "dailymotionextractor"
            "doodextractor"
            "filemoonextractor"
            "gdriveplayerextractor"
            "gogostreamextractor"
            "googledriveextractor"
            "mixdropextractor"
            "mp4uploadextractor"
            "okruextractor"
            "playlistutils"
            "rumbleextractor"
            "seedrandom"
            "streamlareextractor"
            "streamplayextractor"
            "streamwishextractor"
            "vidhideextractor"
            "anilib"
            "vidmolyextractor"
        )
        > "${TEMP_DIR}/lib-extractors-sources.txt"
        for ext_dir in "${EXTRACTOR_WHITELIST[@]}"; do
            find "$EXTRACTORS_DIR/$ext_dir" -name '*.kt' -path '*/src/*' 2>/dev/null >> "${TEMP_DIR}/lib-extractors-sources.txt" || true
        done
        # Also include unpacker (jsunpacker) and synchrony (Deobfuscator) — needed by mp4upload and streamwish
        for dir in unpacker synchrony; do
            find "$EXTRACTORS_DIR/$dir" -name '*.kt' -path '*/src/*' 2>/dev/null >> "${TEMP_DIR}/lib-extractors-sources.txt" || true
        done
        extractor_count=$(wc -l < "${TEMP_DIR}/lib-extractors-sources.txt" 2>/dev/null || echo 0)
        if [ "$extractor_count" -gt 0 ]; then
            log "  Compiling: lib/extractors (${extractor_count} files, aniyomi.lib.*)..."
            mkdir -p "$EXTRACTORS_OUT"
            set +e
            kotlinc -cp "${CLASSPATH}" -d "$EXTRACTORS_OUT" -jvm-target 17 ${KOTLINC_OPTS} @"${TEMP_DIR}/lib-extractors-sources.txt" 2>"${TEMP_DIR}/lib-extractors-compile.log"
            extractor_exit=$?
            set -e
            extractor_class_count=$(find "$EXTRACTORS_OUT" -name '*.class' 2>/dev/null | wc -l)
            if [ "$extractor_class_count" -gt 0 ]; then
                # Prepend extractor classes so freshly compiled extractors (with
                # up-to-date APIs like VidHideExtractor's 2-param constructor)
                # take precedence over old versions in aniyomi-extensions-lib.jar
                # or other dependency JARs.
                prepend_to_cp "$EXTRACTORS_OUT"
                log "    -> ${extractor_class_count} classes ✓"
            else
                log "    -> FAILED: $(head -3 "${TEMP_DIR}/lib-extractors-compile.log" 2>/dev/null)"
            fi
        fi
    else
        add_to_cp "$EXTRACTORS_OUT"
        cached_count=$(find "$EXTRACTORS_OUT" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        log "  lib/extractors already compiled (${cached_count} cached classes) ✓"
    fi
fi

# Step 2c-iii: Compile core/ FIRST (depends on keiyoushi-utils + lib/extractors)
# core provides parallelCatchingFlatMap, parallelMapNotNull, Preferences.kt, etc.
# which lib-multisrc depends on. lib-multisrc must compile AFTER core is on the classpath.
for lib_dir in "${GIT_CLONE_DIR}/core/" "${GIT_CLONE_DIR}/common/" "${GIT_CLONE_DIR}"/lib-*/; do
    [ ! -d "$lib_dir" ] && continue
    lib_name=$(basename "$lib_dir")
    lib_classes="${SHARED_LIBS_DIR}/${lib_name}"
    # Skip only if directory has actual class files (retry if cached empty/broken)
    if [ -d "$lib_classes" ]; then
        cached_classes=$(find "$lib_classes" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        if [ "$cached_classes" -gt 0 ]; then
            add_to_cp "$lib_classes"
            log "  ${lib_name} already compiled (${cached_classes} cached classes) ✓"
            continue
        fi
        rm -rf "$lib_classes"
    fi

    # Exclude test files AND Android-specific Activity files (UrlActivity) — 
    # UrlActivities subclass android.app.Activity and import android.content.ActivityNotFoundException,
    # which have no purpose on macOS/JVM. They handle Android-specific inter-app intents.
    find "$lib_dir" -name "*.kt" ! -path '*/test/*' ! -name '*UrlActivity*' > "${TEMP_DIR}/${lib_name}-sources.txt" 2>/dev/null || true
    src_count=$(wc -l < "${TEMP_DIR}/${lib_name}-sources.txt" 2>/dev/null || echo 0)
    [ "$src_count" -eq 0 ] && continue

    log "  Compiling: ${lib_name} (${src_count} files, excluding tests)..."
    mkdir -p "$lib_classes"

    set +e
    kotlinc -cp "${CLASSPATH}" -d "$lib_classes" -jvm-target 17 ${KOTLINC_OPTS} @"${TEMP_DIR}/${lib_name}-sources.txt" 2>"${TEMP_DIR}/${lib_name}-compile.log"
    local_exit=$?
    set -e

    class_count=$(find "$lib_classes" -name "*.class" 2>/dev/null | wc -l)

    if [ "$class_count" -gt 0 ]; then
        add_to_cp "$lib_classes"
        log "    -> ${class_count} classes ✓"
    elif [ "$local_exit" -ne 0 ]; then
        log "    -> SKIP: compilation failed"
    fi
done

# ---------------------------------------------------------------------------
# Step 2c-iv: Reorder classpath — lib-multisrc before keiyoushi-utils
# ---------------------------------------------------------------------------
#
# keiyoushi-utils was prepended to classpath first (Step 2c-i), while
# lib-multisrc was appended later (Step 2c-iii loop). If both directories
# contain the same class (e.g., leaked/stale AnimeStream from an old build),
# the keiyoushi-utils version wins due to classpath order, shadowing the
# correct version in lib-multisrc.
#
# Fix: after all shared libs are compiled, remove lib-multisrc from its
# current position and prepend it so it's resolved FIRST.
MULTISRC_DIR="${SHARED_LIBS_DIR}/lib-multisrc"
if [ -d "$MULTISRC_DIR" ]; then
    # Remove lib-multisrc from current position in classpath (if present)
    CLASSPATH=$(echo "$CLASSPATH" | tr ':' '\n' | grep -vFx "$MULTISRC_DIR" | tr '\n' ':' | sed 's/:$//')
    # Prepend to front
    CLASSPATH="${MULTISRC_DIR}:${CLASSPATH}"
    log "  Classpath reorder: lib-multisrc → front ✓"
fi

# ---------------------------------------------------------------------------
# Step 2c-v: Package shared libs JAR for runtime extension classpath
# ---------------------------------------------------------------------------
#
# During compilation, shared library classes (keiyoushi-utils, lib-extractors,
# lib-multisrc, core) are on the classpath as directories. At runtime, the
# MacOSExtensionLoader scans extensions/libs/ for JAR files to add to the
# extension's URLClassLoader. This step packages all compiled shared libs into
# a single JAR so extensions can resolve their dependencies at runtime.
SHARED_LIBS_JAR="${EXTENSIONS_DIR}/libs/shared-libs.jar"
mkdir -p "${EXTENSIONS_DIR}/libs"
package_tmpdir=$(mktemp -d)
# Copy shared lib classes into package temp dir. Order matters:
# lib-multisrc MUST be copied LAST so it overrides any identically-named
# classes from keiyoushi-utils (e.g. stale/extracted AnimeStream stubs).
# This matches the classpath reorder in Step 2c-iv where lib-multisrc is
# prepended to the classpath.
for shlib in "${SHARED_LIBS_DIR}"/*/; do
    lib_name=$(basename "$shlib")
    [ "$lib_name" = "lib-multisrc" ] && continue   # skip — copied explicitly last
    [ -d "$shlib" ] && cp -r "$shlib"/* "$package_tmpdir/" 2>/dev/null || true
done
# Copy lib-multisrc LAST so it overrides any conflicts from earlier copies
if [ -d "${SHARED_LIBS_DIR}/lib-multisrc" ]; then
    cp -r "${SHARED_LIBS_DIR}/lib-multisrc"/* "$package_tmpdir/" 2>/dev/null || true
fi
if [ -n "$(ls -A "$package_tmpdir" 2>/dev/null)" ]; then
    (cd "$package_tmpdir" && "${JAR_CMD}" cf "${SHARED_LIBS_JAR}" . 2>/dev/null || true)
    jar_size=$(stat -f%z "${SHARED_LIBS_JAR}" 2>/dev/null || echo "0")
    log "  shared-libs.jar: ${jar_size} bytes → extensions/libs/ ✓"
else
    log "  WARNING: No shared lib classes to package"
fi
rm -rf "$package_tmpdir"

SUCCESS_COUNT=0
FAIL_COUNT=0
SKIPPED_COUNT=0
SUCCESSFUL_PKGS=""
FAILED_NAMES=""
TRUST_DATA="${TEMP_DIR}/trust-data.json"
echo '[]' > "$TRUST_DATA"

for ext_dir in "${EXT_DIRS[@]}"; do
    EXT_NAME=$(basename "$ext_dir")
    echo ""

    SRC_FILES=$(find "$ext_dir" -name "*.kt" 2>/dev/null)
    SRC_COUNT=$(echo "$SRC_FILES" | wc -l | tr -d ' ')

    if [ "$SRC_COUNT" -eq 0 ]; then
        log "  [SKIP] ${EXT_NAME}: no .kt source files"
        SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
        continue
    fi

    # Determine package name
    # NOTE: grep -r includes filename: prefix, so use -h to suppress it.
    # Otherwise the JAR name becomes "path/to/File.kt:com.example.pkg.jar"
    # which contains colons that break the JDK jar command on macOS (APFS).
    PKG=$(grep -rh '^package ' "$ext_dir/src" 2>/dev/null | head -1 | sed 's/[[:space:]]*package //' | sed 's/[[:space:]]*$//' || echo "")
    if [ -z "$PKG" ]; then
        PKG=$(grep -rh '^package ' "$ext_dir"/*.kt 2>/dev/null | head -1 | sed 's/[[:space:]]*package //' | sed 's/[[:space:]]*$//' || echo "")
    fi
    if [ -z "$PKG" ]; then
        PKG="eu.kanade.tachiyomi.animeextension.${LANG}.${EXT_NAME}"
    fi
    JAR_NAME="${PKG}.jar"

    # Find main source class
    MAIN_FILE=$(echo "$SRC_FILES" | xargs grep -l 'AnimeHttpSource\|AnimeCatalogueSource\|CatalogueSource' 2>/dev/null | head -1 || echo "")
    if [ -z "$MAIN_FILE" ]; then
        MAIN_FILE=$(echo "$SRC_FILES" | xargs grep -l 'getVideoList\|getEpisodeList' 2>/dev/null | head -1 || echo "")
    fi
    if [ -z "$MAIN_FILE" ]; then
        MAIN_FILE=$(echo "$SRC_FILES" | sort -rn | head -1)
    fi
    CLASS_NAME=$(basename "$MAIN_FILE" .kt)
    FULL_CLASS_NAME="${PKG}.${CLASS_NAME}"

    # Check if extension uses AnimeHttpSource (anime) or HttpSource (manga)
    # Also check for multisrc theme imports (WcoTheme, AnikotoTheme, AnimeStream, DooPlay, etc.)
    # which transitively extend AnimeHttpSource/ParsedAnimeHttpSource.
    IS_ANIME=false
    if grep -q 'AnimeHttpSource\|AnimeCatalogueSource\|animesource\.model\|import.*multisrc\.' "$MAIN_FILE" 2>/dev/null; then
        IS_ANIME=true
    fi

    if [ "$IS_ANIME" = false ]; then
        log "  [SKIP] ${EXT_NAME}: not an anime extension (no AnimeHttpSource reference)"
        SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
        continue
    fi

    # Check if extension depends on aniyomi.lib.* extractors
    # These are compiled from the lib-*/ directories in the yuzono repo.
    # If lib-multisrc failed to compile, log a warning but don't skip automatically.
    if grep -r 'import aniyomi\.lib\.' "$ext_dir" 2>/dev/null | grep -q .; then
        log "  [NOTE] ${EXT_NAME}: imports aniyomi.lib.* — requires lib-* extractors compiled"
    fi

    # Patch hoster stubs for extensions that extend ParsedAnimeHttpSource
    # but don't implement hosterListSelector/hosterFromElement
    HOSTER_PATCH_SCRIPT="${SCRIPT_DIR}/patch-hoster-stubs.py"
    if [ -f "$HOSTER_PATCH_SCRIPT" ] && [ -d "$ext_dir" ]; then
        python3 "$HOSTER_PATCH_SCRIPT" "$ext_dir" 2>&1 | sed 's/^/    /' || true
    fi

    # Get version code from build.gradle.kts
    VERSION_CODE="100"
    BUILD_FILE=""
    for candidate in "${ext_dir}/build.gradle.kts" "${ext_dir}/build.gradle"; do
        [ -f "$candidate" ] && BUILD_FILE="$candidate" && break
    done
    if [ -n "$BUILD_FILE" ]; then
        VCODE=$(grep -o 'versionCode\s*=\s*[0-9]*' "$BUILD_FILE" 2>/dev/null | grep -o '[0-9]*' | head -1 || echo "100")
        [ -n "$VCODE" ] && VERSION_CODE="$VCODE"
    fi

    # Build extension
    log "  [$((SUCCESS_COUNT+FAIL_COUNT+1))/$TOTAL_COUNT] ${EXT_NAME} (${PKG})"
    log "    Source files: ${SRC_COUNT}, class: ${FULL_CLASS_NAME}"

    EXT_CLASSES_DIR="${TEMP_DIR}/classes-${EXT_NAME}"
    rm -rf "$EXT_CLASSES_DIR"
    mkdir -p "$EXT_CLASSES_DIR"

    # Create META-INF/extension.json
    mkdir -p "${EXT_CLASSES_DIR}/META-INF"
    cat > "${EXT_CLASSES_DIR}/META-INF/extension.json" << JSONEOF
{
  "name": "Aniyomi: ${EXT_NAME}",
  "pkgName": "${PKG}",
  "versionName": "1.0.0",
  "versionCode": ${VERSION_CODE},
  "libVersion": 15.0,
  "lang": "${LANG}",
  "isNsfw": false,
  "isTorrent": false,
  "sourceClass": "${FULL_CLASS_NAME}",
  "pkgFactory": null,
  "hasReadme": false,
  "hasChangelog": false
}
JSONEOF

    # Write source file list
    echo "$SRC_FILES" > "${TEMP_DIR}/${EXT_NAME}-sources.txt"

    set +e
    kotlinc -cp "${CLASSPATH}" -d "${EXT_CLASSES_DIR}" -jvm-target 17 ${KOTLINC_OPTS} @"${TEMP_DIR}/${EXT_NAME}-sources.txt" 2>"${TEMP_DIR}/${EXT_NAME}-compile.log"
    COMPILE_EXIT=$?
    set -e

    CLASS_COUNT=$(find "$EXT_CLASSES_DIR" -name "*.class" 2>/dev/null | wc -l)

    if [ "$CLASS_COUNT" -gt 0 ]; then
        # Package JAR (in subshell to avoid changing CWD for next extension)
        JAR_PATH="${OUTPUT_DIR}/${JAR_NAME}"
        (cd "$EXT_CLASSES_DIR" && "${JAR_CMD}" cf "${JAR_PATH}" META-INF/extension.json $(find . -name '*.class' 2>/dev/null))

        JAR_SIZE=$(stat -f%z "$JAR_PATH" 2>/dev/null || echo "0")
        log "    ✅ ${JAR_NAME} (${CLASS_COUNT} classes, ${JAR_SIZE} bytes)"

        # Install to extensions directory
        mkdir -p "${EXTENSIONS_DIR}"
        cp "$JAR_PATH" "${EXTENSIONS_DIR}/${JAR_NAME}"

        # Compute SHA-256 for trust store (use Python to build JSON safely)
        if command -v shasum >/dev/null 2>&1; then
            JAR_HASH=$(shasum -a 256 "$JAR_PATH" | awk '{print $1}')
        elif command -v sha256sum >/dev/null 2>&1; then
            JAR_HASH=$(sha256sum "$JAR_PATH" | awk '{print $1}')
        else
            JAR_HASH=""
        fi
        if [ -n "$JAR_HASH" ]; then
            python3 -c "
import json
try:
    with open('${TRUST_DATA}') as f:
        data = json.load(f)
except:
    data = []
data.append({'pkgName': '${PKG}', 'versionCode': ${VERSION_CODE}, 'signatureHash': '${JAR_HASH}'})
with open('${TRUST_DATA}', 'w') as f:
    json.dump(data, f, separators=(',', ':'))
" 2>/dev/null || true
        fi

        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        SUCCESSFUL_PKGS="${SUCCESSFUL_PKGS}\n  ✅ ${JAR_NAME}"
    else
        log "    ❌ Compilation failed (exit=${COMPILE_EXIT})"
        sed 's/^/       /' "${TEMP_DIR}/${EXT_NAME}-compile.log" 2>/dev/null | head -5
        FAIL_COUNT=$((FAIL_COUNT + 1))
        FAILED_NAMES="${FAILED_NAMES}\n  ❌ ${EXT_NAME}"
    fi
done

# ---------------------------------------------------------------------------
# Step 4: Generate repo index
# ---------------------------------------------------------------------------
if [ "$SKIP_INDEX" = false ] && [ "$SUCCESS_COUNT" -gt 0 ]; then
    log ""
    log "Step 4: Generating repo index..."

    # Generate index.json with relative paths to JARs
    python3 -c "
import os, json

output_dir = '${OUTPUT_DIR}'
extensions_dir = '${EXTENSIONS_DIR}'
entries = []

for fname in os.listdir(output_dir):
    if not fname.endswith('.jar'):
        continue
    jar_path = os.path.join(output_dir, fname)
    if not os.path.isfile(jar_path):
        continue

    # Try to read extension.json from the JAR
    import zipfile
    try:
        with zipfile.ZipFile(jar_path) as zf:
            if 'META-INF/extension.json' in zf.namelist():
                meta = json.loads(zf.read('META-INF/extension.json'))
                entry = {
                    'name': meta.get('name', fname),
                    'pkgName': meta.get('pkgName', fname.replace('.jar', '')),
                    'versionName': meta.get('versionName', '1.0.0'),
                    'versionCode': meta.get('versionCode', 100),
                    'libVersion': meta.get('libVersion', 15.0),
                    'lang': meta.get('lang', ''),
                    'isNsfw': meta.get('isNsfw', False),
                    'isTorrent': meta.get('isTorrent', False),
                    'apk': fname,
                    'sourceClass': meta.get('sourceClass', ''),
                }
                entries.append(entry)
    except Exception:
        pass

with open(os.path.join(output_dir, 'index.min.json'), 'w') as f:
    json.dump(entries, f, separators=(',', ':'))

print('Generated index with ' + str(len(entries)) + ' entries')
" 2>&1 || true

    # Copy index to extensions dir
    cp "${OUTPUT_DIR}/index.min.json" "${EXTENSIONS_DIR}/macos-source-repo-index.json" 2>/dev/null || true
fi

# ---------------------------------------------------------------------------
# Step 5: Write trust store (merge with existing if present)
# ---------------------------------------------------------------------------
TRUST_DIR="${HOME}/Library/Application Support/Anikku/data/trust"
mkdir -p "$TRUST_DIR" 2>/dev/null || true
TRUST_FILE="${TRUST_DIR}/trusted_extensions.json"
if [ -f "$TRUST_DATA" ]; then
    python3 -c "
import json, os

new_path = '${TRUST_DATA}'
old_path = '${TRUST_FILE}'

with open(new_path) as f:
    new_entries = json.load(f)

# Merge with existing trust store if present
existing = []
if os.path.exists(old_path):
    try:
        with open(old_path) as f:
            existing = json.load(f)
    except:
        pass

# Build index by (pkgName, versionCode) to avoid duplicates
seen = set()
merged = []
for entry in existing + new_entries:
    key = (entry.get('pkgName', ''), entry.get('versionCode', 0))
    if key not in seen:
        seen.add(key)
        merged.append(entry)

with open(old_path, 'w') as f:
    json.dump(merged, f, separators=(',', ':'))

print(str(len(merged)))
" 2>&1
    TRUSTED_COUNT=$(python3 -c "import json; print(len(json.load(open('${TRUST_FILE}'))))" 2>/dev/null || echo "0")
    log "  Trusted ${TRUSTED_COUNT} extension(s) → ${TRUST_FILE}"
else
    log "  No trust data to write"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
log ""
log "╔══════════════════════════════════════════════════════════════╗"
log "║  BUILD COMPLETE"
log "╚══════════════════════════════════════════════════════════════╝"
log "  Language:       ${LANG}"
log "  Total:          ${TOTAL_COUNT}"
log "  ✅ Built:       ${SUCCESS_COUNT}"
log "  ❌ Failed:       ${FAIL_COUNT}"
log "  ⏭️  Skipped:    ${SKIPPED_COUNT}"
log ""
log "  Extensions installed to:"
log "    ${EXTENSIONS_DIR}"
log ""
log "  Repo index:"
log "    ${EXTENSIONS_DIR}/macos-source-repo-index.json"
log ""
if [ -n "$FAILED_NAMES" ]; then
    log "  Failed:${FAILED_NAMES}"
fi
log ""
log "  To add local repo to the app:"
log "    Browse → Extensions → Repos tab"
log "    Add: file://${EXTENSIONS_DIR}/macos-source-repo-index.json"
log "    Fetch → Install desired extensions"
log ""

# Cleanup
if [ "$KEEP_TEMP" != "true" ]; then
    log "Cleaning up temporary files..."
    rm -rf "${TEMP_DIR}"
fi

exit $FAIL_COUNT
