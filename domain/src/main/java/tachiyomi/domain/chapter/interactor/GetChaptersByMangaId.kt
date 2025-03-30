package tachiyomi.domain.chapter.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository

class GetChaptersByMangaId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(animeId: Long, applyScanlatorFilter: Boolean = false): List<Chapter> {
        return try {
            chapterRepository.getEpisodeByAnimeId(animeId, applyScanlatorFilter)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
