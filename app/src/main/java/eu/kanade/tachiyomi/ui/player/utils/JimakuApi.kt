package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException

class JimakuApi(private val apiKey: String, private val cacheDir: File, private val client: OkHttpClient) {
    private val json: Json by injectLazy()

    private suspend fun <T> retryApiCall(url: String, onSuccess: (okhttp3.Response) -> T, defaultValue: T): T {
        var last429Attempt = -1
        var lastIOException: IOException? = null
        repeat(3) { attempt ->
            try {
                val response = withContext(Dispatchers.IO) {
                    client.newCall(
                        GET(
                            url,
                            headers = Headers.Builder()
                                .add("Authorization", apiKey)
                                .build(),
                        ),
                    ).execute()
                }

                when (response.code) {
                    200 -> return onSuccess(response)
                    401 -> throw JimakuAuthException("Unauthorized: Invalid API key")
                    429 -> {
                        last429Attempt = attempt
                        delay(2000)
                    }
                    else -> {
                        logcat(LogPriority.WARN) { "Unexpected status ${response.code} for request" }
                        return defaultValue
                    }
                }
            } catch (e: JimakuAuthException) {
                throw e
            } catch (e: IOException) {
                lastIOException = e
                logcat(LogPriority.WARN, e) { "Network error on attempt ${attempt + 1}" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Retry attempt ${attempt + 1} failed" }
            }
        }
        if (last429Attempt >= 0) {
            throw JimakuRateLimitException("Rate limited")
        }
        lastIOException?.let { e ->
            val networkErrorMessage = when {
                url.contains("/api/entries/search") -> "Network error during search request"
                url.contains("/api/entries/") && url.contains("/files") -> "Network error during files request"
                else -> "Network error during download request"
            }
            throw JimakuNetworkException(networkErrorMessage, e)
        }
        return defaultValue
    }

    private suspend fun retrySearchRequest(url: String): List<JimakuEntry> {
        return retryApiCall(
            url = url,
            onSuccess = { response ->
                json.decodeFromString<List<JimakuEntry>>(response.body.string())
            },
            defaultValue = emptyList(),
        )
    }

    suspend fun searchByAniListId(aniListId: Long): List<JimakuEntry> {
        val url = "https://jimaku.cc/api/entries/search?anilist_id=$aniListId"
        return retrySearchRequest(url)
    }

    suspend fun searchByName(name: String): List<JimakuEntry> {
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        val url = "https://jimaku.cc/api/entries/search?query=$encodedName"
        return retrySearchRequest(url)
    }

    suspend fun getAniListIdFromMal(idMal: Long): Long {
        return withContext(Dispatchers.IO) {
            val query = """
                 query {
                     Media(idMal:$idMal,type: ANIME) {
                         id
                     }
                 }
            """.trimMargin()

            val response = try {
                client.newCall(
                    POST(
                        "https://graphql.anilist.co",
                        body = buildJsonObject { put("query", query) }.toString()
                            .toRequestBody(jsonMime),
                    ),
                ).execute()
            } catch (e: Exception) {
                return@withContext 0L
            }
            val body = response.body.string()
            return@withContext try {
                json.parseToJsonElement(body)
                    .jsonObject["data"]
                    ?.jsonObject?.get("Media")
                    ?.jsonObject?.get("id")
                    ?.jsonPrimitive?.longOrNull ?: 0L
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to parse AniList ID from MAL response" }
                0L
            }
        }
    }

    suspend fun getSubtitleFiles(entryId: Long, episode: Int? = null): List<JimakuFile> {
        val episodeParam = if (episode != null) "?episode=$episode" else ""
        val url = "https://jimaku.cc/api/entries/$entryId/files$episodeParam"
        return retryFilesRequest(url)
    }

    private suspend fun retryFilesRequest(url: String): List<JimakuFile> {
        return retryApiCall(
            url = url,
            onSuccess = { response ->
                json.decodeFromString<List<JimakuFile>>(response.body.string()).filter { file ->
                    val name = file.name.lowercase()
                    !name.endsWith(".zip") && !name.endsWith(".rar") && !name.endsWith(".7z")
                }
            },
            defaultValue = emptyList(),
        )
    }

    suspend fun downloadSubtitleToCache(fileUrl: String, fileName: String): File? {
        return retryDownloadRequest(fileUrl, fileName)
    }

    private suspend fun retryDownloadRequest(fileUrl: String, fileName: String): File? {
        return retryApiCall(
            url = fileUrl,
            onSuccess = { response ->
                File(cacheDir, fileName).apply {
                    writeBytes(response.body.bytes())
                }
            },
            defaultValue = null,
        )
    }
}

class JimakuAuthException(message: String) : Exception(message)
class JimakuRateLimitException(message: String) : Exception(message)
class JimakuNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
data class JimakuEntry(
    val id: Long,
    val name: String,
    @SerialName("english_name")
    val englishName: String? = null,
    @SerialName("japanese_name")
    val japaneseName: String? = null,
    @SerialName("anilist_id")
    val anilistId: Int? = null,
    @SerialName("tmdb_id")
    val tmdbId: String? = null,
    val flags: JimakuFlags? = null,
    @SerialName("last_modified")
    val lastModified: String? = null,
)

@Serializable
data class JimakuFlags(
    val anime: Boolean = false,
    val movie: Boolean = false,
    val adult: Boolean = false,
    val unverified: Boolean = false,
    val external: Boolean = false,
)

@Serializable
data class JimakuFile(
    val url: String,
    val name: String,
    val size: Long,
    @SerialName("last_modified")
    val lastModified: String? = null,
)
