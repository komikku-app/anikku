package eu.kanade.domain.episode.interactor

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.anizip.AniZipService
import eu.kanade.tachiyomi.data.anizip.model.AniZipEpisodeMeta
import eu.kanade.tachiyomi.data.track.TrackerManager
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.track.interactor.GetTracks

class EnrichEpisodesWithAniZip(
    private val aniZipService: AniZipService,
    private val getTracks: GetTracks,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val trackPreferences: TrackPreferences,
) {
    suspend fun await(mangaId: Long, fallbackTrackMangaId: Long? = null): Map<Long, AniZipEpisodeMeta> = withIOContext {
        if (!trackPreferences.enableAniZip().get()) return@withIOContext emptyMap()

        try {
            val tracks = getTracks.await(mangaId).ifEmpty {
                fallbackTrackMangaId?.let { getTracks.await(it) }.orEmpty()
            }
            if (tracks.isEmpty()) return@withIOContext emptyMap()

            val anilistTrack = tracks.firstOrNull { it.trackerId == TrackerManager.ANILIST && it.remoteId > 0 }
            val malTrack = tracks.firstOrNull { it.trackerId == TrackerManager.MYANIMELIST && it.remoteId > 0 }

            if (anilistTrack == null && malTrack == null) return@withIOContext emptyMap()

            val metadata = anilistTrack?.let {
                aniZipService.getMetadata(anilistId = it.remoteId)
            }.orEmpty().ifEmpty {
                malTrack?.let { aniZipService.getMetadata(malId = it.remoteId) }.orEmpty()
            }
            if (metadata.isEmpty()) return@withIOContext emptyMap()

            val chapters = getChaptersByMangaId.await(mangaId)
            if (chapters.isEmpty()) return@withIOContext emptyMap()

            chapters.mapNotNull { chapter ->
                val meta = aniZipService.findMetaForEpisode(metadata, chapter.chapterNumber, chapter.name)
                    ?: return@mapNotNull null
                chapter.id to meta
            }.toMap()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "AniZip: Failed to enrich episodes for manga $mangaId" }
            emptyMap()
        }
    }
}
