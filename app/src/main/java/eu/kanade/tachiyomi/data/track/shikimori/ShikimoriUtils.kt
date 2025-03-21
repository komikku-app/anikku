package eu.kanade.tachiyomi.data.track.shikimori

import eu.kanade.tachiyomi.data.database.models.Track

fun Track.toShikimoriStatus() = when (status) {
    Shikimori.WATCHING -> "watching"
    Shikimori.COMPLETED -> "completed"
    Shikimori.ON_HOLD -> "on_hold"
    Shikimori.DROPPED -> "dropped"
    Shikimori.PLAN_TO_WATCH -> "planned"
    Shikimori.REWATCHING -> "rewatching"
    else -> throw NotImplementedError("Unknown status: $status")
}

fun toTrackStatus(status: String) = when (status) {
    "watching" -> Shikimori.WATCHING
    "completed" -> Shikimori.COMPLETED
    "on_hold" -> Shikimori.ON_HOLD
    "dropped" -> Shikimori.DROPPED
    "planned" -> Shikimori.PLAN_TO_WATCH
    "rewatching" -> Shikimori.REWATCHING
    else -> throw NotImplementedError("Unknown status: $status")
}
