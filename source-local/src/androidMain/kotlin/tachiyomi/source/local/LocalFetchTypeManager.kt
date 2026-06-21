package tachiyomi.source.local

import android.content.Context
import eu.kanade.tachiyomi.source.model.FetchType
import tachiyomi.source.local.io.Archive
import tachiyomi.source.local.io.LocalSourceFileSystem

actual class LocalFetchTypeManager(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
) {
    actual fun find(url: String): FetchType {
        val files = fileSystem.getFilesInAnimeDirectory(url)

        return when {
            files.any { Archive.isSupported(it) } -> FetchType.Episodes
            files.any { it.isDirectory } -> FetchType.Seasons
            else -> FetchType.Episodes
        }
    }
}
