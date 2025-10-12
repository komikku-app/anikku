// AY -->
package tachiyomi.source.local

import android.content.Context
import eu.kanade.tachiyomi.animesource.model.FetchType
import tachiyomi.source.local.io.Format
import tachiyomi.source.local.io.LocalSourceFileSystem

actual class LocalFetchTypeManager(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
) {
    actual fun find(animeUrl: String): FetchType {
        val files = fileSystem.getFilesInMangaDirectory(animeUrl)

        return when {
            files.any { Format.isSupported(it) } -> FetchType.Episodes
            files.any { it.isDirectory } -> FetchType.Seasons
            else -> FetchType.Episodes
        }
    }
}
// <-- AY
