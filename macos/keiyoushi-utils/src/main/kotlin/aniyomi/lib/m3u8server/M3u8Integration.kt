package aniyomi.lib.m3u8server

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient

/**
 * Minimal JVM stub for [M3u8Integration].
 *
 * See [M3u8ServerManager] for the rationale. On JVM the local HTTP server
 * is not needed, so this stub simply passes video lists through unchanged.
 */
class M3u8Integration(
    @Suppress("UNUSED_PARAMETER") client: OkHttpClient,
    @Suppress("UNUSED_PARAMETER") private val serverManager: M3u8ServerManager = M3u8ServerManager(client),
) {
    fun processVideoList(videos: List<Video>): List<Video> = videos
    fun getServerInfo(): String = "M3U8 integration stub"
    fun stopServer() {}
    fun isServerRunning(): Boolean = false
}
