#!/usr/bin/env bash
#
# batch-build-extensions.sh
# ==========================
# Batch builds all available anime extensions as JVM JARs for macOS.
#
# This script:
# 1. Fetches the list of available anime extensions from community repos
# 2. Downloads each extension's APK from the repo
# 3. Converts each APK to a JVM JAR via dex2jar (direct DEX→JVM bytecode)
# 4. Extracts extension metadata from the APK (AndroidManifest.xml)
# 5. Detects source classes from the converted JAR
# 6. Packages META-INF/extension.json into the JAR
# 7. Deploys the JARs to the Anikku extensions directory
# 8. Generates a custom repo index (index.min.json) pointing to the JARs
#
# Usage:
#   ./batch-build-extensions.sh
#   ./batch-build-extensions.sh --single allanime  # Build one extension by keyword
#   ./batch-build-extensions.sh --repo <repo-url>
#   ./batch-build-extensions.sh --keep-temp
#
# Requirements:
#   - dex2jar (brew install dex2jar)
#   - JDK 17+ (for jar command)
#   - curl, unzip, python3, strings
#
# How it works:
#   Android extension APKs contain DEX (Dalvik Executable) bytecode.
#   dex2jar (d2j-dex2jar) converts DEX directly to JVM .class files,
#   producing a valid JAR without needing to decompile to Java source
#   and recompile. This avoids all the issues with R8-obfuscated code,
#   Android API stubs, and javac compilation failures.
#
#   The resulting JAR will reference Android API classes (android.*) at
#   the bytecode level. The MacOSExtensionLoader handles these gracefully
#   by catching NoClassDefFoundError for Android-specific APIs at runtime.
#   The extension's business logic (HTTP calls, HTML parsing) is pure JVM
#   bytecode produced by dex2jar and executes normally.
#
# Extension repos used:
#   - salmanbappi/extensions-repo (APK format) — 43+ anime extensions
#   - keiyoushi/extensions (APK format) — primarily manga extensions.
#     For anime sources, use batch-build-keiyoushi-from-source.sh instead
#     which compiles from yuzono/anime-extensions source code into clean JVM JARs.
#   - msrofficial/anime-repo (APK format) — additional anime extensions

set -euo pipefail 2>/dev/null || set -eu  # pipefail supported on bash 4+

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EXTENSIONS_DIR="${HOME}/Library/Application Support/Anikku/extensions"
TEMP_DIR="/tmp/anikku-batch-build"
OUTPUT_DIR="${TEMP_DIR}/output"
BUILD_LOG="${TEMP_DIR}/build-log.txt"

# Extension repos (APK-based) — format: <index-url>|<repo-name>
REPOS=(
    "https://raw.githubusercontent.com/salmanbappi/extensions-repo/main/index.min.json|salmanbappi"
    "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json|keiyoushi"
    "https://raw.githubusercontent.com/msrofficial/anime-repo/repo/index.min.json|msrofficial"
)

D2J_DEX2JAR="$(which d2j-dex2jar 2>/dev/null || echo "/opt/homebrew/bin/d2j-dex2jar")"

# Find jar command — search common JDK locations on macOS
JAR_CMD=""
for candidate in \
    "${JAVA_HOME}/bin/jar" \
    "/opt/homebrew/opt/openjdk@17/bin/jar" \
    "/opt/homebrew/opt/openjdk@21/bin/jar" \
    "/opt/homebrew/opt/openjdk/bin/jar" \
    "/usr/bin/jar" \
    $(which jar 2>/dev/null || true); do
    if [ -n "$candidate" ] && [ -f "$candidate" ]; then
        JAR_CMD="$candidate"
        break
    fi
done

log() { echo "[*] $*"; }
err() { echo "[!] $*" >&2; }
info() { echo "    $*"; }

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

Batch build all available anime extensions as JVM JARs.

Options:
  --single <keyword>   Build only extensions matching a keyword (e.g., allanime)
  --repo <url>         Use a specific repo URL
  --keep-temp          Keep temporary files for debugging
  --help               Show this help
EOF
    exit 0
}

SINGLE_FILTER=""
CUSTOM_REPO=""
KEEP_TEMP=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --single) SINGLE_FILTER="$2"; shift 2 ;;
        --repo) CUSTOM_REPO="$2"; shift 2 ;;
        --keep-temp) KEEP_TEMP=true; shift ;;
        --help|-h) usage ;;
        *) err "Unknown option: $1"; usage ;;
    esac
done

