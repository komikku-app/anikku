package tachiyomi.domain.manga.interactor

import aniyomi.domain.anime.SeasonAnime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class GetMangaWithChaptersAndSeasons(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
) {

    suspend fun subscribe(
        id: Long,
        applyFilter: Boolean = false,
    ): Flow<Triple<Manga, List<Chapter>, List<SeasonAnime>>> {
        return combine(
            mangaRepository.getMangaByIdAsFlow(id),
            chapterRepository.getChapterByMangaIdAsFlow(id, applyFilter),
            // AY -->
            mangaRepository.getAnimeSeasonsByIdAsFlow(id),
            // <-- AY
        ) { manga, chapters, seasons ->
            Triple(manga, chapters, seasons)
        }
    }

    suspend fun awaitManga(id: Long): Manga {
        return mangaRepository.getMangaById(id)
    }

    suspend fun awaitChapters(id: Long, applyFilter: Boolean = false): List<Chapter> {
        return chapterRepository.getChapterByMangaId(id, applyFilter)
    }

    // AY -->
    suspend fun awaitSeasons(id: Long): List<SeasonAnime> {
        return mangaRepository.getAnimeSeasonsById(id)
    }
    // <-- AY
}
