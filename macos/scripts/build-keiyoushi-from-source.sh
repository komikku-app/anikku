#!/usr/bin/env bash
#
# build-keiyoushi-from-source.sh
# =================================
# Builds a yuzono/anime-extensions extension from source as a JVM JAR for macOS.
#
# Uses git sparse-checkout to download the extension directory PLUS all
# shared library modules (lib-*) from the yuzono/anime-extensions repo,
# then compiles everything against the source-api JARs and Gradle-cached
# dependency JARs.
#
# This avoids android.* references that plague dex2jar-converted APKs.
#
# Usage:
#   ./build-keiyoushi-from-source.sh --pkg miruro --lang en
#
# Options:
#   --pkg <name>    Extension directory name (e.g., miruro, anikage, aniwave)
#   --lang <code>   Language code (default: en)
#   --keep-temp     Keep temporary files for debugging
#   --repo <url>    Git repo URL (default: https://github.com/yuzono/anime-extensions.git)
#   --help          Show this help
#
# Requirements:
#   - JDK 17+ with kotlinc (brew install kotlin)
#   - git, curl, python3
#   - Anikku source-api JARs (built by Gradle task rebuildSourceApiJars)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EXTENSIONS_DIR="${HOME}/Library/Application Support/Anikku/extensions"
TEMP_DIR="/tmp/anikku-source-build"
GIT_CLONE_DIR="${TEMP_DIR}/extensions-source"

# Default: yuzono/anime-extensions (contains actual anime extension sources)
# Keiyoushi/extensions-source contains ONLY manga extensions (HttpSource, not AnimeHttpSource)
REPO_URL="${REPO_URL:-https://github.com/yuzono/anime-extensions.git}"

SOURCE_API_JAR="${PROJECT_DIR}/libs/source-api-jvm.jar"
COMMON_JVM_JAR="${PROJECT_DIR}/libs/common-jvm.jar"

# Auto-detect JAVA_HOME
detect_java_home() {
    # Try common JDK locations
    for candidate in \
        "${JAVA_HOME}" \
        "/opt/homebrew/opt/openjdk@17" \
        "/opt/homebrew/opt/openjdk@21" \
        "/opt/homebrew/opt/openjdk" \
        "/usr/local/opt/openjdk@17" \
        "/usr/local/opt/openjdk" \
        "$(/usr/libexec/java_home 2>/dev/null || true)"; do
        if [ -n "$candidate" ] && [ -f "${candidate}/bin/javac" ]; then
            echo "$candidate"
            return 0
        fi
    done
    echo ""
    return 1
}

JAVA_HOME="$(detect_java_home)"
JAVA_CMD="${JAVA_HOME}/bin/java"
JAVAC_CMD="${JAVA_HOME}/bin/javac"
JAR_CMD="${JAVA_HOME}/bin/jar"

log() { echo "[*] $*"; }
err() { echo "[!] $*" >&2; }

cleanup() {
    if [ "${KEEP_TEMP:-false}" != "true" ]; then
        log "Cleaning up temporary files..."
        rm -rf "${TEMP_DIR}"
    else
        log "Keeping temporary files at: ${TEMP_DIR}"
    fi
}
trap cleanup EXIT

usage() {
    cat <<EOF
Usage: $(basename "$0") --pkg <name> [OPTIONS]

Build a keiyoushi anime extension from source as a JVM JAR.

Required:
  --pkg <name>     Extension directory name (e.g., miruro, anikage, aniwave)

Options:
  --lang <code>    Language code (default: en)
  --keep-temp      Keep temporary files for debugging
  --repo <url>     Git repo URL (default: https://github.com/yuzono/anime-extensions.git)
  --help           Show this help
EOF
    exit 0
}

# Parse arguments
PKG_NAME=""
LANG="en"
KEEP_TEMP=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --pkg) PKG_NAME="$2"; shift 2 ;;
        --lang) LANG="$2"; shift 2 ;;
        --keep-temp) KEEP_TEMP=true; shift ;;
        --repo) REPO_URL="$2"; shift 2 ;;
        --help|-h) usage ;;
        *) err "Unknown option: $1"; usage ;;
    esac
done

if [ -z "$PKG_NAME" ]; then
    err "Error: --pkg is required"
    usage
fi

# Validate prerequisites
if [ -z "$JAVA_HOME" ] || [ ! -f "$JAVA_CMD" ]; then
    err "JDK 17+ not found. Install: brew install openjdk@17"
    err "  Then: export JAVA_HOME=/opt/homebrew/opt/openjdk@17"
    exit 1
fi
if [ ! -f "$JAR_CMD" ]; then
    err "jar command not found at $JAR_CMD"
    exit 1
fi
# Verify jar actually runs (not just exists on disk)
if ! "$JAR_CMD" --version >/dev/null 2>&1; then
    err "jar command at $JAR_CMD fails to run. Check JDK installation."
    err "  Try: brew reinstall openjdk@17"
    exit 1
fi
log "jar: $("$JAR_CMD" --version 2>&1 | head -1)"
if [ ! -f "$SOURCE_API_JAR" ] || [ ! -f "$COMMON_JVM_JAR" ]; then
    err "source-api JARs not found at ${PROJECT_DIR}/libs/"
    err "Build them first: cd ${PROJECT_DIR} && ./gradlew rebuildSourceApiJars"
    exit 1
