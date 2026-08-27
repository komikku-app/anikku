package android.net.http

/**
 * Stub for `android.net.http.SslError`.
 *
 * Constructor properties `error`, `certificate`, and `url` generate
 * `getError()`, `getCertificate()`, and `getUrl()` accessors automatically,
 * so no explicit getter functions are defined.
 */
open class SslError(
    val error: Int,
    val certificate: Any?,
    val url: String?,
) {
    fun addError(error: Int) = Unit
    fun hasError(error: Int): Boolean = false

    companion object {
        const val SSL_NOT_YET_VALID: Int = 0
        const val SSL_EXPIRED: Int = 1
        const val SSL_IDMISMATCH: Int = 2
        const val SSL_UNTRUSTED: Int = 3
        const val SSL_DATE_INVALID: Int = 4
        const val SSL_INVALID: Int = 5
    }
}
