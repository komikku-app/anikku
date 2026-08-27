#!/usr/bin/env bash
#
# convert-keiyoushi-extension.sh
# ==============================
# Converts an Android APK extension to a macOS-compatible JAR using dex2jar.
#
# This script:
# 1. Downloads an extension APK (or uses an existing one)
# 2. Extracts extension metadata from the APK
# 3. Converts DEX bytecode directly to JVM class files using dex2jar
#    (avoids decompilation+recompilation issues with R8-obfuscated code)
# 4. Detects source classes from the converted JAR
# 5. Generates META-INF/extension.json with proper metadata
# 6. Places the JAR in the Anikku extensions directory
#
# Usage:
#   ./convert-keiyoushi-extension.sh [--apk <path>] [--pkg <name>]
#
# Examples:
#   # Download and convert allanime from salmanbappi repo
#   ./convert-keiyoushi-extension.sh --pkg allanime
#
#   # Convert an existing APK file
#   ./convert-keiyoushi-extension.sh --apk ~/Downloads/gogocdn.apk
#
# Requirements:
#   - dex2jar (brew install dex2jar)
#   - JDK 17+ (for jar command)
#   - curl, unzip, python3
#
# Why dex2jar instead of jadx+recompile:
#   Android APKs are typically R8/proguard-obfuscated, producing
#   decompiled Java source that won't recompile. dex2jar converts
#   the DEX bytecode directly to JVM .class files, preserving all
#   method bodies and class structure. The resulting JAR references
#   Android API classes (android.*) at the bytecode level, which
#   MacOSExtensionLoader handles gracefully by catching
#   NoClassDefFoundError at runtime.

set -euo pipefail 2>/dev/null || set -eu  # pipefail supported on bash 4+; macOS default bash 3.2 falls back

# ──────────────────────────────────────────────────────────────────────────────
# Configuration
# ──────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EXTENSIONS_DIR="${HOME}/Library/Application Support/Anikku/extensions"
TEMP_DIR="/tmp/anikku-extension-convert"

# Extension repos
SALMANBAPPI_INDEX="https://raw.githubusercontent.com/salmanbappi/extensions-repo/main/index.min.json"
SALMANBAPPI_APK_BASE="https://raw.githubusercontent.com/salmanbappi/extensions-repo/main/apk"
# keiyoushi/extensions is a primarily-manga APK repo (not anime source).
# For anime extensions, use yuzono/anime-extensions with build-keiyoushi-from-source.sh
# or use the pre-converted JARs from the Anikku macOS Extensions repo.
KEIYOUSHI_INDEX="https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"
KEIYOUSHI_APK_BASE="https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk"

# >>> PREFERRED: Build from anime source instead <<<
# Use macos/scripts/build-keiyoushi-from-source.sh --pkg <name>
# This clones yuzono/anime-extensions and compiles clean JVM JARs,
# avoiding the Android APK→dex2jar conversion entirely.

D2J_DEX2JAR="$(which d2j-dex2jar 2>/dev/null || echo "/opt/homebrew/bin/d2j-dex2jar")"
# Find jar/javac/javap commands — search common JDK locations on macOS
JAR_CMD=""
JAVAC_CMD=""
JAVAP_CMD=""
for cmd in jar javac javap; do
    for candidate in \
        "${JAVA_HOME}/bin/$cmd" \
        "/opt/homebrew/opt/openjdk@17/bin/$cmd" \
        "/opt/homebrew/opt/openjdk@21/bin/$cmd" \
        "/opt/homebrew/opt/openjdk/bin/$cmd" \
        "/usr/bin/$cmd" \
        $(which $cmd 2>/dev/null || true); do
        if [ -n "$candidate" ] && [ -f "$candidate" ]; then
            case "$cmd" in
                jar) JAR_CMD="$candidate" ;;
                javac) JAVAC_CMD="$candidate" ;;
                javap) JAVAP_CMD="$candidate" ;;
            esac
            break
        fi
    done
done

# ──────────────────────────────────────────────────────────────────────────────
# Functions
# ──────────────────────────────────────────────────────────────────────────────

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
Usage: $(basename "$0") [OPTIONS]

Convert an Android extension APK to a macOS JAR extension.

