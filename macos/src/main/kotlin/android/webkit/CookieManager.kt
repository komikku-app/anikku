package android.webkit

/**
 * Stub for `android.webkit.CookieManager` on macOS JVM.
 *
 * Extensions and CloudflareInterceptor use CookieManager for cookie
 * operations. This stub provides a thread-safe in-memory cookie store
 * backed by [okhttp3.CookieJar] semantics where possible.
 */
open class CookieManager private constructor() {

    private val cookieStore = mutableMapOf<String, MutableList<String>>()

    /**
     * Sets a cookie for the given URL. The cookie value should be in the format
     * "name=value; Domain=...; Path=...; etc."
     */
    fun setCookie(url: String, value: String) {
        synchronized(cookieStore) {
            cookieStore.getOrPut(url) { mutableListOf() }.add(value)
        }
    }

    /**
     * Returns all cookies for the given URL as a semicolon-separated string.
     *
     * Matches by domain suffix and path prefix — a cookie set on
     * `https://example.com` will be returned for `https://example.com/path`.
     */
    fun getCookie(url: String): String {
        synchronized(cookieStore) {
            // Collect cookies from entries whose URL is a prefix or domain match
            val matches = mutableListOf<String>()
            for ((storedUrl, cookies) in cookieStore) {
                if (url.startsWith(storedUrl) ||
                    isDomainMatch(storedUrl, url)) {
                    matches.addAll(cookies)
                }
            }
            return matches.joinToString(";")
        }
    }

    private fun isDomainMatch(storedUrl: String, requestUrl: String): Boolean {
        val storedHost = try { java.net.URI(storedUrl).host } catch (_: Exception) { null } ?: return false
        val requestHost = try { java.net.URI(requestUrl).host } catch (_: Exception) { null } ?: return false
        return requestHost.endsWith(storedHost) && requestHost != storedHost
    }

    fun removeAllCookies(callback: (() -> Unit)?) {
        synchronized(cookieStore) {
            cookieStore.clear()
        }
        callback?.invoke()
    }

    @Suppress("unused")
    @Deprecated("Use removeAllCookies(callback) instead")
    fun removeAllCookie() {
        synchronized(cookieStore) {
            cookieStore.clear()
        }
    }

    fun removeSessionCookies(callback: (() -> Unit)?) {
        synchronized(cookieStore) {
            cookieStore.clear()
        }
        callback?.invoke()
    }

    fun flush() {
        // No persistent storage — no-op
    }

    fun setAcceptCookie(accept: Boolean) {
        // No-op on macOS
    }

    fun setAcceptThirdPartyCookies(webView: WebView?, accept: Boolean) {
        // No-op on macOS
    }

    fun acceptThirdPartyCookies(webView: WebView) {
        // No-op on macOS
    }

    fun hasCookies(): Boolean {
        synchronized(cookieStore) {
            return cookieStore.values.any { it.isNotEmpty() }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): CookieManager = Holder.INSTANCE

        private object Holder {
            val INSTANCE = CookieManager()
        }
    }
}
