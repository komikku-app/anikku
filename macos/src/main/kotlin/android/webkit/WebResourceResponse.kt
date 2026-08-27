package android.webkit

import java.io.InputStream

/**
 * Stub for `android.webkit.WebResourceResponse`.
 *
 * Supports both the simple 3-arg constructor and the Android API 21+
 * 6-arg constructor (with status code, reason phrase, response headers).
 */
open class WebResourceResponse(
    val mimeType: String?,
    val encoding: String?,
    val data: InputStream?,
) {
    // Android API 21+ constructor: WebResourceResponse(
    //   String mimeType, String encoding, int statusCode,
    //   String reasonPhrase, Map<String, String> responseHeaders,
    //   InputStream data)
    constructor(
        mimeType: String?,
        encoding: String?,
        statusCode: Int,
        reasonPhrase: String?,
        responseHeaders: Map<String, String>?,
        data: InputStream?,
    ) : this(mimeType, encoding, data)
}
