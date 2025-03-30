package tachiyomi.domain.manga.interactor

import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.manga.repository.MangaRepository

class GetReadMangaNotInLibraryView(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<LibraryAnime> {
        return mangaRepository.getSeenAnimeNotInLibraryView()
    }
}
