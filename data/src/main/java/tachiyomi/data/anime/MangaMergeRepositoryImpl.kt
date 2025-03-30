package tachiyomi.data.anime

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.repository.MangaMergeRepository

class MangaMergeRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaMergeRepository {

    override suspend fun getMergedAnime(): List<Manga> {
        return handler.awaitList { mergedQueries.selectAllMergedAnimes(AnimeMapper::mapAnime) }
    }

    override suspend fun subscribeMergedAnime(): Flow<List<Manga>> {
        return handler.subscribeToList { mergedQueries.selectAllMergedAnimes(AnimeMapper::mapAnime) }
    }

    override suspend fun getMergedAnimeById(id: Long): List<Manga> {
        return handler.awaitList { mergedQueries.selectMergedAnimesById(id, AnimeMapper::mapAnime) }
    }

    override suspend fun subscribeMergedAnimeById(id: Long): Flow<List<Manga>> {
        return handler.subscribeToList { mergedQueries.selectMergedAnimesById(id, AnimeMapper::mapAnime) }
    }

    override suspend fun getReferencesById(id: Long): List<MergedMangaReference> {
        return handler.awaitList { mergedQueries.selectByMergeId(id, MergedAnimeMapper::map) }
    }

    override suspend fun subscribeReferencesById(id: Long): Flow<List<MergedMangaReference>> {
        return handler.subscribeToList { mergedQueries.selectByMergeId(id, MergedAnimeMapper::map) }
    }

    override suspend fun updateSettings(update: MergeMangaSettingsUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAllSettings(values: List<MergeMangaSettingsUpdate>): Boolean {
        return try {
            partialUpdate(*values.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdate(vararg values: MergeMangaSettingsUpdate) {
        handler.await(inTransaction = true) {
            values.forEach { value ->
                mergedQueries.updateSettingsById(
                    id = value.id,
                    getEpisodeUpdates = value.getEpisodeUpdates,
                    downloadEpisodes = value.downloadEpisodes,
                    infoAnime = value.isInfoAnime,
                    episodePriority = value.episodePriority?.toLong(),
                    episodeSortMode = value.episodeSortMode?.toLong(),
                )
            }
        }
    }

    override suspend fun insert(reference: MergedMangaReference): Long? {
        return handler.awaitOneOrNullExecutable {
            mergedQueries.insert(
                infoAnime = reference.isInfoAnime,
                getEpisodeUpdates = reference.getEpisodeUpdates,
                episodeSortMode = reference.episodeSortMode.toLong(),
                episodePriority = reference.episodePriority.toLong(),
                downloadEpisodes = reference.downloadEpisodes,
                mergeId = reference.mergeId!!,
                mergeUrl = reference.mergeUrl,
                animeId = reference.animeId,
                animeUrl = reference.animeUrl,
                animeSource = reference.animeSourceId,
            )
            mergedQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun insertAll(references: List<MergedMangaReference>) {
        handler.await(true) {
            references.forEach { reference ->
                mergedQueries.insert(
                    infoAnime = reference.isInfoAnime,
                    getEpisodeUpdates = reference.getEpisodeUpdates,
                    episodeSortMode = reference.episodeSortMode.toLong(),
                    episodePriority = reference.episodePriority.toLong(),
                    downloadEpisodes = reference.downloadEpisodes,
                    mergeId = reference.mergeId!!,
                    mergeUrl = reference.mergeUrl,
                    animeId = reference.animeId,
                    animeUrl = reference.animeUrl,
                    animeSource = reference.animeSourceId,
                )
            }
        }
    }

    override suspend fun deleteById(id: Long) {
        handler.await {
            mergedQueries.deleteById(id)
        }
    }

    override suspend fun deleteByMergeId(mergeId: Long) {
        handler.await {
            mergedQueries.deleteByMergeId(mergeId)
        }
    }

    override suspend fun getMergeAnimeForDownloading(mergeId: Long): List<Manga> {
        return handler.awaitList { mergedQueries.selectMergedAnimesForDownloadingById(mergeId, AnimeMapper::mapAnime) }
    }
}
