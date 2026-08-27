#!/usr/bin/env bash
#
# build-sparkle-helper.sh
# =======================
# Downloads Sparkle 2.framework and compiles the Swift helper dylib
# used by the Anikku macOS app for auto-updates via JNA.
#
# Usage:
#   ./build-sparkle-helper.sh
#
# Output:
#   build/sparkle/Sparkle.framework/   — Sparkle 2 framework
#   build/sparkle/libSparkleHelper.dylib — Swift helper dylib
#
# Requirements:
#   - Xcode / Swift toolchain (swiftc)
#   - curl, unzip
#
# The Sparkle.framework and helper dylib are bundled into the .app
# by the Gradle build (see build.gradle.kts).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="${PROJECT_DIR}/build/sparkle"
SPARKLE_VERSION="2.6.4"
SPARKLE_ZIP="Sparkle-${SPARKLE_VERSION}.tar.xz"
SPARKLE_URL="https://github.com/sparkle-project/Sparkle/releases/download/${SPARKLE_VERSION}/${SPARKLE_ZIP}"
SPARKLE_FRAMEWORK="${BUILD_DIR}/Sparkle.framework"
HELPER_DYLIB="${BUILD_DIR}/libSparkleHelper.dylib"
SWIFT_SOURCE="${PROJECT_DIR}/src/main/swift/SparkleHelper.swift"

log() { echo "[*] $*"; }
err() { echo "[!] $*" >&2; }

mkdir -p "$BUILD_DIR"

# ---------------------------------------------------------------------------
# Step 1: Download Sparkle.framework
# ---------------------------------------------------------------------------
if [ -d "$SPARKLE_FRAMEWORK" ] && [ -f "${SPARKLE_FRAMEWORK}/Sparkle" ]; then
    log "Sparkle.framework already exists at ${SPARKLE_FRAMEWORK} — skipping download"
else
    log "Downloading Sparkle ${SPARKLE_VERSION}..."
    rm -rf "$SPARKLE_FRAMEWORK" "${BUILD_DIR}/Sparkle-${SPARKLE_VERSION}" 2>/dev/null || true

    curl -sL "$SPARKLE_URL" -o "${BUILD_DIR}/${SPARKLE_ZIP}"

    log "Extracting..."
    cd "$BUILD_DIR"
    tar xf "$SPARKLE_ZIP" 2>/dev/null || unzip -q "$SPARKLE_ZIP" 2>/dev/null || true

    # Find the extracted Sparkle.framework
    FRAMEWORK_PATH=$(find "$BUILD_DIR" -name "Sparkle.framework" -type d 2>/dev/null | head -1)
    if [ -z "$FRAMEWORK_PATH" ]; then
        # Sparkle 2.x tar.xz has the framework at the root
        for candidate in \
            "${BUILD_DIR}/Sparkle.framework" \
            "${BUILD_DIR}/Sparkle-${SPARKLE_VERSION}/Sparkle.framework"; do
            if [ -d "$candidate" ]; then
                FRAMEWORK_PATH="$candidate"
                break
            fi
        done
    fi

    if [ -z "$FRAMEWORK_PATH" ] || [ ! -d "$FRAMEWORK_PATH" ]; then
        err "Could not find Sparkle.framework after extraction"
        err "Contents of ${BUILD_DIR}:"
        ls -la "$BUILD_DIR" 2>/dev/null
        exit 1
    fi

    if [ "$FRAMEWORK_PATH" != "$SPARKLE_FRAMEWORK" ]; then
        mv "$FRAMEWORK_PATH" "$SPARKLE_FRAMEWORK"
    fi

    rm -f "${BUILD_DIR}/${SPARKLE_ZIP}"
    log "Sparkle.framework downloaded to ${SPARKLE_FRAMEWORK}"
fi

# ---------------------------------------------------------------------------
# Step 2: Compile the Swift helper dylib
# ---------------------------------------------------------------------------
if [ -f "$HELPER_DYLIB" ] && [ "$HELPER_DYLIB" -nt "$SWIFT_SOURCE" ]; then
    log "Helper dylib is up to date — skipping"
else
    if [ ! -f "$SWIFT_SOURCE" ]; then
        err "Swift source not found: ${SWIFT_SOURCE}"
        exit 1
    fi

    log "Compiling Swift helper dylib..."
    FRAMEWORKS_DIR="$(dirname "$SPARKLE_FRAMEWORK")"

    swiftc \
        -emit-library \
        -o "$HELPER_DYLIB" \
        -F "$FRAMEWORKS_DIR" \
        -framework Sparkle \
        -Xlinker -rpath -Xlinker "@loader_path/../Frameworks" \
        -module-name SparkleHelper \
        "$SWIFT_SOURCE"

    if [ -f "$HELPER_DYLIB" ]; then
        log "Compiled: ${HELPER_DYLIB} ($(stat -f%z "$HELPER_DYLIB" 2>/dev/null || echo '?') bytes)"
    else
        err "Compilation failed"
        exit 1
    fi
fi

# ---------------------------------------------------------------------------
# Step 3: Verify
# ---------------------------------------------------------------------------
log ""
log "Sparkle helper build complete:"
log "  Framework: ${SPARKLE_FRAMEWORK}"
log "  Dylib:     ${HELPER_DYLIB}"

# Install to app resources so Gradle can bundle it
RESOURCES_DIR="${PROJECT_DIR}/src/main/resources/dist"
mkdir -p "${RESOURCES_DIR}/Frameworks"

# Copy Sparkle.framework
rm -rf "${RESOURCES_DIR}/Frameworks/Sparkle.framework" 2>/dev/null || true
cp -R "$SPARKLE_FRAMEWORK" "${RESOURCES_DIR}/Frameworks/Sparkle.framework"
log "  Copied Sparkle.framework → dist/Frameworks/"

# Copy helper dylib
cp "$HELPER_DYLIB" "${RESOURCES_DIR}/Frameworks/"
log "  Copied libSparkleHelper.dylib → dist/Frameworks/"
