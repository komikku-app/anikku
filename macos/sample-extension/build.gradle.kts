plugins {
    kotlin("jvm") version "2.2.20"
}

repositories {
    mavenCentral()
}

dependencies {
    // RxJava 1.x — needed for stub interface compatibility (compileOnly = not bundled in JAR)
    compileOnly("io.reactivex:rxjava:1.3.8")

    // OkHttp — available on the app classpath, used by the sample source for HTTP fetching
    compileOnly("com.squareup.okhttp3:okhttp:5.1.0")
    compileOnly("org.jsoup:jsoup:1.21.2")

    // Source-api JARs — compiled against the real source-api types instead of stubs.
    // These JARs are pre-built by the root project and must exist at build time.
    compileOnly(files("${rootProject.projectDir}/../libs/source-api-jvm.jar"))
    compileOnly(files("${rootProject.projectDir}/../libs/common-jvm.jar"))
}

kotlin {
    jvmToolchain(17)
}

/**
 * Build the sample extension JAR.
 *
 * Usage:
 *   ./gradlew buildExtensionJar
 *
 * The JAR is produced at build/libs/sample-extension-1.0.0.jar.
 * Install it:
 *   cp build/libs/sample-extension-1.0.0.jar ~/Library/Application\ Support/Anikku/extensions/com.example.animeextension.jar
 *
 * Note: Unlike real extension JARs, this JAR includes stub classes for
 * source-api interfaces (CatalogueSource, Source, SAnime, etc.) because
 * these are typealiases in the main module that don't generate runtime class files.
 */
tasks.register<Jar>("buildExtensionJar") {
    dependsOn("compileKotlin")
    archiveBaseName.set("sample-extension")
    archiveVersion.set("1.0.0")

    // Include compiled extension classes ONLY (no stub interfaces).
    // The sample extension now compiles against the real source-api JARs (compileOnly),
    // so runtime type identity is preserved — no need for stubs.
    from(layout.buildDirectory.dir("classes/kotlin/main")) {
        include("com/example/animeextension/**")
    }

    // Include extension metadata
    from("src/main/resources") {
        include("META-INF/extension.json")
    }

    manifest {
        attributes(
            "Implementation-Title" to "SampleExtension",
            "Implementation-Version" to "1.0.0",
        )
    }
}