fi
if ! command -v kotlinc &>/dev/null; then
    err "kotlinc not found. Install: brew install kotlin"
    exit 1
fi

log "JAVA_HOME: ${JAVA_HOME}"
log "kotlinc: $(which kotlinc)"

log "Repo: ${REPO_URL}"

# ---------------------------------------------------------------------------
# Step 1: Download extension + shared lib source
# ---------------------------------------------------------------------------
log ""
log "╔══════════════════════════════════════════════════════════════╗"
log "║  BUILD: ${PKG_NAME} (lang: ${LANG})"
log "╚══════════════════════════════════════════════════════════════╝"
log ""

mkdir -p "${TEMP_DIR}"
if [ -d "${GIT_CLONE_DIR}" ]; then
    rm -rf "${GIT_CLONE_DIR}"
fi

log "Step 1: Downloading source via git sparse-checkout..."
git clone --depth 1 --filter=blob:none --no-checkout \
    "${REPO_URL}" "${GIT_CLONE_DIR}" 2>&1 | tail -2 || {
    err "Failed to clone ${REPO_URL}"
    err "Check that the URL is accessible"
    exit 1
}

cd "${GIT_CLONE_DIR}"

# Checkout extension directory and all shared libs
SHARED_LIBS=$(git ls-tree --name-only HEAD 2>/dev/null | grep -E '^(lib|core|common)' || true)
SPARSE_PATTERNS="src/${LANG}/${PKG_NAME}"
for lib in $SHARED_LIBS; do
    SPARSE_PATTERNS="$SPARSE_PATTERNS $lib"
done
# Also get gradle version catalog
SPARSE_PATTERNS="$SPARSE_PATTERNS gradle"

git sparse-checkout set $SPARSE_PATTERNS 2>&1
git checkout 2>&1 | tail -2

SRC_DIR="${GIT_CLONE_DIR}/src/${LANG}/${PKG_NAME}"
SRC_COUNT=$(find "${SRC_DIR}" -name "*.kt" -o -name "*.java" 2>/dev/null | wc -l)
SHARED_COUNT=$(ls -d "${GIT_CLONE_DIR}"/lib-*/ "${GIT_CLONE_DIR}"/lib-multisrc/*/ 2>/dev/null | wc -l)

log "Downloaded ${SRC_COUNT} source files, ${SHARED_COUNT} shared lib(s)"
for lib in "${GIT_CLONE_DIR}/lib-"*/ "${GIT_CLONE_DIR}/lib-multisrc/"*/; do
    [ -d "$lib" ] && log "  lib: $(basename "$lib") ($(find "$lib" -name '*.kt' | wc -l) files)"
done

if [ "$SRC_COUNT" -eq 0 ]; then
    err "No source files found for ${PKG_NAME} in language ${LANG}"
    err "Check available extensions: ls src/${LANG}/"
    exit 1
fi

# ---------------------------------------------------------------------------
# Step 2: Parse extension metadata
# ---------------------------------------------------------------------------
log ""
log "Step 2: Parsing extension metadata..."

BUILD_FILE=""
for candidate in "${SRC_DIR}/build.gradle.kts" "${SRC_DIR}/build.gradle" "${GIT_CLONE_DIR}/build.gradle.kts"; do
    if [ -f "$candidate" ]; then
        BUILD_FILE="$candidate"
        break
    fi
done

# Determine package name from source files
ACTUAL_PKG=$(grep -r '^package ' "${SRC_DIR}/src" 2>/dev/null | head -1 | sed 's/.*://' | sed 's/package //' | sed 's/[[:space:]]*$//' || echo "")
if [ -z "$ACTUAL_PKG" ]; then
    # Try source files directly in extension directory
    ACTUAL_PKG=$(find "${SRC_DIR}" -name "*.kt" -exec grep -l '^package ' {} \; 2>/dev/null | head -1 | xargs grep '^package ' | head -1 | sed 's/.*package //' | sed 's/[[:space:]]*$//' || echo "")
fi
if [ -z "$ACTUAL_PKG" ]; then
    ACTUAL_PKG="eu.kanade.tachiyomi.animeextension.${LANG}.${PKG_NAME}"
fi
JAR_NAME="${ACTUAL_PKG}.jar"

# Find main source class (the one that extends AnimeHttpSource or implements CatalogueSource/AnimeSource)
MAIN_SOURCE=$(find "${SRC_DIR}" -name "*.kt" -exec grep -l 'AnimeHttpSource\|AnimeCatalogueSource\|CatalogueSource' {} \; 2>/dev/null | head -1 || echo "")
if [ -z "$MAIN_SOURCE" ]; then
    # Try any class that has getVideoList or getEpisodeList (core source methods)
    MAIN_SOURCE=$(find "${SRC_DIR}" -name "*.kt" -exec grep -l 'getVideoList\|getEpisodeList\|getPopularAnime' {} \; 2>/dev/null | head -1 || echo "")
fi
if [ -z "$MAIN_SOURCE" ]; then
    # Fallback: largest file
    MAIN_SOURCE=$(find "${SRC_DIR}" -name "*.kt" -exec wc -l {} \; 2>/dev/null | sort -rn | head -1 | awk '{print $2}')
fi

if [ -n "$MAIN_SOURCE" ] && [ -f "$MAIN_SOURCE" ]; then
    CLASS_NAME=$(basename "$MAIN_SOURCE" .kt)
    FULL_CLASS_NAME="${ACTUAL_PKG}.${CLASS_NAME}"
else
    FULL_CLASS_NAME="${ACTUAL_PKG}.${PKG_NAME^}"
fi

log "  Package: ${ACTUAL_PKG}"
log "  Source class: ${FULL_CLASS_NAME}"

# Try to get version from build.gradle.kts if available
VERSION_CODE="100"
LIB_VERSION="15.0"
EXT_NAME="${PKG_NAME}"
NSFW="false"

if [ -n "$BUILD_FILE" ] && [ -f "$BUILD_FILE" ]; then
    # Write Python output to a temp file to avoid bash subshell variable scoping
    python3 -c "
import re, sys
with open('${BUILD_FILE}') as f:
    content = f.read()
m = re.search(r'keiyoushi\\s*\\{([^}]+)\\}', content, re.DOTALL)
if m:
    block = m.group(1)
    name_m = re.search(r'name\\s*=\\s*\"([^\"]+)\"', block)
    print(f'EXT_NAME={name_m.group(1) if name_m else \"${PKG_NAME}\"}')
    vc_m = re.search(r'versionCode\\s*=\\s*(\\d+)', block)
    print(f'VERSION_CODE={vc_m.group(1) if vc_m else \"100\"}')
    lib_m = re.search(r'libVersion\\s*=\\s*\"([^\"]+)\"', block)
    print(f'LIB_VERSION={lib_m.group(1) if lib_m else \"15.0\"}')
    nsfw = 'NSFW' in block.upper() or 'MIXED' in block.upper()
    print(f'NSFW={\"true\" if nsfw else \"false\"}')
else:
    print('EXT_NAME=${PKG_NAME}')
    print('VERSION_CODE=100')
    print('LIB_VERSION=15.0')
    print('NSFW=false')
" > "${TEMP_DIR}/extension-metadata.txt" 2>/dev/null || true

    # Read temp file into variables (avoids subshell scoping issue)
    while IFS='=' read -r key value; do
        case "$key" in
            EXT_NAME) EXT_NAME="$value" ;;
            VERSION_CODE) VERSION_CODE="$value" ;;
            LIB_VERSION) LIB_VERSION="$value" ;;
            NSFW) NSFW="$value" ;;
        esac
    done < "${TEMP_DIR}/extension-metadata.txt"