# ---------------------------------------------------------------------------
# Prerequisites
# ---------------------------------------------------------------------------
log "Prerequisites..."
log "  Extensions dir: ${EXTENSIONS_DIR}"

if [ ! -f "$D2J_DEX2JAR" ]; then
    D2J_DEX2JAR="/opt/homebrew/Cellar/dex2jar/*/bin/d2j-dex2jar"
    D2J_DEX2JAR=$(ls $D2J_DEX2JAR 2>/dev/null | head -1 || echo "")
fi

if [ ! -f "$D2J_DEX2JAR" ]; then
    err "d2j-dex2jar not found. Install: brew install dex2jar"
    exit 1
fi

log "  d2j-dex2jar: ${D2J_DEX2JAR}"

if [ -z "$JAR_CMD" ]; then
    err "jar command not found. Ensure JDK 17+ is installed."
    err "Try: export JAVA_HOME=/opt/homebrew/opt/openjdk@17"
    exit 1
fi

mkdir -p "${TEMP_DIR}" "${OUTPUT_DIR}" "${EXTENSIONS_DIR}"

# ---------------------------------------------------------------------------
# Step 1: Fetch extension indices from all repos
# ---------------------------------------------------------------------------
log ""
log "═══════════════════════════════════════════════════════════════"
log "  BATCH EXTENSION BUILD (dex2jar)"
log "═══════════════════════════════════════════════════════════════"
log ""

ALL_EXTENSIONS=()
DECLARED_PKGS=""

