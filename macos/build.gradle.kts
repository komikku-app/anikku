import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.plugin)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin {
                // Include keiyoushi-utils sources so extensions can resolve
                // keiyoushi.utils.* classes (ContextKt, Network, Crypto, etc.)
                // at runtime through the URLClassLoader parent delegation.
                srcDir("keiyoushi-utils/src/main/kotlin")
            }
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
    implementation(libs.serialization.json)

    // Koin DI (replaces Injekt)
    implementation(libs.koin.core)

    // Logging (SLF4J + Logback)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging)

    // SQLDelight (JDBC driver for desktop)
    implementation(libs.sqldelight.jdbc.driver)
    implementation(libs.sqldelight.coroutines)

    // Voyager navigation (desktop compatible)
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.screenmodel)
    implementation(libs.voyager.tab.navigator)
    implementation(libs.voyager.transitions)

    // Material Kolor - dynamic color scheme generation
    implementation(libs.material.kolor)

    // Material Motion - shared axis transitions
    implementation(libs.material.motion)

    // Coil 3 - image loading (Compose Desktop compatible)
    implementation(platform("io.coil-kt.coil3:coil-bom:3.3.0"))
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Moko Resources - i18n string resources (KMP, JVM compatible)
    implementation(libs.moko.resources)

    // Markdown renderer - changelog/about screens
    implementation(libs.markdown.core)
    implementation(libs.markdown.coil)

    // Shared module dependencies (desktop-compatible)
    implementation(libs.rxjava)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.okio)
    implementation(libs.jsoup)
    implementation(libs.disklrucache)
    implementation(libs.kotlinx.immutables)

    // Source API (JVM target) and core/common — replaces macOS stubs
    implementation(files("libs/source-api-jvm.jar"))
    implementation(files("libs/common-jvm.jar"))

    // Transitive deps from source-api/core/common
    implementation("com.github.mihonapp:injekt:91edab2317")
    implementation(kotlin("reflect"))
    implementation("com.github.gpanther:java-nat-sort:natural-comparator-1.1")

    // NanoHTTPd — embedded HTTP server for local video streaming
    implementation(libs.nanohttpd)

    // JNA - Java Native Access for macOS native API calls
    implementation(libs.jna.core)
    implementation(libs.jna.platform)

    // JSON processing
    implementation(libs.org.json)

    // Jackson — required by many keiyoushi extensions for JSON parsing
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.2")

    // Apache Commons — used by extensions
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("commons-codec:commons-codec:1.17.1")
    implementation("org.apache.commons:commons-lang3:3.17.0")

    // kotlinx.serialization-protobuf
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf-jvm:1.9.0")

    // QuickJS — JavaScript engine for extension deobfuscation
    implementation("app.cash.quickjs:quickjs-jvm:0.9.2")

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testRuntimeOnly(libs.junit.vintage.engine)
}

