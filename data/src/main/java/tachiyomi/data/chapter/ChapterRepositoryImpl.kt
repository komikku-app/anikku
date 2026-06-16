package tachiyomi.data.chapter

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga

class ChapterRepositoryImpl(
    private val handler: DatabaseHandler,
) : ChapterRepository {

    override suspend fun addAll(chapters: List<Chapter>): List<Chapter> {
        return try {
            handler.await(inTransaction = true) {
                chapters.map { chapter ->
                    episodesQueries.insert(
                        chapter.mangaId,
                        chapter.url,
                        chapter.name,
                        chapter.scanlator,
                        chapter.read,
                        chapter.bookmark,
                        // AY -->
                        chapter.fillermark,
                        // <-- AY
                        chapter.lastPageRead,
                        chapter.totalPages,
                        chapter.chapterNumber,
                        chapter.sourceOrder,
                        chapter.dateFetch,
                        chapter.dateUpload,
                        chapter.version,
                    )
                    val lastInsertId = episodesQueries.selectLastInsertedRowId().executeAsOne()
                    chapter.copy(id = lastInsertId)
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
            chapterUpdates.forEach { chapterUpdate ->
                episodesQueries.update(
                    animeId = chapterUpdate.mangaId,
                    url = chapterUpdate.url,
                    name = chapterUpdate.name,
                    scanlator = chapterUpdate.scanlator,
                    seen = chapterUpdate.read,
                    bookmark = chapterUpdate.bookmark,
                    // AY -->
                    fillermark = chapterUpdate.fillermark,
                    // <-- AY
                    lastSecondSeen = chapterUpdate.lastPageRead,
                    totalSeconds = chapterUpdate.totalPages,
                    episodeNumber = chapterUpdate.chapterNumber,
                    sourceOrder = chapterUpdate.sourceOrder,
                    dateFetch = chapterUpdate.dateFetch,
                    dateUpload = chapterUpdate.dateUpload,
                    episodeId = chapterUpdate.id,
                    version = chapterUpdate.version,
                    isSyncing = 0,
                )
            }
        }
    }

    override suspend fun removeChaptersWithIds(chapterIds: List<Long>) {
        try {
            handler.await { episodesQueries.removeEpisodesWithIds(chapterIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getChapterByMangaId(mangaId: Long, applyFilter: Boolean): List<Chapter> {
        return handler.awaitList {
            episodesQueries.getEpisodesByAnimeId(
                mangaId,
                applyFilter.toLong(),
                // KMK -->
                Manga.EPISODE_SHOW_NOT_BOOKMARKED,
                Manga.EPISODE_SHOW_BOOKMARKED,
                // KMK <--
                ChapterMapper::mapChapter,
            )
        }
    }

    override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> {
        return handler.awaitList {
            episodesQueries.getScanlatorsByAnimeId(mangaId) { it.orEmpty() }
        }
    }

    override fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            episodesQueries.getScanlatorsByAnimeId(mangaId) { it.orEmpty() }
        }
    }

    override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter> {
        return handler.awaitList {
            episodesQueries.getBookmarkedEpisodesByAnimeId(
                mangaId,
                ChapterMapper::mapChapter,
            )
        }
    }

    // AY -->
    override suspend fun getFillermarkedChaptersByMangaId(mangaId: Long): List<Chapter> {
        return handler.awaitList { episodesQueries.getFillermarkedEpisodesByAnimeId(mangaId, ChapterMapper::mapChapter) }
    }
    // <-- AY

    override suspend fun getChapterById(id: Long): Chapter? {
        return handler.awaitOneOrNull { episodesQueries.getEpisodeById(id, ChapterMapper::mapChapter) }
    }

    override suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyFilter: Boolean): Flow<List<Chapter>> {
        return handler.subscribeToList {
            episodesQueries.getEpisodesByAnimeId(
                mangaId,
                applyFilter.toLong(),
                // KMK -->
                Manga.EPISODE_SHOW_NOT_BOOKMARKED,
                Manga.EPISODE_SHOW_BOOKMARKED,
                // KMK <--
                ChapterMapper::mapChapter,
            )
        }
    }

    override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter? {
        return handler.awaitOneOrNull {
            episodesQueries.getEpisodeByUrlAndAnimeId(
                url,
                mangaId,
                ChapterMapper::mapChapter,
            )
        }
    }

    // SY -->
    override suspend fun getChapterByUrl(url: String): List<Chapter> {
        return handler.awaitList { episodesQueries.getEpisodeByUrl(url, ChapterMapper::mapChapter) }
    }

    override suspend fun getMergedChapterByMangaId(mangaId: Long, applyFilter: Boolean): List<Chapter> {
        return handler.awaitList {
            episodesQueries.getMergedEpisodesByAnimeId(
                mangaId,
                applyFilter.toLong(),
                // KMK -->
                Manga.EPISODE_SHOW_NOT_BOOKMARKED,
                Manga.EPISODE_SHOW_BOOKMARKED,
                // KMK <--
                ChapterMapper::mapChapter,
            )
        }
    }

    override suspend fun getMergedChapterByMangaIdAsFlow(
        mangaId: Long,
        applyFilter: Boolean,
    ): Flow<List<Chapter>> {
        return handler.subscribeToList {
            episodesQueries.getMergedEpisodesByAnimeId(
                mangaId,
                applyFilter.toLong(),
                // KMK -->
                Manga.EPISODE_SHOW_NOT_BOOKMARKED,
                Manga.EPISODE_SHOW_BOOKMARKED,
                // KMK <--
                ChapterMapper::mapChapter,
            )
        }
    }

    override suspend fun getScanlatorsByMergeId(mangaId: Long): List<String> {
        return handler.awaitList {
            episodesQueries.getScanlatorsByMergeId(mangaId) { it.orEmpty() }
        }
    }

    override fun getScanlatorsByMergeIdAsFlow(mangaId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            episodesQueries.getScanlatorsByMergeId(mangaId) { it.orEmpty() }
        }
    }
    // SY <--
}
