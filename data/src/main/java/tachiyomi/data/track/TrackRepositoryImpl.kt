package tachiyomi.data.track

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository

class TrackRepositoryImpl(
    private val handler: DatabaseHandler,
) : TrackRepository {

    override suspend fun getTrackById(id: Long): Track? {
        return handler.awaitOneOrNull { anime_syncQueries.getTrackById(id, TrackMapper::mapTrack) }
    }

    // SY -->
    override suspend fun getTracks(): List<Track> {
        return handler.awaitList {
            anime_syncQueries.getTracks(TrackMapper::mapTrack)
        }
    }

    override suspend fun getTracksByMangaIds(mangaIds: List<Long>): List<Track> {
        return handler.awaitList {
            anime_syncQueries.getTracksByAnimeIds(mangaIds, TrackMapper::mapTrack)
        }
    }
    // SY <--

    override suspend fun getTracksByMangaId(mangaId: Long): List<Track> {
        return handler.awaitList {
            anime_syncQueries.getTracksByAnimeId(mangaId, TrackMapper::mapTrack)
        }
    }

    override fun getTracksAsFlow(): Flow<List<Track>> {
        return handler.subscribeToList {
            anime_syncQueries.getTracks(TrackMapper::mapTrack)
        }
    }

    override fun getTracksByMangaIdAsFlow(mangaId: Long): Flow<List<Track>> {
        return handler.subscribeToList {
            anime_syncQueries.getTracksByAnimeId(mangaId, TrackMapper::mapTrack)
        }
    }

    override suspend fun delete(mangaId: Long, trackerId: Long) {
        handler.await {
            anime_syncQueries.delete(
                animeId = mangaId,
                syncId = trackerId,
            )
        }
    }

    override suspend fun insert(track: Track) {
        insertValues(track)
    }

    override suspend fun insertAll(tracks: List<Track>) {
        insertValues(*tracks.toTypedArray())
    }

    private suspend fun insertValues(vararg tracks: Track) {
        handler.await(inTransaction = true) {
            tracks.forEach { animeTrack ->
                anime_syncQueries.insert(
                    animeId = animeTrack.animeId,
                    syncId = animeTrack.trackerId,
                    remoteId = animeTrack.remoteId,
                    libraryId = animeTrack.libraryId,
                    title = animeTrack.title,
                    lastEpisodeSeen = animeTrack.lastEpisodeSeen,
                    totalEpisodes = animeTrack.totalEpisodes,
                    status = animeTrack.status,
                    score = animeTrack.score,
                    remoteUrl = animeTrack.remoteUrl,
                    startDate = animeTrack.startDate,
                    finishDate = animeTrack.finishDate,
                )
            }
        }
    }
}
