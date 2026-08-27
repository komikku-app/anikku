package android.webkit

/**
 * Stub for `android.webkit.WebView`.
 *
 * Extensions compiled for Android may reference WebView for fetching content
 * (e.g., via AnikotoTheme). On macOS, instantiating a real WebView is not
 * possible — this stub allows the extension's class to load without crashing
 * the JVM with a NoClassDefFoundError.
 *
 * Any method calls to this stub return no-ops or empty values.
 */
open class WebView(context: android.content.Context) {

    open class WebViewTransport {
        var webView: WebView? = null
    }

    // Visual state
    var visibility: Int = android.view.View.VISIBLE

    // Settings
    val settings: WebSettings get() = WebSettings()

    // Client
    var webViewClient: WebViewClient? = null

    // JavaScript interface
    fun addJavascriptInterface(obj: Any, interfaceName: String) {}

    fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
        resultCallback?.onReceiveValue(null)
    }

    fun loadUrl(url: String) = Unit
    fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) = Unit
    fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) = Unit

    fun stopLoading() = Unit
    fun destroy() = Unit

    fun canGoBack(): Boolean = false
    fun canGoForward(): Boolean = false
    fun goBack() = Unit
    fun goForward() = Unit

    fun reload() = Unit

    fun clearHistory() = Unit
    fun clearCache(includeDiskFiles: Boolean) = Unit
    fun clearFormData() = Unit

    val url: String? get() = null

    companion object {
        @Suppress("unused")
        fun setWebContentsDebuggingEnabled(enabled: Boolean) = Unit
    }
}
