// AY -->
package tachiyomi.domain.source.model

import eu.kanade.tachiyomi.animesource.model.FetchType

data class DeletableAnime(
    val animeId: Long,
    val sourceId: Long,
    val fetchType: FetchType,
)
// <-- AY
