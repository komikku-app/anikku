package android.content

/**
 * Expect declaration for android.content.Context.
 * Android: actual typealias = android.content.Context
 * JVM: actual class stub
 */
expect open class Context {
    val cacheDir: java.io.File
    fun getSharedPreferences(name: String, mode: Int): SharedPreferences

    companion object {
        val MODE_PRIVATE: Int
    }
}
