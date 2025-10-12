package tachiyomi.domain.source.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.model.DeletableAnime

class GetSourcesWithNonLibraryManga(
    private val repository: MangaRepository,
) {

    // AY -->
    fun subscribe(): Flow<List<DeletableAnime>> {
        return repository.getDeletableParentAnime()
    }

    suspend fun getDeletableChildren(parentId: Long): List<Anime> {
        return repository.getChildrenByParentId(parentId)
    }
    // <-- AY
}
