package eu.kanade.tachiyomi.data.track.trakt

import eu.kanade.tachiyomi.data.database.models.Track

fun Track.toTraktStatus() = when (status) {
    Trakt.WATCHING -> "watching"
    Trakt.COMPLETED -> "completed"
    Trakt.PLAN_TO_WATCH -> "plantowatch"
    Trakt.DROPPED -> "dropped"
    Trakt.REWATCHING -> "rewatching"
    else -> throw NotImplementedError("Unknown status: $status")
}
