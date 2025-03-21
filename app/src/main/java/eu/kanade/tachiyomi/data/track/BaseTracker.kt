package eu.kanade.tachiyomi.data.track

import android.app.Application
import androidx.annotation.CallSuper
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.InsertTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

abstract class BaseTracker(
    override val id: Long,
    override val name: String,
) : Tracker {

    val trackPreferences: TrackPreferences by injectLazy()
    val networkService: NetworkHelper by injectLazy()
    private val addTracks: AddTracks by injectLazy()
    private val insertTrack: InsertTrack by injectLazy()

    override val client: OkHttpClient
        get() = networkService.client

    // Application and remote support for watching dates
    override val supportsWatchingDates: Boolean = false

    // TODO: Store all scores as 10 point in the future maybe?
    override fun get10PointScore(track: DomainTrack): Double {
        return track.score
    }

    override fun indexToScore(index: Int): Double {
        return index.toDouble()
    }

    @CallSuper
    override fun logout() {
        trackPreferences.setCredentials(this, "", "")
    }

    override val isLoggedIn: Boolean
        get() = getUsername().isNotEmpty() &&
            getPassword().isNotEmpty()

    override val isLoggedInFlow: Flow<Boolean> by lazy {
        combine(
            trackPreferences.trackUsername(this).changes(),
            trackPreferences.trackPassword(this).changes(),
        ) { username, password ->
            username.isNotEmpty() && password.isNotEmpty()
        }
    }

    override fun getUsername() = trackPreferences.trackUsername(this).get()

    override fun getPassword() = trackPreferences.trackPassword(this).get()

    override fun saveCredentials(username: String, password: String) {
        trackPreferences.setCredentials(this, username, password)
    }

    override suspend fun register(item: Track, animeId: Long) {
        item.anime_id = animeId
        try {
            addTracks.bind(this, item, animeId)
        } catch (e: Throwable) {
            withUIContext { Injekt.get<Application>().toast(e.message) }
        }
    }

    override suspend fun setRemoteAnimeStatus(track: Track, status: Long) {
        track.status = status
        if (track.status == getCompletionStatus() && track.total_episodes != 0L) {
            track.last_episode_seen = track.total_episodes.toDouble()
        }
        updateRemote(track)
    }

    override suspend fun setRemoteLastEpisodeSeen(track: Track, episodeNumber: Int) {
        if (
            track.last_episode_seen == 0.0 &&
            track.last_episode_seen < episodeNumber &&
            track.status != getRewatchingStatus()
        ) {
            track.status = getWatchingStatus()
        }
        track.last_episode_seen = episodeNumber.toDouble()
        if (track.total_episodes != 0L && track.last_episode_seen.toLong() == track.total_episodes) {
            track.status = getCompletionStatus()
            track.finished_watching_date = System.currentTimeMillis()
        }
        updateRemote(track)
    }

    override suspend fun setRemoteScore(track: Track, scoreString: String) {
        track.score = indexToScore(getScoreList().indexOf(scoreString))
        updateRemote(track)
    }

    override suspend fun setRemoteStartDate(track: Track, epochMillis: Long) {
        track.started_watching_date = epochMillis
        updateRemote(track)
    }

    override suspend fun setRemoteFinishDate(track: Track, epochMillis: Long) {
        track.finished_watching_date = epochMillis
        updateRemote(track)
    }

    override suspend fun getAnimeMetadata(track: DomainTrack): TrackAnimeMetadata {
        throw NotImplementedError("Not implemented.")
    }

    private suspend fun updateRemote(track: Track): Unit = withIOContext {
        try {
            update(track)
            track.toDomainTrack(idRequired = false)?.let {
                insertTrack.await(it)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to update remote track data id=$id" }
            withUIContext { Injekt.get<Application>().toast(e.message) }
        }
    }
}
