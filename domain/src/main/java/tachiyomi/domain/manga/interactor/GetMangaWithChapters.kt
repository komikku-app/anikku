package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class GetMangaWithChapters(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
) {

    suspend fun subscribe(id: Long, applyScanlatorFilter: Boolean = false): Flow<Pair<Manga, List<Chapter>>> {
        return combine(
            mangaRepository.getAnimeByIdAsFlow(id),
            chapterRepository.getEpisodeByAnimeIdAsFlow(id, applyScanlatorFilter),
        ) { manga, chapters ->
            Pair(manga, chapters)
        }
    }

    suspend fun awaitManga(id: Long): Manga {
        return mangaRepository.getAnimeById(id)
    }

    suspend fun awaitChapters(id: Long, applyScanlatorFilter: Boolean = false): List<Chapter> {
        return chapterRepository.getEpisodeByAnimeId(id, applyScanlatorFilter)
    }
}
