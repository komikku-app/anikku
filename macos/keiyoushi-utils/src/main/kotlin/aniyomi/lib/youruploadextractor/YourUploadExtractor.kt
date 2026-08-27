@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.youruploadextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

class YourUploadExtractor(private val client: OkHttpClient) {
    fun videoFromUrl(url: String, headers: Headers, name: String = "YourUpload", prefix: String = ""): List<Video> = emptyList()
}
