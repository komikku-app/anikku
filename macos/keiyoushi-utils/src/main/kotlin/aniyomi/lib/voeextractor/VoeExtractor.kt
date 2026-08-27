@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.voeextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

class VoeExtractor(private val client: OkHttpClient, private val headers: Headers) {
    fun videosFromUrl(url: String, prefix: String = ""): List<Video> = emptyList()
}
