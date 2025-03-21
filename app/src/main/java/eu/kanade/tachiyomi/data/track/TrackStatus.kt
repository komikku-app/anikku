package eu.kanade.tachiyomi.data.track

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.simkl.Simkl

@Suppress("MagicNumber")
enum class TrackStatus(val int: Long, @StringRes val res: Int) {
    WATCHING(11, R.string.watching),
    REWATCHING(17, R.string.repeating_anime),
    PLAN_TO_WATCH(16, R.string.plan_to_watch),
    PAUSED(4, R.string.on_hold),
    COMPLETED(5, R.string.completed),
    DROPPED(6, R.string.dropped),
    OTHER(7, R.string.not_tracked),
    ;

    companion object {
        @Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod")
        fun parseTrackerStatus(tracker: Long, statusLong: Long): TrackStatus? {
            return when (tracker) {
                (1L) -> {
                    when (statusLong) {
                        MyAnimeList.WATCHING -> WATCHING
                        MyAnimeList.COMPLETED -> COMPLETED
                        MyAnimeList.ON_HOLD -> PAUSED
                        MyAnimeList.PLAN_TO_WATCH -> PLAN_TO_WATCH
                        MyAnimeList.DROPPED -> DROPPED
                        MyAnimeList.REWATCHING -> REWATCHING
                        else -> null
                    }
                }
                TrackerManager.ANILIST -> {
                    when (statusLong) {
                        Anilist.WATCHING -> WATCHING
                        Anilist.REWATCHING -> REWATCHING
                        Anilist.PLAN_TO_WATCH -> PLAN_TO_WATCH
                        Anilist.ON_HOLD -> PAUSED
                        Anilist.COMPLETED -> COMPLETED
                        Anilist.DROPPED -> DROPPED
                        else -> null
                    }
                }
                TrackerManager.KITSU -> {
                    when (statusLong) {
                        Kitsu.WATCHING -> WATCHING
                        Kitsu.COMPLETED -> COMPLETED
                        Kitsu.ON_HOLD -> PAUSED
                        Kitsu.PLAN_TO_WATCH -> PLAN_TO_WATCH
                        Kitsu.DROPPED -> DROPPED
                        else -> null
                    }
                }
                (4L) -> {
                    when (statusLong) {
                        Shikimori.COMPLETED -> COMPLETED
                        Shikimori.ON_HOLD -> PAUSED
                        Shikimori.DROPPED -> DROPPED
                        else -> null
                    }
                }
                (5L) -> {
                    when (statusLong) {
                        Bangumi.COMPLETED -> COMPLETED
                        Bangumi.ON_HOLD -> PAUSED
                        Bangumi.DROPPED -> DROPPED
                        else -> null
                    }
                }
                TrackerManager.SIMKL -> {
                    when (statusLong) {
                        Simkl.WATCHING -> WATCHING
                        Simkl.COMPLETED -> COMPLETED
                        Simkl.ON_HOLD -> PAUSED
                        Simkl.PLAN_TO_WATCH -> PLAN_TO_WATCH
                        Simkl.NOT_INTERESTING -> DROPPED
                        else -> null
                    }
                }
                else -> null
            }
        }
    }
}
