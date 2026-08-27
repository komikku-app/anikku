@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.megaupextractor

import android.app.Application
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

class MegaUpExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val context: Application? = null,
) {
    suspend fun videosFromUrl(
        url: String,
        serverName: String? = null,
    ): List<Video> = emptyList()
}
