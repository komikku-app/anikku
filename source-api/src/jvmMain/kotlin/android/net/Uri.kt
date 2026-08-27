package android.net

import java.io.File
import java.net.URI

/**
 * JVM stub for android.net.Uri.
 *
 * Provides a minimal implementation that can parse standard HTTP/HTTPS/file
 * URIs and return meaningful scheme/path values. This is needed because
 * Tachiyomi/Aniyomi extensions often construct Video objects using the
 * deprecated Video(url, quality, videoUrl, uri) constructor, which passes
 * android.net.Uri to the Video model.
 *
 * On Android the actual implementation is much richer (query parameters,
 * fragments, authority, etc.) but for extension compatibility we only need
 * scheme and path to be non-null for valid URIs.
 */
actual open class Uri private constructor(
    private val uriString: String,
) {
    private val uri: URI? by lazy {
        try { URI(uriString) } catch (_: Exception) { null }
    }

    actual val scheme: String? get() = uri?.scheme
    actual val path: String? get() = uri?.rawPath
    val authority: String? get() = uri?.rawAuthority
    val host: String? get() = uri?.host
    val port: Int get() = uri?.port ?: -1
    val query: String? get() = uri?.rawQuery
    val fragment: String? get() = uri?.rawFragment

    override fun toString(): String = uriString

    actual companion object {
        actual fun parse(uriString: String): Uri = Uri(uriString)
        actual fun fromFile(file: File): Uri = Uri(file.toURI().toString())
    }
}
