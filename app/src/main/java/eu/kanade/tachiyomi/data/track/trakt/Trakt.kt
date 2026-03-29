package eu.kanade.tachiyomi.data.track.trakt

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktOAuth
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainAnimeTrack

class Trakt(id: Long) : BaseTracker(id, "Trakt"), AnimeTracker {

    companion object {
        const val WATCHING = 1L
        const val COMPLETED = 2L
        const val PLAN_TO_WATCH = 3L
        const val DROPPED = 4L
        const val REWATCHING = 5L

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)
            .toImmutableList()
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { TraktInterceptor(this) }

    internal val api by lazy { TraktApi(client, interceptor) }

    override fun getLogo() = R.drawable.ic_tracker_trakt

    override fun getLogoColor() = Color.rgb(237, 34, 36)

    override fun getStatusListAnime(): List<Long> {
        return listOf(WATCHING, COMPLETED, PLAN_TO_WATCH, DROPPED, REWATCHING)
    }

    override fun getStatusForAnime(status: Long): StringResource? = when (status) {
        WATCHING -> MR.strings.watching
        COMPLETED -> MR.strings.completed
        PLAN_TO_WATCH -> MR.strings.plan_to_watch
        DROPPED -> MR.strings.dropped
        REWATCHING -> MR.strings.repeating_anime
        else -> null
    }

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRewatchingStatus(): Long = REWATCHING

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST

    override fun displayScore(track: DomainAnimeTrack): String {
        return track.score.toInt().toString()
    }

    private suspend fun add(track: Track): Track {
        return api.addLibAnime(track)
    }

    override suspend fun update(track: Track, didWatchEpisode: Boolean): Track {
        if (track.status != COMPLETED) {
            if (didWatchEpisode) {
                if (track.last_episode_seen.toLong() == track.total_episodes && track.total_episodes > 0) {
                    track.status = COMPLETED
                } else if (track.status != REWATCHING) {
                    track.status = WATCHING
                }
            }
        }
        return api.updateLibAnime(track)
    }

    override suspend fun bind(track: Track, hasSeenEpisodes: Boolean): Track {
        val remoteTrack = api.findLibAnime(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id

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

    override suspend fun searchAnime(query: String): List<TrackSearch> {
        return api.searchAnime(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibAnime(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_episodes = remoteTrack.total_episodes
        return track
    }

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(code: String) {
        try {
            val oauth = api.accessToken(code)
            interceptor.newAuth(oauth)
            val username = api.getCurrentUser()
            saveCredentials(username, oauth.accessToken)
        } catch (e: Throwable) {
            logout()
            throw e
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.newAuth(null)
    }

    fun saveOAuth(oauth: TraktOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun loadOAuth(): TraktOAuth? {
        return try {
            json.decodeFromString<TraktOAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }
    }
}
