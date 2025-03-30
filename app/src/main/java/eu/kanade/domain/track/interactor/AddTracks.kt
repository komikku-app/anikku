package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.InsertTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZoneOffset

class AddTracks(
    private val insertTrack: InsertTrack,
    private val syncEpisodeProgressWithTrack: SyncEpisodeProgressWithTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val trackerManager: TrackerManager,
) {

    // TODO: update all trackers based on common data
    suspend fun bind(tracker: Tracker, item: Track, animeId: Long) = withNonCancellableContext {
        withIOContext {
            val allEpisodes = getChaptersByMangaId.await(animeId)
            val hasSeenEpisodes = allEpisodes.any { it.seen }
            tracker.bind(item, hasSeenEpisodes)

            var track = item.toDomainTrack(idRequired = false) ?: return@withIOContext

            insertTrack.await(track)

            // TODO: merge into [SyncEpisodeProgressWithTrack]?
            // Update episode progress if newer episodes marked seen locally
            if (hasSeenEpisodes) {
                val latestLocalSeenEpisodeNumber = allEpisodes
                    .sortedBy { it.episodeNumber }
                    .takeWhile { it.seen }
                    .lastOrNull()
                    ?.episodeNumber ?: -1.0

                if (latestLocalSeenEpisodeNumber > track.lastEpisodeSeen) {
                    track = track.copy(
                        lastEpisodeSeen = latestLocalSeenEpisodeNumber,
                    )
                    tracker.setRemoteLastEpisodeSeen(track.toDbTrack(), latestLocalSeenEpisodeNumber.toInt())
                }

                if (track.startDate <= 0) {
                    val firstSeenEpisodeDate = Injekt.get<GetHistory>().await(animeId)
                        .sortedBy { it.seenAt }
                        .firstOrNull()
                        ?.seenAt

                    firstSeenEpisodeDate?.let {
                        val startDate = firstSeenEpisodeDate.time.convertEpochMillisZone(
                            ZoneOffset.systemDefault(),
                            ZoneOffset.UTC,
                        )
                        track = track.copy(
                            startDate = startDate,
                        )
                        tracker.setRemoteStartDate(track.toDbTrack(), startDate)
                    }
                }
            }

            syncEpisodeProgressWithTrack.await(animeId, track, tracker)
        }
    }

    suspend fun bindEnhancedTrackers(manga: Manga, source: Source) = withNonCancellableContext {
        withIOContext {
            trackerManager.loggedInTrackers()
                .filterIsInstance<EnhancedTracker>()
                .filter { it.accept(source) }
                .forEach { service ->
                    try {
                        service.match(manga)?.let { track ->
                            track.anime_id = manga.id
                            (service as Tracker).bind(track)
                            insertTrack.await(track.toDomainTrack(idRequired = false)!!)

                            syncEpisodeProgressWithTrack.await(
                                manga.id,
                                track.toDomainTrack(idRequired = false)!!,
                                service,
                            )
                        }
                    } catch (e: Exception) {
                        logcat(
                            LogPriority.WARN,
                            e,
                        ) { "Could not match anime: ${manga.title} with service $service" }
                    }
                }
        }
    }
}
