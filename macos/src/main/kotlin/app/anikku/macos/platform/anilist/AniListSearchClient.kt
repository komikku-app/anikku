package app.anikku.macos.platform.anilist

import androidx.compose.runtime.compositionLocalOf
import app.anikku.macos.platform.library.AnimeSourceMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * A single AniList anime media entry as returned by the public search query.
 */
data class AniListAnime(
    val id: Int,
    val romajiTitle: String,
    val englishTitle: String? = null,
    val nativeTitle: String? = null,
    val coverUrl: String? = null,
    /** Total episodes of THIS AniList entry (a single season). */
    val episodes: Int? = null,
    val seasonYear: Int? = null,
    /** AniList format: "TV", "MOVIE", "OVA", "SPECIAL", … */
    val format: String? = null,
    /** Synopsis as raw HTML from AniList. */
    val synopsis: String? = null,
) {
    /** Best display title: English when present, otherwise romaji. */
    val displayName: String get() = englishTitle?.takeIf { it.isNotBlank() } ?: romajiTitle
}

/**
 * Composition local for UI access, mirroring `LocalTrackerManager` /
 * `LocalExtensionManager`.
 */
val LocalAniListSearchClient = compositionLocalOf<AniListSearchClient?> { null }

/**
 * Public (unauthenticated) AniList GraphQL search client.
 *
 * AniList accepts search queries without an OAuth token — `SubtitleFetcher`
 * already relies on this for subtitle resolution, and the tracker's own
 * `TrackerManager.searchAniList` requires a Bearer token. Kept separate so the
 * Torrents tab can resolve Nyaa titles → canonical AniList entries without
 * forcing the user to log in.
 */
class AniListSearchClient(
    private val httpClient: OkHttpClient,
    private val endpoint: String = "https://graphql.anilist.co",
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Search AniList for [query]. Returns ranked candidates (best first);
     * empty list on any failure (network, non-2xx, malformed body).
     */
    suspend fun searchAnime(query: String, perPage: Int = 8): List<AniListAnime> {
        val clean = query.trim().take(80)
        if (clean.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val payload = buildJsonObject {
                    put("query", JsonPrimitive(SEARCH_QUERY))
                    put("variables", buildJsonObject {
                        put("search", JsonPrimitive(clean))
                        put("perPage", JsonPrimitive(perPage))
                    })
                }
                val request = Request.Builder()
                    .url(endpoint)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val media = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media") as? JsonArray
                        ?: return@use emptyList()
                    media.mapNotNull { el -> parseMedia(el.jsonObject) }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun parseMedia(obj: JsonObject): AniListAnime? {
        val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return null
        val title = obj["title"]?.jsonObject
        val cover = obj["coverImage"]?.jsonObject
        return AniListAnime(
            id = id,
            romajiTitle = title?.get("romaji")?.jsonPrimitive?.contentOrNull ?: "",
            englishTitle = title?.get("english")?.jsonPrimitive?.contentOrNull,
            nativeTitle = title?.get("native")?.jsonPrimitive?.contentOrNull,
            coverUrl = cover?.get("large")?.jsonPrimitive?.contentOrNull
                ?: cover?.get("medium")?.jsonPrimitive?.contentOrNull,
            episodes = obj["episodes"]?.jsonPrimitive?.intOrNull,
            seasonYear = obj["seasonYear"]?.jsonPrimitive?.intOrNull,
            format = obj["format"]?.jsonPrimitive?.contentOrNull,
            synopsis = obj["description"]?.jsonPrimitive?.contentOrNull,
        )
    }

    companion object {
        private val SEARCH_QUERY = """
            query (${'$'}search: String, ${'$'}perPage: Int) {
              Page(page: 1, perPage: ${'$'}perPage) {
                media(search: ${'$'}search, type: ANIME) {
                  id
                  episodes
                  seasonYear
                  format
                  description
                  title { romaji english native }
                  coverImage { medium large }
                }
              }
            }
        """.trimIndent()

        /**
         * Pick the best AniList candidate for [query] using the same title
         * scoring as the rest of the app (exact normalized match = 1.0,
         * containment = 0.85). Compares romaji, English, and native titles and
         * keeps only high-confidence matches (>= [AnimeSourceMatcher.ACCEPT_THRESHOLD]).
         */
        fun pickBest(query: String, candidates: List<AniListAnime>): AniListAnime? {
            if (candidates.isEmpty()) return null
            return candidates
                .mapNotNull { anime ->
                    val score = bestScore(query, anime)
                    if (score >= AnimeSourceMatcher.ACCEPT_THRESHOLD) anime to score else null
                }
                .maxByOrNull { it.second }
                ?.first
        }

        private fun bestScore(query: String, anime: AniListAnime): Double {
            val names = listOfNotNull(anime.romajiTitle, anime.englishTitle, anime.nativeTitle)
            return names.maxOfOrNull { AnimeSourceMatcher.scoreTitle(query, it) } ?: 0.0
        }
    }
}
