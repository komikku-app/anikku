#!/usr/bin/env bash
#
# generate-appcast.sh
# ===================
# Generates a Sparkle-compatible appcast.xml from GitHub Releases.
#
# Two modes of operation:
#
#   AUTO (recommended):
#     ./generate-appcast.sh --auto
#     Fetches the latest release from GitHub and generates/signs appcast.
#
#   MANUAL:
#     ./generate-appcast.sh --version 1.0.1 --dmg ./Anikku-1.0.1.dmg \
#         --signing-key ./ed25519-key.pem --appcast ./appcast.xml
#
# Requirements:
#   - openssl 3.x+ (for Ed25519 signing)
#   - curl (for auto mode)
#
# The signing key must match the public key distributed with the app
# at macos/src/main/resources/Sparkle/ed25519_pub.pem.
#
# For first-time setup:
#   openssl genpkey -algorithm ed25519 -out ed25519-key.pem
#   openssl pkey -in ed25519-key.pem -pubout -out ed25519-pub.pem

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

log() { echo "[*] $*"; }
err() { echo "[!] $*" >&2; }

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Generate or update a Sparkle appcast.xml for a new release.

Modes:
  --auto                    Fetch latest release from GitHub and generate appcast
  --version <ver> --dmg <p> --signing-key <p>
                            Manual mode: specify version, DMG, and key

Options:
  --appcast <path>          Path to appcast.xml (default: ./appcast.xml)
  --repo <owner/name>       GitHub repo (default: komikku-app/anikku)
  --signing-key <p>         Path to the Ed25519 private key PEM file
  --output-dir <dir>        Directory to write appcast and signatures
  --help                    Show this help

Examples:
  # Auto: fetch latest GitHub release
  ./generate-appcast.sh --auto --signing-key ./ed25519-key.pem

  # Manual: sign a specific DMG
  ./generate-appcast.sh --version 1.0.1 \\
      --dmg ./Anikku-1.0.1.dmg \\
      --signing-key ./ed25519-key.pem
EOF
    exit 0
}

# Parse arguments
AUTO_MODE=false
VERSION=""
DMG_PATH=""
SIGNING_KEY=""
APPCAST_PATH=""
REPO="ErnestHysa/anikku"
OUTPUT_DIR=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --auto) AUTO_MODE=true; shift ;;
        --version) VERSION="$2"; shift 2 ;;
        --dmg) DMG_PATH="$2"; shift 2 ;;
        --signing-key) SIGNING_KEY="$2"; shift 2 ;;
        --appcast) APPCAST_PATH="$2"; shift 2 ;;
        --repo) REPO="$2"; shift 2 ;;
        --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
        --help|-h) usage ;;
        *) err "Unknown option: $1"; usage ;;
    esac
done

# Default signing key location
if [ -z "$SIGNING_KEY" ]; then
    for candidate in \
        "${PROJECT_DIR}/ed25519-key.pem" \
        "${SCRIPT_DIR}/../ed25519-key.pem" \
        "./ed25519-key.pem"; do
        if [ -f "$candidate" ]; then
            SIGNING_KEY="$candidate"
            break
        fi
    done
fi

if [ -z "$SIGNING_KEY" ] || [ ! -f "$SIGNING_KEY" ]; then
    err "Signing key not found. Generate one:"
    err "  openssl genpkey -algorithm ed25519 -out ed25519-key.pem"
    err "  openssl pkey -in ed25519-key.pem -pubout -out ed25519-pub.pem"
    exit 1
fi

APPCAST_PATH="${APPCAST_PATH:-${OUTPUT_DIR:-.}/appcast.xml}"
OUTPUT_DIR="${OUTPUT_DIR:-$(dirname "$APPCAST_PATH")}"
mkdir -p "$OUTPUT_DIR"

