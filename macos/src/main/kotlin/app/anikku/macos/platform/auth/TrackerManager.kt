package app.anikku.macos.platform.auth

import androidx.compose.runtime.compositionLocalOf
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private val logger = KotlinLogging.logger {}

/**
 * High-level manager for tracker authentication and token lifecycle.
 */
class TrackerManager(
    private val oauthManager: TrackerOAuthManager,
    val tokenStore: TrackerTokenStore,
    private val httpClient: OkHttpClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val _loginStatuses = MutableStateFlow(tokenStore.getAllStatuses())
    val loginStatuses: StateFlow<List<TrackerTokenStore.TrackerLoginStatus>> =
        _loginStatuses.asStateFlow()

    fun refreshStatus() {
        _loginStatuses.value = tokenStore.getAllStatuses()
    }

    fun login(
        tracker: String,
        clientId: String,
        clientSecret: String,
        onResult: (Boolean, String) -> Unit,
    ) {
        scope.launch {
            try {
                logger.info { "Starting OAuth login for $tracker..." }
                val token = oauthManager.completeLogin(
                    tracker = tracker,
                    clientId = clientId,
                    clientSecret = clientSecret,
                )
                if (token != null) {
                    val username = lookupUsername(tracker, token.accessToken)
                    tokenStore.saveTokensWithUsername(tracker, token, username ?: tracker)
                    refreshStatus()
                    logger.info { "OAuth login successful for $tracker (user: $username)" }
                    onResult(true, "Logged in to $tracker${if (username != null) " as $username" else ""}")
                } else {
                    logger.warn { "OAuth login failed for $tracker -- no token returned" }
                    onResult(false, "Authentication failed or timed out")
                }
            } catch (e: Exception) {
                logger.error(e) { "OAuth login error for $tracker" }
                onResult(false, "Error: ${e.message?.take(100) ?: "Unknown error"}")
            }
        }
    }

    fun logout(tracker: String): Boolean {
        val removed = tokenStore.removeTokens(tracker)
        if (removed) refreshStatus()
        return removed
    }

    fun isLoggedIn(tracker: String): Boolean = tokenStore.isLoggedIn(tracker)

    fun getUsername(tracker: String): String? = tokenStore.getUsername(tracker)

    fun searchAnime(tracker: String, query: String): List<TrackerSearchResult> {
        val token = tokenStore.getTokens(tracker)?.accessToken ?: return emptyList()
        return try {
            when (tracker) {
                "myanimelist" -> searchMyAnimeList(token, query)
                "anilist" -> searchAniList(token, query)
                else -> emptyList()
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to search $tracker for \"$query\"" }
            emptyList()
        }
    }

    fun updateProgress(
        tracker: String,
        remoteAnimeId: String,
        episodeNumber: Int,
        status: String? = null,
    ): Boolean {
        val token = tokenStore.getTokens(tracker)?.accessToken ?: return false
        return try {
            when (tracker) {
                "myanimelist" -> updateMyAnimeList(token, remoteAnimeId, episodeNumber, status)
                "anilist" -> updateAniList(token, remoteAnimeId, episodeNumber, status)
                else -> false
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to update $tracker progress for anime $remoteAnimeId" }
            false
        }
    }

    fun scrobbleProgress(animeTitle: String, episodeNumber: Int): ScrobbleResult {
        if (animeTitle.isBlank()) return ScrobbleResult()

        val successes = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val notFound = mutableListOf<String>()

        tokenStore.getAllStatuses()
            .filter { it.isLoggedIn }
            .forEach { status ->
                try {
                    val manualId = tokenStore.getAnimeMapping(status.tracker, animeTitle)
                    val matchId: String

                    if (manualId != null) {
                        matchId = manualId
                        logger.info { "Using manual mapping for \"$animeTitle\" on ${status.tracker} -> id=$matchId" }
                    } else {
                        val results = searchAnime(status.tracker, animeTitle)
                        val bestMatch = results.firstOrNull()
                        if (bestMatch == null) {
                            notFound.add(status.tracker)
                            logger.info { "No ${status.tracker} match for \"$animeTitle\"" }
                            return@forEach
                        }
                        matchId = bestMatch.id
                    }

                    val updated = updateProgress(status.tracker, matchId, episodeNumber)
                    if (updated) {
                        successes.add(status.tracker)
                        logger.info { "Scrobbled \"$animeTitle\" ep $episodeNumber to ${status.tracker} (id=$matchId)" }
                    } else {
                        failures.add(status.tracker)
                        logger.warn { "Tracker ${status.tracker} rejected progress update for \"$animeTitle\"" }
                    }
                } catch (e: Exception) {
                    failures.add(status.tracker)
                    logger.error(e) { "Failed to scrobble \"$animeTitle\" to ${status.tracker}" }
                }
            }

        return ScrobbleResult(successes, failures, notFound)
    }

    fun setAnimeMapping(animeTitle: String, tracker: String, trackerAnimeId: String) {
        tokenStore.saveAnimeMapping(tracker, animeTitle, trackerAnimeId)
        logger.info { "Manual mapping: \"${animeTitle.take(40)}\" on $tracker -> $trackerAnimeId" }
    }

    fun clearAnimeMapping(animeTitle: String, tracker: String) {
        tokenStore.removeAnimeMapping(tracker, animeTitle)
        logger.info { "Manual mapping cleared for \"${animeTitle.take(40)}\" on $tracker" }
    }

    // -- Internal tracker implementations ----------------------------------

    private fun searchMyAnimeList(token: String, query: String): List<TrackerSearchResult> {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.myanimelist.net")
            .addPathSegments("v2/anime")
            .addQueryParameter("q", query.take(64))
            .addQueryParameter("limit", "5")
            .addQueryParameter("fields", "id,title,main_picture")
            .build()

        val request = okhttp3.Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val bodyStr = response.body?.string() ?: ""
        response.close()

        val data = JSONObject(bodyStr).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).map { i ->
            val node = data.getJSONObject(i).getJSONObject("node")
            TrackerSearchResult(
                id = node.getInt("id").toString(),
                title = node.getString("title"),
                imageUrl = node.optJSONObject("main_picture")?.optString("medium"),
            )
        }
    }

    private fun searchAniList(token: String, query: String): List<TrackerSearchResult> {
        val escapedQuery = JSONObject.quote(query)
        val dollar = '$'
        val gql = """
            {"query":"query Search(${dollar}q: String) { Page(perPage: 5) { media(search: ${dollar}q, type: ANIME) { id title { romaji english } coverImage { medium } } } }","variables":{"q":$escapedQuery}}
        """.trimIndent()

        val request = okhttp3.Request.Builder()
            .url("https://graphql.anilist.co")
            .header("Authorization", "Bearer $token")
            .post(gql.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val bodyStr = response.body?.string() ?: ""
        response.close()

        val media = JSONObject(bodyStr)
            .optJSONObject("data")
            ?.optJSONObject("Page")
            ?.optJSONArray("media") ?: return emptyList()

        return (0 until media.length()).map { i ->
            val node = media.getJSONObject(i)
            val titleObj = node.optJSONObject("title")
            val title = titleObj?.optString("romaji")
                ?: titleObj?.optString("english")
                ?: node.getString("id")
            TrackerSearchResult(
                id = node.getInt("id").toString(),
                title = title,
                imageUrl = node.optJSONObject("coverImage")?.optString("medium"),
            )
        }
    }

    private fun updateMyAnimeList(
        token: String,
        remoteAnimeId: String,
        episodeNumber: Int,
        status: String?,
    ): Boolean {
        val formBody = FormBody.Builder()
            .add("num_watched_episodes", episodeNumber.coerceAtLeast(0).toString())
        status?.let { formBody.add("status", it) }

        val request = okhttp3.Request.Builder()
            .url("https://api.myanimelist.net/v2/anime/$remoteAnimeId/my_list_status")
            .header("Authorization", "Bearer $token")
            .patch(formBody.build())
            .build()

        val response = httpClient.newCall(request).execute()
        response.close()
        return response.isSuccessful
    }

    private fun updateAniList(
        token: String,
        remoteAnimeId: String,
        episodeNumber: Int,
        status: String?,
    ): Boolean {
        val alStatus = when (status) {
            "watching" -> "CURRENT"
            "completed" -> "COMPLETED"
            "on_hold" -> "PAUSED"
            "dropped" -> "DROPPED"
            "plan_to_watch" -> "PLANNING"
            else -> null
        }

        val mediaId = remoteAnimeId.toIntOrNull() ?: return false
        val variables = JSONObject().apply {
            put("mediaId", mediaId)
            put("progress", episodeNumber.coerceAtLeast(0))
            if (alStatus != null) put("status", alStatus)
        }

        val dollar = '$'
        val statusDeclaration = if (alStatus != null) ", ${dollar}status: MediaListStatus" else ""
        val statusArgument = if (alStatus != null) ", status: ${dollar}status" else ""
        val query = "mutation Save(${dollar}mediaId: Int, ${dollar}progress: Int$statusDeclaration) { SaveMediaListEntry(mediaId: ${dollar}mediaId, progress: ${dollar}progress$statusArgument) { id progress } }"

        val payload = JSONObject().apply {
            put("query", query)
            put("variables", variables)
        }

        val request = okhttp3.Request.Builder()
            .url("https://graphql.anilist.co")
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val response = httpClient.newCall(request).execute()
        response.close()
        return response.isSuccessful
    }

    fun validateAllTokens() {
        tokenStore.getAllStatuses().filter { it.isLoggedIn }.forEach { status ->
            val stored = tokenStore.getTokens(status.tracker) ?: return@forEach
            if (stored.accessToken.isNotEmpty()) {
                val isValid = oauthManager.validateToken(status.tracker, stored.accessToken)
                if (!isValid && stored.refreshToken.isNotEmpty()) {
                    scope.launch {
                        try {
                            val clientId = ""
                            val clientSecret = ""
                            val refreshed = oauthManager.refreshToken(
                                tracker = status.tracker,
                                refreshToken = stored.refreshToken,
                                clientId = clientId,
                                clientSecret = clientSecret,
                            )
                            if (refreshed != null) {
                                tokenStore.saveTokens(status.tracker, refreshed)
                                logger.info { "Token refreshed for ${status.tracker}" }
                            } else {
                                logger.warn { "Token refresh failed for ${status.tracker}" }
                            }
                        } catch (e: Exception) {
                            logger.warn(e) { "Token refresh error for ${status.tracker}" }
                        }
                        refreshStatus()
                    }
                }
            }
        }
    }

    private fun lookupUsername(tracker: String, accessToken: String): String? {
        return try {
            when (tracker) {
                "myanimelist" -> {
                    val request = okhttp3.Request.Builder()
                        .url("https://api.myanimelist.net/v2/users/@me")
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return null
                        response.close()
                        JSONObject(body).optString("name")
                    } else {
                        response.close()
                        null
                    }
                }
                "anilist" -> {
                    val query = """{"query":"query { Viewer { name } }"}"""
                    val request = okhttp3.Request.Builder()
                        .url("https://graphql.anilist.co")
                        .header("Authorization", "Bearer $accessToken")
                        .post(query.toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return null
                        response.close()
                        JSONObject(body)
                            .optJSONObject("data")
                            ?.optJSONObject("Viewer")
                            ?.optString("name")
                    } else {
                        response.close()
                        null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to lookup username for $tracker" }
            null
        }
    }
}

/**
 * Search result returned by a tracker search.
 */
data class TrackerSearchResult(
    val id: String,
    val title: String,
    val imageUrl: String? = null,
)

/**
 * Result of a scrobble attempt across all logged-in trackers.
 */
data class ScrobbleResult(
    val successes: List<String> = emptyList(),
    val failures: List<String> = emptyList(),
    val notFound: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = successes.isEmpty() && failures.isEmpty() && notFound.isEmpty()

    fun toToastMessage(): String? {
        if (isEmpty) return null
        val parts = mutableListOf<String>()
        if (successes.isNotEmpty()) {
            parts.add("Scrobbled to ${successes.joinToString()}")
        }
        if (failures.isNotEmpty()) {
            parts.add("failed: ${failures.joinToString()}")
        }
        if (notFound.isNotEmpty()) {
            parts.add("no match: ${notFound.joinToString()}")
        }
        return parts.joinToString("; ")
    }
}

val LocalTrackerManager = compositionLocalOf<TrackerManager?> { null }
