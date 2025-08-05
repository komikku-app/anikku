package eu.kanade.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler

class GetExcludedScanlators(
    private val handler: DatabaseHandler,
) {

    suspend fun await(mangaId: Long): Set<String> {
        return handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByAnimeId(mangaId)
        }
            .toSet()
    }

    fun subscribe(mangaId: Long): Flow<Set<String>> {
        return handler.subscribeToList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByAnimeId(mangaId)
        }
            .map { it.toSet() }
    }
}
