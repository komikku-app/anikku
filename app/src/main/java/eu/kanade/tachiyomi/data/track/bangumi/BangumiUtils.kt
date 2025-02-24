package eu.kanade.tachiyomi.data.track.bangumi

import eu.kanade.tachiyomi.data.database.models.Track

fun Track.toApiStatus() = when (status) {
    Bangumi.PLAN_TO_WATCH -> 1
    Bangumi.COMPLETED -> 2
    Bangumi.WATCHING -> 3
    Bangumi.ON_HOLD -> 4
    Bangumi.DROPPED -> 5
    else -> 3
}
