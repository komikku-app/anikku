package android.content.res

/**
 * Stub for `android.content.res.Configuration` on macOS JVM.
 */
open class Configuration {
    var orientation: Int = ORIENTATION_UNDEFINED

    companion object {
        const val ORIENTATION_UNDEFINED: Int = 0
        const val ORIENTATION_PORTRAIT: Int = 1
        const val ORIENTATION_LANDSCAPE: Int = 2
    }
}
