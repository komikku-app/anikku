package eu.kanade.tachiyomi.data.track.trakt.dto

import kotlinx.serialization.Serializable

@Serializable
data class TraktWatchlistItem(
    val show: TraktWatchlistShow,
)

@Serializable
data class TraktWatchlistShow(
    val ids: TraktShowIds,
)
