package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.model.MergedAnimeReference
import tachiyomi.domain.anime.repository.AnimeMergeRepository

class InsertMergedReference(
    private val mangaMergedRepository: AnimeMergeRepository,
) {

    suspend fun await(reference: MergedAnimeReference): Long? {
        return mangaMergedRepository.insert(reference)
    }

    suspend fun awaitAll(references: List<MergedAnimeReference>) {
        mangaMergedRepository.insertAll(references)
    }
}