# ---------------------------------------------------------------------------
# Auto mode: fetch latest release from GitHub
# ---------------------------------------------------------------------------
if [ "$AUTO_MODE" = true ]; then
    log "Auto mode: fetching latest release from ${REPO}..."

    API_URL="https://api.github.com/repos/${REPO}/releases/latest"
    RELEASE_JSON=$(curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
        -H "Accept: application/vnd.github.v3+json" \
        -H "User-Agent: Anikku-Appcast-Generator/1.0" \
        "$API_URL")

    if [ -z "$RELEASE_JSON" ]; then
        err "Failed to fetch release data from GitHub"
        exit 1
    fi

    # Extract version/tag
    TAG_NAME=$(echo "$RELEASE_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('tag_name',''))" 2>/dev/null || echo "")
    VERSION="${TAG_NAME#v}"

    if [ -z "$VERSION" ]; then
        err "Could not extract version from GitHub release"
        exit 1
    fi
    log "  Latest release: ${TAG_NAME} (version ${VERSION})"

    # Find DMG asset
    DMG_URL=$(echo "$RELEASE_JSON" | python3 -c "
import sys, json
release = json.load(sys.stdin)
from urllib.parse import urlparse
for asset in release.get('assets', []):
    name = asset.get('name', '')
    url = asset.get('browser_download_url', '')
    parsed = urlparse(url)
    if (name.endswith('.dmg') or 'mac' in name.lower()) and \
            parsed.scheme == 'https' and parsed.hostname in {'github.com', 'www.github.com'}:
        print(url)
        sys.exit(0)
print('')
" 2>/dev/null)

    if [ -z "$DMG_URL" ]; then
        err "No DMG asset found in the latest release"
        err "Available assets:"
        echo "$RELEASE_JSON" | python3 -c "
import sys, json
for a in json.load(sys.stdin).get('assets',[]):
    print(f\"  - {a['name']} ({a['size']} bytes)\")
" 2>/dev/null
        exit 1
    fi

    if [[ "$DMG_URL" != https://github.com/* && "$DMG_URL" != https://www.github.com/* ]]; then
        err "Refusing DMG URL outside GitHub HTTPS hosts"
        exit 1
    fi
    log "  Downloading DMG from a verified HTTPS URL"
    DMG_PATH="${OUTPUT_DIR}/Anikku-${VERSION}.dmg"
    DMG_TMP="${DMG_PATH}.part"
    rm -f "$DMG_TMP"
    curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
        "$DMG_URL" -o "$DMG_TMP"
    if [ ! -s "$DMG_TMP" ]; then
        rm -f "$DMG_TMP"
        err "Failed to download DMG"
        exit 1
    fi
    mv -f "$DMG_TMP" "$DMG_PATH"

    if [ ! -f "$DMG_PATH" ] || [ ! -s "$DMG_PATH" ]; then
        err "Failed to download DMG"
        exit 1
    fi
    log "  Downloaded: $(stat -f%z "$DMG_PATH" 2>/dev/null || stat -c%s "$DMG_PATH") bytes"

    # Get release metadata
    HTML_URL=$(echo "$RELEASE_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('html_url',''))" 2>/dev/null)
    PUB_DATE=$(echo "$RELEASE_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('published_at',''))" 2>/dev/null)
    RELEASE_BODY=$(echo "$RELEASE_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('body',''))" 2>/dev/null)
else
    # Manual mode validation
    if [ -z "$VERSION" ] || [ -z "$DMG_PATH" ]; then
        err "Error: --version and --dmg are required in manual mode (or use --auto)"
        usage
    fi
    if [ ! -f "$DMG_PATH" ]; then
        err "DMG file not found: $DMG_PATH"
        exit 1
    fi
    HTML_URL="https://github.com/${REPO}/releases/tag/v${VERSION}"
    PUB_DATE=$(date -R 2>/dev/null || date -u +"%a, %d %b %Y %H:%M:%S %z")
fi

# ---------------------------------------------------------------------------
# Sign the DMG with Ed25519
# ---------------------------------------------------------------------------
log "Signing DMG with Ed25519 private key..."
DMG_SIZE=$(stat -f%z "$DMG_PATH" 2>/dev/null || stat -c%s "$DMG_PATH")
log "  DMG:       ${DMG_PATH}"
log "  DMG size:  ${DMG_SIZE} bytes"

SIGNATURE=$(openssl pkeyutl \
    -sign \
    -inkey "$SIGNING_KEY" \
    -rawin \
    -in "$DMG_PATH" | base64 | tr -d '\n')
if [ -z "$SIGNATURE" ]; then
    err "Failed to generate Ed25519 signature."
    err "Make sure you're using OpenSSL 3.x+"
    exit 1
fi
log "  Signature generated (${#SIGNATURE} base64 characters)"

# ---------------------------------------------------------------------------
# Generate/update appcast.xml
# ---------------------------------------------------------------------------
# Preserve the exact GitHub asset URL selected above. Reconstructing the
# filename can point Sparkle at a non-existent or unintended artifact.
if [ "$AUTO_MODE" = true ]; then
    DOWNLOAD_URL="$DMG_URL"
else
    DOWNLOAD_URL="https://github.com/${REPO}/releases/download/v${VERSION}/Anikku-${VERSION}.dmg"
fi
RELEASE_NOTES_URL="${HTML_URL}"

# RFC 2822 date
PUB_DATE_RFC=$(python3 -c "
from datetime import datetime, timezone
try:
    dt = datetime.fromisoformat('${PUB_DATE}'.replace('Z', '+00:00'))
    print(dt.strftime('%a, %d %b %Y %H:%M:%S %z'))
except:
    print('${PUB_DATE}')
" 2>/dev/null || echo "$PUB_DATE")

# Build the new item XML
NEW_ITEM=$(cat << ITEMEOF
    <item>
      <title>Version ${VERSION}</title>
      <sparkle:version>${VERSION}</sparkle:version>
      <sparkle:shortVersionString>${VERSION}</sparkle:shortVersionString>
      <pubDate>${PUB_DATE_RFC}</pubDate>
      <enclosure
        url="${DOWNLOAD_URL}"
        sparkle:edSignature="${SIGNATURE}"
        length="${DMG_SIZE}"
        type="application/octet-stream" />
      <sparkle:releaseNotesLink>
        ${RELEASE_NOTES_URL}
      </sparkle:releaseNotesLink>
    </item>
ITEMEOF
)

if [ -f "$APPCAST_PATH" ] && [ -s "$APPCAST_PATH" ]; then
    log "Updating existing appcast: ${APPCAST_PATH}"
    python3 -c "
import sys, re

with open('${APPCAST_PATH}', 'r') as f:
    content = f.read()

new_item = '''${NEW_ITEM}'''

# Check if this version already exists
if 'Version ${VERSION}' in content:
    print(f'Version ${VERSION} already in appcast — skipping')
    sys.exit(0)

# Insert after <channel> (keep newest first = items in reverse chronological order)
# Sparkle uses the first item as the latest, but items in the feed are typically
# ordered newest-first.
# Insert newest-first: before the first existing item, or (on the first
# release) before </channel> so the channel metadata stays above the items.
first_item = content.find('<item>')
if first_item != -1:
    insert_pos = first_item
else:
    insert_pos = content.rfind('</channel>')
content = content[:insert_pos] + '\n' + new_item + content[insert_pos:]

with open('${APPCAST_PATH}', 'w') as f:
    f.write(content)

item_count = content.count('<item>')
print(f'Appcast updated: {item_count} release(s) total')
"
else
    log "Creating new appcast: ${APPCAST_PATH}"
    cat > "$APPCAST_PATH" << XML
<?xml version="1.0" encoding="utf-8"?>
<rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
  <channel>
    <title>Anikku macOS Appcast</title>
    <description>Most recent changes to Anikku macOS</description>
    <language>en</language>

${NEW_ITEM}
  </channel>
</rss>
XML
    log "Created new appcast.xml"
fi

# Also write a JSON version for the GitHub-based fallback checker
JSON_PATH="${APPCAST_PATH%.xml}.json"
python3 -c "
import json
data = {
    'version': '${VERSION}',
    'downloadUrl': '${DOWNLOAD_URL}',
    'releaseNotesUrl': '${RELEASE_NOTES_URL}',
    'signature': '${SIGNATURE}',
    'dmgSize': ${DMG_SIZE},
    'pubDate': '${PUB_DATE_RFC}',
}
with open('${JSON_PATH}', 'w') as f:
    json.dump(data, f, indent=2)
" 2>/dev/null || true

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
log ""
log "═══════════════════════════════════════════════════════════════"
log "  APPCAST GENERATION COMPLETE"
log "═══════════════════════════════════════════════════════════════"
log "  Appcast:       ${APPCAST_PATH}"
log "  Version:       ${VERSION}"
log "  DMG:           ${DMG_PATH} (${DMG_SIZE} bytes)"
log "  Signature generated (${#SIGNATURE} base64 characters)"
log "  Download URL:  ${DOWNLOAD_URL}"
log ""
log "  Next steps:"
log "    1. Upload appcast.xml to your server"
log "    2. Set Info.plist SUFeedURL to the hosted URL"
log "    3. Verify: curl -I <SUFeedURL>"
log ""
log "  Test locally:"
log "    python3 -m http.server 8080 --directory ${OUTPUT_DIR}"
log "    Then use http://localhost:8080/appcast.xml in SUFeedURL"
log "═══════════════════════════════════════════════════════════════"
