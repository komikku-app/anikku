@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.fastreamextractor

import eu.kanade.tachiyomi.animesource.model.Video
import keiyoushi.utils.commonEmptyHeaders
import okhttp3.Headers
import okhttp3.OkHttpClient

class FastreamExtractor(private val client: OkHttpClient, private val headers: Headers = commonEmptyHeaders) {
    fun videosFromUrl(url: String, prefix: String = "Fastream:", needsSleep: Boolean = true): List<Video> = emptyList()
}
