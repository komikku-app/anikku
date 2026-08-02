package tachiyomi.domain.anime.repository

import tachiyomi.domain.anime.model.CustomAnimeInfo

/** Desktop ABI mirror of the shared domain repository contract. */
interface CustomAnimeRepository {
    fun get(animeId: Long): CustomAnimeInfo?
    fun set(animeInfo: CustomAnimeInfo)
}
