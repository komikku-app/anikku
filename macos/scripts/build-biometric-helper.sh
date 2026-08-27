#!/usr/bin/env bash
# Build the LocalAuthentication bridge used by MacOSBiometricAuth.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="${PROJECT_DIR}/build/native"
SWIFT_SOURCE="${PROJECT_DIR}/src/main/swift/BiometricHelper.swift"
HELPER_DYLIB="${BUILD_DIR}/libAnikkuBiometric.dylib"

case "$(uname -m)" in
    arm64) TARGET_ARCH="arm64" ;;
    x86_64) TARGET_ARCH="x86_64" ;;
    *) echo "Unsupported macOS architecture: $(uname -m)" >&2; exit 1 ;;
esac

mkdir -p "$BUILD_DIR"

swiftc \
    -parse-as-library \
    -emit-library \
    -target "${TARGET_ARCH}-apple-macosx12.0" \
    -framework LocalAuthentication \
    -module-name AnikkuBiometric \
    -o "$HELPER_DYLIB" \
    "$SWIFT_SOURCE"

test -s "$HELPER_DYLIB"
file "$HELPER_DYLIB"
nm -gU "$HELPER_DYLIB" | grep -q '_anikku_biometric_can_evaluate'
nm -gU "$HELPER_DYLIB" | grep -q '_anikku_biometric_evaluate'
echo "Biometric helper built: $HELPER_DYLIB"
