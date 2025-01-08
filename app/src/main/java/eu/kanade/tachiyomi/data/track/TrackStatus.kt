package eu.kanade.tachiyomi.data.track

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.simkl.Simkl
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

enum class TrackStatus(val long: Long, val res: StringResource) {
    WATCHING(11L, MR.strings.watching),
    REWATCHING(17L, MR.strings.repeating_anime),
    PLAN_TO_WATCH(16L, MR.strings.plan_to_watch),
    PAUSED(4L, MR.strings.on_hold),
    COMPLETED(5L, MR.strings.completed),
    DROPPED(6L, MR.strings.dropped),
    OTHER(7L, SYMR.strings.not_tracked),
    ;

    companion object {
        fun parseTrackerStatus(trackerManager: TrackerManager, tracker: Long, status: Long): TrackStatus? {
            return when (tracker) {
                trackerManager.myAnimeList.id -> {
                    when (status) {
                        MyAnimeList.WATCHING -> WATCHING
                        MyAnimeList.COMPLETED -> COMPLETED
                        MyAnimeList.ON_HOLD -> PAUSED
                        MyAnimeList.PLAN_TO_WATCH -> PLAN_TO_WATCH
                        MyAnimeList.DROPPED -> DROPPED
                        MyAnimeList.REWATCHING -> REWATCHING
                        else -> null
                    }
                }
                trackerManager.aniList.id -> {
                    when (status) {
                        Anilist.WATCHING -> WATCHING
                        Anilist.COMPLETED -> COMPLETED
                        Anilist.ON_HOLD -> PAUSED
                        Anilist.PLAN_TO_WATCH -> PLAN_TO_WATCH
                        Anilist.DROPPED -> DROPPED
                        Anilist.REWATCHING -> REWATCHING
                        else -> null
                    }
                }
                trackerManager.kitsu.id -> {
                    when (status) {
                        Kitsu.WATCHING -> WATCHING
                        Kitsu.COMPLETED -> COMPLETED
                        Kitsu.ON_HOLD -> PAUSED
                        Kitsu.PLAN_TO_WATCH -> PLAN_TO_WATCH
                        Kitsu.DROPPED -> DROPPED
                        else -> null
                    }
                }
                trackerManager.shikimori.id -> {
                    when (status) {
                        Shikimori.WATCHING -> WATCHING
                        Shikimori.COMPLETED -> COMPLETED
                        Shikimori.ON_HOLD -> PAUSED
                        Shikimori.PLAN_TO_WATCH -> PLAN_TO_WATCH
                        Shikimori.DROPPED -> DROPPED
                        Shikimori.REWATCHING -> REWATCHING
                        else -> null
                    }
                }
                trackerManager.bangumi.id -> {
                    when (status) {
                        Bangumi.WATCHING -> WATCHING
                        Bangumi.COMPLETED -> COMPLETED
                        Bangumi.ON_HOLD -> PAUSED
                        Bangumi.PLAN_TO_WATCH -> PLAN_TO_WATCH
                        Bangumi.DROPPED -> DROPPED
                        else -> WATCHING
                    }
                }
                trackerManager.simkl.id -> {
                    when (status) {
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
