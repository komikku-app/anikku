plugins {
    id("mihon.library")
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("com.github.ben-manes.versions")
}

android {
    namespace = "eu.kanade.tachiyomi.core.common"
}

kotlin {
    androidTarget()
    jvm()

    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.i18n)
                // SY -->
                implementation(projects.i18nSy)
                // SY <--

                api(libs.rxjava)

                api(libs.okhttp.core)
                api(libs.okhttp.logging)
                api(libs.okhttp.brotli)
                api(libs.okhttp.dnsoverhttps)
                api(libs.okio)

                implementation(libs.jsoup)

                // Sort
                implementation(libs.natural.comparator)

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                api(kotlinx.serialization.json)
                api(kotlinx.serialization.json.okio)
            }
        }

        androidMain {
            dependencies {
                implementation(projects.i18n)
                // SY -->
                implementation(projects.i18nSy)
                // SY <--

                api(libs.logcat)
                api(libs.rxjava)

                api(libs.okhttp.core)
                api(libs.okhttp.logging)
                api(libs.okhttp.brotli)
                api(libs.okhttp.dnsoverhttps)
                api(libs.okio)

                implementation(libs.image.decoder)

                implementation(libs.unifile)
                implementation(libs.libarchive)

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                api(kotlinx.serialization.json)
                api(kotlinx.serialization.json.okio)

                api(libs.preferencektx)

                implementation(libs.jsoup)

                implementation(libs.natural.comparator)

                // JavaScript engine
                implementation(libs.bundles.js.engine)

                // FFmpeg-kit
                implementation(aniyomilibs.ffmpeg.kit)

                // SY -->
                implementation(sylibs.xlog)
                implementation(sylibs.exifinterface)
                // SY <--

                implementation(libs.injekt)
                implementation(aniyomilibs.torrentserver)
            }
        }

        jvmMain {
            dependencies {
                implementation(projects.i18n)
                // SY -->
                implementation(projects.i18nSy)
                // SY <--

                api(libs.logcat)
                api(libs.rxjava)

                api(libs.okhttp.core)
                api(libs.okhttp.logging)
                api(libs.okhttp.brotli)
                api(libs.okhttp.dnsoverhttps)
                api(libs.okio)

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                api(kotlinx.serialization.json)
                api(kotlinx.serialization.json.okio)

                implementation(libs.injekt)
                implementation(libs.jsoup)
                implementation(libs.natural.comparator)
            }
        }
    }
}
