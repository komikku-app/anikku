package tachiyomi.data.source

import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.AnimesPage
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SAnime
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.repository.SourcePagingSourceType

class SourceSearchPagingSource(source: CatalogueSource, val query: String, val filters: FilterList) :
    SourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): AnimesPage {
        return source.getSearchAnime(currentPage, query, filters)
    }
}

class SourcePopularPagingSource(source: CatalogueSource) : SourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): AnimesPage {
        return source.getPopularAnime(currentPage)
    }
}

class SourceLatestPagingSource(source: CatalogueSource) : SourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): AnimesPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class SourcePagingSource(
    protected open val source: CatalogueSource,
) : SourcePagingSourceType() {

    abstract suspend fun requestNextPage(currentPage: Int): AnimesPage

    override suspend fun load(
        params: LoadParams<Long>,
    ): LoadResult<Long, SAnime> {
        val page = params.key ?: 1

        val animesPage = try {
            withIOContext {
                requestNextPage(page.toInt())
                    .takeIf { it.animes.isNotEmpty() }
                    ?: throw NoResultsException()
            }
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }

        // SY -->
        return getPageLoadResult(params, animesPage)
        // SY <--
    }

    // SY -->
    open fun getPageLoadResult(
        params: LoadParams<Long>,
        animesPage: AnimesPage,
    ): LoadResult.Page<Long, SAnime> {
        val page = params.key ?: 1

        return LoadResult.Page(
            data = animesPage.animes,
            prevKey = null,
            nextKey = if (animesPage.hasNextPage) page + 1 else null,
        )
    }
    // SY <--

    override fun getRefreshKey(
        state: PagingState<Long, SAnime>,
    ): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

class NoResultsException : Exception()
