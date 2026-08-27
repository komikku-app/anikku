#!/usr/bin/env bash
#
# preconvert-extensions.sh
# =========================
# Batch pre-converts popular Android APK extensions to macOS JVM JARs
# and generates a repo index suitable for hosting on GitHub Pages.
#
# This is the recommended way to make extensions available WITHOUT requiring
# users to install jadx or dex2jar. Run this script once, then upload the
# output/ directory to any static host (GitHub Pages, your own server, etc.)
# and set the app's default repo URL to point there.
#
# Usage:
#   ./preconvert-extensions.sh                    # Convert ALL popular extensions
#   ./preconvert-extensions.sh --list             # List available extensions
#   ./preconvert-extensions.sh --only allanime     # Convert only one extension
#   ./preconvert-extensions.sh --output ~/my-repo  # Custom output directory
#
# Requirements:
#   - dex2jar (brew install dex2jar)
#   - JDK 17+ (for jar, javap, javac commands)
#   - curl, python3
#
# Output:
#   output/
#   ├── index.min.json          ← Repo index (points to JARs via relative paths)
#   ├── eu.kanade.tachiyomi.animeextension.en.allanime.jar
#   ├── eu.kanade.tachiyomi.animeextension.en.animepahe.jar
#   ├── eu.kanade.tachiyomi.animeextension.en.anineko.jar
#   ├── ...
#
# To deploy to GitHub Pages:
#   1. Create a new repo (e.g., yourname/anikku-extensions-jar)
#   2. Upload the output/ contents to the repo
#   3. Enable GitHub Pages on the repo (Settings → Pages → Deploy from main branch /docs)
#   4. Set the app's default repo URL to:
#      https://raw.githubusercontent.com/yourname/anikku-extensions-jar/main/
#   5. Or use the repo's GitHub Pages URL:
#      https://yourname.github.io/anikku-extensions-jar/

set -euo pipefail 2>/dev/null || set -eu

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/output"
TEMP_BASE="/tmp/anikku-preconvert"
WORK_DIR="${TEMP_BASE}/work"
BUILD_LOG="${TEMP_BASE}/build-log.txt"

# Default repo for downloading APK sources
SALMANBAPPI_INDEX="https://raw.githubusercontent.com/salmanbappi/extensions-repo/main/index.min.json"
SALMANBAPPI_APK_BASE="https://raw.githubusercontent.com/salmanbappi/extensions-repo/main/apk"

# keiyoushi/extensions is a primarily-manga APK repo.
# For dedicated anime extension sources, use yuzono/anime-extensions instead
# with the batch-build-keiyoushi-from-source.sh script (clean JVM compilation).
KEIYOUSHI_INDEX="https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"
KEIYOUSHI_APK_BASE="https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk"

# ──────────────────────────────────────────────────────────────────────────────
# Popular anime extensions (curated list — English + multi-language sources)
# Format: short_name:lang
#   - en  = English
#   - es  = Spanish
#   - all = Works for any language/region
# ──────────────────────────────────────────────────────────────────────────────
POPULAR_EXTENSIONS=(
    "allanime:en"
    "animepahe:en"
    "animesogo:en"
    "anineko:en"
    "animestream:en"
    "anitusk:en"
    "anidb:en"
    "reanime:en"
    # Non-English
    "reanime:es"
    "anikoto:all"
    "animex:all"
    "anivix:all"
)

log() { echo "[*] $*"; }
info() { echo "    $*"; }
err() { echo "[!] $*" >&2; }
warn() { echo "[W] $*"; }

cleanup() {
    if [ "${KEEP_TEMP:-false}" != "true" ]; then
        rm -rf "${TEMP_BASE}"
    else
        log "Keeping temporary files at: ${TEMP_BASE}"
    fi
}
trap cleanup EXIT

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Pre-convert popular Android APK extensions to macOS JVM JARs.

Options:
  --list               List available extensions and exit
  --only <name>        Convert only one extension (e.g., allanime)
  --from-repo <repo>   Source repo: salmanbappi (default) or keiyoushi
  --output <dir>       Custom output directory (default: scripts/output/)
  --keep-temp          Keep temporary files for debugging
  --help               Show this help
