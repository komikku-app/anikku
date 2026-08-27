package android.webkit

/**
 * Stub for `android.webkit.WebSettings`.
 *
 * WebSettings is automatically instantiated by WebView. This stub allows
 * the WebView stub to load without throwing NoClassDefFoundError.
 */
open class WebSettings {

    var allowFileAccess: Boolean = false
    var allowContentAccess: Boolean = false
    var allowFileAccessFromFileURLs: Boolean = false
    var allowUniversalAccessFromFileURLs: Boolean = false
    var blockNetworkImage: Boolean = false
    var blockNetworkLoads: Boolean = false
    var builtInZoomControls: Boolean = false
    var cacheMode: Int = LOAD_DEFAULT
    var cursiveFontFamily: String = "cursive"
    var databaseEnabled: Boolean = false
    var defaultFixedFontSize: Int = 16
    var defaultFontSize: Int = 16
    var defaultTextEncodingName: String = "UTF-8"
    var displayZoomControls: Boolean = false
    var domStorageEnabled: Boolean = false
    var fantasyFontFamily: String = "fantasy"
    var fixedFontFamily: String = "monospace"
    var forceDark: Int = FORCE_DARK_OFF
    var geolocationEnabled: Boolean = false
    var javaScriptCanOpenWindowsAutomatically: Boolean = false
    var javaScriptEnabled: Boolean = false
    var layoutAlgorithm: LayoutAlgorithm = LayoutAlgorithm.NARROW_COLUMNS
    var loadWithOverviewMode: Boolean = false
    var loadsImagesAutomatically: Boolean = true
    var mediaPlaybackRequiresUserGesture: Boolean = true
    var minimumFontSize: Int = 8
    var minimumLogicalFontSize: Int = 8
    var mixedContentMode: Int = MIXED_CONTENT_NEVER_ALLOW
    var needInitialFocus: Boolean = false
    var offscreenPreRaster: Boolean = false
    var sansSerifFontFamily: String = "sans-serif"
    var serifFontFamily: String = "serif"
    var standardFontFamily: String = "sans-serif"
    var textZoom: Int = 100
    var useWideViewPort: Boolean = false
    var userAgentString: String? =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"

    enum class LayoutAlgorithm {
        NORMAL,
        SINGLE_COLUMN,
        NARROW_COLUMNS,
        TEXT_AUTOSIZING,
    }

    companion object {
        const val LOAD_DEFAULT: Int = -1
        const val LOAD_CACHE_ELSE_NETWORK: Int = 1
        const val LOAD_CACHE_ONLY: Int = 3
        const val LOAD_NORMAL: Int = 0
        const val LOAD_NO_CACHE: Int = 2

        const val FORCE_DARK_OFF: Int = 0
        const val FORCE_DARK_AUTO: Int = 1
        const val FORCE_DARK_ON: Int = 2

        const val MIXED_CONTENT_ALWAYS_ALLOW: Int = 0
        const val MIXED_CONTENT_NEVER_ALLOW: Int = 1
        const val MIXED_CONTENT_COMPATIBILITY_MODE: Int = 2
    }
}
