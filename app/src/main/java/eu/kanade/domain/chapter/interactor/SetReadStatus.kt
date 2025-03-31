package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.download.interactor.DeleteDownload
import exh.source.MERGED_SOURCE_ID
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class SetReadStatus(
    private val downloadPreferences: DownloadPreferences,
    private val deleteDownload: DeleteDownload,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    // SY -->
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    // SY <--
) {

    private val mapper = { chapter: Chapter, seen: Boolean ->
        ChapterUpdate(
            seen = seen,
            lastSecondSeen = if (!seen) 0 else null,
            id = chapter.id,
        )
    }

    suspend fun await(seen: Boolean, vararg chapters: Chapter): Result = withNonCancellableContext {
        val episodesToUpdate = chapters.filter {
            when (seen) {
                true -> !it.seen
                false -> it.seen || it.lastSecondSeen > 0
            }
        }
        if (episodesToUpdate.isEmpty()) {
            return@withNonCancellableContext Result.NoEpisodes
        }

        try {
            chapterRepository.updateAll(
                episodesToUpdate.map { mapper(it, seen) },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        if (seen && downloadPreferences.removeAfterMarkedAsSeen().get()) {
            episodesToUpdate
                .groupBy { it.animeId }
                .forEach { (animeId, episodes) ->
                    deleteDownload.awaitAll(
                        manga = mangaRepository.getMangaById(animeId),
                        chapters = episodes.toTypedArray(),
                    )
                }
        }

        Result.Success
    }

    suspend fun await(animeId: Long, seen: Boolean): Result = withNonCancellableContext {
        await(
            seen = seen,
            chapters = chapterRepository
                .getChapterByMangaId(animeId)
                .toTypedArray(),
        )
    }

    // SY -->
    private suspend fun awaitMerged(animeId: Long, seen: Boolean) = withNonCancellableContext f@{
        return@f await(
            seen = seen,
            chapters = getMergedChaptersByMangaId
                .await(animeId, dedupe = false)
                .toTypedArray(),
        )
    }

    suspend fun await(manga: Manga, seen: Boolean) = if (manga.source == MERGED_SOURCE_ID) {
        awaitMerged(manga.id, seen)
    } else {
        await(manga.id, seen)
    }
    // SY <--

    sealed interface Result {
        data object Success : Result
        data object NoEpisodes : Result
        data class InternalError(val error: Throwable) : Result
    }
}
