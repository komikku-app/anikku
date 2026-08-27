package android.os

/**
 * Stub for `android.os.Build` on macOS JVM.
 */
object Build {
    const val BOARD: String = "anikku"
    const val BRAND: String = "anikku"
    const val DEVICE: String = "anikku"
    const val FINGERPRINT: String = "anikku/1.0"
    const val HARDWARE: String = "anikku"
    const val HOST: String = "localhost"
    const val ID: String = "ANIKKU"
    const val MANUFACTURER: String = "Anikku"
    const val MODEL: String = "Anikku"
    const val PRODUCT: String = "anikku"
    const val TAGS: String = "release-keys"
    const val TYPE: String = "user"
    const val USER: String = "anikku"
    const val UNKNOWN: String = "unknown"

    /** Time of system build in seconds since epoch */
    const val TIME: Long = 0L

    object VERSION {
        const val CODENAME: String = "REL"
        const val INCREMENTAL: String = "1"
        const val RELEASE: String = "14"
        const val SDK_INT: Int = 34
        const val SECURITY_PATCH: String = "2024-01-05"
        const val BASE_OS: String = ""
        const val PREVIEW_SDK_INT: Int = 0
        const val RESOURCES_SDK_INT: Int = 34

        @JvmStatic
        fun getActiveCodename(): String = "REL"
    }

    object VERSION_CODES {
        const val BASE: Int = 1
        const val BASE_1_1: Int = 2
        const val CUPCAKE: Int = 3
        const val CUR_DEVELOPMENT: Int = 10000
        const val DONUT: Int = 4
        const val ECLAIR: Int = 5
        const val ECLAIR_0_1: Int = 6
        const val ECLAIR_MR1: Int = 7
        const val FROYO: Int = 8
        const val GINGERBREAD: Int = 9
        const val GINGERBREAD_MR1: Int = 10
        const val HONEYCOMB: Int = 11
        const val HONEYCOMB_MR1: Int = 12
        const val HONEYCOMB_MR2: Int = 13
        const val ICE_CREAM_SANDWICH: Int = 14
        const val ICE_CREAM_SANDWICH_MR1: Int = 15
        const val JELLY_BEAN: Int = 16
        const val JELLY_BEAN_MR1: Int = 17
        const val JELLY_BEAN_MR2: Int = 18
        const val KITKAT: Int = 19
        const val KITKAT_WATCH: Int = 20
        const val LOLLIPOP: Int = 21
        const val LOLLIPOP_MR1: Int = 22
        const val M: Int = 23
        const val N: Int = 24
        const val N_MR1: Int = 25
        const val O: Int = 26
        const val O_MR1: Int = 27
        const val P: Int = 28
        const val Q: Int = 29
        const val R: Int = 30
        const val S: Int = 31
        const val S_V2: Int = 32
        const val TIRAMISU: Int = 33
        const val UPSIDE_DOWN_CAKE: Int = 34
    }
}
