package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class EpisodeSortingFlagsMigration : Migration {
    override val version = 8f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val mangaRepository = migrationContext.get<MangaRepository>() ?: return@withIOContext false

        // Sorting flags were shifted up by one bit to avoid overlapping with fillermark flags.
        // Old mask: 0x00000300 (bits 8-9), new mask: 0x00000600 (bits 9-10).
        val oldSortingMask = 0x00000300L
        val newSortingMask = 0x00000600L
        val combinedMask = oldSortingMask or newSortingMask

        val updates = mangaRepository.getAll()
            .mapNotNull { manga ->
                val oldSorting = manga.chapterFlags and oldSortingMask
                if (oldSorting == 0L) return@mapNotNull null
                val newFlags = (manga.chapterFlags and combinedMask.inv()) or (oldSorting shl 1)
                MangaUpdate(id = manga.id, chapterFlags = newFlags)
            }

        if (updates.isNotEmpty()) {
            mangaRepository.updateAll(updates)
        }

        return@withIOContext true
    }
}