EOF
    exit 0
}

LIST_ONLY=false
SINGLE_FILTER=""
REPO_SOURCE="salmanbappi"
KEEP_TEMP=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --list) LIST_ONLY=true; shift ;;
        --only) SINGLE_FILTER="$2"; shift 2 ;;
        --from-repo) REPO_SOURCE="$2"; shift 2 ;;
        --output) OUTPUT_DIR="$2"; shift 2 ;;
        --keep-temp) KEEP_TEMP=true; shift ;;
        --help|-h) usage ;;
        *) err "Unknown option: $1"; usage ;;
    esac
done

# ──────────────────────────────────────────────────────────────────────────────
# Find required tools
# ──────────────────────────────────────────────────────────────────────────────

D2J_DEX2JAR="$(which d2j-dex2jar 2>/dev/null || echo "/opt/homebrew/bin/d2j-dex2jar")"
if [ ! -f "$D2J_DEX2JAR" ]; then
    D2J_DEX2JAR=$(ls /opt/homebrew/Cellar/dex2jar/*/bin/d2j-dex2jar 2>/dev/null | head -1 || echo "")
fi

JAR_CMD=""; JAVAC_CMD=""; JAVAP_CMD=""
for cmd in jar javac javap; do
    for candidate in \
        "${JAVA_HOME}/bin/$cmd" \
        "/opt/homebrew/opt/openjdk@17/bin/$cmd" \
        "/opt/homebrew/opt/openjdk@21/bin/$cmd" \
        "/opt/homebrew/opt/openjdk/bin/$cmd" \
        "/usr/bin/$cmd" \
        $(which "$cmd" 2>/dev/null || true); do
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

SOURCE_API_JAR="${PROJECT_DIR}/libs/source-api-jvm.jar"
COMMON_JVM_JAR="${PROJECT_DIR}/libs/common-jvm.jar"

# ──────────────────────────────────────────────────────────────────────────────
# List mode
# ──────────────────────────────────────────────────────────────────────────────
if [ "$LIST_ONLY" = true ]; then
    echo ""
    echo "Available extensions for pre-conversion:"
    echo "══════════════════════════════════════════"
    for entry in "${POPULAR_EXTENSIONS[@]}"; do
        name="${entry%%:*}"
        lang="${entry##*:}"
        printf "  %-20s (lang: %s)\n" "$name" "$lang"
    done
    echo ""
    echo "Use --only <name> to convert a single extension."
    exit 0
fi

# ──────────────────────────────────────────────────────────────────────────────
# Prerequisites check
# ──────────────────────────────────────────────────────────────────────────────
log "Checking prerequisites..."
log ""

if ! command -v python3 &>/dev/null; then
    err "python3 not found. Install Xcode Command Line Tools: xcode-select --install"
    err "  or install via Homebrew: brew install python"
    exit 1
fi

if [ ! -f "$D2J_DEX2JAR" ]; then
    err "d2j-dex2jar not found. Install: brew install dex2jar"
    exit 1
fi
if [ -z "$JAR_CMD" ]; then
    err "jar command not found. Ensure JDK 17+ is installed."
    exit 1
fi

log "  d2j-dex2jar: ${D2J_DEX2JAR}"
log "  jar:         ${JAR_CMD}"
log "  javap:       ${JAVAP_CMD:-not found (optional)}"
log "  javac:       ${JAVAC_CMD:-not found (optional)}"
log "  source-api:  ${SOURCE_API_JAR}"
log "  common-jvm:  ${COMMON_JVM_JAR}"
log ""

mkdir -p "${OUTPUT_DIR}" "${WORK_DIR}"

# ──────────────────────────────────────────────────────────────────────────────
# Fetch repo index
# ──────────────────────────────────────────────────────────────────────────────
if [ "$REPO_SOURCE" = "keiyoushi" ]; then
    REPO_INDEX="$KEIYOUSHI_INDEX"
    REPO_APK_BASE="$KEIYOUSHI_APK_BASE"
    log "Fetching extension index from keiyoushi repo..."
else
    REPO_INDEX="$SALMANBAPPI_INDEX"
    REPO_APK_BASE="$SALMANBAPPI_APK_BASE"
    log "Fetching extension index from salmanbappi repo..."
fi
INDEX=$(curl -sL --connect-timeout 15 "$REPO_INDEX" 2>/dev/null || echo "[]")
EXT_COUNT=$(echo "$INDEX" | python3 -c "import sys,json; data=json.load(sys.stdin); print(len(data))" 2>/dev/null || echo "0")
info "Found ${EXT_COUNT} extensions in index"
log ""

# ──────────────────────────────────────────────────────────────────────────────
# Build bridge stubs once for all extensions
# ──────────────────────────────────────────────────────────────────────────────
log "Building bridge stubs (CatalogueSource + Source bytecode)..."
STUBS_DIR="${TEMP_BASE}/stubs"
mkdir -p "${STUBS_DIR}/classes"

if [ -n "$JAVAC_CMD" ] && [ -f "$SOURCE_API_JAR" ] && [ -f "$COMMON_JVM_JAR" ]; then
    cat > "${STUBS_DIR}/CatalogueSource.java" << 'JAVAEOF'
package eu.kanade.tachiyomi.source;
public interface CatalogueSource extends eu.kanade.tachiyomi.animesource.AnimeCatalogueSource {
}
JAVAEOF

    cat > "${STUBS_DIR}/Source.java" << 'JAVAEOF'
package eu.kanade.tachiyomi.source;
public interface Source extends eu.kanade.tachiyomi.animesource.AnimeSource {
}
JAVAEOF

    "$JAVAC_CMD" -cp "${SOURCE_API_JAR}:${COMMON_JVM_JAR}" -d "${STUBS_DIR}/classes" \
        "${STUBS_DIR}/CatalogueSource.java" "${STUBS_DIR}/Source.java" 2>/dev/null || \
        warn "Bridge stub compilation failed — some extensions may not load"
fi
log ""

# ──────────────────────────────────────────────────────────────────────────────
# Determine which extensions to convert
# ──────────────────────────────────────────────────────────────────────────────
TO_CONVERT=()
if [ -n "$SINGLE_FILTER" ]; then
    for entry in "${POPULAR_EXTENSIONS[@]}"; do
        name="${entry%%:*}"
        if [[ "$name" == "$SINGLE_FILTER" ]]; then
            TO_CONVERT+=("$entry")
            break
        fi
    done
    if [ ${#TO_CONVERT[@]} -eq 0 ]; then
        err "Extension '${SINGLE_FILTER}' not found in popular list."
        err "Use --list to see available extensions."
        exit 1
    fi
else
    TO_CONVERT=("${POPULAR_EXTENSIONS[@]}")
fi

# ──────────────────────────────────────────────────────────────────────────────
# Main conversion loop
# ──────────────────────────────────────────────────────────────────────────────
TOTAL=${#TO_CONVERT[@]}
SUCCESS=0
FAIL=0
SUCCESSFUL_JARS=""
FAILED_NAMES=""

log "Converting ${TOTAL} extension(s)..."
log ""

for entry in "${TO_CONVERT[@]}"; do
    PKG_NAME="${entry%%:*}"
    LANG="${entry##*:}"
    EXT_DIR="${WORK_DIR}/${PKG_NAME}"
    mkdir -p "$EXT_DIR"

    printf "  [%-3d/%-3d] %s (lang: %s)\n" $((SUCCESS + FAIL + 1)) "$TOTAL" "$PKG_NAME" "$LANG"

    # ── 1. Find extension in repo index ──
    EXT_JSON=$(echo "$INDEX" | python3 -c "
import sys, json
data = json.load(sys.stdin)
kw = '${PKG_NAME}'.lower()
for ext in data:
    pkg = ext.get('pkg', '').lower()
    name = ext.get('name', '').lower()
    apk = ext.get('apk', '').lower()
    lang = ext.get('lang', '')
    if kw in pkg or kw in name or kw in apk:
        if lang == '${LANG}' or lang == 'all':
            print(json.dumps(ext))
            break
" 2>/dev/null || echo "")

    if [ -z "$EXT_JSON" ]; then
        warn "  SKIP: Extension not found in repo index"
        FAIL=$((FAIL + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${PKG_NAME} (not in repo)\n"
        continue
    fi

    EXT_NAME=$(echo "$EXT_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('name','unknown'))" 2>/dev/null)
    EXT_APK=$(echo "$EXT_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('apk',''))" 2>/dev/null || echo "")
    IS_NSFW=$(echo "$EXT_JSON" | python3 -c "import sys,json; print('true' if json.load(sys.stdin).get('nsfw',0) == 1 else 'false')" 2>/dev/null || echo "false")

    # ── 2. Download APK ──
    APK_URL="${REPO_APK_BASE}/${EXT_APK}"
    if ! curl -sL --connect-timeout 30 -o "${EXT_DIR}/extension.apk" "$APK_URL" 2>/dev/null || [ ! -s "${EXT_DIR}/extension.apk" ]; then
        warn "  FAIL: Download failed"
        FAIL=$((FAIL + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${PKG_NAME} (download)\n"
        continue
    fi

    APK_SIZE=$(stat -f%z "${EXT_DIR}/extension.apk" 2>/dev/null || echo "0")
    if [ "$APK_SIZE" -lt 1000 ]; then
        warn "  SKIP: File too small (${APK_SIZE} bytes)"
        FAIL=$((FAIL + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${PKG_NAME} (too small)\n"
        continue
    fi

    # ── 3. Extract metadata from APK ──
    APK_PKG=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${EXT_DIR}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        m = re.search(r'package=\"([^\"]+)\"', text)
        print(m.group(1) if m else '')
    except: print('')
" 2>/dev/null || echo "")

    VERSION_NAME=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${EXT_DIR}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        m = re.search(r'versionName=\"([^\"]+)\"', text)
        print(m.group(1) if m else '1.0.0')
    except: print('1.0.0')
" 2>/dev/null || echo "1.0.0")

    VERSION_CODE=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${EXT_DIR}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        m = re.search(r'versionCode=\"?([0-9]+)\"?', text)
        print(m.group(1) if m else '100')
    except: print('100')
" 2>/dev/null || echo "100")

    LIB_VERSION=14
    info "  Package: ${APK_PKG:-unknown}"
    info "  Version: ${VERSION_NAME} (code: ${VERSION_CODE})"

    # ── 4. Convert DEX → JVM bytecode via dex2jar ──
    D2J_OUTPUT="${EXT_DIR}/dex2jar.jar"
    set +e
    "$D2J_DEX2JAR" -f -o "$D2J_OUTPUT" "${EXT_DIR}/extension.apk" 2>"${EXT_DIR}/dex2jar-err.log"
    D2J_EXIT=$?
    set -e

    if [ ! -f "$D2J_OUTPUT" ] || [ ! -s "$D2J_OUTPUT" ]; then
        warn "  FAIL: dex2jar conversion (exit: ${D2J_EXIT})"
        FAIL=$((FAIL + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${PKG_NAME} (dex2jar)\n"
        continue
    fi

    CLASS_COUNT=$("$JAR_CMD" tf "$D2J_OUTPUT" 2>/dev/null | grep -c '\.class$' || echo "0")
    if [ "$CLASS_COUNT" -eq 0 ]; then
        warn "  FAIL: No class files"
        FAIL=$((FAIL + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${PKG_NAME} (no classes)\n"
        continue
    fi

    info "  dex2jar: ${CLASS_COUNT} classes"

    # ── 5. Detect source class via javap (bytecode analysis) ──
    SOURCE_CLASS=""
    if [ -n "$JAVAP_CMD" ]; then
        SCAN_DIR="${EXT_DIR}/scan"
        mkdir -p "$SCAN_DIR"
        cd "$SCAN_DIR"
        "$JAR_CMD" xf "$D2J_OUTPUT" 2>/dev/null || true

        # Primary scan: look in animeextension package
        for classfile in $(find . -name '*.class' -path '*/eu/kanade/tachiyomi/animeextension/*' \
            -not -name '*\$*' -not -name 'R.class' -not -name 'BuildConfig.class' 2>/dev/null | head -10); do
            SUPERTYPE=$("$JAVAP_CMD" -p "$classfile" 2>/dev/null | head -2 | grep -E '(extends|implements)')
            if echo "$SUPERTYPE" | grep -qiE '(Source|Catalogue|Configurable|AnimeHttp|HttpSource|tachiyomi|animesource)'; then
                SOURCE_CLASS=$(echo "$classfile" | sed 's|^\./||; s|/|.|g; s|\.class||')
                break
            fi
        done

        # Fallback: broader scan
        if [ -z "$SOURCE_CLASS" ]; then
            for classfile in $(find . -name '*.class' -not -name '*\$*' 2>/dev/null | \
                grep -vE '(android/|java/|javax/|kotlin/|dalvik/|okhttp|okio|jsoup|org/apache|com/fasterxml)' | head -10); do
                SUPERTYPE=$("$JAVAP_CMD" -p "$classfile" 2>/dev/null | head -2 | grep -E '(extends|implements)')
                if echo "$SUPERTYPE" | grep -qiE '(tachiyomi|animesource)'; then
                    SOURCE_CLASS=$(echo "$classfile" | sed 's|^\./||; s|/|.|g; s|\.class||')
                    break
                fi
            done
        fi
    fi

    if [ -z "$SOURCE_CLASS" ]; then
        SOURCE_CLASS="${APK_PKG:-eu.kanade.tachiyomi.extension.${LANG}.${PKG_NAME}}"
        warn "  Using fallback source class: ${SOURCE_CLASS}"
    else
        info "  Source class: ${SOURCE_CLASS}"
    fi

    # ── 6. Package JAR with extension.json ──
    JAR_NAME="${APK_PKG:-eu.kanade.tachiyomi.extension.${LANG}.${PKG_NAME}}.jar"
    FINAL_JAR="${OUTPUT_DIR}/${JAR_NAME}"

    MERGE_DIR="${EXT_DIR}/merge"
    rm -rf "$MERGE_DIR"
    mkdir -p "$MERGE_DIR"

    cd "$MERGE_DIR"
    "$JAR_CMD" xf "$D2J_OUTPUT" 2>/dev/null || true

    # Write extension.json
    mkdir -p "${MERGE_DIR}/META-INF"
    cat > "${MERGE_DIR}/META-INF/extension.json" << JSONEOF
{
  "name": "Aniyomi: ${EXT_NAME:-${PKG_NAME}}",
  "pkgName": "${APK_PKG:-eu.kanade.tachiyomi.extension.${LANG}.${PKG_NAME}}",
  "versionName": "${VERSION_NAME}",
  "versionCode": ${VERSION_CODE:-100},
  "libVersion": ${LIB_VERSION}.0,
  "lang": "${LANG}",
  "isNsfw": ${IS_NSFW:-false},
  "isTorrent": false,
  "sourceClass": "${SOURCE_CLASS}",
  "pkgFactory": null,
  "hasReadme": false,
  "hasChangelog": false
}
JSONEOF

    # Remove unwanted META-INF entries
    rm -f "${MERGE_DIR}/META-INF/MANIFEST.MF" 2>/dev/null || true
    rm -f "${MERGE_DIR}/META-INF/CERT.RSA" "${MERGE_DIR}/META-INF/CERT.SF" 2>/dev/null || true
    rm -f "${MERGE_DIR}/META-INF/ANDROIDD.SF" "${MERGE_DIR}/META-INF/ANDROIDD.RSA" 2>/dev/null || true

    # Repack
    cd "$MERGE_DIR"
    "$JAR_CMD" cf "$FINAL_JAR" . 2>/dev/null || true

    # ── 7. Inject bridge classes ──
    if [ -d "${STUBS_DIR}/classes" ]; then
        BRIDGE_COUNT=$(find "${STUBS_DIR}/classes" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        if [ "$BRIDGE_COUNT" -gt 0 ]; then
            cd "${STUBS_DIR}/classes"
            BRIDGE_PATHS=""
            for cls in $(find . -name '*.class' 2>/dev/null); do
                BRIDGE_PATHS="$BRIDGE_PATHS ${cls#./}"
            done
            "$JAR_CMD" uf "$FINAL_JAR" $BRIDGE_PATHS 2>/dev/null || true
        fi
    fi

    JAR_SIZE=$(stat -f%z "$FINAL_JAR" 2>/dev/null || echo "0")
    FINAL_CLASSES=$("$JAR_CMD" tf "$FINAL_JAR" 2>/dev/null | grep -c '\.class$' || echo "0")
    SUCCESS=$((SUCCESS + 1))
    SUCCESSFUL_JARS="${SUCCESSFUL_JARS}  ${JAR_NAME}\n"

    info "  ✅ Built: ${JAR_NAME} (${FINAL_CLASSES} classes, ${JAR_SIZE} bytes)"
done

# ──────────────────────────────────────────────────────────────────────────────
# Generate repo index (index.min.json)
# ──────────────────────────────────────────────────────────────────────────────
log ""
log "Generating repo index..."

python3 -c "
import json, os, zipfile

output_dir = '${OUTPUT_DIR}'
entries = []

for fname in sorted(os.listdir(output_dir)):
    if not fname.endswith('.jar'):
        continue
    pkg_name = fname[:-4]
    jar_path = os.path.join(output_dir, fname)
    if not os.path.isfile(jar_path):
        continue

    meta = {}
    try:
        with zipfile.ZipFile(jar_path) as z:
            if 'META-INF/extension.json' in z.namelist():
                meta = json.loads(z.read('META-INF/extension.json'))
    except Exception:
        pass

    entries.append({
        'name': meta.get('name', pkg_name.split('.')[-1].title()),
        'pkg': meta.get('pkgName', pkg_name),
        'apk': fname,
        'lang': meta.get('lang', 'en'),
        'code': meta.get('versionCode', 100),
        'version': meta.get('versionName', '1.0.0'),
        'nsfw': 1 if meta.get('isNsfw', False) else 0,
        'torrent': 1 if meta.get('isTorrent', False) else 0,
        'sources': []
    })

with open(os.path.join(output_dir, 'index.min.json'), 'w') as f:
    json.dump(entries, f, separators=(',', ':'))

print(f'  Wrote {len(entries)} entries to index.min.json')
" 2>&1

# ──────────────────────────────────────────────────────────────────────────────
# Summary
# ──────────────────────────────────────────────────────────────────────────────
log ""
log "═══════════════════════════════════════════════════════════════"
log "  PRE-CONVERSION COMPLETE"
log "═══════════════════════════════════════════════════════════════"
log "  Attempted: ${TOTAL}"
log "  ✅ Success: ${SUCCESS}"
log "  ❌ Failed:  ${FAIL}"
log ""
log "  Output directory: ${OUTPUT_DIR}"
log "  Repo index:       ${OUTPUT_DIR}/index.min.json"
log ""

if [ -n "$SUCCESSFUL_JARS" ]; then
    log "  ✅ Converted JARs:"
    echo -e "$SUCCESSFUL_JARS"
fi

if [ -n "$FAILED_NAMES" ]; then
    log "  ❌ Failed:"
    echo -e "$FAILED_NAMES"
fi

log ""
log "═══════════════════════════════════════════════════════════════"
log "  NEXT STEPS"
log "═══════════════════════════════════════════════════════════════"
log ""
log "  1. Upload the output directory to a static host:"
log ""
log "     GitHub Pages (recommended):"
log "       gh repo create yourname/anikku-extensions-jar --public"
log "       git -C ${OUTPUT_DIR} init"
log "       git -C ${OUTPUT_DIR} add ."
log "       git -C ${OUTPUT_DIR} commit -m 'Pre-converted JARs'"
log "       git -C ${OUTPUT_DIR} remote add origin git@github.com:yourname/anikku-extensions-jar.git"
log "       git -C ${OUTPUT_DIR} push -u origin main"
log "       # Then enable GitHub Pages for this repo"
log ""
log "  2. Set the app's default repo URL to the raw GitHub URL:"
log "       https://raw.githubusercontent.com/yourname/anikku-extensions-jar/main/"
log ""
log "  3. Users open Anikku → Browse → Extensions → Repos tab"
log "     → Add this URL → Fetch → Install extensions without needing jadx!"
log ""
log "  ⚠️  REQUIRED: Update macos/.../ExtensionsScreen.kt to point to your repo."
log "     Change the defaultRepoUrl in that file."
log "═══════════════════════════════════════════════════════════════"
