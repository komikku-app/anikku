package app.anikku.macos.platform.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * An intro/outro skip window from the AniSkip API.
 *
 * [skipType] is one of `op`, `ed`, `mixed-op`, `mixed-ed`, `recap` (see
 * [label] for the user-facing name). Times are in seconds within the episode.
 */
data class SkipInterval(
    val skipType: String,
    val startTime: Double,
    val endTime: Double,
) {
    val isIntro: Boolean get() = skipType == "op" || skipType == "mixed-op"
    val isEnding: Boolean get() = skipType == "ed" || skipType == "mixed-ed"
    val isRecap: Boolean get() = skipType == "recap"

    /** User-facing label for the skip button: "Intro", "Outro", "Recap". */
    val label: String
        get() = when {
            isIntro -> "Intro"
            isEnding -> "Outro"
            isRecap -> "Recap"
            else -> "Skip"
        }
}

/**
 * Client for the free, key-less AniSkip API (`api.aniskip.com`).
 *
 * The API is keyed by the MyAnimeList ID of the media plus the episode number
 * (NOT the AniList ID — resolve the MAL ID via
 * `SubtitleFetcher.resolveAniListMalId`). Returns an empty list on any failure
 * (`found: false`, non-2xx, malformed body) — skipping is strictly best-effort.
 */
class AniSkipClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://api.aniskip.com",
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetch skip windows (OP/ED/recap) for the given MAL media + episode.
     * Never throws.
     */
    suspend fun fetchSkipTimes(malId: Int, episode: Int): List<SkipInterval> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/v2/skip-times/$malId/$episode" +
                    "?types[]=op&types[]=ed&types[]=recap&episodeLength=0"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                    val found = root["found"]?.jsonPrimitive?.booleanOrNull ?: return@use emptyList()
                    if (!found) return@use emptyList()
                    val results = root["results"] as? JsonArray ?: return@use emptyList()
                    results.mapNotNull { el -> parseInterval(el.jsonObject) }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun parseInterval(obj: kotlinx.serialization.json.JsonObject): SkipInterval? {
        val interval = obj["interval"]?.jsonObject ?: return null
        val start = interval["startTime"]?.jsonPrimitive?.doubleOrNull ?: return null
        val end = interval["endTime"]?.jsonPrimitive?.doubleOrNull ?: return null
        if (end <= start) return null
        return SkipInterval(
            skipType = obj["skipType"]?.jsonPrimitive?.contentOrNull ?: "op",
            startTime = start,
            endTime = end,
        )
    }
}
