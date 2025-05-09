package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.core.common.storage.extension

object Archive {

    private val SUPPORTED_ARCHIVE_TYPES =
        listOf("avi", "flv", "mkv", "mov", "mp4", "webm", "wmv", "torrent", "m3u", "m3u8")

    fun isSupported(file: UniFile): Boolean = with(file) {
        return file.extension?.lowercase() in SUPPORTED_ARCHIVE_TYPES
    }
}
