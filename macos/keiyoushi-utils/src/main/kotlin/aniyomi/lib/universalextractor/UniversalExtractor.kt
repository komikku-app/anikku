@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.universalextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

class UniversalExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(
        origRequestUrl: String,
        origRequestHeader: Headers,
        customQuality: String? = null,
        prefix: String = "",
    ): List<Video> = emptyList()
}
