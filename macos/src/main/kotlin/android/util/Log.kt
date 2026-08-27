package android.util

/**
 * Stub for `android.util.Log` on macOS JVM.
 *
 * Extensions compiled for Android call `Log.d()`, `Log.e()`, etc. for logging.
 * This stub provides the class so the JVM can resolve it without throwing
 * NoClassDefFoundError. All log messages are discarded.
 */
object Log {
    const val DEBUG: Int = 3
    const val ERROR: Int = 6
    const val INFO: Int = 4
    const val VERBOSE: Int = 2
    const val WARN: Int = 5

    @JvmStatic
    fun d(tag: String, msg: String): Int = 0

    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable): Int = 0

    @JvmStatic
    fun e(tag: String, msg: String): Int = 0

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable): Int = 0

    @JvmStatic
    fun i(tag: String, msg: String): Int = 0

    @JvmStatic
    fun i(tag: String, msg: String, tr: Throwable): Int = 0

    @JvmStatic
    fun v(tag: String, msg: String): Int = 0

    @JvmStatic
    fun v(tag: String, msg: String, tr: Throwable): Int = 0

    @JvmStatic
    fun w(tag: String, msg: String): Int = 0

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable): Int = 0

    @JvmStatic
    fun w(tag: String, tr: Throwable): Int = 0

    @JvmStatic
    fun wtf(tag: String, msg: String): Int = 0

    @JvmStatic
    fun wtf(tag: String, tr: Throwable): Int = 0

    @JvmStatic
    fun wtf(tag: String, msg: String, tr: Throwable): Int = 0

    @JvmStatic
    fun println(priority: Int, tag: String, msg: String): Int = 0

    @JvmStatic
    fun isLoggable(tag: String, level: Int): Boolean = false

    @JvmStatic
    fun getStackTraceString(tr: Throwable): String = ""
}
