#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# copy-extension-deps.sh
#
# Copies extension runtime dependencies from the Gradle cache to macos/libs/
# so that buildClassLoader's shared-libs scan provides them to extension
# class loaders.
#
# This covers the most common library dependencies that Android anime
# extensions reference at runtime:
#   - kotlinx-serialization-json
#   - okio (used by kotlinx-serialization)
#   - okhttp3 (HTTP client)
#   - gson (JSON parsing, used by many extensions)
#   - jsoup (HTML parsing)
#
# Extensions compiled against Jackson or other libraries not yet in the
# classpath will produce NoSuchMethodError at runtime. The recommended
# approach is to build extensions from source via buildKeiyoushiExtension
# which compiles against the source-api JARs directly.
#
# Usage:
#   bash macos/scripts/copy-extension-deps.sh
#
# Run after a clean checkout or after upgrading dependency versions.
# ---------------------------------------------------------------------------
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIBS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)/libs"
CACHE="${HOME}/.gradle/caches/modules-2/files-2.1"

mkdir -p "$LIBS_DIR"

echo "=== Copying extension runtime deps to $LIBS_DIR ==="

copied=0

# Map of Maven group:artifact to copy
# Order: most commonly needed first
DEPS=(
    # Serialization (required by all modern extensions using kotlinx.serialization)
    "org.jetbrains.kotlinx/kotlinx-serialization-json-jvm"
    "org.jetbrains.kotlinx/kotlinx-serialization-json-okio-jvm"
    "com.squareup.okio/okio-jvm"

    # HTTP client (required by virtually all extensions)
    # Only copy okhttp-jvm (multiplatform JVM artifact) to avoid class conflicts
    # with the older plain okhttp artifact (4.x). Both export okhttp3.* packages.
    "com.squareup.okhttp3/okhttp-jvm"
    "com.squareup.okhttp3/logging-interceptor"

    # JSON parsing (gson is common, jackson is added by build.gradle.kts)
    "com.google.code.gson/gson"

    # HTML parsing (used by many source scrapers)
    "org.jsoup/jsoup"

    # Apache Commons — used by extensions for StringSubstitutor, encoding, etc.
    "org.apache.commons/commons-text"
    "commons-codec/commons-codec"
    "org.apache.commons/commons-lang3"
)

for dep in "${DEPS[@]}"; do
    jar=$(find "$CACHE/$dep" -name '*.jar' -not -name '*sources*' -not -name '*module*' 2>/dev/null | sort -V -r | head -1)
    if [ -z "$jar" ]; then
        echo "  WARNING: No JAR found for $dep"
        continue
    fi

    dest="$LIBS_DIR/$(basename "$jar")"
    if [ ! -f "$dest" ]; then
        cp -n "$jar" "$LIBS_DIR/" 2>/dev/null && echo "  Copied $(basename "$jar")" && copied=$((copied + 1))
    else
        echo "  Already present: $(basename "$jar")"
    fi
done

echo "=== Done: $copied JAR(s) copied ==="
