@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.uqloadextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient

class UqloadExtractor(private val client: OkHttpClient) {
    suspend fun videosFromUrl(url: String, prefix: String = ""): List<Video> = emptyList()
}
