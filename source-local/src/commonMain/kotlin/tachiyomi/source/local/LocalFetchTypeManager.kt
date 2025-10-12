package tachiyomi.source.local

import eu.kanade.tachiyomi.animesource.model.FetchType

expect class LocalFetchTypeManager {
    fun find(animeUrl: String): FetchType
}
