package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retry
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.manga.repository.MangaRepository
import kotlin.time.Duration.Companion.seconds

class GetLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<LibraryAnime> {
        return mangaRepository.getLibraryAnime()
    }

    fun subscribe(): Flow<List<LibraryAnime>> {
        return mangaRepository.getLibraryAnimeAsFlow()
            // SY -->
            .retry {
                if (it is NullPointerException) {
                    delay(5.seconds)
                    true
                } else {
                    false
                }
            }
        // SY <--
    }
}
