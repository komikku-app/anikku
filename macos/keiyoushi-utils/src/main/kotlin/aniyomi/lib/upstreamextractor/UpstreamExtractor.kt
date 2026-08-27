@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.upstreamextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient

class UpstreamExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, prefix: String = ""): List<Video> = emptyList()
}
