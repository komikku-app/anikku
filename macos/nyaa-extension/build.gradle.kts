plugins {
    kotlin("jvm") version "2.2.20"
}

repositories {
    mavenCentral()
}

dependencies {
    // RxJava 1.x — needed for stub interface compatibility
    compileOnly("io.reactivex:rxjava:1.3.8")

    // Jsoup — HTML parsing
    compileOnly("org.jsoup:jsoup:1.21.2")
    // OkHttp — needed by source-api JAR's Video.Headers type
    compileOnly("com.squareup.okhttp3:okhttp:5.1.0")

    // Source-api JARs — compiled against the real source-api types instead of stubs.
    compileOnly(files("${project.projectDir}/../libs/source-api-jvm.jar"))
    compileOnly(files("${project.projectDir}/../libs/common-jvm.jar"))
}

kotlin {
    jvmToolchain(17)
}

/**
 * Build the Nyaa.si extension JAR.
 *
 * Usage:
 *   ./gradlew -p macos buildNyaaExtension
 *   OR from the nyaa-extension dir:
 *   ./gradlew buildNyaaExtensionJar
 *
 * The JAR is produced at build/libs/nyaa-extension-1.0.0.jar.
 * Install it:
 *   cp build/libs/nyaa-extension-1.0.0.jar ~/Library/Application\ Support/Anikku/extensions/
 */
tasks.register<Jar>("buildNyaaExtensionJar") {
    dependsOn("compileKotlin")
    archiveBaseName.set("nyaa-extension")
    archiveVersion.set("1.0.0")

    // Include compiled extension classes ONLY
    from(layout.buildDirectory.dir("classes/kotlin/main")) {
        include("eu/kanade/tachiyomi/animeextension/en/nyaasi/**")
    }

    // Include extension metadata
    from("src/main/resources") {
        include("META-INF/extension.json")
    }

    manifest {
        attributes(
            "Implementation-Title" to "NyaaExtension",
            "Implementation-Version" to "1.0.0",
        )
    }
}
