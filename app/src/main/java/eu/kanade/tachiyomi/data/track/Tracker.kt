package eu.kanade.tachiyomi.data.track

import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import tachiyomi.domain.track.model.Track as DomainTrack

interface Tracker {

    val id: Long

    val name: String

    val client: OkHttpClient

    // Application and remote support for watching dates
    val supportsWatchingDates: Boolean

    @ColorInt
    fun getLogoColor(): Int

    @DrawableRes
    fun getLogo(): Int

    fun getStatusListAnime(): List<Long>

    fun getStatusForAnime(status: Long): StringResource?

    fun getWatchingStatus(): Long

    fun getRewatchingStatus(): Long

    fun getCompletionStatus(): Long

    fun getScoreList(): ImmutableList<String>

    // TODO: Store all scores as 10 point in the future maybe?
    fun get10PointScore(track: DomainTrack): Double

    fun indexToScore(index: Int): Double

    fun displayScore(track: DomainTrack): String

    suspend fun update(track: Track, didWatchEpisode: Boolean = false): Track

    suspend fun bind(track: Track, hasSeenEpisodes: Boolean = false): Track

    suspend fun search(query: String): List<TrackSearch>

    suspend fun refresh(track: Track): Track

    suspend fun login(username: String, password: String)

    @CallSuper
    fun logout()

    val isLoggedIn: Boolean

    val isLoggedInFlow: Flow<Boolean>

    fun getUsername(): String

    fun getPassword(): String

    fun saveCredentials(username: String, password: String)

    // TODO: move this to an interactor, and update all trackers based on common data
    suspend fun register(item: Track, animeId: Long)

    suspend fun setRemoteAnimeStatus(track: Track, status: Long)

    suspend fun setRemoteLastEpisodeSeen(track: Track, episodeNumber: Int)

    suspend fun setRemoteScore(track: Track, scoreString: String)

    suspend fun setRemoteStartDate(track: Track, epochMillis: Long)

    suspend fun setRemoteFinishDate(track: Track, epochMillis: Long)

    // KMK -->
    fun hasNotStartedWatching(status: Long): Boolean
    // KMK <--
}
