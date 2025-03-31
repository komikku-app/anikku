package tachiyomi.domain.chapter.interactor

import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.model.MergedMangaReference

class GetMergedChaptersByMangaId(
    private val chapterRepository: ChapterRepository,
    private val getMergedReferencesById: GetMergedReferencesById,
) {

    suspend fun await(
        mangaId: Long,
        dedupe: Boolean = true,
        applyScanlatorFilter: Boolean = false,
    ): List<Chapter> {
        return transformMergedChapters(
            getMergedReferencesById.await(mangaId),
            getFromDatabase(mangaId, applyScanlatorFilter),
            dedupe,
        )
    }

    suspend fun subscribe(
        mangaId: Long,
        dedupe: Boolean = true,
        applyScanlatorFilter: Boolean = false,
    ): Flow<List<Chapter>> {
        return try {
            chapterRepository.getMergedChapterByMangaIdAsFlow(mangaId, applyScanlatorFilter)
                .combine(getMergedReferencesById.subscribe(mangaId)) { chapters, references ->
                    transformMergedChapters(references, chapters, dedupe)
                }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            flowOf(emptyList())
        }
    }

    private suspend fun getFromDatabase(
        mangaId: Long,
        applyScanlatorFilter: Boolean = false,
    ): List<Chapter> {
        return try {
            chapterRepository.getMergedChapterByMangaId(mangaId, applyScanlatorFilter)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    private fun transformMergedChapters(
        mangaReferences: List<MergedMangaReference>,
        chapterList: List<Chapter>,
        dedupe: Boolean,
    ): List<Chapter> {
        return if (dedupe) dedupeChapterList(mangaReferences, chapterList) else chapterList
    }

    private fun dedupeChapterList(
        mangaReferences: List<MergedMangaReference>,
        chapterList: List<Chapter>,
    ): List<Chapter> {
        return when (mangaReferences.firstOrNull { it.animeSourceId == MERGED_SOURCE_ID }?.episodeSortMode) {
            MergedMangaReference.EPISODE_SORT_NO_DEDUPE, MergedMangaReference.EPISODE_SORT_NONE -> chapterList
            MergedMangaReference.EPISODE_SORT_PRIORITY -> dedupeByPriority(mangaReferences, chapterList)
            MergedMangaReference.EPISODE_SORT_MOST_EPISODES -> {
                findSourceWithMostChapters(chapterList)?.let { mangaId ->
                    chapterList.filter { it.animeId == mangaId }
                } ?: chapterList
            }
            MergedMangaReference.EPISODE_SORT_HIGHEST_EPISODE_NUMBER -> {
                findSourceWithHighestChapterNumber(chapterList)?.let { mangaId ->
                    chapterList.filter { it.animeId == mangaId }
                } ?: chapterList
            }
            else -> chapterList
        }
    }

    private fun findSourceWithMostChapters(chapterList: List<Chapter>): Long? {
        return chapterList.groupBy { it.animeId }.maxByOrNull { it.value.size }?.key
    }

    private fun findSourceWithHighestChapterNumber(chapterList: List<Chapter>): Long? {
        return chapterList.maxByOrNull { it.episodeNumber }?.animeId
    }

    private fun dedupeByPriority(
        mangaReferences: List<MergedMangaReference>,
        chapterList: List<Chapter>,
    ): List<Chapter> {
        val sortedChapterList = mutableListOf<Chapter>()

        var existingChapterIndex: Int
        chapterList.groupBy { it.animeId }
            .entries
            .sortedBy { (mangaId) ->
                mangaReferences.find { it.animeId == mangaId }?.episodePriority ?: Int.MAX_VALUE
            }
            .forEach { (_, chapters) ->
                existingChapterIndex = -1
                chapters.forEach { chapter ->
                    val oldChapterIndex = existingChapterIndex
                    if (chapter.isRecognizedNumber) {
                        existingChapterIndex = sortedChapterList.indexOfFirst {
                            // check if the chapter is not already there
                            it.isRecognizedNumber &&
                                it.episodeNumber == chapter.episodeNumber &&
                                // allow multiple chapters of the same number from the same source
                                it.animeId != chapter.animeId
                        }
                        if (existingChapterIndex == -1) {
                            sortedChapterList.add(oldChapterIndex + 1, chapter)
                            existingChapterIndex = oldChapterIndex + 1
                        }
                    } else {
                        sortedChapterList.add(oldChapterIndex + 1, chapter)
                        existingChapterIndex = oldChapterIndex + 1
                    }
                }
            }

        return sortedChapterList.mapIndexed { index, chapter ->
            chapter.copy(sourceOrder = index.toLong())
        }
    }
}
