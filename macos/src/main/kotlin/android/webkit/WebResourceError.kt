package android.webkit

/**
 * Stub for `android.webkit.WebResourceError` on macOS JVM.
 *
 * Used by [CloudflareInterceptor] in the `onReceivedError` callback.
 */
open class WebResourceError(
    val errorCode: Int,
    val description: CharSequence?,
)
