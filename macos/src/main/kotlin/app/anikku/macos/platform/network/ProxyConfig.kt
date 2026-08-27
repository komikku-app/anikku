package app.anikku.macos.platform.network

/**
 * Proxy type: how to route network traffic for extension HTTP requests.
 */
enum class ProxyType {
    /** No proxy — direct connection. */
    DISABLED,
    /** HTTP proxy. */
    HTTP,
    /** SOCKS4 proxy. */
    SOCKS4,
    /** SOCKS5 proxy. */
    SOCKS5,
}

/**
 * Proxy configuration used by [MacOSNetworkHelper] to configure OkHttp's proxy.
 *
 * @param type Proxy type (HTTP, SOCKS4, SOCKS5). DISABLED means no proxy.
 * @param host Proxy hostname or IP address.
 * @param port Proxy port number.
 * @param username Optional proxy authentication username.
 * @param password Optional proxy authentication password.
 */
data class ProxyConfig(
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
) {
    /**
     * Whether this proxy configuration is enabled (non-DISABLED with valid host:port).
     */
    val isEnabled: Boolean
        get() = type != ProxyType.DISABLED && host.isNotBlank() && port > 0
}
