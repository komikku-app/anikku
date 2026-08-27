@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.streamtapeextractor

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient

class StreamTapeExtractor(private val client: OkHttpClient) {
    fun videoFromUrl(url: String, quality: String = "Streamtape", subtitleList: List<Track> = emptyList()): Video? = null

    fun videosFromUrl(url: String, quality: String = "Streamtape", subtitleList: List<Track> = emptyList()): List<Video> = emptyList()
}