Options:
  --apk <path>    Path to an existing APK file
  --pkg <name>    Package name keyword to download (e.g., allanime, gogocdn)
  --lang <code>   Language filter for auto-download (default: en)
  --from-repo <r> Repo source: salmanbappi (default) or keiyoushi
  --keep-temp     Keep temporary files for debugging
  --help          Show this help

Examples:
  ./convert-keiyoushi-extension.sh --pkg allanime
  ./convert-keiyoushi-extension.sh --pkg allanime --from-repo keiyoushi
  ./convert-keiyoushi-extension.sh --apk ~/Downloads/extension.apk
EOF
    exit 0
}

# ──────────────────────────────────────────────────────────────────────────────
# Parse arguments
# ──────────────────────────────────────────────────────────────────────────────

APK_PATH=""
PKG_NAME=""
LANG_FILTER="en"
REPO_SOURCE="salmanbappi"
KEEP_TEMP=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --apk) APK_PATH="$2"; shift 2 ;;
        --pkg) PKG_NAME="$2"; shift 2 ;;
        --lang) LANG_FILTER="$2"; shift 2 ;;
        --from-repo) REPO_SOURCE="$2"; shift 2 ;;
        --keep-temp) KEEP_TEMP=true; shift ;;
        --help|-h) usage ;;
        *) err "Unknown option: $1"; usage ;;
    esac
done

# ──────────────────────────────────────────────────────────────────────────────
# Prerequisites check
# ──────────────────────────────────────────────────────────────────────────────

log "Checking prerequisites..."

if [ ! -f "$D2J_DEX2JAR" ]; then
    D2J_DEX2JAR="/opt/homebrew/Cellar/dex2jar/*/bin/d2j-dex2jar"
    D2J_DEX2JAR=$(ls $D2J_DEX2JAR 2>/dev/null | head -1 || echo "")
fi
if [ ! -f "$D2J_DEX2JAR" ]; then
    err "d2j-dex2jar not found. Install: brew install dex2jar"
    exit 1
fi

if [ -z "$JAR_CMD" ]; then
    err "jar command not found. Ensure JDK 17+ is installed."
    err "Try: export JAVA_HOME=/opt/homebrew/opt/openjdk@17"
    exit 1
fi

log "  d2j-dex2jar: ${D2J_DEX2JAR}"
log "  jar: ${JAR_CMD}"

# ──────────────────────────────────────────────────────────────────────────────
# Step 1: Get the APK
# ──────────────────────────────────────────────────────────────────────────────

mkdir -p "${TEMP_DIR}"

if [ -n "$APK_PATH" ]; then
    log "Using existing APK: ${APK_PATH}"
    if [ ! -f "$APK_PATH" ]; then
        err "APK file not found: ${APK_PATH}"
        exit 1
    fi
    cp "$APK_PATH" "${TEMP_DIR}/extension.apk"
