@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.streamsilkextractor

import eu.kanade.tachiyomi.animesource.model.Video
import keiyoushi.utils.commonEmptyHeaders
import okhttp3.Headers
import okhttp3.OkHttpClient

class StreamSilkExtractor(private val client: OkHttpClient, private val headers: Headers = commonEmptyHeaders) {
    fun videosFromUrl(url: String, prefix: String): List<Video> = videosFromUrl(url) { "${prefix}StreamSilk:$it" }

    fun videosFromUrl(url: String, videoNameGen: (String) -> String = { quality -> "StreamSilk:$quality" }): List<Video> = emptyList()
}
