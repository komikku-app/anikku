@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.dopeflixextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

class DopeFlixExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val megaCloudAPI: String,
) {
    fun getVideosFromUrl(url: String, name: String): List<Video> = emptyList()
}
