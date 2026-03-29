package eu.kanade.tachiyomi.data.track.trakt

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktOAuth
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktSearchResult
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktShowProgress
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktUserSettings
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktWatchlistItem
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

class TraktApi(private val client: OkHttpClient, interceptor: TraktInterceptor) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder()
        .addInterceptor(interceptor)
        .build()

    suspend fun addLibAnime(track: Track): Track {
        return withIOContext {
            when (track.status) {
                Trakt.PLAN_TO_WATCH -> {
                    addToWatchlist(track)
                }
                else -> {
                    addToHistory(track)
                }
            }
            if (track.score > 0) {
                updateRating(track)
            }
            track
        }
    }

    suspend fun updateLibAnime(track: Track): Track {
        return withIOContext {
            when (track.status) {
                Trakt.PLAN_TO_WATCH -> {
                    removeFromHistory(track)
                    addToWatchlist(track)
                }
                else -> {
                    removeFromWatchlist(track)
                    updateHistory(track)
                }
            }
            if (track.score > 0) {
                updateRating(track)
            } else {
                removeRating(track)
            }
            track
        }
    }

    suspend fun searchAnime(query: String): List<TrackSearch> {
        return withIOContext {
            val searchUrl = "$API_URL/search/show,movie".toUri().buildUpon()
                .appendQueryParameter("query", query)
                .appendQueryParameter("extended", "full")
                .appendQueryParameter("limit", "20")
                .build()
            with(json) {
                client.newCall(GET(searchUrl.toString()))
                    .awaitSuccess()
                    .parseAs<List<TraktSearchResult>>()
                    .mapNotNull { it.toTrackSearch() }
            }
        }
    }

    suspend fun findLibAnime(track: Track): Track? {
        return withIOContext {
            // Check watched progress first
            try {
                val progress = getWatchedProgress(track.remote_id)
                if (progress.completed > 0) {
                    val status = if (progress.aired > 0 && progress.completed >= progress.aired) {
                        Trakt.COMPLETED
                    } else {
                        Trakt.WATCHING
                    }
                    return@withIOContext Track.create(TrackerManager.TRAKT).apply {
                        remote_id = track.remote_id
                        library_id = track.remote_id
                        last_episode_seen = progress.completed.toDouble()
                        total_episodes = progress.aired.toLong()
                        this.status = status
                        tracking_url = track.tracking_url
                        title = track.title
                    }
                }
            } catch (e: Exception) {
                // Not in watched history, fall through to watchlist check
            }

            // Check watchlist
            val inWatchlist = isInWatchlist(track.remote_id)
            if (inWatchlist) {
                Track.create(TrackerManager.TRAKT).apply {
                    remote_id = track.remote_id
                    library_id = track.remote_id
                    last_episode_seen = 0.0
                    total_episodes = track.total_episodes
                    status = Trakt.PLAN_TO_WATCH
                    tracking_url = track.tracking_url
                    title = track.title
                }
            } else {
                null
            }
        }
    }

    suspend fun getLibAnime(track: Track): Track {
        return findLibAnime(track) ?: throw Exception("Could not find show on Trakt")
    }

    suspend fun getCurrentUser(): String {
        return withIOContext {
            with(json) {
                authClient.newCall(GET("$API_URL/users/settings"))
                    .awaitSuccess()
                    .parseAs<TraktUserSettings>()
                    .user.username
            }
        }
    }

    suspend fun accessToken(code: String): TraktOAuth {
        return withIOContext {
            with(json) {
                client.newCall(accessTokenRequest(code))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private suspend fun getWatchedProgress(traktId: Long): TraktShowProgress {
        return withIOContext {
            with(json) {
                authClient.newCall(GET("$API_URL/shows/$traktId/progress/watched"))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private suspend fun isInWatchlist(traktId: Long): Boolean {
        return withIOContext {
            with(json) {
                authClient.newCall(GET("$API_URL/users/me/watchlist/shows"))
                    .awaitSuccess()
                    .parseAs<List<TraktWatchlistItem>>()
                    .any { it.show.ids.trakt == traktId }
            }
        }
    }

    private suspend fun addToWatchlist(track: Track) {
        val payload = buildJsonObject {
            putJsonArray("shows") {
                addJsonObject {
                    putJsonObject("ids") {
                        put("trakt", track.remote_id)
                    }
                }
            }
        }.toString().toRequestBody(jsonMime)
        authClient.newCall(POST("$API_URL/sync/watchlist", body = payload)).awaitSuccess()
    }

    private suspend fun removeFromWatchlist(track: Track) {
        val payload = buildJsonObject {
            putJsonArray("shows") {
                addJsonObject {
                    putJsonObject("ids") {
                        put("trakt", track.remote_id)
                    }
                }
            }
        }.toString().toRequestBody(jsonMime)
        authClient.newCall(POST("$API_URL/sync/watchlist/remove", body = payload)).awaitSuccess()
    }

    private suspend fun addToHistory(track: Track) {
        if (track.last_episode_seen <= 0) return
        val payload = buildHistoryPayload(track, true)
        authClient.newCall(POST("$API_URL/sync/history", body = payload)).awaitSuccess()
    }

    private suspend fun removeFromHistory(track: Track) {
        val payload = buildHistoryPayload(track, false)
        authClient.newCall(POST("$API_URL/sync/history/remove", body = payload)).awaitSuccess()
    }

    private suspend fun updateHistory(track: Track) {
        // Remove all existing season 1 history, then re-add up to current episode
        removeFromHistory(track)
        addToHistory(track)
    }

    private fun buildHistoryPayload(track: Track, add: Boolean) = buildJsonObject {
        putJsonArray("shows") {
            addJsonObject {
                putJsonObject("ids") {
                    put("trakt", track.remote_id)
                }
                putJsonArray("seasons") {
                    addJsonObject {
                        put("number", 1)
                        if (add && track.last_episode_seen > 0) {
                            putJsonArray("episodes") {
                                for (epNum in 1..track.last_episode_seen.toInt()) {
                                    addJsonObject {
                                        put("number", epNum)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }.toString().toRequestBody(jsonMime)

    private suspend fun updateRating(track: Track) {
        val rating = track.score.toInt().coerceIn(1, 10)
        val payload = buildJsonObject {
            putJsonArray("shows") {
                addJsonObject {
                    put("rating", rating)
                    putJsonObject("ids") {
                        put("trakt", track.remote_id)
                    }
                }
            }
        }.toString().toRequestBody(jsonMime)
        authClient.newCall(POST("$API_URL/sync/ratings", body = payload)).awaitSuccess()
    }

    private suspend fun removeRating(track: Track) {
        val payload = buildJsonObject {
            putJsonArray("shows") {
                addJsonObject {
                    putJsonObject("ids") {
                        put("trakt", track.remote_id)
                    }
                }
            }
        }.toString().toRequestBody(jsonMime)
        authClient.newCall(POST("$API_URL/sync/ratings/remove", body = payload)).awaitSuccess()
    }

    private fun accessTokenRequest(code: String) = POST(
        OAUTH_TOKEN_URL,
        body = buildJsonObject {
            put("code", code)
            put("client_id", CLIENT_ID)
            put("client_secret", CLIENT_SECRET)
            put("redirect_uri", REDIRECT_URL)
            put("grant_type", "authorization_code")
        }.toString().toRequestBody(jsonMime),
    )

    companion object {
        // Register your Trakt app at https://trakt.tv/oauth/applications/new
        // Set the redirect URI to: anikku://trakt-auth
        const val CLIENT_ID = "bb8c13fa479ebae4cfa28b9ec8941673f2a45b7b06bdd4eae4e3e4ac5d76bbd4"
        private const val CLIENT_SECRET = "21fdf5fe8695a61af328e1b986b63cbd1e5c0a6cb3a7449ec34a042483a93a89"

        private const val API_URL = "https://api.trakt.tv"
        private const val OAUTH_AUTHORIZE_URL = "https://trakt.tv/oauth/authorize"
        private const val OAUTH_TOKEN_URL = "$API_URL/oauth/token"
        private const val REDIRECT_URL = "anikku://trakt-auth"

        fun authUrl(): Uri = OAUTH_AUTHORIZE_URL.toUri().buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URL)
            .build()

        fun refreshTokenRequest(refreshToken: String): Request = POST(
            OAUTH_TOKEN_URL,
            body = buildJsonObject {
                put("refresh_token", refreshToken)
                put("client_id", CLIENT_ID)
                put("client_secret", CLIENT_SECRET)
                put("redirect_uri", REDIRECT_URL)
                put("grant_type", "refresh_token")
            }.toString().toRequestBody(jsonMime),
        )
    }
}
