@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.rapidshareextractor

import android.app.Application
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

class RapidShareExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val context: Application? = null,
) {
    suspend fun videosFromUrl(url: String, prefix: String, preferredLang: String): List<Video> = emptyList()
}
