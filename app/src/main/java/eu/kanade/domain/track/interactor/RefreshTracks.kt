package eu.kanade.domain.track.interactor

import android.app.Application
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.ank.AMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RefreshTracks(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertTrack,
    private val syncEpisodeProgressWithTrack: SyncChapterProgressWithTrack,
) {

    /**
     * Fetches updated tracking data from all logged in trackers.
     * Also sync chapter progress with the [EnhancedTracker] or all trackers based on [enhancedTrackersOnly].
     *
     * @return List of refresh results, both failed & succeed updates.
     */
    suspend fun await(
        mangaId: Long,
        // KMK -->
        enhancedTrackersOnly: Boolean = true,
        // KMK <--
        // AM -->
        skipCompleted: Boolean = false,
    ): List<RefreshResult> {
        // <-- AM
        return supervisorScope {
            return@supervisorScope getTracks.await(mangaId)
                .map { it to trackerManager.get(it.trackerId) }
                .filter { (_, service) -> service?.isLoggedIn == true }
                .map { (track, service) ->
                    async {
                        return@async try {
                            // AM -->
                            // ANK -->
                            val isCompleted = track.totalEpisodes > 0 && track.totalEpisodes == track.lastEpisodeSeen.toLong()
                            val track = if (!(skipCompleted && isCompleted)) {
                                // ANK <--
                                // <-- AM
                                val updatedTrack = service!!.refresh(track.toDbTrack()).toDomainTrack()!!
                                insertTrack.await(updatedTrack)
                                syncEpisodeProgressWithTrack.await(
                                    mangaId,
                                    updatedTrack,
                                    service,
                                    // KMK -->
                                    enhancedTrackersOnly = enhancedTrackersOnly,
                                )
                                    ?.let {
                                        val context = Injekt.get<Application>()
                                        withUIContext {
                                            context.toast(context.stringResource(AMR.strings.sync_progress_from_trackers_up_to_episode, it))
                                        }
                                    }
                                // KMK <--
                                // ANK -->
                                updatedTrack
                            } else {
                                track
                                // ANK <--
                            }

                            // AM -->
                            RefreshResult.Success(track)
                        } catch (e: Throwable) {
                            RefreshResult.Failure(service!!, e)
                            // <-- AM
                        }
                    }
                }
                .awaitAll()
        }
    }
}

// AM -->
sealed interface RefreshResult {
    data class Failure(val tracker: Tracker, val error: Throwable) : RefreshResult
    data class Success(val track: Track) : RefreshResult
}
// <-- AM
