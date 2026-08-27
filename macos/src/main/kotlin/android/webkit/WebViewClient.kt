package android.webkit

import android.graphics.Bitmap
import android.net.http.SslError

/**
 * Stub for `android.webkit.WebViewClient`.
 */
open class WebViewClient {

    open fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = Unit
    open fun onPageFinished(view: WebView, url: String) = Unit
    open fun onLoadResource(view: WebView, url: String?) = Unit

    open fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
    open fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = false

    open fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }

    open fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) = Unit

    open fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String,
        realm: String,
    ) = Unit

    open fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) = Unit

    open fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? = null
    open fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? = null
}
