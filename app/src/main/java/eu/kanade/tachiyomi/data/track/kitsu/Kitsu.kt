package eu.kanade.tachiyomi.data.track.kitsu

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import tachiyomi.domain.track.model.Track as DomainTrack

class Kitsu(id: Long) : BaseTracker(id, "Kitsu"), DeletableTracker {

    companion object {
        const val WATCHING = 11L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 15L
    }

    override val supportsWatchingDates: Boolean = true

    private val json: Json by injectLazy()

    private val interceptor by lazy { KitsuInterceptor(this) }

    private val api by lazy { KitsuApi(client, interceptor) }

    override fun getLogo() = R.drawable.ic_tracker_kitsu

    override fun getLogoColor() = Color.rgb(51, 37, 50)

    override fun getStatusListAnime(): List<Long> {
        return listOf(WATCHING, PLAN_TO_WATCH, COMPLETED, ON_HOLD, DROPPED)
    }

    override fun getStatusForAnime(status: Long): StringResource? = when (status) {
        WATCHING -> MR.strings.currently_watching
        PLAN_TO_WATCH -> MR.strings.want_to_watch
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        else -> null
    }

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRewatchingStatus(): Long = -1

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> {
        val df = DecimalFormat("0.#")
        return (listOf("0") + IntRange(2, 20).map { df.format(it / 2f) }).toImmutableList()
    }

    override fun indexToScore(index: Int): Double {
        return if (index > 0) (index + 1) / 2.0 else 0.0
    }

    override fun displayScore(track: DomainTrack): String {
        val df = DecimalFormat("0.#")
        return df.format(track.score)
    }

    private suspend fun add(track: Track): Track {
        return api.addLibAnime(track, getUserId())
    }

    override suspend fun update(track: Track, didWatchEpisode: Boolean): Track {
        if (track.status != COMPLETED) {
            if (didWatchEpisode) {
                if (track.last_episode_seen.toLong() == track.total_episodes && track.total_episodes > 0) {
                    track.status = COMPLETED
                    track.finished_watching_date = System.currentTimeMillis()
                } else {
                    track.status = WATCHING
                    if (track.last_episode_seen == 1.0) {
                        track.started_watching_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api.updateLibAnime(track)
    }

    override suspend fun delete(track: DomainTrack) {
        api.removeLibAnime(track)
    }

    override suspend fun bind(track: Track, hasSeenEpisodes: Boolean): Track {
        val remoteTrack = api.findLibAnime(track, getUserId())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.remote_id = remoteTrack.remote_id

            if (track.status != COMPLETED) {
                track.status = if (hasSeenEpisodes) WATCHING else track.status
            }

            update(track)
        } else {
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        return api.searchAnime(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibAnime(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_episodes = remoteTrack.total_episodes
        return track
    }

    override suspend fun login(username: String, password: String) {
        val token = api.login(username, password)
        interceptor.newAuth(token)
        val userId = api.getCurrentUser()
        saveCredentials(username, userId)
    }

    override fun logout() {
        super.logout()
        interceptor.newAuth(null)
    }

    private fun getUserId(): String {
        return getPassword()
    }

    fun saveToken(oauth: KitsuOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): KitsuOAuth? {
        return try {
            json.decodeFromString<KitsuOAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }
    }

    // KMK -->
    override fun hasNotStartedWatching(status: Long): Boolean = status == PLAN_TO_WATCH
    // KMK <--
}