else
    log "Fetching extension index from ${REPO_SOURCE}..."
    if [ "$REPO_SOURCE" = "keiyoushi" ]; then
        INDEX_URL="$KEIYOUSHI_INDEX"
        APK_BASE="$KEIYOUSHI_APK_BASE"
    else
        INDEX_URL="$SALMANBAPPI_INDEX"
        APK_BASE="$SALMANBAPPI_APK_BASE"
    fi

    INDEX=$(curl -sL --connect-timeout 15 "$INDEX_URL" 2>/dev/null || echo "[]")
    EXT_COUNT=$(echo "$INDEX" | python3 -c "import sys,json; data=json.load(sys.stdin); print(len(data))" 2>/dev/null || echo "0")
    log "  Found ${EXT_COUNT} extensions in index"

    log "Finding extension matching '${PKG_NAME}' (lang: ${LANG_FILTER})..."

    EXT_JSON=$(echo "$INDEX" | python3 -c "
import sys, json
data = json.load(sys.stdin)
pkg_filter = '${PKG_NAME}'.lower()
lang_filter = '${LANG_FILTER}'
found = []
for ext in data:
    pkg = ext.get('pkg', '').lower()
    name = ext.get('name', '').lower()
    apk = ext.get('apk', '').lower()
    lang = ext.get('lang', '')
    if pkg_filter and (pkg_filter in pkg or pkg_filter in name or pkg_filter in apk):
        if lang_filter and lang != lang_filter:
            continue
        found.append(ext)
        break
if not found and pkg_filter:
    # Broader match
    for ext in data:
        pkg = ext.get('pkg', '').lower()
        if pkg_filter in pkg:
            found.append(ext)
            break
if not found and not pkg_filter:
    if data:
        found.append(data[0])
if found:
    print(json.dumps(found[0]))
else:
    print('')
" 2>/dev/null)

    if [ -z "$EXT_JSON" ]; then
        err "No matching extension found for '${PKG_NAME}' in ${REPO_SOURCE} index"
        exit 1
    fi

    EXT_NAME=$(echo "$EXT_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('name','unknown'))" 2>/dev/null || echo "$PKG_NAME")
    EXT_PKG=$(echo "$EXT_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('pkg',''))" 2>/dev/null || echo "")
    EXT_APK=$(echo "$EXT_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('apk',''))" 2>/dev/null || echo "")
    EXT_LANG=$(echo "$EXT_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('lang',''))" 2>/dev/null || echo "$LANG_FILTER")
    IS_NSFW=$(echo "$EXT_JSON" | python3 -c "import sys,json; print('true' if json.load(sys.stdin).get('nsfw',0) == 1 else 'false')" 2>/dev/null || echo "false")

    if [ -z "$EXT_PKG" ]; then
        EXT_PKG="eu.kanade.tachiyomi.extension.${EXT_LANG:-en}.${PKG_NAME}"
    fi

    log "Found: ${EXT_NAME} (${EXT_PKG}, lang: ${EXT_LANG})"

    APK_URL="${APK_BASE}/${EXT_APK}"
    log "Downloading from: ${APK_URL}"
    if ! curl -sL --connect-timeout 30 -o "${TEMP_DIR}/extension.apk" "$APK_URL" 2>/dev/null || [ ! -s "${TEMP_DIR}/extension.apk" ]; then
        err "Failed to download APK from ${APK_URL}"
        exit 1
    fi

    FILE_SIZE=$(stat -f%z "${TEMP_DIR}/extension.apk" 2>/dev/null || echo "0")
    log "Downloaded ${FILE_SIZE} bytes"

    if [ "$FILE_SIZE" -lt 1000 ]; then
        err "Downloaded file too small (${FILE_SIZE} bytes) — probably a 404"
        cat "${TEMP_DIR}/extension.apk" | head -c 200
        exit 1
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# Step 2: Extract metadata from APK
# ──────────────────────────────────────────────────────────────────────────────

log "Extracting metadata from APK..."

# Extract version info from AndroidManifest.xml (binary XML, parse as latin-1)
APK_PKG=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${TEMP_DIR}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        m = re.search(r'package=\"([^\"]+)\"', text)
        print(m.group(1) if m else '${EXT_PKG}')
    except:
        print('${EXT_PKG}')
" 2>/dev/null || echo "${EXT_PKG}")

VERSION_NAME=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${TEMP_DIR}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        m = re.search(r'versionName=\"([^\"]+)\"', text)
        print(m.group(1) if m else '1.0.0')
    except:
        print('1.0.0')
" 2>/dev/null || echo "1.0.0")

VERSION_CODE=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${TEMP_DIR}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        m = re.search(r'versionCode=\"?([0-9]+)\"?', text)
        print(m.group(1) if m else '100')
    except:
        print('100')
" 2>/dev/null || echo "100")

# The app's MacOSExtensionLoader expects libVersion in the range [12, 15].
# Hardcode to 14 (the latest supported version) rather than trying to
# derive it from the original APK's version name (which is typically 1.x).
LIB_VERSION=14

log "  Package: ${APK_PKG}"
log "  Version: ${VERSION_NAME} (code: ${VERSION_CODE})"
log "  libVersion: ${LIB_VERSION}"
log "  Language: ${EXT_LANG:-en}"

# ──────────────────────────────────────────────────────────────────────────────
# Step 3: Convert DEX to JVM bytecode using dex2jar
# ──────────────────────────────────────────────────────────────────────────────

log "Converting DEX to JVM bytecode with d2j-dex2jar..."

D2J_OUTPUT="${TEMP_DIR}/extension-dex2jar.jar"

set +e
"$D2J_DEX2JAR" -f -o "$D2J_OUTPUT" "${TEMP_DIR}/extension.apk" 2>"${TEMP_DIR}/dex2jar-err.log"
D2J_EXIT=$?
set -e

if [ ! -f "$D2J_OUTPUT" ] || [ ! -s "$D2J_OUTPUT" ]; then
    err "d2j-dex2jar conversion failed (exit code: ${D2J_EXIT})"
    err "Error log:"
    sed 's/^/  /' "${TEMP_DIR}/dex2jar-err.log" 2>/dev/null | head -15
    exit 1
fi

D2J_SIZE=$(stat -f%z "$D2J_OUTPUT" 2>/dev/null || echo "0")
CLASS_COUNT=$("$JAR_CMD" tf "$D2J_OUTPUT" 2>/dev/null | grep -c '\.class$' || echo "0")

log "  Output: ${D2J_SIZE} bytes, ${CLASS_COUNT} classes"

if [ "$CLASS_COUNT" -eq 0 ]; then
    err "No class files found in dex2jar output"
    exit 1
fi

# ──────────────────────────────────────────────────────────────────────────────
# Step 4: Detect source classes
# ──────────────────────────────────────────────────────────────────────────────

log "Detecting source classes..."

# Disable set -e for source class detection, as javap may fail or return odd
# exit codes for some classes in obfuscated/dex-converted JARs.
set +e

SOURCE_CLASSES=""

# Extract and scan each non-library class
SCAN_DIR="${TEMP_DIR}/scan"
mkdir -p "$SCAN_DIR"
cd "$SCAN_DIR"
"$JAR_CMD" xf "$D2J_OUTPUT" 2>/dev/null || true

for classfile in $(find . -name '*.class' -path '*/eu/kanade/tachiyomi/animeextension/*' -not -name '*\$*' -not -name 'R.class' -not -name 'BuildConfig.class' 2>/dev/null | head -10); do
    # Only check the class declaration line (first 1-2 lines of javap output)
    # to avoid false positives from generic method bounds like "? extends Source"
    SUPERTYPE=$("$JAVAP_CMD" -p "$classfile" 2>/dev/null | head -2 | grep -E '(extends|implements)')
    # Check for ANY source API parent class — includes direct parents like AnimeHttpSource
    # AND indirect parents like extensions.utils.Source (which extends AnimeHttpSource)
    if echo "$SUPERTYPE" | grep -qiE '(Source|Catalogue|Configurable|AnimeHttp|HttpSource|tachiyomi|animesource)'; then
        SOURCE_CLASSES=$(echo "$classfile" | sed 's|^\./||; s|/|.|g; s|\.class||')
        log "  Found source class via bytecode: ${SOURCE_CLASSES}"
        break
    fi
done

# Fallback: just use the package name as the source class if above didn't find anything
if [ -z "$SOURCE_CLASSES" ]; then
    # Look for any non-library class in the main package
    for classfile in $(find . -name '*.class' -not -name '*\$*' 2>/dev/null | \
        grep -vE '(android/|java/|javax/|kotlin/|dalvik/|okhttp|okio|jsoup|org/apache|com/fasterxml|com/google)' | head -10); do
        CLASS_NAME=$(echo "$classfile" | sed 's|^\./||; s|/|.|g; s|\.class||')
        # Only check class declaration line (avoids false positives from generic bounds)
        SUPERTYPE=$("$JAVAP_CMD" -p "$classfile" 2>/dev/null | head -2 | grep -E '(extends|implements)')
        # Accept classes that extend something in the tachiyomi/animesource package
        if echo "$SUPERTYPE" | grep -qiE '(tachiyomi|animesource)'; then
            SOURCE_CLASSES="$CLASS_NAME"
            log "  Found source class via fallback scan: ${SOURCE_CLASSES}"
            break
        fi
    done
fi

if [ -z "$SOURCE_CLASSES" ]; then
    log "  WARNING: Could not determine source class, using package name as fallback"
    SOURCE_CLASSES="$APK_PKG"
fi

log "  Source class: ${SOURCE_CLASSES}"

# Re-enable set -e
set -e

# ──────────────────────────────────────────────────────────────────────────────
# Step 5: Package with META-INF/extension.json
# ──────────────────────────────────────────────────────────────────────────────

log "Packaging final extension JAR..."

JAR_NAME="${APK_PKG}.jar"
FINAL_JAR="${TEMP_DIR}/${JAR_NAME}"

# Create META-INF directory
META_DIR="${TEMP_DIR}/META-INF"
mkdir -p "$META_DIR"

cat > "${META_DIR}/extension.json" << JSONEOF
{
  "name": "Aniyomi: ${EXT_NAME:-${PKG_NAME}}",
  "pkgName": "${APK_PKG}",
  "versionName": "${VERSION_NAME}",
  "versionCode": ${VERSION_CODE:-100},
  "libVersion": ${LIB_VERSION:-15.0},
  "lang": "${EXT_LANG:-en}",
  "isNsfw": ${IS_NSFW:-false},
  "isTorrent": false,
  "sourceClass": "${SOURCE_CLASSES}",
  "pkgFactory": null,
  "hasReadme": false,
  "hasChangelog": false
}
JSONEOF

log "  extension.json:"
sed 's/^/    /' "${META_DIR}/extension.json"

# Merge dex2jar output with extension.json into a clean JAR
# Strategy: Extract both JAR and metadata into a temp dir, then repack
MERGE_DIR="${TEMP_DIR}/merge"
rm -rf "$MERGE_DIR"
mkdir -p "$MERGE_DIR"

cd "$MERGE_DIR"
"$JAR_CMD" xf "$D2J_OUTPUT" 2>/dev/null || true

# Add/overwrite our META-INF/extension.json
mkdir -p "${MERGE_DIR}/META-INF"
cp "${META_DIR}/extension.json" "${MERGE_DIR}/META-INF/"

# Also remove any unwanted META-INF entries from the original JAR
rm -f "${MERGE_DIR}/META-INF/MANIFEST.MF" 2>/dev/null || true
rm -f "${MERGE_DIR}/META-INF/CERT.RSA" 2>/dev/null || true
rm -f "${MERGE_DIR}/META-INF/CERT.SF" 2>/dev/null || true
rm -f "${MERGE_DIR}/META-INF/ANDROIDD.SF" 2>/dev/null || true
rm -f "${MERGE_DIR}/META-INF/ANDROIDD.RSA" 2>/dev/null || true

# Repack everything
cd "$MERGE_DIR"
"$JAR_CMD" cf "$FINAL_JAR" . 2>/dev/null || true

JAR_SIZE=$(stat -f%z "$FINAL_JAR" 2>/dev/null || echo "0")
log "  Final JAR: ${JAR_SIZE} bytes, ${CLASS_COUNT} classes"

if [ ! -f "$FINAL_JAR" ] || [ ! -s "$FINAL_JAR" ]; then
    err "JAR packaging failed"
    exit 1
fi

# ──────────────────────────────────────────────────────────────────────────────
# Step 5b: Inject missing typealias bridge classes
# ──────────────────────────────────────────────────────────────────────────────

log "Injecting missing typealias bridge classes for runtime bytecode compatibility..."

# The source-api uses Kotlin typealiases (e.g., typealias CatalogueSource = AnimeCatalogueSource)
# which work at compile time but don't create real JVM .class files. Converted Android
# extension JARs reference these class names at the bytecode level, causing
# ClassNotFoundException at runtime. We inject small Java bridge interfaces that
# extend the real animesource classes, providing the missing .class files.

SOURCE_API_JAR="${PROJECT_DIR}/libs/source-api-jvm.jar"
COMMON_JVM_JAR="${PROJECT_DIR}/libs/common-jvm.jar"
STUBS_DIR="${TEMP_DIR}/stubs"
mkdir -p "$STUBS_DIR"

# Find javac
JAVAC_CMD=""
for candidate in \
    "${JAVA_HOME}/bin/javac" \
    "/opt/homebrew/opt/openjdk@17/bin/javac" \
    "/opt/homebrew/opt/openjdk@21/bin/javac" \
    "/opt/homebrew/opt/openjdk/bin/javac" \
    $(which javac 2>/dev/null || true); do
    if [ -n "$candidate" ] && [ -f "$candidate" ]; then
        JAVAC_CMD="$candidate"
        break
    fi
done

if [ -n "$JAVAC_CMD" ] && [ -f "$SOURCE_API_JAR" ]; then
    log "  Found javac: ${JAVAC_CMD}"
    
    # Create the CatalogueSource bridge
    cat > "${STUBS_DIR}/CatalogueSource.java" << 'JAVAEOF'
package eu.kanade.tachiyomi.source;

/**
 * Real JVM bridge interface for bytecode compatibility.
 * Converted extension JARs reference this class name at runtime.
 * Extends the real AnimeCatalogueSource so all methods are available.
 */
public interface CatalogueSource extends eu.kanade.tachiyomi.animesource.AnimeCatalogueSource {
}
JAVAEOF
    
    # Create the Source bridge
    cat > "${STUBS_DIR}/Source.java" << 'JAVAEOF'
package eu.kanade.tachiyomi.source;

/**
 * Real JVM bridge interface for bytecode compatibility.
 */
public interface Source extends eu.kanade.tachiyomi.animesource.AnimeSource {
}
JAVAEOF

    log "  Compiling bridge stubs..."
    "$JAVAC_CMD" -cp "${SOURCE_API_JAR}:${COMMON_JVM_JAR}" -d "${STUBS_DIR}/classes" \
        "${STUBS_DIR}/CatalogueSource.java" "${STUBS_DIR}/Source.java" 2>"${TEMP_DIR}/javac-err.log" || {
        log "  WARNING: Bridge stub compilation failed (non-fatal):"
        sed 's/^/    /' "${TEMP_DIR}/javac-err.log" 2>/dev/null | head -5
    }
    
    # Inject bridge classes into the final JAR if they were compiled
    if [ -d "${STUBS_DIR}/classes" ]; then
        BRIDGE_CLASSES=$(find "${STUBS_DIR}/classes" -name '*.class' 2>/dev/null)
        BRIDGE_COUNT=$(echo "$BRIDGE_CLASSES" | wc -l | tr -d ' ')
        if [ "$BRIDGE_COUNT" -gt 0 ]; then
            log "  Injecting ${BRIDGE_COUNT} bridge class(es) into extension JAR..."
            cd "${STUBS_DIR}/classes"
            # Use explicit file list to avoid shell expansion issues with find
            INJECT_PATHS=""
            for cls in $BRIDGE_CLASSES; do
                rel_path="${cls#${STUBS_DIR}/classes/}"
                INJECT_PATHS="$INJECT_PATHS $rel_path"
            done
            "$JAR_CMD" uf "$FINAL_JAR" $INJECT_PATHS 2>&1 || log "  WARNING: jar uf failed (exit code: $?)"
            UPDATED_SIZE=$(stat -f%z "$FINAL_JAR" 2>/dev/null || echo "0")
            UPDATED_CLASSES=$("$JAR_CMD" tf "$FINAL_JAR" 2>/dev/null | grep -c '\.class$' || echo "0")
            log "  Updated JAR: ${UPDATED_SIZE} bytes, ${UPDATED_CLASSES} classes"
        fi
    fi
else
    log "  Skipping bridge injection: javac or source-api JAR not found"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Step 6: Install to extensions directory
# ──────────────────────────────────────────────────────────────────────────────

log "Installing extension..."
mkdir -p "${EXTENSIONS_DIR}"
cp "$FINAL_JAR" "${EXTENSIONS_DIR}/${JAR_NAME}"
log "  Installed to: ${EXTENSIONS_DIR}/${JAR_NAME}"

# ├─────────────────────────────────────────────────────────────────────────────
# Summary
# ──────────────────────────────────────────────────────────────────────────────

log ""
log "═══════════════════════════════════════════════════════════════"
log "  Extension conversion complete!"
log "═══════════════════════════════════════════════════════════════"
log "  Name:     ${EXT_NAME:-${PKG_NAME}}"
log "  Package:  ${APK_PKG}"
log "  Version:  ${VERSION_NAME} (${VERSION_CODE})"
log "  JAR:      ${EXTENSIONS_DIR}/${JAR_NAME}"
log "  Classes:  ${CLASS_COUNT}"
log "  Size:     ${JAR_SIZE} bytes"
log "  Source:   ${SOURCE_CLASSES}"
log ""
log "  To test:"
log "    1. Launch the Anikku macOS app"
log "    2. Go to Browse tab"
log "    3. Find '${EXT_NAME:-${PKG_NAME}}' in the source list"
log "    4. Click to browse anime"
log "    5. Select an anime to see episodes"
log "    6. Play an episode to test mpv streaming"
log ""
log "  Note: The extension may need to be trusted first."
log "═══════════════════════════════════════════════════════════════"
