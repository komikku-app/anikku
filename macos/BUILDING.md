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

The current Compose surface uses libmpv's software-rendering path. Without
libmpv, the app runs in mock mode — the player UI works but no video will play.
Packaged application bundles include the required library.

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

For the normal pre-commit validation pass (compile, deterministic tests, and
Sparkle updater configuration):

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

Output: `macos/build/compose/binaries/main/dmg/Anikku-1.0.0.dmg`

Verify the generated application bundle and disk image:

```bash
./gradlew verifyPackage
hdiutil verify build/compose/binaries/main/dmg/Anikku-1.0.0.dmg
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
    └── EXTENSION-METADATA.md
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

### "Unresolved reference: MPVLib"

The mpv JNA bindings require libmpv at runtime. Make sure mpv is installed:

```bash
brew install mpv
```

### Build fails with "Kotlin compiler embedded"

Ensure you're using JDK 17+:

```bash
java -version
```

### "Native library not found" on first run

The app searches for `libmpv.1.dylib` in the following locations:
1. `/opt/homebrew/lib/libmpv.1.dylib` (Apple Silicon)
2. `/usr/local/lib/libmpv.1.dylib` (Intel)
3. `$APPDIR/Contents/Frameworks/libmpv.1.dylib` (bundled)

Install mpv via Homebrew to resolve this.

### Tests fail to run

Make sure you're using JDK 17 and have all test dependencies cached:

```bash
./gradlew clean test --no-daemon
```
