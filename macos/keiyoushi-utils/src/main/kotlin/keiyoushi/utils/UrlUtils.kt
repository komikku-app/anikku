package keiyoushi.utils

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlUtils {

    private val firstHttpsRegex by lazy { Regex("""^.*(?=https?://)""") }

    fun fixUrl(url: String): String? = when {
        url.isEmpty() -> null
        url.startsWith("http") ||
            // Do not fix JSON objects when passed as urls.
            url.startsWith("{\"") -> url
        url.startsWith("//") -> "https:$url"
        else -> url.replaceFirst(firstHttpsRegex, "")
    }

    fun fixUrl(url: String, baseUrl: String): String? {
        val baseHttpUrl = baseUrl.toHttpUrlOrNull() ?: return null
        return when {
            url.isEmpty() -> null
            url.startsWith("http") ||
                // Do not fix JSON objects when passed as urls.
                url.startsWith("{\"") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> {
                // Will be: http[s]://<domain>/<url>
                baseHttpUrl.newBuilder().encodedPath("/").build().toString()
                    .substringBeforeLast("/") + url
            }
            else -> {
                // Ensure the path starts with '/' for proper baseUrl concatenation.
                val normalizedUrl = if (url.startsWith("/")) url else "/$url"

                // Build the parent path by removing the last path segment
                val currentPath = baseHttpUrl.encodedPath
                val parentPath = if (currentPath.count { it == '/' } > 1) {
                    currentPath.substringBeforeLast("/") + "/"
                } else {
                    "/"
                }

                // Rebuild base URL with the parent path (preserves scheme + host)
                // Note: HttpUrl.Builder.encodedPath() is a method, not a property setter
                val basePath = baseHttpUrl.newBuilder()
                    .encodedPath(parentPath)
                    .query(null)
                    .fragment(null)
                    .build()
                    .toString()

                basePath + normalizedUrl.trimStart('/')
            }
        }
    }

}
