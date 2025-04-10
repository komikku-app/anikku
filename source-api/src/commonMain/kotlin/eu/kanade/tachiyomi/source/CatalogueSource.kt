package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage

typealias CatalogueSource = eu.kanade.tachiyomi.animesource.AnimeCatalogueSource

suspend fun CatalogueSource.getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
    getSearchAnime(page, query, filters)

suspend fun CatalogueSource.getPopularManga(page: Int): MangasPage = getPopularAnime(page)
