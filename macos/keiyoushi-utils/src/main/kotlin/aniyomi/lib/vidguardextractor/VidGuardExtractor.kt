@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.vidguardextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient

class VidGuardExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, prefix: String): List<Video> = videosFromUrl(url) { "${prefix}VidGuard:$it" }

    fun videosFromUrl(url: String, videoNameGen: (String) -> String = { quality -> "VidGuard:$quality" }): List<Video> = emptyList()
}
