# Building Anikku macOS

This guide explains how to build the Anikku macOS application from source.

## Prerequisites

### JDK 17+

Anikku requires JDK 17 or later. We recommend using [SDKMAN](https://sdkman.io/) or Homebrew:

```bash
# Via SDKMAN
sdk install java 17.0.19-tem
sdk use java 17.0.19-tem

# Via Homebrew
brew install openjdk@17
```

Verify your Java version:

```bash
java -version
# openjdk version "17.0.19" 2026-01-20
```

### libmpv (Required for Video Playback)

For native video playback during development, install libmpv:

```bash
brew install mpv
```

The Compose surface uses libmpv's Render API with a software frame-transfer
surface and `hwdec=auto-copy-safe` when the platform supports it. Without
libmpv, the app remains usable but playback reports an unavailable/error state;
it never simulates watch progress. Packaged builds include the required ABI 2
library.

### Gradle

The project includes a Gradle wrapper (`gradlew`), so you don't need to install Gradle separately.

## Build Commands

All commands should be run from the `macos/` directory:

```bash
cd macos
```

### Compile

```bash
./gradlew compileKotlin
```

This uses the existing `libs/source-api-jvm.jar` and `libs/common-jvm.jar`, so
normal incremental builds do not rebuild the Android/shared projects.

Refresh those shared JARs when their source has changed:

```bash
./gradlew compileKotlin -PrefreshSourceApi=true
```

### Run

```bash
./gradlew run
```

This compiles and launches the application.

### Run Tests

```bash
./gradlew quickTest
```

`quickTest` runs the deterministic unit, UI-state, security, storage, and local
HTTP tests. It excludes live extension-site sweeps, real streaming, and tests
that require a local media file.

Run the entire suite, including live network and playback checks, explicitly:

```bash
./gradlew test
```

Useful focused native/release gates:

```bash
# Download/checksum/launch the pinned native TorrServer and query its JSON API
./gradlew nativeTorrServerTest

# Generate a high-bitrate 1080p60 fixture; verify playback, seeks, and heap bounds
./gradlew performancePlaybackTest

# Production renderer lifecycle and visible-pixel tests
./gradlew test --tests MPVRenderExperiment

# Live extension browse → episode → stream → play → seek acceptance
./gradlew test --tests StreamingEndToEndTest
```

Live tests depend on third-party sites and are evidence for the moment they run,
not a permanent source-availability guarantee.

For the normal pre-commit validation pass (compile, deterministic tests, and
Sparkle/TorrServer configuration):

```bash
./gradlew quickCheck
```

### Package as .app Bundle

```bash
./gradlew packageDmg
```

Compose packaging rejects Homebrew's JDK vendor by default. Prefer Temurin or
Corretto for release builds. If Homebrew is the only installed JDK, the same
local build can be attempted with the documented Compose override:

```bash
./gradlew packageDmg -Pcompose.desktop.packaging.checkJdkVendor=false
```

Output: `macos/build/compose/binaries/main/dmg/Anikku-1.0.2.dmg`

Packaging downloads the architecture-specific TorrServer `MatriX.141.1`
helper from its HTTPS GitHub release, verifies the pinned SHA-256, and stages it
with libmpv, Sparkle, the Touch ID helper, third-party notices, and the Java
runtime. The downloaded helper is cached under `build/torrserver/`.

Verify the generated application bundle and disk image:

```bash
./gradlew verifyPackage
hdiutil verify build/compose/binaries/main/dmg/Anikku-1.0.2.dmg
```

For Developer ID signing and notarization (requires owner credentials):

```bash
./gradlew packageDmg -Psign=true \
  -PsignIdentity="Developer ID Application: Name (TEAMID)"

APPLE_ID="account@example.com" \
APPLE_TEAM_ID="TEAMID" \
APPLE_PASSWORD="@keychain:AC_PASSWORD" \
./gradlew submitForNotarization \
  -PdmgPath=build/compose/binaries/main/dmg/Anikku-1.0.2.dmg

xcrun stapler staple build/compose/binaries/main/dmg/Anikku-1.0.2.dmg
```

### Package as .pkg Installer

```bash
./gradlew packagePkg
```

### Clean Build

```bash
./gradlew clean
```

### Full Build (Compile + Test + Package)

```bash
./gradlew build
```

The full build intentionally includes every test and can take much longer than
`quickCheck` because extension compatibility depends on external anime sites,
Cloudflare challenges, DNS, and streaming timeouts.

## Project Structure

```
macos/
├── build.gradle.kts              # Main build configuration
├── settings.gradle.kts           # Project settings
├── gradle.properties             # JVM and Compose properties
├── gradle/
│   ├── libs.versions.toml        # Version catalog
│   └── wrapper/                  # Gradle wrapper
├── src/
│   ├── main/
│   │   ├── kotlin/app/anikku/macos/
│   │   │   ├── AnikkuApp.kt          # Entry point
│   │   │   ├── AnikkuApplication.kt  # App lifecycle
│   │   │   ├── di/                   # Koin modules
│   │   │   ├── platform/             # Platform adapters
│   │   │   │   ├── auth/             # OAuth server
│   │   │   │   ├── database/         # SQLDelight driver
│   │   │   │   ├── discord/          # Discord RPC
│   │   │   │   ├── extension/        # Extension loader
│   │   │   │   ├── logging/          # Logging & crash reporting
│   │   │   │   ├── media/            # FFmpeg, HTTP server
│   │   │   │   ├── network/          # OkHttp client, cookies
│   │   │   │   ├── notification/     # macOS notifications
│   │   │   │   ├── preference/       # JSON preference store
│   │   │   │   ├── security/         # Biometric auth
│   │   │   │   ├── storage/          # File system management
│   │   │   │   ├── sync/             # Cloud sync
│   │   │   │   ├── update/           # Update checker
│   │   │   │   └── web/              # Browser launcher
│   │   │   ├── player/              # mpv JNA integration
│   │   │   │   ├── MPVLib.kt        # JNA bindings
│   │   │   │   ├── MPVEventLoop.kt  # Event processing
│   │   │   │   ├── MPVVideoSurface.kt # Render surface
│   │   │   │   ├── PlayerViewModel.kt # State management
│   │   │   │   └── Utils.kt         # Formatting utilities
│   │   │   └── ui/                  # Compose Desktop UI
│   │   │       ├── theme/           # Color schemes, typography
│   │   │       ├── components/      # Reusable components
│   │   │       ├── screens/         # Screen implementations
│   │   │       └── settings/        # Settings screen
│   │   └── resources/
│   │       ├── entitlements.plist   # macOS entitlements
│   │       └── icons/               # App icons
│   └── test/
│       └── kotlin/app/anikku/macos/ # Unit & UI tests
└── docs/
    ├── ARCHITECTURE.md
    ├── MPV-JNA.md
    └── extension development and migration guides
```

## IDE Setup

### IntelliJ IDEA

1. Open the root `anikku/` project in IntelliJ IDEA
2. File → Project Structure → Project SDK → Select JDK 17
3. Open `macos/settings.gradle.kts` and click "Load Gradle Project"
4. Run configuration: Create a Gradle Run Configuration with task `:macos:run`

### VS Code

1. Install the "Kotlin" extension
2. Install the "Gradle for Java" extension
3. Open the root `anikku/` directory
4. Run Gradle tasks from the VS Code command palette

## Troubleshooting

### Player reports that libmpv is unavailable

Development runs require libmpv at runtime. Make sure mpv is installed:

```bash
brew install mpv
```

### Build fails with "Kotlin compiler embedded"

Ensure you're using JDK 17+:

```bash
java -version
```

### "Native library not found" on a development run

The app checks the packaged resources directory first, then Homebrew and
MacPorts ABI 2/ABI 1 paths, including:

1. `compose.application.resources.dir/libmpv.2.dylib` (packaged JVM property)
2. `/opt/homebrew/lib/libmpv.2.dylib` (Apple Silicon Homebrew)
3. `/usr/local/lib/libmpv.2.dylib` (Intel Homebrew)
4. `/opt/local/lib/libmpv.2.dylib` (MacPorts)
5. ABI 1 compatibility paths and JNA's normal library lookup

Install mpv via Homebrew to resolve this.

The complete native ownership and threading contract is documented in
[docs/MPV-JNA.md](docs/MPV-JNA.md).

### Tests fail to run

Make sure you're using JDK 17 and have all test dependencies cached:

```bash
./gradlew clean test --no-daemon
```
