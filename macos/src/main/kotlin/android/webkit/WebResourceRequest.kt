package android.webkit

/**
 * Stub for `android.webkit.WebResourceRequest`.
 */
interface WebResourceRequest {
    val url: java.net.URI?
    val isRedirect: Boolean
    val hasGesture: Boolean
    val method: String?
    val requestHeaders: Map<String, String>?
}
