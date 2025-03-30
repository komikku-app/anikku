package tachiyomi.domain.history.interactor

import exh.source.MERGED_SOURCE_ID
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetManga
import kotlin.math.max

class GetNextEpisodes(
    private val getChaptersByMangaId: GetChaptersByMangaId,
    // SY -->
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    // SY <--
    private val getManga: GetManga,
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(onlyUnseen: Boolean = true): List<Chapter> {
        val history = historyRepository.getLastHistory() ?: return emptyList()
        return await(history.animeId, history.episodeId, onlyUnseen)
    }

    suspend fun await(animeId: Long, onlyUnseen: Boolean = true): List<Chapter> {
        val anime = getManga.await(animeId) ?: return emptyList()

        // SY -->
        if (anime.source == MERGED_SOURCE_ID) {
            val episodes = getMergedChaptersByMangaId.await(animeId, applyScanlatorFilter = true)
                .sortedWith(getChapterSort(anime, sortDescending = false))

            return if (onlyUnseen) {
                episodes.filterNot { it.seen }
            } else {
                episodes
            }
        }
        // SY <--

        val episodes = getChaptersByMangaId.await(animeId, applyScanlatorFilter = true)
            .sortedWith(getChapterSort(anime, sortDescending = false))

        return if (onlyUnseen) {
            episodes.filterNot { it.seen }
        } else {
            episodes
        }
    }

    suspend fun await(
        animeId: Long,
        fromEpisodeId: Long,
        onlyUnseen: Boolean = true,
    ): List<Chapter> {
        val episodes = await(animeId, onlyUnseen)
        val currEpisodeIndex = episodes.indexOfFirst { it.id == fromEpisodeId }
        val nextEpisodes = episodes.subList(max(0, currEpisodeIndex), episodes.size)

        if (onlyUnseen) {
            return nextEpisodes
        }

        // The "next episode" is either:
        // - The current episode if it isn't completely seen
        // - The episodes after the current episode if the current one is completely seen
        val fromEpisode = episodes.getOrNull(currEpisodeIndex)
        return if (fromEpisode != null && !fromEpisode.seen) {
            nextEpisodes
        } else {
            nextEpisodes.drop(1)
        }
    }
}
