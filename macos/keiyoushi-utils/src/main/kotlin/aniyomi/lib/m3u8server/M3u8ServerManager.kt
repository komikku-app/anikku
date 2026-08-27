package aniyomi.lib.m3u8server

import okhttp3.OkHttpClient

/**
 * Minimal JVM stub for [M3u8ServerManager].
 *
 * The real implementation (in extensions-source/lib/m3u8server/) starts a local
 * NanoHTTPD server to proxy and modify M3U8 playlist files and segments for
 * playback in Android's WebView-based video player. On macOS/JVM this local
 * HTTP-server approach is unnecessary since the mpv-based player handles
 * M3U8 streams natively.
 *
 * All methods are no-ops that simply allow extension source code to compile.
 */
class M3u8ServerManager(
    @Suppress("UNUSED_PARAMETER") private val client: OkHttpClient,
) {
    fun isRunning(): Boolean = false
    fun startServer(port: Int = 0) {}
    fun stopServer() {}
    fun getServerUrl(): String? = null
    fun getServerInfo(): String = "M3U8 server stub (not running)"
    fun processM3u8Url(m3u8Url: String): String? = null
    suspend fun processSegmentUrl(segmentUrl: String, headers: Map<String, String> = emptyMap()): ByteArray? = null
}
