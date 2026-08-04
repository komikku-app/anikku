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
                "kitsu" -> searchKitsu(token, query)
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
                "kitsu" -> updateKitsu(token, remoteAnimeId, episodeNumber, status)
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

    /**
     * Fetch the logged-in user's AniList media list (all entries across lists).
     * Returns null when not logged in or the request fails.
     */
    fun fetchAniListLibrary(): List<AniListLibraryEntry>? {
        val token = tokenStore.getTokens("anilist")?.accessToken ?: return null
        return try {
            fetchAniListLibrary(token)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch AniList library" }
            null
        }
    }

    /**
     * Build the GraphQL payload for the user's media list. Extracted for
     * testability — the username must be single-quoted (JSONObject.quote adds
     * the surrounding quotes itself; wrapping them again produced invalid JSON
     * that AniList rejected with "No query or mutation provided").
     */
    internal fun buildAniListLibraryQuery(username: String?): String {
        val dollar = '$'
        val listArg = if (username != null) "userName: ${dollar}user, " else ""
        val variableJson = if (username != null) ",\"user\":${JSONObject.quote(username)}" else ""
        return """
            {"query":"query List(${if (username != null) "${dollar}user: String, " else ""}${dollar}type: MediaType) { MediaListCollection(${listArg}type: ${dollar}type) { lists { entries { status score progress media { id status episodes title { romaji english } coverImage { extraLarge } genres description(asHtml: false) } } } } }","variables":{"type":"ANIME"$variableJson}}
        """.trimIndent()
    }

    private fun fetchAniListLibrary(token: String): List<AniListLibraryEntry> {
        val username = tokenStore.getUsername("anilist")?.takeIf { it.isNotBlank() }
        val gql = buildAniListLibraryQuery(username)

        val request = okhttp3.Request.Builder()
            .url("https://graphql.anilist.co")
            .header("Authorization", "Bearer $token")
            .post(gql.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return emptyList()
        }
        val bodyStr = response.body?.string() ?: ""
        response.close()

        val root = JSONObject(bodyStr)
        val lists = root.optJSONObject("data")
            ?.optJSONObject("MediaListCollection")
            ?.optJSONArray("lists") ?: return emptyList()

        val result = mutableListOf<AniListLibraryEntry>()
        for (i in 0 until lists.length()) {
            val entries = lists.getJSONObject(i).optJSONArray("entries") ?: continue
            for (j in 0 until entries.length()) {
                val entry = entries.getJSONObject(j)
                val media = entry.optJSONObject("media") ?: continue
                val titleObj = media.optJSONObject("title")
                val title = titleObj?.optString("english")?.takeIf { it.isNotBlank() }
                    ?: titleObj?.optString("romaji")?.takeIf { it.isNotBlank() }
                    ?: continue
                val genres = media.optJSONArray("genres")?.let { array ->
                    (0 until array.length()).map { array.getString(it) }
                }.orEmpty()
                val cover = media.optJSONObject("coverImage")
                result += AniListLibraryEntry(
                    mediaId = media.getLong("id"),
                    title = title,
                    status = entry.optString("status", "CURRENT"),
                    score = entry.optInt("score", 0),
                    progress = entry.optInt("progress", 0),
                    totalEpisodes = if (media.has("episodes") && !media.isNull("episodes")) {
                        media.optInt("episodes")
                    } else null,
                    coverUrl = (cover?.optString("extraLarge")?.takeIf { it.isNotBlank() }
                        ?: cover?.optString("large")?.takeIf { it.isNotBlank() }),
                    genres = genres.takeIf { it.isNotEmpty() },
                    description = media.optString("description").takeIf { it.isNotBlank() },
                    mediaStatus = media.optString("status").takeIf { it.isNotBlank() },
                )
            }
        }
        logger.info { "Fetched ${result.size} AniList library entries" }
        return result
    }

    // -- Internal tracker implementations ----------------------------------

    /**
     * Fetch the logged-in user's MyAnimeList animelist (all statuses).
     * Returns null when not logged in or the request fails.
     */
    fun fetchMyAnimeListLibrary(): List<MalLibraryEntry>? {
        val token = tokenStore.getTokens("myanimelist")?.accessToken ?: return null
        return try {
            val url = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("api.myanimelist.net")
                .addPathSegments("v2/users/@me/animelist")
                .addQueryParameter("fields", "id,title,main_picture,num_watched_episodes,status,num_episodes")
                .addQueryParameter("limit", "1000")
                .addQueryParameter("status", "all")
                .build()
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return emptyList()
            }
            val bodyStr = response.body?.string() ?: ""
            response.close()

            val result = parseMalLibrary(bodyStr)
            logger.info { "Fetched ${result.size} MAL library entries" }
            result
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch MAL library" }
            null
        }
    }

    /**
     * Fetch the logged-in user's Kitsu anime library. Kitsu tracks library
     * entries (kind=anime) with their own ids — the id used for progress
     * updates differs from the anime id.
     */
    fun fetchKitsuLibrary(): List<KitsuLibraryEntry>? {
        val token = tokenStore.getTokens("kitsu")?.accessToken ?: return null
        return try {
            val userId = fetchKitsuUserId(token) ?: return null
            val url = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("kitsu.io")
                .addPathSegments("api/edge/library-entries")
                .addQueryParameter("filter[kind]", "anime")
                .addQueryParameter("filter[user_id]", userId)
                .addQueryParameter("include", "anime")
                .addQueryParameter("page[limit]", "500")
                .build()
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return emptyList()
            }
            val bodyStr = response.body?.string() ?: ""
            response.close()
            val result = parseKitsuLibrary(bodyStr)
            logger.info { "Fetched ${result.size} Kitsu library entries" }
            result
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch Kitsu library" }
            null
        }
    }

    private fun fetchKitsuUserId(token: String): String? {
        val request = okhttp3.Request.Builder()
            .url("https://kitsu.io/api/edge/users?filter[self]=true")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                response.close()
                JSONObject(body)
                    .optJSONArray("data")
                    ?.optJSONObject(0)
                    ?.optString("id")
                    ?.takeIf { it.isNotBlank() }
            } else {
                response.close()
                null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to resolve Kitsu user id" }
            null
        }
    }

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

    private fun searchKitsu(token: String, query: String): List<TrackerSearchResult> {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("kitsu.io")
            .addPathSegments("api/edge/anime")
            .addQueryParameter("filter[text]", query.take(64))
            .addQueryParameter("page[limit]", "5")
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
        return (0 until data.length()).mapNotNull { i ->
            val node = data.getJSONObject(i)
            val attributes = node.optJSONObject("attributes") ?: return@mapNotNull null
            val title = attributes.optString("canonicalTitle").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            TrackerSearchResult(
                id = node.getString("id"),
                title = title,
                imageUrl = attributes.optJSONObject("posterImage")?.optString("small"),
            )
        }
    }

    private fun updateKitsu(
        token: String,
        remoteAnimeId: String,
        episodeNumber: Int,
        status: String?,
    ): Boolean {
        // Kitsu progress updates PATCH the library-entry resource, not the
        // anime. Resolve the entry id for (user, anime) first.
        val userId = fetchKitsuUserId(token) ?: return false
        val lookupUrl = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("kitsu.io")
            .addPathSegments("api/edge/library-entries")
            .addQueryParameter("filter[user_id]", userId)
            .addQueryParameter("filter[anime_id]", remoteAnimeId)
            .build()
        val lookupRequest = okhttp3.Request.Builder()
            .url(lookupUrl)
            .header("Authorization", "Bearer $token")
            .build()
        val lookupResponse = httpClient.newCall(lookupRequest).execute()
        val entryId = try {
            if (!lookupResponse.isSuccessful) {
                null
            } else {
                val body = lookupResponse.body?.string() ?: ""
                JSONObject(body).optJSONArray("data")?.optJSONObject(0)?.optString("id")
            }
        } finally {
            lookupResponse.close()
        }
        if (entryId == null) return false

        val kitsuStatus = when (status) {
            "watching" -> "current"
            "completed" -> "completed"
            "on_hold" -> "on_hold"
            "dropped" -> "dropped"
            "plan_to_watch" -> "planned"
            else -> null
        }
        val attributes = JSONObject().apply {
            put("progress", episodeNumber.coerceAtLeast(0))
            if (kitsuStatus != null) put("status", kitsuStatus)
        }
        val body = JSONObject().apply {
            put("data", JSONObject().apply {
                put("type", "libraryEntries")
                put("id", entryId)
                put("attributes", attributes)
            })
        }

        val request = okhttp3.Request.Builder()
            .url("https://kitsu.io/api/edge/library-entries/$entryId")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/vnd.api+json")
            .patch(body.toString().toRequestBody("application/vnd.api+json".toMediaTypeOrNull()))
            .build()

        val response = httpClient.newCall(request).execute()
        response.close()
        return response.isSuccessful
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
                            val (clientId, clientSecret) =
                                tokenStore.getClientCredentials(status.tracker) ?: ("" to "")
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
                "kitsu" -> fetchKitsuUserId(accessToken)?.let { userId ->
                    val request = okhttp3.Request.Builder()
                        .url("https://kitsu.io/api/edge/users/$userId")
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return null
                        response.close()
                        JSONObject(body).optJSONObject("data")
                            ?.optJSONObject("attributes")
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
 * A single entry from the user's AniList media list, as returned by
 * [TrackerManager.fetchAniListLibrary].
 */
data class AniListLibraryEntry(
    val mediaId: Long,
    val title: String,
    /** AniList MediaListStatus: CURRENT, PLANNING, COMPLETED, DROPPED, PAUSED, REPEATING. */
    val status: String = "CURRENT",
    val score: Int = 0,
    val progress: Int = 0,
    val totalEpisodes: Int? = null,
    val coverUrl: String? = null,
    val genres: List<String>? = null,
    val description: String? = null,
    /** AniList MediaStatus of the anime itself: RELEASING, FINISHED, NOT_YET_RELEASED, CANCELLED, HIATUS. */
    val mediaStatus: String? = null,
)

/**
 * A single entry from the user's MyAnimeList animelist.
 */
data class MalLibraryEntry(
    val malId: Long,
    val title: String,
    /** MAL list status: watching, completed, on_hold, dropped, plan_to_watch. */
    val status: String = "watching",
    val progress: Int = 0,
    val totalEpisodes: Int? = null,
    val coverUrl: String? = null,
)

/**
 * A single entry from the user's Kitsu library. Kitsu progress updates are
 * keyed by the library-entry id (not the anime id), so both are carried.
 */
data class KitsuLibraryEntry(
    val kitsuId: Long,
    val libraryEntryId: String,
    val title: String,
    /** Kitsu status: current, completed, on_hold, dropped, planned. */
    val status: String = "current",
    val progress: Int = 0,
    val totalEpisodes: Int? = null,
    val coverUrl: String? = null,
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

internal fun parseMalLibrary(body: String): List<MalLibraryEntry> {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
    val data = root.optJSONArray("data") ?: return emptyList()
    val result = mutableListOf<MalLibraryEntry>()
    for (i in 0 until data.length()) {
        val item = data.getJSONObject(i)
        val node = item.optJSONObject("node") ?: continue
        val listStatus = item.optJSONObject("list_status")
        val picture = node.optJSONObject("main_picture")
        val title = node.optString("title").takeIf { it.isNotBlank() } ?: continue
        result += MalLibraryEntry(
            malId = node.getLong("id"),
            title = title,
            status = listStatus?.optString("status") ?: "watching",
            progress = listStatus?.optInt("num_watched_episodes", 0) ?: 0,
            totalEpisodes = if (node.has("num_episodes") && !node.isNull("num_episodes")) {
                node.optInt("num_episodes")
            } else null,
            coverUrl = picture?.optString("large")?.takeIf { it.isNotBlank() }
                ?: picture?.optString("medium")?.takeIf { it.isNotBlank() },
        )
    }
    return result
}

internal fun parseKitsuLibrary(body: String): List<KitsuLibraryEntry> {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
    val data = root.optJSONArray("data") ?: return emptyList()
    val included = root.optJSONArray("included") ?: return emptyList()
    val animeById = mutableMapOf<String, JSONObject>()
    for (i in 0 until included.length()) {
        val node = included.getJSONObject(i)
        if (node.optString("type") == "anime") {
            animeById[node.getString("id")] = node
        }
    }
    val result = mutableListOf<KitsuLibraryEntry>()
    for (i in 0 until data.length()) {
        val entry = data.getJSONObject(i)
        val attributes = entry.optJSONObject("attributes") ?: continue
        val animeRef = entry.optJSONObject("relationships")
            ?.optJSONObject("anime")
            ?.optJSONObject("data")
        val animeId = animeRef?.optString("id") ?: continue
        val anime = animeById[animeId] ?: continue
        val animeAttributes = anime.optJSONObject("attributes")
        val title = animeAttributes?.optString("canonicalTitle")
            ?.takeIf { it.isNotBlank() } ?: continue
        val poster = animeAttributes?.optJSONObject("posterImage")
        val kitsuId = animeId.toLongOrNull() ?: continue
        result += KitsuLibraryEntry(
            kitsuId = kitsuId,
            libraryEntryId = entry.getString("id"),
            title = title,
            status = attributes.optString("status", "current"),
            progress = attributes.optInt("progress", 0),
            totalEpisodes = animeAttributes?.optInt("episodeCount", 0)?.takeIf { it > 0 },
            coverUrl = poster?.optString("original")?.takeIf { it.isNotBlank() }
                ?: poster?.optString("large")?.takeIf { it.isNotBlank() },
        )
    }
    return result
}


val LocalTrackerManager = compositionLocalOf<TrackerManager?> { null }
