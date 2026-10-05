package eu.kanade.tachiyomi.data.anizip

import androidx.collection.LruCache
import eu.kanade.tachiyomi.data.anizip.model.AniZipEpisodeMeta
import eu.kanade.tachiyomi.data.anizip.model.AniZipResponse
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class AniZipService(
    private val networkHelper: NetworkHelper,
    private val json: Json,
) {
    private val client: OkHttpClient
        get() = networkHelper.client

    private val cache = LruCache<String, Map<String, AniZipEpisodeMeta>>(150)

    suspend fun getMetadata(anilistId: Long? = null, malId: Long? = null): Map<String, AniZipEpisodeMeta> {
        val cacheKey = when {
            anilistId != null && anilistId > 0 -> "anilist_$anilistId"
            malId != null && malId > 0 -> "mal_$malId"
            else -> return emptyMap()
        }

        synchronized(cache) {
            cache.get(cacheKey)?.let { return it }
        }

        val url = when {
            anilistId != null && anilistId > 0 -> "https://api.ani.zip/v1/episodes?anilist_id=$anilistId"
            malId != null && malId > 0 -> "https://api.ani.zip/v1/episodes?mal_id=$malId"
            else -> return emptyMap()
        }

        return withIOContext {
            try {
                logcat(LogPriority.INFO) { "AniZip: Fetching metadata from $url" }
                val response = client.newCall(GET(url)).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        logcat(LogPriority.WARN) { "AniZip: HTTP ${resp.code} for $url" }
                        synchronized(cache) { cache.put(cacheKey, emptyMap()) }
                        return@withIOContext emptyMap()
                    }

                    val body = resp.body.string()
                    if (body.isBlank()) {
                        synchronized(cache) { cache.put(cacheKey, emptyMap()) }
                        return@withIOContext emptyMap()
                    }

                    val parsed = json.decodeFromString<AniZipResponse>(body)
                    val episodes = parsed.episodes
                    if (episodes.isNullOrEmpty()) {
                        synchronized(cache) { cache.put(cacheKey, emptyMap()) }
                        return@withIOContext emptyMap()
                    }

                    val result = episodes.entries.associate { (key, ep) ->
                        val airDateStr = ep.bestAirDate
                        key to AniZipEpisodeMeta(
                            episodeNumber = ep.episodeNumber?.toString() ?: key,
                            title = ep.getPreferredTitle(),
                            overview = ep.bestOverview,
                            image = ep.image?.takeIf { it.isNotBlank() },
                            rating = ep.formattedRating,
                            airDate = airDateStr,
                            airDateMillis = parseAirDateMillis(airDateStr),
                        )
                    }

                    synchronized(cache) { cache.put(cacheKey, result) }
                    result
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "AniZip: Error fetching metadata from $url" }
                synchronized(cache) { cache.put(cacheKey, emptyMap()) }
                emptyMap()
            }
        }
    }

    fun findMetaForEpisode(
        metadata: Map<String, AniZipEpisodeMeta>,
        episodeNumber: Double,
        episodeName: String,
    ): AniZipEpisodeMeta? {
        if (metadata.isEmpty()) return null

        if (episodeNumber >= 0) {
            val intNum = episodeNumber.toInt()
            if (episodeNumber == intNum.toDouble()) {
                metadata[intNum.toString()]?.let { return it }
                metadata["0$intNum"]?.let { return it }
            } else {
                metadata[episodeNumber.toString()]?.let { return it }
            }
        }

        Regex("""(?:Episode|Ep\.?|E)\s*(\d+)""", RegexOption.IGNORE_CASE).find(episodeName)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?.let { metadata[it.toString()]?.let { m -> return m } }

        Regex("""(?:Special|SP|S)\s*(\d+)""", RegexOption.IGNORE_CASE).find(episodeName)
            ?.groupValues?.get(1)
            ?.let { spNum ->
                metadata["S$spNum"]?.let { return it }
                metadata["SP$spNum"]?.let { return it }
            }

        return null
    }

    companion object {
        fun parseAirDateMillis(dateStr: String?): Long? {
            if (dateStr.isNullOrBlank()) return null
            return try {
                if (dateStr.contains("T")) {
                    java.time.Instant.parse(dateStr).toEpochMilli()
                } else {
                    java.time.LocalDate.parse(dateStr.trim())
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
