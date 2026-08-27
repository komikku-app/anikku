package android.webkit

/**
 * Stub for `android.webkit.URLUtil` on macOS JVM.
 *
 * Extensions (e.g., StreamingCommunity) use `URLUtil.isValidUrl()` to validate
 * user-supplied domain overrides. On macOS there is no Android framework, so
 * this stub provides a minimal URL-scheme check.
 */
object URLUtil {

    /** Returns true if [url] has an http/https scheme. */
    fun isValidUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    /** Returns true if [url] has an http/https scheme. */
    fun isHttpUrl(url: String): Boolean = isValidUrl(url)

    /** Returns true if [url] has an https scheme. */
    fun isHttpsUrl(url: String): Boolean = url.startsWith("https://", ignoreCase = true)
}
