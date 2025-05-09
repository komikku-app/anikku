package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository

class RemoveHistory(
    private val repository: HistoryRepository,
) {

    suspend fun awaitAll(): Boolean {
        return repository.deleteAllHistory()
    }

    suspend fun await(history: HistoryWithRelations) {
        repository.resetHistory(history.id)
    }

    suspend fun await(animeId: Long) {
        repository.resetHistoryByAnimeId(animeId)
    }

    // SY -->
    suspend fun awaitById(historyId: Long) {
        repository.resetHistory(historyId)
    }
    // SY <--
}
