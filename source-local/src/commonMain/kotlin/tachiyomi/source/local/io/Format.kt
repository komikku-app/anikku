package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.core.common.storage.extension

object Format {

    private val SUPPORTED_ARCHIVE_TYPES =
        listOf("avi", "flv", "mkv", "mov", "mp4", "webm", "wmv", "torrent", "m3u", "m3u8")

    fun isSupported(file: UniFile): Boolean {
        return file.extension?.lowercase() in SUPPORTED_ARCHIVE_TYPES
    }
}
