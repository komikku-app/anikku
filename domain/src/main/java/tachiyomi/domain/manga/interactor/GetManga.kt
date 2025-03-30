package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class GetManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(id: Long): Manga? {
        return try {
            mangaRepository.getAnimeById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun subscribe(id: Long): Flow<Manga> {
        return mangaRepository.getAnimeByIdAsFlow(id)
    }

    fun subscribe(url: String, sourceId: Long): Flow<Manga?> {
        return mangaRepository.getAnimeByUrlAndSourceIdAsFlow(url, sourceId)
    }

    // SY -->
    suspend fun await(url: String, sourceId: Long): Manga? {
        return mangaRepository.getAnimeByUrlAndSourceId(url, sourceId)
    }
    // SY <--
}
