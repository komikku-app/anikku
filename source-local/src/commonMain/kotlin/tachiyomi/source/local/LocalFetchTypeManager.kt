package tachiyomi.source.local

import eu.kanade.tachiyomi.source.model.FetchType

expect class LocalFetchTypeManager {
    fun find(url: String): FetchType
}
