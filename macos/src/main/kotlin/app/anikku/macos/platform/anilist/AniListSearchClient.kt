package app.anikku.macos.platform.anilist

import androidx.compose.runtime.compositionLocalOf
import app.anikku.macos.platform.library.AnimeSourceMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
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
 * One scheduled airing of an anime episode (AniList airingSchedules entry).
 */
data class AiringEpisode(
    val media: AniListAnime,
    val episode: Int,
    /** Epoch seconds when the episode airs. */
    val airingAt: Long,
)

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
        val data = graphQL(
            SEARCH_QUERY,
            buildJsonObject {
                put("search", JsonPrimitive(clean))
                put("perPage", JsonPrimitive(perPage))
            },
        ) ?: return emptyList()
        val media = data["Page"]?.jsonObject?.get("media") as? JsonArray ?: return emptyList()
        return media.mapNotNull { el -> parseMedia(el.jsonObject) }
    }

    /**
     * Anime airing within the next ~8 days, sorted by air time ascending.
     * Powers the Discover tab's schedule section.
     */
    suspend fun airingThisWeek(
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
        perPage: Int = 75,
    ): List<AiringEpisode> {
        val data = graphQL(
            AIRING_QUERY,
            buildJsonObject {
                put("now", JsonPrimitive(nowEpochSeconds))
                put("end", JsonPrimitive(nowEpochSeconds + 8 * 24 * 3600))
                put("perPage", JsonPrimitive(perPage))
            },
        ) ?: return emptyList()
        val schedules = data["Page"]?.jsonObject?.get("airingSchedules") as? JsonArray ?: return emptyList()
        return schedules.mapNotNull { el ->
            val obj = el.jsonObject
            val media = parseMedia(obj["media"]?.jsonObject ?: return@mapNotNull null) ?: return@mapNotNull null
            val episode = obj["episode"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val airingAt = obj["airingAt"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            AiringEpisode(media = media, episode = episode, airingAt = airingAt)
        }
    }

    /** Trending-now anime (by AniList's TRENDING_DESC sort). */
    suspend fun trending(perPage: Int = 24): List<AniListAnime> {
        val data = graphQL(MEDIA_LIST_QUERY("TRENDING_DESC"), buildJsonObject {
            put("perPage", JsonPrimitive(perPage))
        }) ?: return emptyList()
        return parseMediaPage(data)
    }

    /**
     * The current seasonal chart (most popular first). [season] is one of
     * WINTER/SPRING/SUMMER/FALL, [year] the 4-digit season year.
     */
    suspend fun seasonal(season: String, year: Int, perPage: Int = 24): List<AniListAnime> {
        val data = graphQL(MEDIA_LIST_QUERY("POPULARITY_DESC"), buildJsonObject {
            put("season", JsonPrimitive(season))
            put("year", JsonPrimitive(year))
            put("perPage", JsonPrimitive(perPage))
        }) ?: return emptyList()
        return parseMediaPage(data)
    }

    /**
     * "Because you watched…" — takes the user's highest-scored anime (by
     * [userName]) and returns AniList's recommendations for those titles.
     * Empty when no username is provided or the user has no scored entries.
     */
    suspend fun recommendationsFor(userName: String?, perPage: Int = 20): List<AniListAnime> {
        if (userName.isNullOrBlank()) return emptyList()
        val listData = graphQL(MEDIA_LIST_USER_QUERY, buildJsonObject {
            put("userName", JsonPrimitive(userName))
            put("perPage", JsonPrimitive(15))
        }) ?: return emptyList()
        val ids = (listData["Page"]?.jsonObject?.get("mediaList") as? JsonArray)
            ?.mapNotNull { it.jsonObject["mediaId"]?.jsonPrimitive?.intOrNull }
            ?: return emptyList()
        if (ids.isEmpty()) return emptyList()

        val recData = graphQL(RECOMMENDATIONS_QUERY, buildJsonObject {
            put("ids", JsonArray(ids.map { JsonPrimitive(it) }))
            put("perPage", JsonPrimitive(perPage))
        }) ?: return emptyList()
        val recs = recData["Page"]?.jsonObject?.get("recommendations") as? JsonArray ?: return emptyList()
        return recs.mapNotNull { el ->
            parseMedia(el.jsonObject["mediaRecommendation"]?.jsonObject ?: return@mapNotNull null)
        }
    }

    private fun parseMediaPage(data: JsonObject): List<AniListAnime> {
        val media = data["Page"]?.jsonObject?.get("media") as? JsonArray ?: return emptyList()
        return media.mapNotNull { el -> parseMedia(el.jsonObject) }
    }

    /** POST a GraphQL query and return the `data` object, or null on any failure. */
    private suspend fun graphQL(query: String, variables: JsonObject): JsonObject? =
        withContext(Dispatchers.IO) {
            try {
                val payload = buildJsonObject {
                    put("query", JsonPrimitive(query))
                    put("variables", variables)
                }
                val request = Request.Builder()
                    .url(endpoint)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    json.parseToJsonElement(response.body?.string().orEmpty())
                        .jsonObject["data"]?.jsonObject
                }
            } catch (_: Exception) {
                null
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

        private val AIRING_QUERY = """
            query (${'$'}now: Int, ${'$'}end: Int, ${'$'}perPage: Int) {
              Page(page: 1, perPage: ${'$'}perPage) {
                airingSchedules(airingAt_greater: ${'$'}now, airingAt_lesser: ${'$'}end, sort: TIME) {
                  id
                  episode
                  airingAt
                  media {
                    id
                    episodes
                    seasonYear
                    format
                    title { romaji english native }
                    coverImage { medium large }
                  }
                }
              }
            }
        """.trimIndent()

        private val MEDIA_LIST_USER_QUERY = """
            query (${'$'}userName: String, ${'$'}perPage: Int) {
              Page(page: 1, perPage: ${'$'}perPage) {
                mediaList(userName: ${'$'}userName, type: ANIME, sort: SCORE_DESC) {
                  mediaId
                }
              }
            }
        """.trimIndent()

        private val RECOMMENDATIONS_QUERY = """
            query (${'$'}ids: [Int], ${'$'}perPage: Int) {
              Page(page: 1, perPage: ${'$'}perPage) {
                recommendations(mediaId_in: ${'$'}ids, sort: RATING_DESC) {
                  mediaRecommendation {
                    id
                    episodes
                    seasonYear
                    format
                    title { romaji english native }
                    coverImage { medium large }
                  }
                }
              }
            }
        """.trimIndent()

        /** media list query for trending / seasonal charts. */
        private fun MEDIA_LIST_QUERY(sort: String): String = """
            query (${'$'}season: MediaSeason, ${'$'}year: Int, ${'$'}perPage: Int) {
              Page(page: 1, perPage: ${'$'}perPage) {
                media(season: ${'$'}season, seasonYear: ${'$'}year, sort: $sort, type: ANIME) {
                  id
                  episodes
                  seasonYear
                  format
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
