package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import kotlin.math.max

class SyncChapterProgressWithTrack(
    private val updateChapter: UpdateChapter,
    private val insertTrack: InsertTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    suspend fun await(
        animeId: Long,
        remoteTrack: Track,
        tracker: Tracker,
    ) {
        if (tracker !is EnhancedTracker) {
            return
        }

        val sortedEpisodes = getChaptersByMangaId.await(animeId)
            .sortedBy { it.episodeNumber }
            .filter { it.isRecognizedNumber }

        val episodeUpdates = sortedEpisodes
            .filter { episode -> episode.episodeNumber <= remoteTrack.lastEpisodeSeen && !episode.seen }
            .map { it.copy(seen = true).toChapterUpdate() }

        // only take into account continuous watching
        val localLastSeen = sortedEpisodes.takeWhile { it.seen }.lastOrNull()?.episodeNumber ?: 0F
        val lastSeen = max(remoteTrack.lastEpisodeSeen, localLastSeen.toDouble())
        val updatedTrack = remoteTrack.copy(lastEpisodeSeen = lastSeen)

        try {
            tracker.update(updatedTrack.toDbTrack())
            updateChapter.awaitAll(episodeUpdates)
            insertTrack.await(updatedTrack)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e)
        }
    }
}
