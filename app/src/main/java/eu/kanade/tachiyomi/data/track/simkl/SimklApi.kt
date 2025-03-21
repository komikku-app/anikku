package eu.kanade.tachiyomi.data.track.simkl

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklAnimeResponse
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklOAuth
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklSearchResult
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklSyncResult
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklSyncWatched
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklUser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

class SimklApi(private val client: OkHttpClient, interceptor: SimklInterceptor) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibAnime(track: Track): Track {
        return withIOContext {
            val type = track.tracking_url
                .substringAfter("/")
                .substringBefore("/")
            val mediaType = if (type == "movies") "movies" else "shows"
            addToList(track, mediaType)

            track
        }
    }

    private suspend fun addToList(track: Track, mediaType: String) {
        val payload = buildJsonObject {
            putJsonArray(mediaType) {
                addJsonObject {
                    putJsonObject("ids") {
                        put("simkl", track.remote_id)
                    }
                    put("to", track.toSimklStatus())
                }
            }
        }.toString().toRequestBody(jsonMime)
        authClient.newCall(
            POST("$API_URL/sync/add-to-list", body = payload),
        ).awaitSuccess()
    }

    private suspend fun updateRating(track: Track, mediaType: String) {
        val payload = buildJsonObject {
            putJsonArray(mediaType) {
                addJsonObject {
                    putJsonObject("ids") {
                        put("simkl", track.remote_id)
                    }
                    put("rating", track.score.toInt())
                }
            }
        }.toString().toRequestBody(jsonMime)

        if (track.score == 0.0) {
            authClient.newCall(
                POST("$API_URL/sync/ratings/remove", body = payload),
            ).awaitSuccess()
        } else {
            authClient.newCall(
                POST("$API_URL/sync/ratings", body = payload),
            ).awaitSuccess()
        }
    }

    private suspend fun updateProgress(track: Track) {
        // first remove
        authClient.newCall(
            POST("$API_URL/sync/history/remove", body = buildProgressObject(track, false)),
        ).awaitSuccess()
        // then add again
        authClient.newCall(
            POST("$API_URL/sync/history", body = buildProgressObject(track, true)),
        ).awaitSuccess()
    }

    private fun buildProgressObject(track: Track, add: Boolean = true) = buildJsonObject {
        putJsonArray("shows") {
            addJsonObject {
                putJsonObject("ids") {
                    put("simkl", track.remote_id)
                }
                putJsonArray("seasons") {
                    addJsonObject {
                        put("number", 1)
                        if (add) {
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

    suspend fun updateLibAnime(track: Track): Track {
        return withIOContext {
            // determine media type
            val type = track.tracking_url
                .substringAfter("/")
                .substringBefore("/")
            val mediaType = if (type == "movies") "movies" else "shows"
            // update progress only for shows
            if (type != "movies") {
                updateProgress(track)
            }
            // add to correct list
            addToList(track, mediaType)
            // update rating
            updateRating(track, mediaType)

            track
        }
    }

    suspend fun searchAnime(search: String, type: String): List<TrackSearch> {
        return withIOContext {
            val searchUrl = "$API_URL/search/$type".toUri().buildUpon()
                .appendQueryParameter("q", search)
                .appendQueryParameter("extended", "full")
                .appendQueryParameter("client_id", CLIENT_ID)
                .build()
            with(json) {
                client.newCall(GET(searchUrl.toString()))
                    .awaitSuccess()
                    .parseAs<List<SimklSearchResult>>()
                    .map { it.toTrackSearch(type) }
            }
        }
    }

    /**
     * Checks if the given [track] exists in the user's list and
     * returns all info about it or null if it isn't found.
     */
    suspend fun findLibAnime(track: Track): Track? {
        return withIOContext {
            val payload = buildJsonArray {
                addJsonObject {
                    put("simkl", track.remote_id)
                }
            }.toString().toRequestBody(jsonMime)
            val foundAnime = with(json) {
                authClient.newCall(
                    POST("$API_URL/sync/watched", body = payload),
                )
                    .awaitSuccess()
                    .parseAs<List<SimklSyncWatched>>()
                    .firstOrNull() ?: return@withIOContext null
            }

            if (foundAnime.result != true) return@withIOContext null
            val lastWatched = foundAnime.lastWatched ?: return@withIOContext null
            val status = foundAnime.list ?: return@withIOContext null
            val type = track.tracking_url
                .substringAfter("/")
                .substringBefore("/")
            val queryType = if (type == "tv") "shows" else type
            val url = "$API_URL/sync/all-items/$queryType/$status".toUri().buildUpon()
                .appendQueryParameter("date_from", lastWatched)
                .build()

            val typeName = if (type == "movies") "movie" else "show"
            val listAnime = with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<SimklSyncResult>()
                    .getFromType(queryType)
                    ?.firstOrNull { item ->
                        item.getFromType(typeName).ids.simkl == track.remote_id
                    } ?: return@withIOContext null
            }

            listAnime.toAnimeTrack(typeName, type, status)
        }
    }

    fun getCurrentUser(): Int {
        return runBlocking {
            with(json) {
                authClient.newCall(GET("$API_URL/users/settings"))
                    .awaitSuccess()
                    .parseAs<SimklUser>()
                    .account.id
            }
        }
    }

    suspend fun getSimklAnimeMetadata(track: DomainTrack): TrackAnimeMetadata {
        return withIOContext {
            val type = track.remoteUrl
                .substringAfter("/")
                .substringBefore("/")
            val url = "$API_URL/$type/${track.remoteId}?extended=full&client_id=$CLIENT_ID"
            with(json) {
                authClient.newCall(GET(url))
                    .awaitSuccess()
                    .parseAs<SimklAnimeResponse>()
                    .let { anime ->
                        TrackAnimeMetadata(
                            remoteId = anime.id,
                            title = anime.title,
                            thumbnailUrl = anime.poster?.let { "$POSTERS_URL${it}_m.webp" },
                            description = anime.overview,
                            authors = when (type) {
                                "anime" -> anime.studios?.joinToString { it.name }
                                "movies" -> anime.director
                                else -> anime.network
                            },
                            artists = anime.network,
                        )
                    }
            }
        }
    }

    suspend fun accessToken(code: String): SimklOAuth {
        return withIOContext {
            with(json) {
                client.newCall(accessTokenRequest(code))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun accessTokenRequest(code: String) = POST(
        OAUTH_URL,
        body = buildJsonObject {
            put("code", code)
            put("client_id", CLIENT_ID)
            put("client_secret", CLIENT_SECRET)
            put("redirect_uri", REDIRECT_URL)
            put("grant_type", "authorization_code")
        }.toString().toRequestBody(jsonMime),
    )

    companion object {
        const val CLIENT_ID = "f9911f4e260444a04e4939713995902a0f8168f57f548bda1907fc7196de6673"
        private const val CLIENT_SECRET = "6403240b6a96fb1f5aeccb8f6fec39b02810195e378a76d43b5088465ce6f62e"

        private const val BASE_URL = "https://simkl.com"
        private const val API_URL = "https://api.simkl.com"
        private const val OAUTH_URL = "$API_URL/oauth/token"
        private const val LOGIN_URL = "$BASE_URL/oauth/authorize"
        const val POSTERS_URL = "https://simkl.in/posters/"

        private const val REDIRECT_URL = "anikku://simkl-auth"

        fun authUrl(): Uri =
            LOGIN_URL.toUri().buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URL)
                .build()
    }
}