tasks.test {
    dependsOn("buildTestExtensionJar")
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

val slowValidationTests = listOf(
    "**/*IntegrationTest.class",
    "**/ExtensionCompatibilityTest.class",
    "**/StreamingEndToEndTest.class",
    "**/MPVPlaybackTest.class",
    "**/MPVRenderExperiment.class",
)

tasks.register<Test>("quickTest") {
    description = "Run deterministic tests without live extensions, streaming, or local-media playback"
    group = "verification"
    dependsOn("buildTestExtensionJar")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    exclude(slowValidationTests)
    testLogging {
        events("passed", "failed", "skipped")
    }
}

tasks.register("quickCheck") {
    description = "Compile the app, run deterministic tests, and validate updater configuration"
    group = "verification"
    dependsOn("quickTest", "validateSparkleConfiguration", "validateTorrServerConfiguration")
}

// ---- Extension Build Tasks ------------------------------------------------

tasks.register<Jar>("buildTestExtensionJar") {
    dependsOn("compileKotlin")
    archiveBaseName.set("test-extension")
    archiveVersion.set("1.0.0")
    from("${layout.buildDirectory.get()}/classes/kotlin/main") {
        include("app/anikku/macos/testextension/**")
    }
    from("src/main/resources/test-extension") {
        into("META-INF")
    }
    manifest {
        attributes("Implementation-Title" to "TestExtension", "Implementation-Version" to "1.0.0")
    }
}

tasks.register<Exec>("rebuildSourceApiJars") {
    workingDir = rootProject.projectDir.parentFile
    commandLine(
        "bash", "-c",
        "./gradlew :source-api:jvmJar :core:common:jvmJar --no-daemon -q && " +
        "cp \$(ls source-api/build/libs/source-api-jvm-*.jar | grep -v -- -sources | head -1) macos/libs/source-api-jvm.jar && " +
        "cp \$(ls core/common/build/libs/common-jvm-*.jar | grep -v -- -sources | head -1) macos/libs/common-jvm.jar && " +
        "echo '=== Copying extension runtime deps ===' && " +
        "bash macos/scripts/copy-extension-deps.sh",
    )
}

val refreshSourceApi = providers.gradleProperty("refreshSourceApi")
    .map(String::toBoolean)
    .orElse(false)

tasks.named("compileKotlin") {
    if (refreshSourceApi.get()) {
        dependsOn("rebuildSourceApiJars")
    }
}

tasks.register<Exec>("downloadKeiyoushiExtension") {
    description = "Download an extension JAR/APK for reference"
    group = "verification"

    val extNameFilter = project.findProperty("extName") as? String ?: "allanime"
    val extensionsDir = "${System.getProperty("user.home")}/Library/Application Support/Anikku/extensions"
    val D = "$"

    commandLine(
        "bash", "-c",
        """
set -euo pipefail
OUT_DIR="$extensionsDir"
mkdir -p "${D}OUT_DIR"
JAR_INDEX="https://raw.githubusercontent.com/ErnestHysa/anikku-extensions-jar/main/index.min.json"
APK_INDEX="https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"
PYTHON_SCRIPT='import sys, json
data = json.load(sys.stdin)
ext_name = "$extNameFilter".lower().strip()
for ext in data:
    name = ext.get("name", "").lower()
    lang = ext.get("lang", "")
    pkg = ext.get("pkg", "").lower()
    apk = ext.get("apk", "").lower()
    if ext_name in name or ext_name in pkg or ext_name in apk:
        print(json.dumps(ext)); sys.exit(0)
if data: print(json.dumps(data[0]))
else: print("ERROR: Empty index"); sys.exit(1)'
JAR_JSON=$(curl -sL "${D}JAR_INDEX" | python3 -c "${D}PYTHON_SCRIPT" 2>/dev/null || echo "")
if [ -n "${D}JAR_JSON" ]; then
    APK_NAME=$(echo "${D}JAR_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["apk"])' 2>/dev/null || echo "")
    if [ -n "${D}APK_NAME" ]; then
        curl -sL "https://raw.githubusercontent.com/ErnestHysa/anikku-extensions-jar/main/${D}APK_NAME" -o "${D}OUT_DIR/${D}APK_NAME"
        if [ -s "${D}OUT_DIR/${D}APK_NAME" ]; then
            echo "Downloaded $(wc -c < "${D}OUT_DIR/${D}APK_NAME") bytes"
            exit 0
        fi
    fi
fi
APK_JSON=$(curl -sL "${D}APK_INDEX" | python3 -c "${D}PYTHON_SCRIPT" 2>/dev/null || echo "")
if [ -n "${D}APK_JSON}" ]; then
    APK_NAME=$(echo "${D}APK_JSON}" | python3 -c 'import sys,json; print(json.load(sys.stdin)["apk"])' 2>/dev/null || echo "")
    if [ -n "${D}APK_NAME" ]; then
        curl -sL "https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/${D}APK_NAME" -o "${D}OUT_DIR/${D}APK_NAME"
        echo "Downloaded $(wc -c < "${D}OUT_DIR/${D}APK_NAME") bytes"
        exit 0
    fi
fi
echo "ERROR: Could not find extension matching '$extNameFilter'"
exit 1
""".trimIndent(),
    )
}

val buildKeiyoushiExtName: String? by project
val buildKeiyoushiExtLang: String? by project

// ---- Nyaa.si Extension Build -----------------------------------------------

/**
 * Build the Nyaa.si torrent search extension JAR.
 *
 * The Nyaa.si extension is a separate Gradle sub-project at macos/nyaa-extension/.
 * This task delegates to that project's build script.
 *
 * Usage:
 *   ./gradlew -p macos buildNyaaExtension
 *
 * After building, the JAR is at:
 *   macos/nyaa-extension/build/libs/nyaa-extension-1.0.0.jar
 *
 * Install it:
 *   cp macos/nyaa-extension/build/libs/nyaa-extension-1.0.0.jar \
 *     ~/Library/Application\ Support/Anikku/extensions/
 */
tasks.register("buildNyaaExtension") {
    description = "Build the Nyaa.si torrent search extension JAR"
    group = "extension"

    doLast {
        val extDir = file("${project.projectDir}/nyaa-extension")
        if (!extDir.isDirectory) {
            throw GradleException("Nyaa extension directory not found at ${extDir.absolutePath}")
        }

        logger.lifecycle("Building Nyaa.si extension from ${extDir.absolutePath}")

        project.exec {
            workingDir = extDir
            commandLine(
                "./gradlew", "buildNyaaExtensionJar", "--no-daemon", "-q",
            )
            environment("JAVA_HOME", System.getenv("JAVA_HOME") ?: "/opt/homebrew/opt/openjdk@17")
        }

        val jarFile = file("${extDir}/build/libs/nyaa-extension-1.0.0.jar")
        if (jarFile.isFile) {
            logger.lifecycle("✅ Nyaa.si extension built: ${jarFile.absolutePath} (${jarFile.length()} bytes)")

            // Optionally copy to extensions directory
            val extensionsDir = file("${System.getProperty("user.home")}/Library/Application Support/Anikku/extensions")
            if (extensionsDir.isDirectory) {
                val target = File(extensionsDir, "eu.kanade.tachiyomi.animeextension.en.nyaasi.jar")
                jarFile.copyTo(target, overwrite = true)
                logger.lifecycle("✅ Copied to: ${target.absolutePath}")
            }
        } else {
            logger.warn("⚠️ JAR not found at expected path: ${jarFile.absolutePath}")
        }
    }
}

tasks.register("buildKeiyoushiExtension") {
    description = "Build a single yuzono anime extension from source as JVM JAR"
    group = "extension"

    doLast {
        val extName = buildKeiyoushiExtName
            ?: throw GradleException("Usage: -PbuildKeiyoushiExtName=<name>")
        val extLang = buildKeiyoushiExtLang?.ifBlank { "en" } ?: "en"
        val scriptPath = "${project.projectDir}/scripts/build-keiyoushi-from-source.sh"

        logger.lifecycle("Building extension: $extName (lang: $extLang)")
        logger.lifecycle("Script: $scriptPath")

        project.exec {
            commandLine(
                "bash", scriptPath,
                "--pkg", extName,
                "--lang", extLang,
            )
            workingDir = project.projectDir
        }
    }
}

val batchExtLang: String? by project
val batchExtLimit: String? by project

tasks.register("batchBuildKeiyoushiExtensions") {
    description = "Batch-build ALL yuzono anime extensions from source as JVM JARs"
    group = "extension"

    doLast {
        val scriptPath = "${project.projectDir}/scripts/batch-build-keiyoushi-from-source.sh"
        val lang = (batchExtLang?.ifBlank { "en" }) ?: "en"

        logger.lifecycle("Batch-building extensions for language: $lang")
        logger.lifecycle("Script: $scriptPath")

        val args = mutableListOf("bash", scriptPath, "--lang", lang)
        val limit = batchExtLimit
        if (!limit.isNullOrBlank()) {
            args.add("--limit")
            args.add(limit)
        }

        project.exec {
            commandLine(args)
            workingDir = project.projectDir
        }
    }
}

// ---- Package Version Property ----------------------------------------------
val appVersion: String by project
val appVersionName: String by project

// Pinned native torrent helper. Distribution builds fetch the binary for the
// build machine's architecture and verify it before placing it in the .app.
val torrServerVersion = "MatriX.141.1"
val torrServerArchitecture = when (System.getProperty("os.arch").lowercase()) {
    "aarch64", "arm64" -> "arm64"
    else -> "amd64"
}
val torrServerChecksums = mapOf(
    "arm64" to "a91adbfcec069a0db204ae909d098832d16220c154e86e409b10e2f243e1c7f9",
    "amd64" to "fbf13d00e9619524b3caba12302886151e3e84219fd08549bb6f585285dfc5ab",
)
val torrServerBinaryName = "TorrServer-darwin-$torrServerArchitecture"
val torrServerBinary = layout.buildDirectory.file("torrserver/$torrServerBinaryName")

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

tasks.register("validateTorrServerConfiguration") {
    description = "Validate the pinned TorrServer release metadata without downloading it"
    group = "verification"
    doLast {
        val checksum = torrServerChecksums.getValue(torrServerArchitecture)
        require(Regex("[0-9a-f]{64}").matches(checksum)) { "Invalid TorrServer SHA-256" }
        val uri = URI("https://github.com/YouROK/TorrServer/releases/download/$torrServerVersion/$torrServerBinaryName")
        require(uri.scheme == "https" && uri.host == "github.com") { "TorrServer download must use GitHub HTTPS" }
        logger.lifecycle("TorrServer configuration valid: $torrServerVersion $torrServerArchitecture")
    }
}

tasks.register("downloadTorrServer") {
    description = "Download and verify the pinned native TorrServer helper"
    group = "distribution"
    dependsOn("validateTorrServerConfiguration")
    inputs.property("version", torrServerVersion)
    inputs.property("architecture", torrServerArchitecture)
    inputs.property("sha256", torrServerChecksums.getValue(torrServerArchitecture))
    outputs.file(torrServerBinary)
    outputs.upToDateWhen {
        val target = torrServerBinary.get().asFile
        target.isFile && sha256(target) == torrServerChecksums.getValue(torrServerArchitecture)
    }

    doLast {
        val target = torrServerBinary.get().asFile
        val expected = torrServerChecksums.getValue(torrServerArchitecture)
        if (target.isFile && sha256(target) == expected) {
            target.setExecutable(true, false)
            logger.lifecycle("Using verified cached ${target.name}")
            return@doLast
        }

        target.parentFile.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.download")
        val uri = URI("https://github.com/YouROK/TorrServer/releases/download/$torrServerVersion/$torrServerBinaryName")
        logger.lifecycle("Downloading $uri")
        uri.toURL().openStream().use { input ->
            temporary.outputStream().buffered().use(input::copyTo)
        }
        val actual = sha256(temporary)
        if (actual != expected) {
            temporary.delete()
            throw GradleException("TorrServer checksum mismatch: expected $expected, got $actual")
        }
        Files.move(
            temporary.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
        check(target.setExecutable(true, false)) { "Unable to make ${target.name} executable" }
    }
}

tasks.register<Test>("nativeTorrServerTest") {
    description = "Launch the verified bundled TorrServer and exercise its localhost JSON API"
    group = "verification"
    dependsOn("downloadTorrServer", "compileTestKotlin")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    include("**/TorrentServerNativeIntegrationTest.class")
    systemProperty("anikku.test.torrserver.bin", torrServerBinary.get().asFile.absolutePath)
    testLogging { events("passed", "failed", "skipped") }
}

// Compose Desktop only consumes app resources from common/, <os>/, or
// <os>-<arch>/ below appResourcesRootDir. Keep the checked-in native binaries
// in their existing layout and create the expected hierarchy as a build output.
val prepareNativeAppResources by tasks.registering(Sync::class) {
    description = "Stage native libraries for Compose Desktop packaging"
    group = "distribution"
    dependsOn("buildSparkleHelper", "buildBiometricHelper", "downloadTorrServer")

    from("src/main/resources/dist") {
        into("common")
        exclude("Frameworks/Sparkle.framework/**")
        exclude("Frameworks/libSparkleHelper.dylib")
    }
    from(layout.buildDirectory.dir("sparkle")) {
        into("common/Frameworks")
        include("Sparkle.framework/**")
        include("libSparkleHelper.dylib")
    }
    from(layout.buildDirectory.dir("native")) {
        into("common/Frameworks")
        include("libAnikkuBiometric.dylib")
    }
    from(layout.buildDirectory.dir("torrserver")) {
        into("common/TorrServer")
        include("TorrServer-darwin-*")
        filePermissions { unix("rwxr-xr-x") }
    }
    from("THIRD_PARTY_NOTICES.md") {
        into("common")
    }
    into(layout.buildDirectory.dir("native-app-resources"))
}

// ---- Desktop Application Configuration ------------------------------------

compose.desktop {
    application {
        mainClass = "app.anikku.macos.AnikkuAppKt"

        jvmArgs += listOf(
            "-Xmx2G",
            "-Dapple.awt.application.appearance=system",
            "--add-exports", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-exports", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg)
            packageName = "Anikku"
            packageVersion = appVersion
            description = "A native macOS anime watching application"
            vendor = "Anikku"
            licenseFile.set(project.file("../LICENSE"))

            // Add JVM args for native library search path (Sparkle helper dylib)
            // and module system access for JNA reflective calls.
            jvmArgs += listOf(
                "-Djava.library.path=\$APPDIR/resources/Frameworks:\$APPDIR/resources",
            )

            macOS {
                bundleID = "app.anikku.macos"
                iconFile.set(project.file("src/main/resources/icons/app.icns"))
                minimumSystemVersion = "12.0"
                appCategory = "public.app-category.entertainment"
                entitlementsFile.set(project.file("src/main/resources/entitlements.plist"))
                infoPlist {
                    val publicKey = project.file("src/main/resources/Sparkle/ed25519_pub.pem")
                        .readLines()
                        .filterNot { it.trim().startsWith("-") || it.isBlank() }
                        .joinToString("")
                        .trim()
                    extraKeysRawXml = """
                        <key>SUFeedURL</key>
                        <string>https://anikku.app/sparkle/appcast.xml</string>
                        <key>SUPublicEDKey</key>
                        <string>$publicKey</string>
                        <key>NSFaceIDUsageDescription</key>
                        <string>Use biometric authentication to unlock Anikku.</string>
                    """.trimIndent()
                }

                // ---- Code Signing Configuration ----
                // Usage: ./gradlew -p macos packageDmg -Psign=true
                val shouldSign = project.findProperty("sign") as? String ?: "false"
                if (shouldSign == "true") {
                    val signIdentity = project.findProperty("signIdentity") as? String
                        ?: "Developer ID Application: Komikku App (TEAMID)"

                    signing {
                        sign.set(true)
                        identity.set(signIdentity)
                    }
                }
            }

            // Compose places these files in Contents/app/resources/ and exposes
            // that directory through compose.application.resources.dir.
            appResourcesRootDir.set(layout.buildDirectory.dir("native-app-resources"))
        }
    }
}

// ---- Build & Package Tasks -------------------------------------------------

/**
 * Download Sparkle.framework and compile the Swift helper dylib.
 * Only runs when the dylib doesn't exist (cached) or --force passed.
 *
 * Usage:
 *   ./gradlew -p macos buildSparkleHelper
 *   ./gradlew -p macos buildSparkleHelper -Pforce=true
 */
tasks.register<Exec>("buildSparkleHelper") {
    description = "Download Sparkle.framework and compile the Swift helper dylib"
    group = "distribution"

    val scriptPath = "${project.projectDir}/scripts/build-sparkle-helper.sh"
    val dylibPath = "${layout.buildDirectory.get()}/sparkle/libSparkleHelper.dylib"
    inputs.file(scriptPath)
    inputs.file("src/main/swift/SparkleHelper.swift")
    outputs.file(dylibPath)
    outputs.dir("${layout.buildDirectory.get()}/sparkle/Sparkle.framework")
    outputs.upToDateWhen { project.findProperty("force") as? String != "true" }

    commandLine("bash", scriptPath)
}

tasks.register<Exec>("buildBiometricHelper") {
    description = "Compile the LocalAuthentication helper dylib"
    group = "distribution"

    val scriptPath = "${project.projectDir}/scripts/build-biometric-helper.sh"
    val dylibPath = "${layout.buildDirectory.get()}/native/libAnikkuBiometric.dylib"
    inputs.file(scriptPath)
    inputs.file("src/main/swift/BiometricHelper.swift")
    outputs.file(dylibPath)

    commandLine("bash", scriptPath)
}

// Wire Sparkle helper build and configuration validation before packageDmg.
tasks.whenTaskAdded {
    if (name == "packageDmg") {
        dependsOn("buildSparkleHelper")
        dependsOn("validateSparkleConfiguration")
    }
    if (name == "prepareAppResources") {
        dependsOn(prepareNativeAppResources)
    }
}

/**
 * Notarize a built DMG for macOS distribution.
 *
 * Requires:
 *   - A signed DMG (build with -Psign=true first)
 *   - APPLE_ID, APPLE_TEAM_ID, APPLE_PASSWORD env vars
 *
 * Usage:
 *   ./gradlew -p macos submitForNotarization -PdmgPath=/path/to/Anikku-1.0.0.dmg
 */
val submitForNotarizationDmg: String? by project

tasks.register<Exec>("submitForNotarization") {
    description = "Submit the DMG for Apple notarization"
    group = "distribution"

    doFirst {
        val dmgFile = submitForNotarizationDmg
            ?: throw GradleException("Usage: -PdmgPath=/path/to/Anikku.dmg (required)")
        val dmg = file(dmgFile)
        if (!dmg.isFile) {
            throw GradleException("DMG not found: ${dmg.absolutePath}")
        }

        val appleId = System.getenv("APPLE_ID")
            ?: throw GradleException("APPLE_ID env var not set")
        val teamId = System.getenv("APPLE_TEAM_ID")
            ?: throw GradleException("APPLE_TEAM_ID env var not set")
        val password = System.getenv("APPLE_PASSWORD") ?: "@keychain:AC_PASSWORD"

        logger.lifecycle("Notarizing: ${dmg.absolutePath}")
        logger.lifecycle("  Apple ID: ${appleId}")
        logger.lifecycle("  Team ID: ${teamId}")

        commandLine(
            "xcrun", "notarytool", "submit", dmg.absolutePath,
            "--apple-id", appleId,
            "--team-id", teamId,
            "--password", password,
            "--wait",
        )
    }

    doLast {
        logger.lifecycle("  If successful, staple: xcrun stapler staple ${submitForNotarizationDmg}")
    }
}

/**
 * Verify the packaged .app bundle has all required components.
 *
 * Usage:
 *   ./gradlew -p macos verifyPackage -PappPath=/path/to/Anikku.app
 */
val verifyAppPath: String? by project

tasks.register("verifyPackage") {
    description = "Verify the packaged .app bundle"
    group = "distribution"

    doLast {
        val app = verifyAppPath
            ?: file("build/compose/binaries/main/app/Anikku.app").absolutePath

        val appDir = file(app)
        if (!appDir.isDirectory) {
            throw GradleException("App bundle not found at: $app")
        }

        logger.lifecycle("Verifying: ${appDir.absolutePath}")

        val infoPlist = File(appDir, "Contents/Info.plist")
        if (infoPlist.isFile) {
            val text = infoPlist.readText()
            logger.lifecycle("  [Info.plist] Found (${text.length} bytes)")
            listOf("SUFeedURL", "SUPublicEDKey", "LSApplicationCategoryType", "NSFaceIDUsageDescription").forEach { key ->
                if (key !in text) throw GradleException("Info.plist is missing $key")
                logger.lifecycle("    $key: present")
            }
        } else {
            throw GradleException("Info.plist not found")
        }

        val resourcesDir = File(appDir, "Contents/app/resources")
        val libmpv = listOf(
            File(resourcesDir, "libmpv.2.dylib"),
            File(appDir, "Contents/Frameworks/libmpv.2.dylib"),
            File(appDir, "Contents/Resources/libmpv.2.dylib"),
        ).firstOrNull { it.isFile }
        if (libmpv != null) {
            logger.lifecycle("  [libmpv] Found (${libmpv.length()} bytes)")
        } else {
            throw GradleException("libmpv.2.dylib is not bundled")
        }

        val sparkleHelper = File(resourcesDir, "Frameworks/libSparkleHelper.dylib")
        val sparkleFramework = File(resourcesDir, "Frameworks/Sparkle.framework/Versions/B/Sparkle")
        if (!sparkleHelper.isFile) throw GradleException("Sparkle helper dylib is not bundled")
        if (!sparkleFramework.isFile) throw GradleException("Sparkle.framework is not bundled")
        logger.lifecycle("  [Sparkle] Framework and helper found")

        val biometricHelper = File(resourcesDir, "Frameworks/libAnikkuBiometric.dylib")
        if (!biometricHelper.isFile) throw GradleException("LocalAuthentication helper dylib is not bundled")
        logger.lifecycle("  [Touch ID] LocalAuthentication helper found")

        val torrServer = File(resourcesDir, "TorrServer/$torrServerBinaryName")
        if (!torrServer.isFile || !torrServer.canExecute()) {
            throw GradleException("Executable $torrServerBinaryName is not bundled")
        }
        if (sha256(torrServer) != torrServerChecksums.getValue(torrServerArchitecture)) {
            throw GradleException("Bundled $torrServerBinaryName failed checksum verification")
        }
        logger.lifecycle("  [TorrServer] $torrServerVersion $torrServerArchitecture verified")

        val launcher = File(appDir, "Contents/MacOS/Anikku")
        val javaRuntime = File(appDir, "Contents/runtime/Contents/MacOS/libjli.dylib")
        if (!launcher.isFile) throw GradleException("Native launcher is missing")
        if (!javaRuntime.isFile) throw GradleException("Bundled Java runtime is missing")
        logger.lifecycle("  [Runtime] Launcher and Java runtime found")

        try {
            val proc = ProcessBuilder("codesign", "--verify", "--deep", "--strict", "--verbose=2", appDir.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.reader().readText()
            if (proc.waitFor() == 0) {
                logger.lifecycle("  [Signing] Bundle signature is valid")
            } else {
                throw GradleException("Bundle signature is invalid:\n$output")
            }
        } catch (e: GradleException) {
            throw e
        } catch (e: Exception) {
            throw GradleException("Could not verify bundle signature", e)
        }
    }
}

/**
 * List all available distribution-related tasks.
 */
tasks.register("listDistributionTasks") {
    description = "List all distribution-related Gradle tasks"
    group = "distribution"

    doLast {
        logger.lifecycle("")
        logger.lifecycle("Available distribution tasks:")
        logger.lifecycle("  packageDmg          - Build unsigned DMG")
        logger.lifecycle("  packageDmg -Psign=true - Build signed DMG")
        logger.lifecycle("  submitForNotarization - Submit DMG for Apple notarization")
        logger.lifecycle("  verifyPackage       - Verify .app bundle integrity")
        logger.lifecycle("  generateAppcast     - Generate Sparkle appcast entry")
        logger.lifecycle("")
        logger.lifecycle("Workflow:")
        logger.lifecycle("  1. ./gradlew -p macos packageDmg                   # Build DMG")
        logger.lifecycle("  2. ./gradlew -p macos verifyPackage                # Verify bundle")
        logger.lifecycle("  3. ./gradlew -p macos packageDmg -Psign=true       # Sign")
        logger.lifecycle("  4. ./gradlew -p macos submitForNotarization -PdmgPath=...    # Notarize")
        logger.lifecycle("")
    }
}

// ---- Sparkle Public Key and feed validation -------------------------------
val sparkleFeedUrl = "https://anikku.app/sparkle/appcast.xml"
val sparkleEd25519SubjectPublicKeyLength = 44
val sparkleEd25519SignatureLength = 64

fun readSparklePublicKey(): String {
    val pemFile = file("src/main/resources/Sparkle/ed25519_pub.pem")
    if (!pemFile.isFile) {
        throw GradleException("Sparkle Ed25519 public key is missing: ${pemFile.path}")
    }
    val key = pemFile.readLines()
        .filterNot { it.trim().startsWith("-") || it.isBlank() }
        .joinToString("")
        .trim()
    if (key.isBlank()) {
        throw GradleException("Sparkle Ed25519 public key is empty")
    }
    if (key.contains("PLACEHOLDER", ignoreCase = true)) {
        throw GradleException("Sparkle Ed25519 public key is still a placeholder")
    }

    val der = try {
        Base64.getDecoder().decode(key)
    } catch (e: IllegalArgumentException) {
        throw GradleException("Sparkle Ed25519 public key is not valid base64", e)
    }
    val expectedPrefix = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65,
        0x70, 0x03, 0x21, 0x00,
    )
    if (der.size != sparkleEd25519SubjectPublicKeyLength ||
        !der.copyOfRange(0, expectedPrefix.size).contentEquals(expectedPrefix)
    ) {
        throw GradleException("Sparkle Ed25519 public key is not a DER-encoded Ed25519 SubjectPublicKeyInfo")
    }
    return key
}

private fun validateSparkleFeedUrl(value: String) {
    val uri = URI(value)
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) {
        throw GradleException("Sparkle feed URL must be an authenticated HTTPS URL without credentials/fragments")
    }
}

tasks.register("validateSparkleConfiguration") {
    description = "Validate Sparkle's public key, feed URL, and signed appcast entries"
    group = "verification"

    doLast {
        val publicKey = readSparklePublicKey()
        validateSparkleFeedUrl(sparkleFeedUrl)
        val appcastFile = file("src/main/resources/Sparkle/appcast.xml")
        if (!appcastFile.isFile) {
            throw GradleException("Sparkle appcast is missing: ${appcastFile.path}")
        }

        val document = try {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }.newDocumentBuilder().parse(appcastFile)
        } catch (e: Exception) {
            throw GradleException("Sparkle appcast is not valid safe XML", e)
        }

        val enclosures = document.getElementsByTagName("enclosure")
        for (index in 0 until enclosures.length) {
            val enclosure = enclosures.item(index)
            val url = enclosure.attributes?.getNamedItem("url")?.nodeValue.orEmpty()
            val signature = enclosure.attributes?.getNamedItem("sparkle:edSignature")?.nodeValue.orEmpty()
            val length = enclosure.attributes?.getNamedItem("length")?.nodeValue.orEmpty()
            if (!url.startsWith("https://") || URI(url).host.isNullOrBlank()) {
                throw GradleException("Sparkle enclosure $index does not use a valid HTTPS URL")
            }
            if (signature.contains("REPLACE_WITH", ignoreCase = true) || signature.isBlank()) {
                throw GradleException("Sparkle enclosure $index has no real Ed25519 signature")
            }
            val decodedSignature = try { Base64.getDecoder().decode(signature) } catch (e: IllegalArgumentException) {
                throw GradleException("Sparkle enclosure $index has an invalid base64 signature", e)
            }
            if (decodedSignature.size != sparkleEd25519SignatureLength) {
                throw GradleException("Sparkle enclosure $index signature must be 64 bytes")
            }
            if (length.toLongOrNull()?.takeIf { it > 0 } == null) {
                throw GradleException("Sparkle enclosure $index must declare a positive artifact length")
            }
        }

        logger.lifecycle("Sparkle configuration valid: Ed25519 key (${publicKey.length} base64 chars), HTTPS feed, ${enclosures.length} signed appcast enclosure(s)")
    }
}