fi

log "  Name: ${EXT_NAME}"
log "  Version code: ${VERSION_CODE}"

# ---------------------------------------------------------------------------
# Step 3: Build classpath from Gradle cache
# ---------------------------------------------------------------------------
log ""
log "Step 3: Building classpath..."

CLASSPATH="${SOURCE_API_JAR}:${COMMON_JVM_JAR}"
GRADLE_CACHE="${HOME}/.gradle/caches/modules-2/files-2.1"

add_to_cp() {
    local item="$1"
    if [ -z "$item" ]; then return; fi
    if [ ! -e "$item" ]; then return; fi
    if echo "$CLASSPATH" | tr ':' '\n' | grep -Fxq "$item"; then
        return
    fi
    CLASSPATH="${CLASSPATH}:${item}"
}

# Prepend macOS Android stubs FIRST so they override any stale stubs in JARs
MACOS_CLASSES_DIR="${PROJECT_DIR}/build/classes/kotlin/main"
if [ -d "$MACOS_CLASSES_DIR" ]; then
    CLASSPATH="${MACOS_CLASSES_DIR}:${CLASSPATH}"
    log "  Android stubs (priority): ${MACOS_CLASSES_DIR} ✓"
fi

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

# Also scan macos/libs/ for shared JARs
if [ -d "${PROJECT_DIR}/libs" ]; then
    for j in "${PROJECT_DIR}"/libs/*.jar; do
        [ -f "$j" ] && add_to_cp "$j"
    done
fi

# Add compiled macOS module classes (Android stubs: android.*, androidx.*)
# Already prepended to CLASSPATH above for priority over JAR stubs.
# This fallback exists for builds where the macOS module wasn't pre-compiled.
if [ -d "$MACOS_CLASSES_DIR" ]; then
    if ! echo "$CLASSPATH" | tr ':' '\n' | grep -Fxq "$MACOS_CLASSES_DIR"; then
        add_to_cp "$MACOS_CLASSES_DIR"
        log "  Android stubs: ${MACOS_CLASSES_DIR} ✓"
    fi
fi

# Kotlin stdlib from brew
KOTLIN_LIB=$(brew --prefix kotlin 2>/dev/null || echo "/opt/homebrew/opt/kotlin")
if [ -d "$KOTLIN_LIB/libexec/lib" ]; then
    for j in "$KOTLIN_LIB"/libexec/lib/*.jar; do
        add_to_cp "$j"
    done
    log "  Kotlin stdlib: $(ls "$KOTLIN_LIB"/libexec/lib/*.jar 2>/dev/null | wc -l) JARs"
elif [ -d "$KOTLIN_LIB/libexec/libexec" ]; then
    # Some kotlin installations use different paths
    for j in "$KOTLIN_LIB"/libexec/libexec/*.jar; do
        add_to_cp "$j"
    done
    log "  Kotlin stdlib: found in alternative location"
fi

# Also check Gradle wrapper kotlin distribution
KOTLIN_PROJECT_DIR="${PROJECT_DIR}/.gradle"
if [ -d "$KOTLIN_PROJECT_DIR" ]; then
    for j in $(find "$KOTLIN_PROJECT_DIR" -name 'kotlin-stdlib-*.jar' 2>/dev/null | head -5); do
        add_to_cp "$j"
    done
fi

# Common extension dependencies (from Gradle cache)
log "  Resolving dependencies..."
find_dep "org.jetbrains.kotlinx" "kotlinx-coroutines-core-jvm" && log "    coroutines ✓"
find_dep "org.jetbrains.kotlinx" "kotlinx-serialization-json-jvm" && log "    serialization ✓"
find_dep "org.jetbrains.kotlinx" "kotlinx-serialization-core-jvm" && log "    serialization-core ✓"
find_dep "org.jetbrains.kotlinx" "kotlinx-serialization-protobuf-jvm" && log "    serialization-protobuf ✓" || true
find_dep "com.squareup.okhttp3" "okhttp-jvm" || find_dep "com.squareup.okhttp3" "okhttp" && log "    okhttp ✓"
find_dep "com.squareup.okio" "okio-jvm" && log "    okio ✓"
find_dep "org.jsoup" "jsoup" && log "    jsoup ✓"
find_dep "io.reactivex" "rxjava" && log "    rxjava ✓"
find_dep "com.github.mihonapp" "injekt" && log "    injekt ✓"
find_dep "uy.kohesive.injekt" "injekt-api" && log "    injekt-api ✓"
find_dep "uy.kohesive.injekt" "injekt-core" && log "    injekt-core ✓"
find_dep "com.fasterxml.jackson.core" "jackson-core" && log "    jackson-core ✓"
find_dep "com.fasterxml.jackson.core" "jackson-databind" && log "    jackson-databind ✓"
find_dep "com.google.code.gson" "gson" && log "    gson ✓"
find_dep "org.jetbrains" "kotlin-reflect" && log "    kotlin-reflect ✓" || true
find_dep "com.squareup.okhttp3" "logging-interceptor" && log "    okhttp-logging ✓" || true
find_dep "com.squareup.okhttp3" "okhttp-brotli" && log "    okhttp-brotli ✓" || true
find_dep "app.cash.quickjs" "quickjs-jvm" && log "    quickjs-jvm ✓" || true

# Kotlinx-serialization compiler plugin (required for @Serializable .serializer() methods)
SERIALIZATION_PLUGIN="${KOTLIN_LIB}/libexec/lib/kotlinx-serialization-compiler-plugin.jar"
if [ ! -f "$SERIALIZATION_PLUGIN" ]; then
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
# Step 3b: Compile shared library modules (in dependency order)
# ---------------------------------------------------------------------------
log ""
SHARED_LIBS_DIR="${TEMP_DIR}/shared-libs-classes"

# Step 3b-i: Compile keiyoushi-utils FIRST (provides keiyoushi.utils.* needed by lib/extractors)
KEIYOUSHI_UTILS_DIR="${PROJECT_DIR}/keiyoushi-utils/src/main/kotlin"
if [ -d "$KEIYOUSHI_UTILS_DIR" ]; then
    UTILS_NAME="keiyoushi-utils"
    UTILS_CLASSES="${SHARED_LIBS_DIR}/${UTILS_NAME}"
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
            log "Compiling: ${UTILS_NAME} (${utils_src_count} files, pure JVM port)..."
            mkdir -p "$UTILS_CLASSES"
            set +e
            kotlinc -cp "${CLASSPATH}" -d "$UTILS_CLASSES" -jvm-target 17 ${KOTLINC_OPTS} @"${TEMP_DIR}/${UTILS_NAME}-sources.txt" 2>"${TEMP_DIR}/${UTILS_NAME}-compile.log"
            utils_exit=$?
            set -e
            utils_class_count=$(find "$UTILS_CLASSES" -name "*.class" 2>/dev/null | wc -l)
            if [ "$utils_class_count" -gt 0 ]; then
                add_to_cp "$UTILS_CLASSES"
                log "  -> ${utils_class_count} classes ✓"
            else
                log "  -> FAILED: $(head -3 "${TEMP_DIR}/${UTILS_NAME}-compile.log" 2>/dev/null)"
            fi
        fi
    else
        add_to_cp "$UTILS_CLASSES"
        cached_count=$(find "$UTILS_CLASSES" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        log "${UTILS_NAME} already compiled (${cached_count} cached classes) ✓"
    fi
fi

# Step 3b-ii: Compile lib/*/ extractor modules (aniyomi.lib.* package)
# Must compile AFTER keiyoushi-utils since several extractors import keiyoushi.utils.*
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
        # WHITELIST: only compile extractors needed by target extensions.
        EXTRACTOR_WHITELIST=(
            "doodextractor"
            "filemoonextractor"
            "gogostreamextractor"
            "mp4uploadextractor"
            "okruextractor"
            "playlistutils"
            "streamlareextractor"
            "streamwishextractor"
            "vidhideextractor"
            "vidmolyextractor"
            "anilib"
            "burstcloudextractor"
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
            log "Compiling: lib/extractors (${extractor_count} files, aniyomi.lib.*)..."
            mkdir -p "$EXTRACTORS_OUT"
            set +e
            kotlinc -cp "${CLASSPATH}" -d "$EXTRACTORS_OUT" -jvm-target 17 ${KOTLINC_OPTS} @"${TEMP_DIR}/lib-extractors-sources.txt" 2>"${TEMP_DIR}/lib-extractors-compile.log"
            extractor_exit=$?
            set -e
            extractor_class_count=$(find "$EXTRACTORS_OUT" -name '*.class' 2>/dev/null | wc -l)
            if [ "$extractor_class_count" -gt 0 ]; then
                add_to_cp "$EXTRACTORS_OUT"
                log "  -> ${extractor_class_count} classes ✓"
            else
                log "  -> FAILED: $(head -3 "${TEMP_DIR}/lib-extractors-compile.log" 2>/dev/null)"
            fi
        fi
    else
        add_to_cp "$EXTRACTORS_OUT"
        cached_count=$(find "$EXTRACTORS_OUT" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        log "lib/extractors already compiled (${cached_count} cached classes) ✓"
    fi
fi

# Step 3b-iii: Patch core module source files for JVM compatibility
# ===================================================================
# The cloned repo's core/ module has Preferences.kt with Android-specific
# calls (setDefaultValue, setEnabled) and Coroutines.kt with overload
# ambiguity that conflict with the macOS keiyoushi-utils.

# Patch Coroutines.kt: fix parallelMapNotNull overload ambiguity
# Kotlin compiler generates a synthetic non-inline overload for inline suspend
# fns with generic params, causing ambiguity between macOS keiyoushi-utils
# version and the cloned repo's version.
CORE_COROUTINES="${GIT_CLONE_DIR}/core/src/main/kotlin/keiyoushi/utils/Coroutines.kt"
if [ -f "$CORE_COROUTINES" ] && grep -q 'suspend inline fun.*parallelMapNotNull' "$CORE_COROUTINES" 2>/dev/null; then
    # Remove inline+crossinline from parallelMapNotNull
    sed -i '' '/parallelMapNotNull(crossinline/s/ inline / /' "$CORE_COROUTINES" 2>/dev/null || true
    sed -i '' '/parallelMapNotNull(crossinline/s/(crossinline /(/' "$CORE_COROUTINES" 2>/dev/null || true
    sed -i '' '/parallelMapNotNull(f:/s/<A, B>/<A, B : Any>/' "$CORE_COROUTINES" 2>/dev/null || true
    log "  Patched: parallelMapNotNull — removed inline+crossinline, added B : Any"

    # Patch parallelMapNotNullBlocking: remove inline+crossinline
    sed -i '' '/parallelMapNotNullBlocking(crossinline/s/ inline / /' "$CORE_COROUTINES" 2>/dev/null || true
    sed -i '' '/parallelMapNotNullBlocking(crossinline/s/^inline //' "$CORE_COROUTINES" 2>/dev/null || true
    sed -i '' '/parallelMapNotNullBlocking(crossinline/s/(crossinline /(/' "$CORE_COROUTINES" 2>/dev/null || true
    log "  Patched: parallelMapNotNullBlocking — removed inline+crossinline"

    # Patch parallelCatchingFlatMap: remove inline+crossinline
    sed -i '' '/parallelCatchingFlatMap(crossinline/s/ inline / /' "$CORE_COROUTINES" 2>/dev/null || true
    sed -i '' '/parallelCatchingFlatMap(crossinline/s/(crossinline /(/' "$CORE_COROUTINES" 2>/dev/null || true
    log "  Patched: parallelCatchingFlatMap — removed inline+crossinline"

    # Patch parallelCatchingFlatMapBlocking: remove inline+crossinline
    sed -i '' '/parallelCatchingFlatMapBlocking(crossinline/s/ inline / /' "$CORE_COROUTINES" 2>/dev/null || true
    sed -i '' '/parallelCatchingFlatMapBlocking(crossinline/s/^inline //' "$CORE_COROUTINES" 2>/dev/null || true
    sed -i '' '/parallelCatchingFlatMapBlocking(crossinline/s/(crossinline /(/' "$CORE_COROUTINES" 2>/dev/null || true
    log "  Patched: parallelCatchingFlatMapBlocking — removed inline+crossinline"
fi

# Patch Preferences.kt: remove Android-specific calls and fix nullability
CORE_PREFS="${GIT_CLONE_DIR}/core/src/main/kotlin/keiyoushi/utils/Preferences.kt"
if [ -f "$CORE_PREFS" ]; then
    # Remove setDefaultValue() calls (no-ops on JVM)
    sed -i '' '/setDefaultValue(/d' "$CORE_PREFS" 2>/dev/null || true
    # Remove setEnabled() calls (no-ops on JVM)
    sed -i '' '/setEnabled(/d' "$CORE_PREFS" 2>/dev/null || true
    # Fix Context? nullability in Toast.makeText
    sed -i '' 's/Toast\.makeText(context,/Toast.makeText(context!!,/g' "$CORE_PREFS" 2>/dev/null || true
    log "  Patched: Preferences.kt — removed setDefaultValue/setEnabled, fixed context!!"
fi

# Patch DataLifeEngine: fix entryValues null safety
DATA_LIFE_ENGINE="${GIT_CLONE_DIR}/lib-multisrc/datalifeengine/src/eu/kanade/tachiyomi/multisrc/datalifeengine/DataLifeEngine.kt"
if [ -f "$DATA_LIFE_ENGINE" ] && grep -q 'putString(key' "$DATA_LIFE_ENGINE" 2>/dev/null; then
    sed -i '' 's/putString(key, entry)/putString(key!!, entry!!)/g' "$DATA_LIFE_ENGINE"
    log "  Patched: DataLifeEngine.kt — key!! and entry!! in putString"
fi

# Patch DooPlay: fix entryValues null safety
DOO_PLAY="${GIT_CLONE_DIR}/lib-multisrc/dooplay/src/eu/kanade/tachiyomi/multisrc/dooplay/DooPlay.kt"
if [ -f "$DOO_PLAY" ] && grep -q 'putString(key' "$DOO_PLAY" 2>/dev/null; then
    sed -i '' 's/putString(key, entry)/putString(key!!, entry!!)/g' "$DOO_PLAY"
    log "  Patched: DooPlay.kt — key!! and entry!! in putString"
fi

# Patch DopeFlix: MutableSet property delegate type mismatch
DOPE_FLIX="${GIT_CLONE_DIR}/lib-multisrc/dopeflix/src/eu/kanade/tachiyomi/multisrc/dopeflix/DopeFlix.kt"
if [ -f "$DOPE_FLIX" ] && grep -q 'hosterNames.toSet())!!' "$DOPE_FLIX" 2>/dev/null; then
    sed -i '' '/hosterNames.toSet())!!/s/!! }/!!.toMutableSet() }/' "$DOPE_FLIX"
    log "  Patched: DopeFlix.kt — MutableSet<String> via .toMutableSet()"
fi

# ===================================================================
# Step 3b-iv: Compile shared library modules (lib-*, lib-multisrc/*, common, core) in dependency order
for lib_dir in "${GIT_CLONE_DIR}"/lib-*/ "${GIT_CLONE_DIR}"/lib-multisrc/*/ "${GIT_CLONE_DIR}/common/" "${GIT_CLONE_DIR}/core/"; do
    [ ! -d "$lib_dir" ] && continue
    lib_name=$(basename "$lib_dir")
    lib_classes="${SHARED_LIBS_DIR}/${lib_name}"
    # Skip only if directory has actual class files (retry if cached empty/broken)
    if [ -d "$lib_classes" ]; then
        cached_classes=$(find "$lib_classes" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        [ "$cached_classes" -gt 0 ] && continue
        rm -rf "$lib_classes"
    fi

    # Exclude test files AND Android-specific Activity files (UrlActivity) —
    # UrlActivities subclass android.app.Activity and have no purpose on macOS/JVM.
    find "$lib_dir" -name "*.kt" ! -path '*/test/*' ! -name '*UrlActivity*' > "${TEMP_DIR}/${lib_name}-sources.txt" 2>/dev/null || true
    src_count=$(wc -l < "${TEMP_DIR}/${lib_name}-sources.txt" 2>/dev/null || echo 0)
    [ "$src_count" -eq 0 ] && continue

    log "Compiling shared lib: ${lib_name}..."
    mkdir -p "$lib_classes"

    set +e
    kotlinc -cp "${CLASSPATH}" -d "$lib_classes" -jvm-target 17 ${KOTLINC_OPTS} @"${TEMP_DIR}/${lib_name}-sources.txt" 2>"${TEMP_DIR}/${lib_name}-compile.log"
    local_exit=$?
    set -e

    class_count=$(find "$lib_classes" -name "*.class" 2>/dev/null | wc -l)

    if [ "$class_count" -gt 0 ]; then
        add_to_cp "$lib_classes"
        log "  -> ${class_count} classes ✓"
    elif [ "$local_exit" -ne 0 ]; then
        log "  -> WARNING: compilation failed (${local_exit}) - $(head -1 "${TEMP_DIR}/${lib_name}-compile.log" 2>/dev/null)"
    fi
done

# ---------------------------------------------------------------------------
# Step 3b-v: Apply source patches for specific extensions
# ---------------------------------------------------------------------------

# Patch: anidb — fix extension function hiding supertype member
ANIDB_SRC=$(find "${SRC_DIR}" -name "AniDB.kt" 2>/dev/null | head -1)
if [ -n "$ANIDB_SRC" ] && [ -f "$ANIDB_SRC" ]; then
    # 1. sortVideos extension function conflicts with supertype member.
    #    Remove 'private' so it can be marked 'override' (private+override invalid)
    sed -i '' 's/private fun List<Video>.sortVideos/override fun List<Video>.sortVideos/' "$ANIDB_SRC" 2>/dev/null || true
    # 1b. Remove 'override' from disableRelatedAnimesBySearch (not in our JVM stubs)
    sed -i '' 's/override val disableRelatedAnimesBySearch/val disableRelatedAnimesBySearch/' "$ANIDB_SRC" 2>/dev/null || true
    # 2. Remove setDefaultValue() calls on SwitchPreferenceCompat (not in JVM stubs)
    sed -i '' '/setDefaultValue(/d' "$ANIDB_SRC" 2>/dev/null || true
    log "Patched: AniDB.kt — sortVideos override + setDefaultValue removal"
fi

# Patch: miruro — keep AniLib import (anilib extractor is on classpath)
# The anilib extractor is compiled in Step 3b-ii and provides AniLib class
# on the classpath. Removing the import causes unresolved reference errors
# (27 AniLib references throughout Miruro.kt). The import stays intact.
MIRURO_SRC=$(find "${SRC_DIR}" -name "Miruro.kt" 2>/dev/null | head -1)
if [ -n "$MIRURO_SRC" ] && [ -f "$MIRURO_SRC" ]; then
    log "  NOTE: Miruro.kt uses AniLib via import — anilib extractor is on classpath ✓"
fi

# Patch: superstream — add CloudflareInterceptor to custom OkHttpClient
# The extension creates its own OkHttpClient (configureToIgnoreCertificate) without
# CloudflareInterceptor, causing Cloudflare 403 blocks → JsonDecodingException.
SUPERSTREAM_API_SRC=$(find "${GIT_CLONE_DIR}/src/en/superstream" -name "SuperStreamAPI.kt" 2>/dev/null | head -1)
if [ -n "$SUPERSTREAM_API_SRC" ] && [ -f "$SUPERSTREAM_API_SRC" ]; then
    sed -i '' '/^import okhttp3\.OkHttpClient$/a\
import app.anikku.macos.platform.network.CloudflareInterceptor\
import app.anikku.macos.platform.network.MacOSCookieJar\
' "$SUPERSTREAM_API_SRC" 2>/dev/null || true
    sed -i '' '/\.readTimeout(70, TimeUnit\.SECONDS)/a\
            .addInterceptor(CloudflareInterceptor(MacOSCookieJar(java.io.File.createTempFile("cf_", "jar"))) { "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" })\
' "$SUPERSTREAM_API_SRC" 2>/dev/null || true
    log "Patched: SuperStreamAPI.kt — added CloudflareInterceptor to custom OkHttpClient"
fi

# Patch: superstream — JVM compatibility (setDefaultValue + null-safe context)
SUPERSTREAM_SRC=$(find "${GIT_CLONE_DIR}/src/en/superstream" -name "SuperStream.kt" 2>/dev/null | head -1)
if [ -n "$SUPERSTREAM_SRC" ] && [ -f "$SUPERSTREAM_SRC" ]; then
    # Remove setDefaultValue() calls (not available in JVM Preference stubs)
    sed -i '' '/setDefaultValue(/d' "$SUPERSTREAM_SRC" 2>/dev/null || true
    # screen.context is Context? in JVM stubs but used as Context — add !!
    sed -i '' 's/screen\.context)/screen.context!!)/g' "$SUPERSTREAM_SRC" 2>/dev/null || true
    sed -i '' 's/\.makeText(screen\.context,/.makeText(screen.context!!,/g' "$SUPERSTREAM_SRC" 2>/dev/null || true
    log "Patched: SuperStream.kt — removed setDefaultValue, added context!! null-safety"
fi

# ---------------------------------------------------------------------------
# Step 4: Compile the extension
# ---------------------------------------------------------------------------
log ""
log "Step 4: Compiling extension..."

CLASSES_DIR="${TEMP_DIR}/classes"
mkdir -p "$CLASSES_DIR"

# Find all source files (extension source + any shared libs not separately compiled)
find "${SRC_DIR}" -name "*.kt" > "${TEMP_DIR}/kotlin-sources.txt" 2>/dev/null
find "${SRC_DIR}" -name "*.java" >> "${TEMP_DIR}/kotlin-sources.txt" 2>/dev/null
KT_COUNT=$(wc -l < "${TEMP_DIR}/kotlin-sources.txt" 2>/dev/null || echo 0)
log "Sources: ${KT_COUNT} Kotlin/Java files"

# Create META-INF/extension.json
mkdir -p "${CLASSES_DIR}/META-INF"
cat > "${CLASSES_DIR}/META-INF/extension.json" << JSONEOF
{
  "name": "Aniyomi: ${EXT_NAME}",
  "pkgName": "${ACTUAL_PKG}",
  "versionName": "1.0.0",
  "versionCode": ${VERSION_CODE},
  "libVersion": ${LIB_VERSION:-15.0},
  "lang": "${LANG}",
  "isNsfw": ${NSFW:-false},
  "isTorrent": false,
  "sourceClass": "${FULL_CLASS_NAME}",
  "pkgFactory": null,
  "hasReadme": false,
  "hasChangelog": false
}
JSONEOF

set +e
kotlinc -cp "${CLASSPATH}" -d "${CLASSES_DIR}" -jvm-target 17 ${KOTLINC_OPTS} @"${TEMP_DIR}/kotlin-sources.txt" 2>"${TEMP_DIR}/compile-err.log"
COMPILE_EXIT=$?
set -e

CLASS_COUNT=$(find "$CLASSES_DIR" -name "*.class" 2>/dev/null | wc -l)
log "Compile exit: ${COMPILE_EXIT}, classes: ${CLASS_COUNT}"

if [ "$CLASS_COUNT" -eq 0 ]; then
    err "Compilation failed!"
    err "Check ${TEMP_DIR}/compile-err.log for details."
    sed 's/^/  /' "${TEMP_DIR}/compile-err.log" 2>/dev/null | head -30
    KEEP_TEMP=true
    exit 1
fi

# ---------------------------------------------------------------------------
# Step 5: Package as JAR
# ---------------------------------------------------------------------------
log ""
log "Step 5: Packaging JAR..."

JAR_PATH="${TEMP_DIR}/${JAR_NAME}"

# Merge only extension-specific shared libraries (NOT keiyoushi-utils which
# the app already provides). Only include successfully compiled modules.
if [ -d "$SHARED_LIBS_DIR" ]; then
    MERGED=0
    for mod in "$SHARED_LIBS_DIR"/*/; do
        [ ! -d "$mod" ] && continue
        mod_name=$(basename "$mod")
        # Skip app-provided modules (keiyoushi-utils) and failed/empty modules
        case "$mod_name" in
            "keiyoushi-utils"|"lib-extractors") continue ;;
        esac
        class_cnt=$(find "$mod" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        if [ "$class_cnt" -gt 0 ]; then
            cp -r "$mod"/* "$CLASSES_DIR/" 2>/dev/null || true
            MERGED=$((MERGED + class_cnt))
        fi
    done
    log "Merged shared libs: ${MERGED} classes"
fi

# Also include CloudflareInterceptor and MacOSCookieJar from the macOS module.
# These provide CDP-based Cloudflare bypass for extensions like superstream
# that create their own OkHttpClient (bypassing the app's CloudflareInterceptor).
if [ -d "$MACOS_CLASSES_DIR" ]; then
    for macos_class in CloudflareInterceptor MacOSCookieJar ChromeCDPClient FallbackDns; do
        find "$MACOS_CLASSES_DIR" -path "*/app/anikku/macos/platform/network/${macos_class}*" -name '*.class' 2>/dev/null | while IFS= read -r f; do
            rel="${f#$MACOS_CLASSES_DIR/}"
            mkdir -p "$CLASSES_DIR/$(dirname "$rel")"
            cp "$f" "$CLASSES_DIR/$rel"
        done
    done
    log "Included: CloudflareInterceptor + MacOSCookieJar + ChromeCDPClient + FallbackDns from macOS module ✓"
fi

cd "$CLASSES_DIR"

# Verify extension.json exists
if [ ! -f "META-INF/extension.json" ]; then
    err "META-INF/extension.json missing!"
    exit 1
fi

# Count classes and package with $JAVA_HOME/bin/jar explicitly.
# Primary: JDK 9+ @filelist (reads arguments from file -- no command-line limit).
# Fallback: macOS xargs pipe (find -print0 | xargs -0) if @filelist somehow fails.
CLASS_COUNT=$(find . -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
log "Packaging ${CLASS_COUNT} classes with ${JAR_CMD}..."

# Build file list: META-INF/extension.json first, then all .class files
{
    echo "META-INF/extension.json"
    find . -name '*.class' -print
} > "${TEMP_DIR}/jar-classes.txt"

set +e
# --- Primary: @filelist (JDK 9+). Handles unlimited files without shell limits. ---
"${JAR_CMD}" cf "${JAR_PATH}" @"${TEMP_DIR}/jar-classes.txt" 2>"${TEMP_DIR}/jar-err.log"
JAR_EXIT=$?

# --- Fallback: macOS xargs pipe (if @filelist fails for any reason) ---
if [ "$JAR_EXIT" -ne 0 ]; then
    log "@filelist failed (exit ${JAR_EXIT}), retrying with xargs pipe..."
    # ⚠ If xargs splits into multiple jar cf invocations, only the last batch
    # survives (each cf overwrites the JAR). For <1000 files this won't trigger
    # (~60KB paths vs 256KB ARG_MAX). Safe as fallback; @filelist always works on JDK 17+.
    find . -name '*.class' -print0 | xargs -0 "${JAR_CMD}" cf "${JAR_PATH}" 2>>"${TEMP_DIR}/jar-err.log"
    JAR_EXIT=$?
fi
set -e

if [ "$JAR_EXIT" -ne 0 ]; then
    err "jar packaging failed (exit ${JAR_EXIT}): $(cat "${TEMP_DIR}/jar-err.log" 2>/dev/null | head -5)"
    err "  JAR_CMD=${JAR_CMD}"
    err "  JAVA_HOME=${JAVA_HOME}"
    err "  Classes: ${CLASS_COUNT}"
    err "  File list: $(wc -l < "${TEMP_DIR}/jar-classes.txt") entries"
    exit 1
fi

JAR_SIZE=$(stat -f%z "${JAR_PATH}" 2>/dev/null || echo "0")
log "JAR: ${JAR_PATH} (${JAR_SIZE} bytes, ${CLASS_COUNT} classes)"

# Verify JAR contains extension.json
if ! "${JAR_CMD}" tf "${JAR_PATH}" 2>/dev/null | grep -q 'extension.json'; then
    err "JAR is missing META-INF/extension.json!"
    err "  JAR_CMD=${JAR_CMD}"
    exit 1
fi

# ---------------------------------------------------------------------------
# Step 6: Install
# ---------------------------------------------------------------------------
log ""
log "Step 6: Installing..."

mkdir -p "${EXTENSIONS_DIR}"
cp "${JAR_PATH}" "${EXTENSIONS_DIR}/${JAR_NAME}"
log "Installed: ${EXTENSIONS_DIR}/${JAR_NAME}"

# ---------------------------------------------------------------------------
log ""
log "╔══════════════════════════════════════════════════════════════╗"
log "║  BUILD COMPLETE!"
log "║  ${EXT_NAME} (${ACTUAL_PKG})"
log "║  ${CLASS_COUNT} classes, ${JAR_SIZE} bytes"
log "║  Source class: ${FULL_CLASS_NAME}"
log "╚══════════════════════════════════════════════════════════════╝"
log ""
log "  Restart Anikku or use Browse → Extensions to see it."
log "  Trust the extension on first use."
