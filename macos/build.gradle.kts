import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
    testRuntimeOnly(libs.junit.vintage.engine)
}

tasks.test {
    dependsOn("buildTestExtensionJar")
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
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

tasks.named("compileKotlin") {
    dependsOn("rebuildSourceApiJars")
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
                "-Djava.library.path=\$APPDIR/Contents/Frameworks",
            )

            macOS {
                bundleID = "app.anikku.macos"
                iconFile.set(project.file("src/main/resources/icons/app.icns"))
                minimumSystemVersion = "12.0"
                entitlementsFile.set(project.file("src/main/resources/entitlements.plist"))

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

            // Bundle native libraries into Contents/Resources/
            appResourcesRootDir.set(file("src/main/resources/dist"))
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

    onlyIf {
        val force = project.findProperty("force") as? String == "true"
        force || !file(dylibPath).isFile
    }

    commandLine("bash", scriptPath)
}

// Wire Sparkle helper build before packageDmg
tasks.whenTaskAdded {
    if (name == "packageDmg") {
        dependsOn("buildSparkleHelper")
    }
}

/**
 * Patch the generated Info.plist with Sparkle auto-updater keys.
 * Runs after packageDmg to inject SUFeedURL and SUPublicEDKey.
 *
 * Usage:
 *   ./gradlew -p macos patchInfoPlist -PappPath=/path/to/Anikku.app
 *
 * The Info.plist Sparkle entries are:
 *   SUFeedURL: https://anikku.app/sparkle/appcast.xml
 *   SUPublicEDKey: (from src/main/resources/Sparkle/ed25519_pub.pem)
 *   NSHighResolutionCapable: true
 *   LSApplicationCategoryType: public.app-category.entertainment
 */
val patchInfoPlistAppPath: String? by project

tasks.register("patchInfoPlist") {
    description = "Inject Sparkle keys into the generated Info.plist"
    group = "distribution"

    doLast {
        val appDir = patchInfoPlistAppPath?.let { file(it) }
            ?: file("build/compose/binaries/main/dmg/Anikku.app")

        if (!appDir.isDirectory) {
            logger.warn("App bundle not found at ${appDir.absolutePath}")
            logger.warn("Provide -PappPath=/path/to/Anikku.app")
            return@doLast
        }

        val infoPlistFile = File(appDir, "Contents/Info.plist")
        if (!infoPlistFile.isFile) {
            logger.warn("Info.plist not found — skipping")
            return@doLast
        }

        val publicKey = readSparklePublicKey()
        val feedUrl = "https://anikku.app/sparkle/appcast.xml"

        if (publicKey.startsWith("PLACEHOLDER")) {
            logger.warn("Sparkle Ed25519 public key is a placeholder!")
            logger.warn("Generate keys: openssl genpkey -algorithm ed25519 -out ed25519-key.pem")
            logger.warn("             openssl pkey -in ed25519-key.pem -pubout -out ed25519-pub.pem")
            logger.warn("Then copy ed25519-pub.pem to macos/src/main/resources/Sparkle/ed25519_pub.pem")
        }

        var plist = infoPlistFile.readText()

        val sparkleEntries = """
    <key>SUFeedURL</key>
    <string>${feedUrl}</string>
    <key>SUPublicEDKey</key>
    <string>${publicKey}</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>LSApplicationCategoryType</key>
    <string>public.app-category.entertainment</string>
"""

        if (plist.contains("SUFeedURL")) {
            logger.lifecycle("  Info.plist already has SUFeedURL — skipping")
        } else {
            plist = plist.replace("</dict>\n</plist>", "${sparkleEntries}</dict>\n</plist>")
            infoPlistFile.writeText(plist)
            logger.lifecycle("  Patched Info.plist with Sparkle keys")
            logger.lifecycle("    SUFeedURL: ${feedUrl}")
            logger.lifecycle("    SUPublicEDKey: ${publicKey.take(24)}...")
        }
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
            ?: file("build/compose/binaries/main/dmg/Anikku.app").let {
                if (it.isDirectory) it.absolutePath
                else {
                    val dmgDir = file("build/compose/binaries/main/dmg")
                    val found = dmgDir.listFiles()?.find { it.extension == "app" }
                    found?.absolutePath ?: throw GradleException(
                        "Cannot find .app bundle. Build packageDmg first.",
                    )
                }
            }

        val appDir = file(app)
        if (!appDir.isDirectory) {
            throw GradleException("App bundle not found at: $app")
        }

        logger.lifecycle("Verifying: ${appDir.absolutePath}")

        val infoPlist = File(appDir, "Contents/Info.plist")
        if (infoPlist.isFile) {
            val text = infoPlist.readText()
            logger.lifecycle("  [Info.plist] Found (${text.length} bytes)")
            if ("SUFeedURL" in text) logger.lifecycle("    SUFeedURL: present")
            else logger.lifecycle("    SUFeedURL: missing")
            if ("SUPublicEDKey" in text) logger.lifecycle("    SUPublicEDKey: present")
            else logger.lifecycle("    SUPublicEDKey: missing")
        } else {
            logger.lifecycle("  [Info.plist] NOT FOUND")
        }

        // Check libmpv
        val libmpv = listOf(
            File(appDir, "Contents/Frameworks/libmpv.2.dylib"),
            File(appDir, "Contents/Resources/libmpv.2.dylib"),
        ).firstOrNull { it.isFile }
        if (libmpv != null) {
            logger.lifecycle("  [libmpv] Found (${libmpv.length()} bytes)")
        } else {
            logger.lifecycle("  [libmpv] Not bundled")
        }

        // Check code signing
        try {
            val proc = ProcessBuilder("codesign", "-dvvv", "--deep", appDir.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.reader().readText()
            if (proc.waitFor() == 0) {
                val authority = output.lines().find { it.contains("Authority=") }
                    ?.substringAfter("Authority=") ?: "unknown"
                logger.lifecycle("  [Signing] Signed (Authority: $authority)")
            } else {
                logger.lifecycle("  [Signing] Not signed")
            }
        } catch (_: Exception) {
            logger.lifecycle("  [Signing] Cannot verify")
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
        logger.lifecycle("  patchInfoPlist      - Inject Sparkle keys into Info.plist")
        logger.lifecycle("  submitForNotarization - Submit DMG for Apple notarization")
        logger.lifecycle("  verifyPackage       - Verify .app bundle integrity")
        logger.lifecycle("  generateAppcast     - Generate Sparkle appcast entry")
        logger.lifecycle("")
        logger.lifecycle("Workflow:")
        logger.lifecycle("  1. ./gradlew -p macos packageDmg                   # Build DMG")
        logger.lifecycle("  2. ./gradlew -p macos patchInfoPlist               # Add Sparkle keys")
        logger.lifecycle("  3. ./gradlew -p macos verifyPackage                # Verify bundle")
        logger.lifecycle("  4. ./gradlew -p macos packageDmg -Psign=true       # Sign")
        logger.lifecycle("  5. ./gradlew -p macos submitForNotarization -PdmgPath=...    # Notarize")
        logger.lifecycle("")
    }
}

// ---- Sparkle Public Key ----------------------------------------------------
fun readSparklePublicKey(): String {
    val pemFile = file("src/main/resources/Sparkle/ed25519_pub.pem")
    return if (pemFile.isFile) {
        pemFile.readLines()
            .filterNot { it.startsWith("-") || it.isBlank() }
            .joinToString("")
            .trim()
            .ifEmpty { "PLACEHOLDER_SPARKLE_PUBLIC_KEY" }
    } else {
        "PLACEHOLDER_SPARKLE_PUBLIC_KEY"
    }
}

// Wire patchInfoPlist after packageDmg using whenTaskAdded
tasks.whenTaskAdded {
    if (name == "packageDmg") {
        finalizedBy("patchInfoPlist")
    }
}
