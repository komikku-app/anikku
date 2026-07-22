package android.net

import java.io.File

/**
 * Stub for `android.net.Uri` on macOS JVM.
 *
 * Delegates to [java.net.URI] internally for parsing and provides
 * the Android Uri API surface needed by extensions and app code.
 */
open class Uri private constructor(
    private val uriString: String,
    private val jvmUri: java.net.URI,
) {

    val scheme: String? get() = jvmUri.scheme
    val host: String? get() = jvmUri.host
    val port: Int get() = jvmUri.port
    val path: String? get() = jvmUri.rawPath
    val query: String? get() = jvmUri.rawQuery
    val fragment: String? get() = jvmUri.rawFragment
    val authority: String? get() = jvmUri.rawAuthority

    val lastPathSegment: String?
        get() {
            val p = path ?: return null
            val trimmed = p.trimEnd('/')
            return trimmed.substringAfterLast('/').ifEmpty { null }
        }

    val pathSegments: List<String>
        get() {
            val p = path ?: return emptyList()
            return p.split("/").filter { it.isNotEmpty() }
        }

    fun getQueryParameter(key: String): String? {
        val q = query ?: return null
        val value = q.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it[0] == key }
            ?.getOrNull(1)
            ?: return null
        // Android's getQueryParameter URL-decodes the value automatically.
        // Extensions expect decoded strings (e.g. Base64 data, JSON tokens).
        return safeDecode(value)
    }

    fun getQueryParameterNames(): Set<String> {
        val q = query ?: return emptySet()
        return q.split("&")
            .map { it.split("=", limit = 2)[0] }
            .toSet()
    }

    fun buildUpon(): Builder = Builder().apply {
        scheme(this@Uri.scheme)
        authority(this@Uri.authority)
        path(this@Uri.path)
        query(this@Uri.query)
        fragment(this@Uri.fragment)
    }

    /**
     * Resolves this (possibly relative) Uri against [base].
     * Equivalent to Android's `base.buildUpon().appendEncodedPath(getPath()).build()` semantics.
     */
    fun resolveUri(base: Uri?): Uri {
        if (base == null) return this
        // Simple resolve: if this Uri is relative, resolve against base
        if (jvmUri.isAbsolute) return this
        return try {
            parse(base.jvmUri.resolve(jvmUri).toString())
        } catch (_: Exception) {
            this
        }
    }

    override fun toString(): String = uriString

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Uri) return false
        return uriString == other.uriString
    }

    override fun hashCode(): Int = uriString.hashCode()

    class Builder {
        private var scheme: String? = null
        private var authority: String? = null
        private var path: String? = null
        private var query: String? = null
        private var fragment: String? = null
        private var opaquePart: String? = null

        fun scheme(scheme: String?): Builder = apply { this.scheme = scheme }
        fun authority(authority: String?): Builder = apply { this.authority = authority }
        fun path(path: String?): Builder = apply { this.path = path }
        fun query(query: String?): Builder = apply { this.query = query }
        fun fragment(fragment: String?): Builder = apply { this.fragment = fragment }
        fun opaquePart(opaquePart: String?): Builder = apply { this.opaquePart = opaquePart }
        fun appendPath(newSegment: String): Builder = apply {
            path = if (path != null) "$path/$newSegment" else "/$newSegment"
        }
        fun appendQueryParameter(key: String, value: String?): Builder = apply {
            val pair = if (value != null) "$key=$value" else key
            query = if (query != null) "$query&$pair" else pair
        }

        fun build(): Uri {
            if (opaquePart != null) {
                val sb = StringBuilder()
                if (scheme != null) sb.append(scheme).append(":")
                sb.append(opaquePart)
                if (fragment != null) sb.append("#").append(fragment)
                return parse(sb.toString())
            }
            val sb = StringBuilder()
            if (scheme != null) sb.append(scheme).append("://")
            if (authority != null) sb.append(authority)
            if (path != null) sb.append(path)
            if (query != null) sb.append("?").append(query)
            if (fragment != null) sb.append("#").append(fragment)
            return parse(sb.toString())
        }
    }

    companion object {
        @JvmField
        val EMPTY: Uri = Uri("", java.net.URI("about:empty"))

        @JvmStatic
        fun parse(uriString: String): Uri {
            return try {
                val jvmUri = java.net.URI(uriString)
                Uri(uriString, jvmUri)
            } catch (_: Exception) {
                // Android Uri.parse() never throws — returns a Uri even for invalid strings
                Uri(uriString, java.net.URI(""))
            }
        }

        @JvmStatic
        fun fromFile(file: File): Uri {
            return parse(file.toURI().toString())
        }

        @JvmStatic
        fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

        @JvmStatic
        fun encode(value: String, allow: String): String {
            // Simple encoding with allowed characters
            return java.net.URLEncoder.encode(value, "UTF-8")
                .replace("+", "%20")
        }

        @JvmStatic
        fun decode(value: String): String = safeDecode(value)

        @JvmStatic
        fun withAppendedPath(baseUri: Uri, pathSegment: String): Uri {
            return baseUri.buildUpon().appendPath(pathSegment).build()
        }

        /**
         * Lenient URL decoder that mimics Android's tolerant behavior.
         * java.net.URLDecoder throws IllegalArgumentException on malformed
         * percent escapes (e.g. stray "%" without 2 hex digits), but Android
         * silently ignores them. Anime streaming sites frequently have
         * malformed percent-encoding in their URLs.
         */
        private fun safeDecode(value: String): String {
            return try {
                java.net.URLDecoder.decode(value, "UTF-8")
            } catch (_: IllegalArgumentException) {
                // Fix malformed percent sequences by encoding stray % signs
                // before retrying. Pattern: % not followed by 2 hex digits.
                val fixed = value.replace(Regex("%(?![0-9a-fA-F]{2})"), "%25")
                try {
                    java.net.URLDecoder.decode(fixed, "UTF-8")
                } catch (_: Exception) {
                    value  // Last resort: return original
                }
            } catch (_: Exception) {
                value
            }
        }
    }
}
