package tachiyomi.data.episode

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository

class ChapterRepositoryImpl(
    private val handler: DatabaseHandler,
) : ChapterRepository {

    override suspend fun addAll(chapters: List<Chapter>): List<Chapter> {
        return try {
            handler.await(inTransaction = true) {
                chapters.map { episode ->
                    episodesQueries.insert(
                        episode.animeId,
                        episode.url,
                        episode.name,
                        episode.scanlator,
                        episode.seen,
                        episode.bookmark,
                        // AM (FILLERMARK) -->
                        episode.fillermark,
                        // <-- AM (FILLERMARK)
                        episode.lastSecondSeen,
                        episode.totalSeconds,
                        episode.episodeNumber,
                        episode.sourceOrder,
                        episode.dateFetch,
                        episode.dateUpload,
                        episode.version,
                    )
                    val lastInsertId = episodesQueries.selectLastInsertedRowId().executeAsOne()
                    episode.copy(id = lastInsertId)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun update(chapterUpdate: ChapterUpdate) {
        partialUpdate(chapterUpdate)
    }

    override suspend fun updateAll(chapterUpdates: List<ChapterUpdate>) {
        partialUpdate(*chapterUpdates.toTypedArray())
    }

    private suspend fun partialUpdate(vararg chapterUpdates: ChapterUpdate) {
        handler.await(inTransaction = true) {
            chapterUpdates.forEach { episodeUpdate ->
                episodesQueries.update(
                    animeId = episodeUpdate.animeId,
                    url = episodeUpdate.url,
                    name = episodeUpdate.name,
                    scanlator = episodeUpdate.scanlator,
                    seen = episodeUpdate.seen,
                    bookmark = episodeUpdate.bookmark,
                    // AM (FILLERMARK) -->
                    fillermark = episodeUpdate.fillermark,
                    // <-- AM (FILLERMARK)
                    lastSecondSeen = episodeUpdate.lastSecondSeen,
                    totalSeconds = episodeUpdate.totalSeconds,
                    episodeNumber = episodeUpdate.episodeNumber,
                    sourceOrder = episodeUpdate.sourceOrder,
                    dateFetch = episodeUpdate.dateFetch,
                    dateUpload = episodeUpdate.dateUpload,
                    episodeId = episodeUpdate.id,
                    version = episodeUpdate.version,
                    isSyncing = 0,
                )
            }
        }
    }

    override suspend fun removeEpisodesWithIds(episodeIds: List<Long>) {
        try {
            handler.await { episodesQueries.removeEpisodesWithIds(episodeIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getEpisodeByAnimeId(animeId: Long, applyScanlatorFilter: Boolean): List<Chapter> {
        return handler.awaitList {
            episodesQueries.getEpisodesByAnimeId(animeId, applyScanlatorFilter.toLong(), EpisodeMapper::mapEpisode)
        }
    }

    override suspend fun getScanlatorsByAnimeId(animeId: Long): List<String> {
        return handler.awaitList {
            episodesQueries.getScanlatorsByAnimeId(animeId) { it.orEmpty() }
        }
    }

    override fun getScanlatorsByAnimeIdAsFlow(animeId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            episodesQueries.getScanlatorsByAnimeId(animeId) { it.orEmpty() }
        }
    }

    override suspend fun getBookmarkedEpisodesByAnimeId(animeId: Long): List<Chapter> {
        return handler.awaitList {
            episodesQueries.getBookmarkedEpisodesByAnimeId(
                animeId,
                EpisodeMapper::mapEpisode,
            )
        }
    }

    // AM (FILLERMARK) -->
    override suspend fun getFillermarkedEpisodesByAnimeId(animeId: Long): List<Chapter> {
        return handler.awaitList { episodesQueries.getFillermarkedEpisodesByAnimeId(animeId, EpisodeMapper::mapEpisode) }
    }
    // <-- AM (FILLERMARK)

    override suspend fun getEpisodeById(id: Long): Chapter? {
        return handler.awaitOneOrNull { episodesQueries.getEpisodeById(id, EpisodeMapper::mapEpisode) }
    }

    override suspend fun getEpisodeByAnimeIdAsFlow(animeId: Long, applyScanlatorFilter: Boolean): Flow<List<Chapter>> {
        return handler.subscribeToList {
            episodesQueries.getEpisodesByAnimeId(animeId, applyScanlatorFilter.toLong(), EpisodeMapper::mapEpisode)
        }
    }

    override suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): Chapter? {
        return handler.awaitOneOrNull {
            episodesQueries.getEpisodeByUrlAndAnimeId(
                url,
                animeId,
                EpisodeMapper::mapEpisode,
            )
        }
    }

    // SY -->
    override suspend fun getEpisodeByUrl(url: String): List<Chapter> {
        return handler.awaitList { episodesQueries.getEpisodeByUrl(url, EpisodeMapper::mapEpisode) }
    }

    override suspend fun getMergedEpisodeByAnimeId(animeId: Long, applyScanlatorFilter: Boolean): List<Chapter> {
        return handler.awaitList {
            episodesQueries.getMergedEpisodesByAnimeId(
                animeId,
                applyScanlatorFilter.toLong(),
                EpisodeMapper::mapEpisode,
            )
        }
    }

    override suspend fun getMergedEpisodeByAnimeIdAsFlow(
        animeId: Long,
        applyScanlatorFilter: Boolean,
    ): Flow<List<Chapter>> {
        return handler.subscribeToList {
            episodesQueries.getMergedEpisodesByAnimeId(
                animeId,
                applyScanlatorFilter.toLong(),
                EpisodeMapper::mapEpisode,
            )
        }
    }

    override suspend fun getScanlatorsByMergeId(animeId: Long): List<String> {
        return handler.awaitList {
            episodesQueries.getScanlatorsByMergeId(animeId) { it.orEmpty() }
        }
    }

    override fun getScanlatorsByMergeIdAsFlow(animeId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            episodesQueries.getScanlatorsByMergeId(animeId) { it.orEmpty() }
        }
    }
    // SY <--
}