for repo_entry in "${REPOS[@]}"; do
    REPO_URL="${repo_entry%%|*}"
    REPO_NAME="${repo_entry##*|}"

    log "Fetching index from: ${REPO_NAME}..."

    if [ -n "$CUSTOM_REPO" ]; then
        REPO_URL="$CUSTOM_REPO"
    fi

    REPO_INDEX=$(curl -sL --connect-timeout 15 "$REPO_URL" 2>/dev/null || echo "[]")

    EXT_COUNT=$(echo "$REPO_INDEX" | python3 -c "import sys,json; data=json.load(sys.stdin); print(len(data))" 2>/dev/null || echo "0")
    log "  Found ${EXT_COUNT} extensions in ${REPO_NAME}"

    # Collect extension entries
    # If --single is specified, filter by keyword
    if [ -n "$SINGLE_FILTER" ]; then
        log "  Filtering for: ${SINGLE_FILTER}"
        ANIME_EXTS=$(echo "$REPO_INDEX" | python3 -c "
import sys, json
data = json.load(sys.stdin)
keyword = '${SINGLE_FILTER}'.lower()
for ext in data:
    pkg = ext.get('pkg', '').lower()
    name = ext.get('name', '').lower()
    apk = ext.get('apk', '').lower()
    if keyword in pkg or keyword in name or keyword in apk:
        print(json.dumps(ext))
" 2>/dev/null || echo "")
    else
        # Filter for English anime-specific packages (broad filter)
        ANIME_EXTS=$(echo "$REPO_INDEX" | python3 -c "
import sys, json
data = json.load(sys.stdin)
# Keywords that suggest anime extensions
anime_kw = ['allanime', 'animepahe', 'animesogo', 'animestream', 'anineko',
            'anitusk', 'reanime', 'anidb', 'nineanime', 'hanime', 'animesama',
            'animegdr', 'animexnovel', 'lunaranime', 'gogoanime', 'gogocdn',
            'zoro', 'kaido', 'marin', 'haho', 'm4u', 'flix', 'genoanime']
for ext in data:
    pkg = ext.get('pkg', '').lower()
    lang = ext.get('lang', '')
    for kw in anime_kw:
        if kw in pkg and (lang == 'en' or lang == 'all'):
            print(json.dumps(ext))
            break
" 2>/dev/null || echo "")
    fi

    while IFS= read -r line; do
        if [ -n "$line" ]; then
            ALL_EXTENSIONS+=("$line")
            PKG=$(echo "$line" | python3 -c "import sys,json; print(json.load(sys.stdin).get('pkg',''))" 2>/dev/null || echo "")
            if [ -n "$PKG" ]; then
                if [ -n "$DECLARED_PKGS" ]; then
                    DECLARED_PKGS="${DECLARED_PKGS},${PKG}"
                else
                    DECLARED_PKGS="${PKG}"
                fi
            fi
        fi
    done <<< "$ANIME_EXTS"
done

log "Total anime extensions to process: ${#ALL_EXTENSIONS[@]}"
log ""

# ---------------------------------------------------------------------------
# Step 2: Download and convert each extension using dex2jar
# ---------------------------------------------------------------------------

SUCCESS_COUNT=0
FAIL_COUNT=0
SUCCESSFUL_JARS=""
FAILED_NAMES=""
TRUST_ENTRIES_JSON="["

for ext_json in "${ALL_EXTENSIONS[@]}"; do
    EXT_NAME=$(echo "$ext_json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('name','unknown'))" 2>/dev/null || echo "unknown")
    EXT_PKG=$(echo "$ext_json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('pkg',''))" 2>/dev/null || echo "")
    EXT_APK=$(echo "$ext_json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('apk',''))" 2>/dev/null || echo "")
    EXT_LANG=$(echo "$ext_json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('lang',''))" 2>/dev/null || echo "")

    if [ -z "$EXT_PKG" ] || [ -z "$EXT_APK" ]; then
        log "  SKIP: Missing package name or APK URL for ${EXT_NAME}"
        continue
    fi

    # Use the last segment of the package as the short name
    SHORT_NAME="${EXT_PKG##*.}"
    EXT_TEMP="${TEMP_DIR}/ext-${SHORT_NAME}"
    mkdir -p "$EXT_TEMP"

    log "  [${SUCCESS_COUNT}/${#ALL_EXTENSIONS[@]}] ${EXT_NAME} (${EXT_PKG})"

    # -----------------------------------------------------------------------
    # Step 2a: Download APK
    # -----------------------------------------------------------------------
    APK_URL="https://raw.githubusercontent.com/salmanbappi/extensions-repo/main/apk/${EXT_APK}"
    if ! curl -sL -o "${EXT_TEMP}/extension.apk" "$APK_URL" 2>/dev/null || [ ! -s "${EXT_TEMP}/extension.apk" ]; then
        APK_URL="https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/${EXT_APK}"
        curl -sL -o "${EXT_TEMP}/extension.apk" "$APK_URL" 2>/dev/null || true
    fi

    if [ ! -s "${EXT_TEMP}/extension.apk" ]; then
        info "  FAIL: Could not download APK"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${EXT_NAME} (download failed)\\n"
        continue
    fi

    APK_SIZE=$(stat -f%z "${EXT_TEMP}/extension.apk" 2>/dev/null || echo "0")
    info "  Downloaded: ${APK_SIZE} bytes"

    if [ "$APK_SIZE" -lt 1000 ]; then
        info "  SKIP: File too small (probably a 404)"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${EXT_NAME} (APK too small)\\n"
        continue
    fi

    # -----------------------------------------------------------------------
    # Step 2b: Extract metadata from APK (AndroidManifest.xml)
    # -----------------------------------------------------------------------
    info "  Extracting metadata from APK..."

    # Extract version info from the AndroidManifest.xml (binary XML, parse as latin-1)
    VERSION_NAME=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${EXT_TEMP}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        # versionName is usually quoted
        m = re.search('versionName=\x22([^\x22]+)\x22', text)
        print(m.group(1) if m else '1.0.0')
    except Exception:
        print('1.0.0')
" 2>/dev/null || echo "1.0.0")

    VERSION_CODE=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${TEMP_DIR}/ext-${SHORT_NAME}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        m = re.search('versionCode=\x22?([0-9]+)\x22?', text)
        print(m.group(1) if m else '100')
    except Exception:
        print('100')
" 2>/dev/null || echo "100")

    # libVersion: default to 15 since we can't read extension's gradle deps from APK
    LIB_VERSION="15.0"

    # Also try to find the real package from the APK
    APK_PKG=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${EXT_TEMP}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        m = re.search('package=\x22([^\x22]+)\x22', text)
        print(m.group(1) if m else '${EXT_PKG}')
    except Exception:
        print('${EXT_PKG}')
" 2>/dev/null || echo "${EXT_PKG}")

    info "  Package: ${APK_PKG}, Version: ${VERSION_NAME} (${VERSION_CODE}), lib: ${LIB_VERSION}"

    # -----------------------------------------------------------------------
    # Step 2c: Convert APK to JAR via dex2jar (direct DEX→JVM bytecode)
    # -----------------------------------------------------------------------
    info "  Converting DEX to JVM bytecode with d2j-dex2jar..."

    # d2j-dex2jar outputs a JAR in the same directory as the input by default
    D2J_OUTPUT="${EXT_TEMP}/extension-dex2jar.jar"

    # Run dex2jar
    set +e
    "$D2J_DEX2JAR" -f -o "$D2J_OUTPUT" "${EXT_TEMP}/extension.apk" 2>"${EXT_TEMP}/dex2jar-err.log" >"${EXT_TEMP}/dex2jar-out.log"
    D2J_EXIT=$?
    set -e

    if [ ! -f "$D2J_OUTPUT" ] || [ ! -s "$D2J_OUTPUT" ]; then
        info "  FAIL: d2j-dex2jar conversion failed (exit code: ${D2J_EXIT})"
        info "  Error log:"
        sed 's/^/    /' "${EXT_TEMP}/dex2jar-err.log" 2>/dev/null | head -10
        FAIL_COUNT=$((FAIL_COUNT + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${EXT_NAME} (dex2jar)\\n"
        continue
    fi

    D2J_SIZE=$(stat -f%z "$D2J_OUTPUT" 2>/dev/null || echo "0")
    info "  dex2jar output: ${D2J_SIZE} bytes"

    CLASS_COUNT=$("$JAR_CMD" tf "$D2J_OUTPUT" 2>/dev/null | grep -c '\.class$' || echo "0")
    info "  dex2jar produced ${CLASS_COUNT} class files"

    if [ "$CLASS_COUNT" -eq 0 ]; then
        info "  FAIL: No class files in dex2jar output"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${EXT_NAME} (no classes)\\n"
        continue
    fi

    # -----------------------------------------------------------------------
    # Step 2d: Detect source classes from the JAR
    # -----------------------------------------------------------------------
    info "  Detecting source classes..."

    # Only look for classes within the extension's own package namespace.
    # This avoids accidentally picking library classes (e.g. JsUnpacker) as sources.
    # Filter out \$\$ classes (DEX lambdas like \$\$ExternalSyntheticLambda0) and
    # \$ inner classes (serializers, etc.) to prefer the actual source class.
    SOURCE_CLASSES=$("$JAR_CMD" tf "$D2J_OUTPUT" 2>/dev/null | \
        grep '\.class$' | \
        sed 's|/|.|g; s|\.class||' | \
        grep "^${APK_PKG}\." | \
        grep -vE '(\$\$|\$[A-Za-z]|R\$|BuildConfig|Manifest|databinding)' | \
        head -5 | \
        tr '\n' ';' | \
        sed 's/;$//' || true)

    if [ -z "$SOURCE_CLASSES" ]; then
        info "  WARNING: No classes in package namespace, falling back to all non-stdlib classes"
        SOURCE_CLASSES=$("$JAR_CMD" tf "$D2J_OUTPUT" 2>/dev/null | \
            grep '\.class$' | \
            sed 's|/|.|g; s|\.class||' | \
            grep -vE '^(android\.|java\.|javax\.|kotlin\.|dalvik\.|com\.android\.)' | \
            grep -vE '(R\$|BuildConfig|Manifest|databinding)' | \
            head -5 | \
            tr '\n' ';' | \
            sed 's/;$//' || true)
    fi

    if [ -z "$SOURCE_CLASSES" ]; then
        info "  WARNING: No non-Android classes found, using all classes (excluding well-known)"
        SOURCE_CLASSES=$("$JAR_CMD" tf "$D2J_OUTPUT" 2>/dev/null | \
            grep '\.class$' | \
            sed 's|/|.|g; s|\.class||' | \
            grep -vE '(R\$|\$|BuildConfig|Manifest|databinding|android\.|java\.|javax\.|kotlin\.|dalvik\.)' | \
            head -5 | \
            tr '\n' ';' | \
            sed 's/;$//' || true)
    fi

    if [ -z "$SOURCE_CLASSES" ]; then
        info "  WARNING: Could not determine source classes, using package-derived name"
        # Derive class name from package: last segment, capitalized
        DERIVED=$(python3 -c "
import sys
pkg = '${APK_PKG}'
seg = pkg.rsplit('.', 1)[-1] if '.' in pkg else pkg
print('${APK_PKG}.' + seg[0].upper() + seg[1:] if seg else '${APK_PKG}.Source')
" 2>/dev/null)
        SOURCE_CLASSES="$DERIVED"
    fi

    info "  Source classes: ${SOURCE_CLASSES}"

    # -----------------------------------------------------------------------
    # Step 2e: Package with META-INF/extension.json
    # -----------------------------------------------------------------------
    info "  Packaging extension JAR with metadata..."

    JAR_NAME="${APK_PKG}.jar"
    FINAL_JAR="${OUTPUT_DIR}/${JAR_NAME}"

    # Create a temporary directory to add META-INF to the JAR
    META_DIR="${EXT_TEMP}/meta"
    mkdir -p "$META_DIR"

    # Determine NSFW status from index data
    IS_NSFW=$(echo "$ext_json" | python3 -c "
import sys, json
try:
    ext = json.load(sys.stdin)
    print('true' if ext.get('nsfw', 0) == 1 else 'false')
except:
    print('false')
" 2>/dev/null || echo "false")

    # Write extension.json
    cat > "${META_DIR}/extension.json" << JSONEOF
{
  "name": "Aniyomi: ${EXT_NAME}",
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

    info "  extension.json generated:"
    sed 's/^/    /' "${META_DIR}/extension.json"

    # Copy the dex2jar output and add META-INF
    cp "$D2J_OUTPUT" "$FINAL_JAR"

    # Add META-INF/extension.json to the JAR
    mkdir -p "${META_DIR}/META-INF"
    cp "${META_DIR}/extension.json" "${META_DIR}/META-INF/"
    cd "$META_DIR"
    "$JAR_CMD" uf "$FINAL_JAR" META-INF/extension.json 2>/dev/null || {
        # If jar update fails (e.g., no manifest)
        mkdir -p "${META_DIR}/META-INF"
        cp "${META_DIR}/extension.json" "${META_DIR}/META-INF/"
        cd "$META_DIR"
        # Merge: extract both JARs into a temp dir and repack
        MERGE_DIR="${EXT_TEMP}/merge"
        mkdir -p "$MERGE_DIR"
        cd "$MERGE_DIR"
        "$JAR_CMD" xf "$D2J_OUTPUT" 2>/dev/null || true
        cp -r "${META_DIR}/META-INF" "$MERGE_DIR/" 2>/dev/null || true
        # Remove existing META-INF from dex2jar if any, to use ours
        rm -rf "${MERGE_DIR}/META-INF/extension.json" 2>/dev/null || true
        mkdir -p "${MERGE_DIR}/META-INF"
        cp "${META_DIR}/extension.json" "${MERGE_DIR}/META-INF/" 2>/dev/null || true
        cd "$MERGE_DIR"
        "$JAR_CMD" cf "$FINAL_JAR" . 2>/dev/null || true
    }

    if [ -f "$FINAL_JAR" ] && [ -s "$FINAL_JAR" ]; then
        cp "$FINAL_JAR" "${EXTENSIONS_DIR}/${JAR_NAME}"
        SUCCESSFUL_JARS="${SUCCESSFUL_JARS}  ${JAR_NAME}\\n"
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        FINAL_SIZE=$(stat -f%z "$FINAL_JAR" 2>/dev/null || echo "0")
        info "  ✅ Built: ${JAR_NAME} (${CLASS_COUNT} classes, ${FINAL_SIZE} bytes)"
        info "     Installed to: ${EXTENSIONS_DIR}/${JAR_NAME}"

        # Compute SHA-256 hash for trust
        if command -v shasum >/dev/null 2>&1; then
            JAR_HASH=$(shasum -a 256 "$FINAL_JAR" | awk '{print $1}')
        elif command -v sha256sum >/dev/null 2>&1; then
            JAR_HASH=$(sha256sum "$FINAL_JAR" | awk '{print $1}')
        else
            JAR_HASH="unknown"
        fi
        if [ "$JAR_HASH" != "unknown" ]; then
            if [ "$TRUST_ENTRIES_JSON" != "[" ]; then
                TRUST_ENTRIES_JSON="${TRUST_ENTRIES_JSON},"
            fi
            TRUST_ENTRIES_JSON="${TRUST_ENTRIES_JSON}{\"pkgName\":\"${APK_PKG}\",\"versionCode\":${VERSION_CODE:-100},\"signatureHash\":\"${JAR_HASH}\"}"
        fi
    else
        info "  ❌ JAR packaging failed"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${EXT_NAME} (JAR packaging)\\n"
    fi
done

# ---------------------------------------------------------------------------
# Step 3: Generate repo index
# ---------------------------------------------------------------------------
log ""
log "Step 3: Generating extension repo index..."
log ""

# Build a JSON array of successful extensions for the index
python3 -c "
import json, os

entries = []
output_dir = '${OUTPUT_DIR}'
extensions_dir = '${EXTENSIONS_DIR}'

# Scan the output directory for JARs and their extension.json
for fname in os.listdir(output_dir):
    if not fname.endswith('.jar'):
        continue
    pkg_name = fname[:-4]  # Remove .jar
    # Try to read extension.json from the JAR
    jar_path = os.path.join(output_dir, fname)
    import zipfile
    try:
        with zipfile.ZipFile(jar_path) as z:
            if 'extension.json' in z.namelist():
                meta = json.loads(z.read('extension.json'))
                entries.append({
                    'name': meta.get('name', pkg_name.split('.')[-1].title()),
                    'pkg': meta.get('pkgName', pkg_name),
                    'apk': fname,
                    'lang': meta.get('lang', 'en'),
                    'code': meta.get('versionCode', 100),
                    'version': meta.get('versionName', '1.0.0'),
                    'nsfw': 1 if meta.get('isNsfw', False) else 0,
                    'torrent': 1 if meta.get('isTorrent', False) else 0,
                    'sources': meta.get('sourceClass', '').split(';') if meta.get('sourceClass') else []
                })
            else:
                entries.append({
                    'name': pkg_name.split('.')[-1].title(),
                    'pkg': pkg_name,
                    'apk': fname,
                    'lang': 'en',
                    'code': 100,
                    'version': '1.0.0',
                    'nsfw': 0,
                    'torrent': 0,
                    'sources': []
                })
    except Exception:
        entries.append({
            'name': pkg_name.split('.')[-1].title(),
            'pkg': pkg_name,
            'apk': fname,
            'lang': 'en',
            'code': 100,
            'version': '1.0.0',
            'nsfw': 0,
            'torrent': 0,
            'sources': []
        })

with open(os.path.join(output_dir, 'index.min.json'), 'w') as f:
    json.dump(entries, f, separators=(',', ':'))

print('Generated index with ' + str(len(entries)) + ' entries')
" 2>&1 || true

# Also copy index to extensions dir
cp "${OUTPUT_DIR}/index.min.json" "${EXTENSIONS_DIR}/macos-repo-index.json" 2>/dev/null || true

# -----------------------------------------------------------------------
# Write trust store: auto-trust all successfully built extensions
# -----------------------------------------------------------------------
TRUST_ENTRIES_JSON="${TRUST_ENTRIES_JSON}]"
TRUST_DIR="${HOME}/Library/Application Support/Anikku/data/trust"
mkdir -p "$TRUST_DIR" 2>/dev/null || true
TRUST_FILE="${TRUST_DIR}/trusted_extensions.json"
if echo "$TRUST_ENTRIES_JSON" > "$TRUST_FILE" 2>/dev/null; then
    TRUSTED_COUNT=$(echo "$TRUST_ENTRIES_JSON" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d))" 2>/dev/null || echo "0")
    log "  Trusted ${TRUSTED_COUNT} extension(s) → ${TRUST_FILE}"
else
    log "  WARNING: Could not write trust store to ${TRUST_FILE}"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
log ""
log "═══════════════════════════════════════════════════════════════"
log "  BUILD SUMMARY"
log "═══════════════════════════════════════════════════════════════"
log "  Total extensions attempted: ${#ALL_EXTENSIONS[@]}"
log "  ✅ Successfully built:      ${SUCCESS_COUNT}"
log "  ❌ Failed:                   ${FAIL_COUNT}"
log ""
log "  JARs in: ${EXTENSIONS_DIR}/"
log "  Repo index: ${EXTENSIONS_DIR}/macos-repo-index.json"
log ""

if [ -n "$SUCCESSFUL_JARS" ]; then
    log "  ✅ Successfully built:"
    echo -e "$SUCCESSFUL_JARS"
fi

if [ -n "$FAILED_NAMES" ]; then
    log "  ❌ Failed:"
    echo -e "$FAILED_NAMES"
fi

log ""
log "  To add this repo to the app:"
log "    1. Open Anikku → Browse → Extensions"
log "    2. Go to the Repos tab"
log "    3. Add repo URL:"
log "       file://${EXTENSIONS_DIR}/macos-repo-index.json"
log "    4. Tap Fetch to load available extensions"
log "    5. Install the ones you want"
log ""
log "  Or host the JARs online:"
log "    1. Upload ${OUTPUT_DIR}/ to a web server"
log "    2. The index URL would be: https://your-server.com/index.min.json"
log "    3. JAR URLs are relative to the index directory"
log "═══════════════════════════════════════════════════════════════"
