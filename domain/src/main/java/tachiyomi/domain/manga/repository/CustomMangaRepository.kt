package tachiyomi.domain.manga.repository

import tachiyomi.domain.manga.model.CustomMangaInfo

interface CustomMangaRepository {

    fun get(animeId: Long): CustomMangaInfo?

    fun set(animeInfo: CustomMangaInfo)
}
