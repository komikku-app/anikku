package android.webkit

/**
 * Stub for `android.webkit.HttpAuthHandler`.
 */
open class HttpAuthHandler {
    fun proceed(username: String, password: String) = Unit
    fun cancel() = Unit
    fun useHttpAuthUsernamePassword(): Boolean = false
}
