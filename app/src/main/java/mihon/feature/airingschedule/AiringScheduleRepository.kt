package mihon.feature.airingschedule

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Fetches the weekly airing schedule from AniList's GraphQL API.
 *
 * AniList is a public data API (not a browser-rendered site), so any transient 403/429/503
 * response it returns is virtually never an interactive Cloudflare challenge that a WebView
 * could solve - it is almost always AniList's own rate limiting kicking in when many page
 * requests are made back-to-back. To make the weekly refresh reliable long-term, every request
 * here is retried with exponential backoff + jitter, and pages are spaced out slightly, instead
 * of surfacing a hard failure to the user for a transient hiccup.
 */
class AiringScheduleRepository {

    private val networkHelper: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    private val client get() = networkHelper.client

    suspend fun getWeeklySchedule(
        weekStart: Long,
        weekEnd: Long,
        includeAdult: Boolean = false,
    ): List<AiringScheduleEntry> {
        return withIOContext {
            var page = 1
            val allEntries = mutableListOf<AiringScheduleEntry>()
            var hasNextPage = true
            while (hasNextPage && page <= 10) {
                val result = fetchPageWithRetry(weekStart, weekEnd, page, includeAdult)
                allEntries.addAll(result.entries)
                hasNextPage = result.hasNextPage
                page++
                if (hasNextPage) {
                    // Small courtesy delay between paginated requests so we don't look like a
                    // burst/bot to AniList's own rate limiter or edge network.
                    delay(PAGE_DELAY_MS)
                }
            }
            allEntries
        }
    }

    private suspend fun fetchPageWithRetry(
        weekStart: Long,
        weekEnd: Long,
        page: Int,
        includeAdult: Boolean,
    ): PageResult {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < MAX_RETRIES) {
            try {
                return fetchPage(weekStart, weekEnd, page, includeAdult)
            } catch (e: HttpException) {
                lastError = e
                if (e.code !in RETRYABLE_HTTP_CODES) throw e
            } catch (e: IOException) {
                lastError = e
            }
            attempt++
            if (attempt < MAX_RETRIES) {
                delay(backoffDelayMs(attempt))
            }
        }
        throw lastError ?: IOException("Failed to fetch airing schedule")
    }

    private fun backoffDelayMs(attempt: Int): Long {
        val exponential = BASE_DELAY_MS * 2.0.pow(attempt - 1).toLong()
        val jitter = Random.nextLong(0, 500)
        return min(exponential + jitter, MAX_DELAY_MS)
    }

    private suspend fun fetchPage(
        weekStart: Long,
        weekEnd: Long,
        page: Int,
        includeAdult: Boolean,
    ): PageResult {
        val query = """
        |query AiringSchedule(${'$'}weekStart: Int, ${'$'}weekEnd: Int, ${'$'}page: Int) {
            |Page(page: ${'$'}page, perPage: 50) {
                |pageInfo {
                    |hasNextPage
                |}
                |airingSchedules(airingAt_greater: ${'$'}weekStart, airingAt_lesser: ${'$'}weekEnd, sort: TIME) {
                    |id
                    |airingAt
                    |episode
                    |media {
                        |id
                        |title {
                            |userPreferred
                            |english
                            |romaji
                            |native
                        |}
                        |coverImage {
                            |large
                        |}
                        |episodes
                        |status
                        |averageScore
                        |format
                        |isAdult
                        |genres
                    |}
                |}
            |}
        |}
        """.trimMargin()

        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("weekStart", weekStart.toInt())
                put("weekEnd", weekEnd.toInt())
                put("page", page)
            }
        }

        with(json) {
            val result = client.newCall(
                POST(API_URL, body = payload.toString().toRequestBody(jsonMime)),
            ).awaitSuccess().parseAs<ALScheduleResponse>()

            return PageResult(
                entries = result.data.page.airingSchedules.mapNotNull { it.toEntry(includeAdult) },
                hasNextPage = result.data.page.pageInfo.hasNextPage,
            )
        }
    }

    private fun ALAiringSchedule.toEntry(includeAdult: Boolean): AiringScheduleEntry? {
        val m = media ?: return null
        if (!includeAdult && m.isAdult == true) return null
        return AiringScheduleEntry(
            scheduleId = id,
            airingAt = airingAt.toLong(),
            episode = episode,
            mediaId = m.id,
            titleUserPreferred = m.title.userPreferred.orEmpty(),
            titleEnglish = m.title.english,
            titleRomaji = m.title.romaji,
            titleNative = m.title.native,
            coverImageUrl = m.coverImage.large.orEmpty(),
            totalEpisodes = m.episodes,
            averageScore = m.averageScore,
            format = m.format,
            status = m.status,
            isAdult = m.isAdult ?: false,
            genres = m.genres.orEmpty(),
        )
    }

    private data class PageResult(val entries: List<AiringScheduleEntry>, val hasNextPage: Boolean)

    companion object {
        private const val API_URL = "https://graphql.anilist.co/"

        // Transient errors worth retrying: 429 (rate limited), 502/503/504 (upstream hiccups).
        // 403 is intentionally excluded - AniList returns that for genuinely bad requests, and
        // retrying it would just waste time.
        private val RETRYABLE_HTTP_CODES = setOf(429, 502, 503, 504)
        private const val MAX_RETRIES = 5
        private const val BASE_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 20_000L
        private const val PAGE_DELAY_MS = 250L
    }
}

@Serializable
private data class ALScheduleResponse(val data: ALScheduleData)

@Serializable
private data class ALScheduleData(@SerialName("Page") val page: ALSchedulePage)

@Serializable
private data class ALSchedulePage(
    val pageInfo: ALPageInfo,
    val airingSchedules: List<ALAiringSchedule>,
)

@Serializable
private data class ALPageInfo(val hasNextPage: Boolean)

@Serializable
private data class ALAiringSchedule(
    val id: Int,
    val airingAt: Int,
    val episode: Int,
    val media: ALMedia? = null,
)

@Serializable
private data class ALMedia(
    val id: Int,
    val title: ALTitle,
    val coverImage: ALCoverImage,
    val episodes: Int? = null,
    val status: String? = null,
    val averageScore: Int? = null,
    val format: String? = null,
    val isAdult: Boolean? = null,
    val genres: List<String>? = null,
)

@Serializable
private data class ALTitle(
    val userPreferred: String? = null,
    val english: String? = null,
    val romaji: String? = null,
    val native: String? = null,
)

@Serializable
private data class ALCoverImage(val large: String? = null)
