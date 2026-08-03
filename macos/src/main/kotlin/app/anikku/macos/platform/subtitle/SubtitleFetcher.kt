package app.anikku.macos.platform.subtitle

import app.anikku.macos.platform.security.MacOSKeychain
import app.anikku.macos.platform.security.MacOSSecretStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

/**
 * A downloadable subtitle candidate found by [SubtitleFetcher].
 *
 * @property provider Which provider found it: "jimaku" or "opensubtitles".
 * @property title Human-readable label (file name / episode + language).
 * @property language ISO 639-1 code (e.g. "en", "ja") when known, else "und".
 * @property downloadUrl Direct URL to download the subtitle file.
 * @property cacheKey Stable cache key used to derive the local cache file name.
 */
data class SubtitleCandidate(
    val provider: String,
    val title: String,
    val language: String,
    val downloadUrl: String,
    val cacheKey: String,
    val format: String = "srt",
) {
    /** Whether this candidate is English or an undetermined language. */
    val isEnglish: Boolean get() = language == "en" || language == "eng" || language == "und"
}

/**
 * Credentials needed by the subtitle providers. All fields are optional; the
 * app degrades gracefully when a provider's key is missing (that provider is
 * skipped, never crashing playback).
 */
data class SubtitleCredentials(
    val jimakuToken: String = "",
    val openSubtitlesApiKey: String = "",
    val openSubtitlesUsername: String = "",
    val openSubtitlesPassword: String = "",
) {
    val jimakuConfigured: Boolean get() = jimakuToken.isNotBlank()
    val openSubtitlesConfigured: Boolean get() =
        openSubtitlesApiKey.isNotBlank() && openSubtitlesUsername.isNotBlank() && openSubtitlesPassword.isNotBlank()
}

/**
 * Persists subtitle provider credentials in the macOS keychain.
 *
 * The developer-provided defaults (baked into the app) are returned when the
 * keychain has no user override, so end users don't need to configure anything.
 * Users may override any field via Settings (stored in the keychain).
 */
class SubtitleCredentialStore(
    private val keychain: MacOSSecretStore,
    private val bakedDefaults: SubtitleCredentials = SubtitleCredentials(),
) {
    companion object {
        private const val KEY_JIMAKU = "jimaku_token"
        private const val KEY_OS_API = "opensubtitles_api_key"
        private const val KEY_OS_USER = "opensubtitles_username"
        private const val KEY_OS_PASS = "opensubtitles_password"
    }

    fun load(): SubtitleCredentials = SubtitleCredentials(
        jimakuToken = keychain.retrieve(KEY_JIMAKU)?.takeIf { it.isNotBlank() } ?: bakedDefaults.jimakuToken,
        openSubtitlesApiKey = keychain.retrieve(KEY_OS_API)?.takeIf { it.isNotBlank() } ?: bakedDefaults.openSubtitlesApiKey,
        openSubtitlesUsername = keychain.retrieve(KEY_OS_USER)?.takeIf { it.isNotBlank() } ?: bakedDefaults.openSubtitlesUsername,
        openSubtitlesPassword = keychain.retrieve(KEY_OS_PASS)?.takeIf { it.isNotBlank() } ?: bakedDefaults.openSubtitlesPassword,
    )

    fun save(credentials: SubtitleCredentials) {
        fun put(key: String, value: String) {
            if (value.isBlank()) keychain.delete(key) else keychain.store(key, value)
        }
        put(KEY_JIMAKU, credentials.jimakuToken)
        put(KEY_OS_API, credentials.openSubtitlesApiKey)
        put(KEY_OS_USER, credentials.openSubtitlesUsername)
        put(KEY_OS_PASS, credentials.openSubtitlesPassword)
    }

    /** Whether any provider is usable at all (drives the Settings UI hint). */
    fun anyConfigured(): Boolean = load().let { it.jimakuConfigured || it.openSubtitlesConfigured }
}

/**
 * Automatic English-subtitle fetching for anime streams.
 *
 * ## Pipeline
 *
 * 1. [resolveAniListId] — map a (possibly localized) anime title to its
 *    canonical AniList ID using the public AniList GraphQL API (no key).
 * 2. [searchJimaku] — query the anime-native Jimaku database for subtitle
 *    entries matching the AniList ID + episode; download the best English file.
 * 3. [searchOpenSubtitles] — fall back to the OpenSubtitles.com REST API
 *    (title + season/episode search, English first).
 * 4. Files are cached under `cacheDirectory/subs/` so an episode is only
 *    fetched once.
 *
 * Every method is failure-safe: missing keys, network errors, or empty
 * results return empty lists / null and never throw to the caller.
 */
class SubtitleFetcher(
    private val client: OkHttpClient,
    private val credentialStore: SubtitleCredentialStore,
    private val cacheDirectory: File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ---- Public API -------------------------------------------------------

    /**
     * Best-effort auto-fetch: resolve the AniList ID, then find an English
     * Jimaku subtitle for [episodeNumber]. Returns the local cached file path
     * or null when nothing usable was found.
     */
    suspend fun fetchAutoEnglishSubtitle(
        animeTitle: String,
        episodeNumber: Double,
    ): File? = withContext(Dispatchers.IO) {
        try {
            val credentials = credentialStore.load()
            if (!credentials.jimakuConfigured) {
                logger.debug { "SUB: Jimaku not configured — skipping auto-fetch" }
                return@withContext null
            }
            val anilistId = resolveAniListId(animeTitle, episodeNumber) ?: run {
                logger.warn { "SUB: AniList lookup failed for '$animeTitle'" }
                return@withContext null
            }
            val candidates = searchJimaku(credentials, anilistId, episodeNumber)
                .filter { it.isEnglish }
                .sortedWith(compareByDescending<SubtitleCandidate> { it.language == "en" }.thenBy { it.title })
            val best = candidates.firstOrNull() ?: return@withContext null
            downloadToCache(best)?.also {
                logger.info { "SUB: auto-fetched '${best.title}' from Jimaku -> $it" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "SUB: auto-fetch failed (title='$animeTitle' ep=$episodeNumber)" }
            null
        }
    }

    /**
     * Search OpenSubtitles for the given title + episode. Returns candidates
     * sorted with English first, then other languages (for the player's
     * subtitle dropdown "Search OpenSubtitles" entry).
     */
    suspend fun searchOpenSubtitles(
        animeTitle: String,
        episodeNumber: Double,
    ): List<SubtitleCandidate> = withContext(Dispatchers.IO) {
        try {
            val credentials = credentialStore.load()
            if (!credentials.openSubtitlesConfigured) {
                logger.debug { "SUB: OpenSubtitles not configured — skipping search" }
                return@withContext emptyList()
            }
            searchOpenSubtitles(credentials, animeTitle, episodeNumber)
        } catch (e: Exception) {
            logger.warn(e) { "SUB: OpenSubtitles search failed (title='$animeTitle')" }
            emptyList()
        }
    }

    /**
     * Download a candidate (from either provider) into the local cache and
     * return the file, or null on failure. Callers feed the file path to the
     * player's external-subtitle mechanism.
     */
    suspend fun downloadCandidate(candidate: SubtitleCandidate): File? =
        withContext(Dispatchers.IO) {
            try {
                downloadToCache(candidate)
            } catch (e: Exception) {
                logger.warn(e) { "SUB: download failed for '${candidate.title}'" }
                null
            }
        }

    // ---- AniList title resolution ----------------------------------------

    /** A single AniList search result (id + episode count). */
    data class Media(val id: Int?, val episodes: Int?)

    /**
     * Resolve a free-text anime title to an AniList media ID.
     * Uses the public GraphQL API (no auth). Returns null when unresolved.
     *
     * Season-aware: when [episodeNumber] is provided, picks the candidate whose
     * episode range covers it (e.g. "Solo Leveling" + ep 13 resolves to the
     * Season 2 media, not Season 1). Falls back to the top search result when
     * episode ranges are unknown or [episodeNumber] is not positive.
     */
    fun resolveAniListId(title: String, episodeNumber: Double = 0.0): Int? {
        val cleanTitle = title.trim().take(80)
        if (cleanTitle.isEmpty()) return null

        val query = """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 5) {
                media(search: ${'$'}search, type: ANIME) {
                  id
                  episodes
                  title { romaji english native }
                }
              }
            }
        """.trimIndent()
        val payload = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject {
                put("query", JsonPrimitive(query))
                put("variables", buildJsonObject { put("search", JsonPrimitive(cleanTitle)) })
            },
        )

        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                logger.warn { "SUB: AniList HTTP ${response.code}" }
                return null
            }
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val media = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media") as? JsonArray ?: return null

            val candidates = media.mapNotNull { el ->
                val obj = el.jsonObject
                Media(
                    id = obj["id"]?.jsonPrimitive?.intOrNull,
                    episodes = obj["episodes"]?.jsonPrimitive?.intOrNull,
                )
            }
            if (candidates.isEmpty()) return null

            logger.info { "SUB: AniList resolved '$title' (ep=${episodeNumber.toInt()}) -> id=${pickSeasonMatch(candidates, episodeNumber)}" }
            return pickSeasonMatch(candidates, episodeNumber)
        }
    }

    /**
     * Pick the AniList media that best matches the requested episode.
     * Extracted for testability.
     *
     * Strategy:
     * 1. Top-ranked title match wins when it doesn't clearly exclude the
     *    episode — i.e. its episode count is unknown (airing) or covers it.
     * 2. Otherwise (a completed season that is too short) fall through to the
     *    next candidate whose range covers the episode — this is what lets
     *    "Solo Leveling" + ep 13 resolve to Season 2.
     * 3. Fall back to the first candidate with a known range, then the top
     *    result.
     *
     * @param candidates List of (id, episodes) from the AniList search, in rank order.
     * @param episodeNumber Requested episode (0 = unknown / top result).
     */
    internal fun pickSeasonMatch(candidates: List<Media>, episodeNumber: Double): Int? {
        val requested = episodeNumber.toInt()
        if (requested <= 0) return candidates.firstOrNull()?.id

        // Pass 1: top-ranked match unless it clearly can't cover the episode.
        candidates.firstOrNull { (_, eps) ->
            eps == null || eps >= requested
        }?.id?.let { return it }

        // Pass 2: any later candidate whose completed range covers the episode.
        candidates.firstOrNull { (_, eps) ->
            eps != null && eps >= requested
        }?.id?.let { return it }

        // Fallback: first with a known range, then top result.
        return candidates.firstOrNull { (_, eps) -> eps != null }?.id
            ?: candidates.firstOrNull()?.id
    }

    // ---- Jimaku -----------------------------------------------------------

    private suspend fun searchJimaku(
        credentials: SubtitleCredentials,
        anilistId: Int,
        episodeNumber: Double,
    ): List<SubtitleCandidate> {
        if (!credentials.jimakuConfigured) return emptyList()
        val episode = episodeNumber.toInt().coerceAtLeast(1)

        // 1. Find the media entry on Jimaku by AniList ID.
        val entriesRequest = Request.Builder()
            .url("https://jimaku.cc/api/entries/search?anilist_id=$anilistId")
            .header("Authorization", credentials.jimakuToken)
            .header("Accept", "application/json")
            .build()
        val entries = runCatching {
            client.newCall(entriesRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn { "SUB: Jimaku entries HTTP ${response.code}" }
                    null
                } else {
                    json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonArray
                }
            }
        }.getOrNull() ?: return emptyList()

        // 2. For each entry, list its files and match the target episode.
        val results = mutableListOf<SubtitleCandidate>()
        for (entry in entries) {
            val entryId = entry.jsonObject["id"]?.jsonPrimitive?.longOrNull ?: continue
            val files = listEntryFiles(credentials, entryId)
            for (file in files) {
                val name = file.jsonObject["name"]?.jsonPrimitive?.content ?: continue
                if (!fileMatchesEpisode(name, episode)) continue
                val url = file.jsonObject["url"]?.jsonPrimitive?.content ?: continue
                val language = file.jsonObject["language"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "und"
                results += SubtitleCandidate(
                    provider = "jimaku",
                    title = name,
                    language = language,
                    downloadUrl = url,
                    cacheKey = "jimaku-$anilistId-ep$episode-${entryId}-${file.jsonObject["id"]?.jsonPrimitive?.longOrNull}",
                    format = name.substringAfterLast('.', "srt").lowercase(),
                )
            }
        }
        return results
    }

    private fun listEntryFiles(credentials: SubtitleCredentials, entryId: Long): JsonArray {
        val request = Request.Builder()
            .url("https://jimaku.cc/api/entries/$entryId/files")
            .header("Authorization", credentials.jimakuToken)
            .header("Accept", "application/json")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonArray ?: JsonArray(emptyList())
                } else {
                    JsonArray(emptyList())
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "SUB: Jimaku files HTTP failed for entry $entryId" }
            JsonArray(emptyList())
        }
    }

    /** Match a subtitle file name against an episode number (E12, 12, #12...). */
    internal fun fileMatchesEpisode(filename: String, episode: Int): Boolean {
        val lower = filename.lowercase()
        // "S1E12" / "ep12" / "episode 12" / "- 12 -" / "#12"
        val patterns = listOf(
            Regex("""e(?:p(?:isode)?)?[ ._-]*(\d+)"""),
            Regex("""(?:^|\s|[._-])0*(\d+)(?:\s|$|[._-])"""),
            Regex("""#0*(\d+)"""),
        )
        for (pattern in patterns) {
            for (match in pattern.findAll(lower)) {
                val value = match.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
                if (value == episode) return true
            }
        }
        return false
    }

    // ---- OpenSubtitles ----------------------------------------------------

    private suspend fun searchOpenSubtitles(
        credentials: SubtitleCredentials,
        animeTitle: String,
        episodeNumber: Double,
    ): List<SubtitleCandidate> {
        val token = openSubtitlesLogin(credentials) ?: return emptyList()
        val episode = episodeNumber.toInt().coerceAtLeast(1)

        val url = buildString {
            append("https://api.opensubtitles.com/api/v1/subtitles?")
            append("query=").append(java.net.URLEncoder.encode(animeTitle.trim().take(100), "UTF-8"))
            // No season_number: hardcoding one would always match season 1 for
            // multi-season shows. Search by title + episode number only, then
            // let the user pick the right release (English first).
            append("&episode_number=").append(episode)
            append("&languages=en")
        }

        val request = Request.Builder()
            .url(url)
            .header("Api-Key", credentials.openSubtitlesApiKey)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "Anikku v1.0.1")
            .header("Accept", "application/json")
            .build()

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                logger.warn { "SUB: OpenSubtitles search HTTP ${response.code}" }
                null
            } else {
                response.body?.string()
            }
        } ?: return emptyList()

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val items = root["data"] as? JsonArray ?: return emptyList()

        val candidates = mutableListOf<SubtitleCandidate>()
        for (item in items) {
            val attrs = item.jsonObject["attributes"]?.jsonObject ?: continue
            val language = attrs["language"]?.jsonPrimitive?.content ?: "und"
            val files = attrs["files"] as? JsonArray ?: continue
            val release = attrs["release"]?.jsonPrimitive?.content ?: ""
            for (file in files) {
                val fileObj = file.jsonObject
                val fileId = fileObj["file_id"]?.jsonPrimitive?.longOrNull ?: continue
                val fileName = fileObj["file_name"]?.jsonPrimitive?.content ?: release
                val langLabel = languageLabel(language)
                candidates += SubtitleCandidate(
                    provider = "opensubtitles",
                    title = "$fileName — $langLabel",
                    language = language,
                    downloadUrl = "opensubtitles://download/$fileId", // resolved in downloadToCache
                    cacheKey = "os-$fileId",
                    format = "srt",
                )
            }
        }

        // English first, then by release name.
        return candidates.sortedWith(
            compareByDescending<SubtitleCandidate> { it.language == "en" }.thenBy { it.title },
        )
    }

    private fun openSubtitlesLogin(credentials: SubtitleCredentials): String? {
        val payload = buildJsonObject {
            put("username", JsonPrimitive(credentials.openSubtitlesUsername))
            put("password", JsonPrimitive(credentials.openSubtitlesPassword))
        }
        val request = Request.Builder()
            .url("https://api.opensubtitles.com/api/v1/login")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Api-Key", credentials.openSubtitlesApiKey)
            .header("User-Agent", "Anikku v1.0.1")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn { "SUB: OpenSubtitles login HTTP ${response.code}" }
                    null
                } else {
                    json.parseToJsonElement(response.body?.string().orEmpty())
                        .jsonObject["token"]?.jsonPrimitive?.content
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "SUB: OpenSubtitles login failed" }
            null
        }
    }

    // ---- Download + cache -------------------------------------------------

    private suspend fun downloadToCache(candidate: SubtitleCandidate): File? {
        val safeKey = candidate.cacheKey
            .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
            .take(120)
        val dir = cacheDirectory.apply { mkdirs() }
        val target = File(dir, "$safeKey.${candidate.format}")

        val existing = pickCachedFile(dir, candidate.cacheKey)
        if (existing != null && existing.isFile && existing.length() > 0) {
            logger.debug { "SUB: cache hit for ${candidate.title}" }
            return existing
        }

        // OpenSubtitles: "opensubtitles://download/<fileId>" must first be
        // exchanged for a real download link via POST /download.
        val finalUrl = if (candidate.downloadUrl.startsWith("opensubtitles://")) {
            openSubtitlesResolveLink(candidate) ?: return null
        } else {
            candidate.downloadUrl
        }

        val request = Request.Builder().url(finalUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                logger.warn { "SUB: download HTTP ${response.code} for ${candidate.title}" }
                return null
            }
            val bytes = response.body?.bytes() ?: return null
            if (bytes.isEmpty()) return null
            target.writeBytes(bytes)
        }
        return target
    }

    /** Exchange an OpenSubtitles file_id for a signed download URL. */
    private fun openSubtitlesResolveLink(candidate: SubtitleCandidate): String? {
        val credentials = credentialStore.load()
        if (!credentials.openSubtitlesConfigured) return null
        val fileId = candidate.downloadUrl.removePrefix("opensubtitles://download/")
        val token = openSubtitlesLogin(credentials) ?: return null

        val payload = buildJsonObject { put("file_id", JsonPrimitive(fileId)) }
        val request = Request.Builder()
            .url("https://api.opensubtitles.com/api/v1/download")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Api-Key", credentials.openSubtitlesApiKey)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "Anikku v1.0.1")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn { "SUB: OpenSubtitles download-link HTTP ${response.code}" }
                    null
                } else {
                    json.parseToJsonElement(response.body?.string().orEmpty())
                        .jsonObject["link"]?.jsonPrimitive?.content
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "SUB: OpenSubtitles download-link failed" }
            null
        }
    }

    /**
     * Find an already-cached file for [cacheKey] regardless of the on-disk
     * extension (older cache entries used a bare hash name).
     */
    private fun pickCachedFile(dir: File, cacheKey: String): File? {
        val safeKey = cacheKey.replace(Regex("""[^A-Za-z0-9._-]"""), "_").take(120)
        return dir.listFiles()?.firstOrNull { it.name.startsWith(safeKey) }
    }

    /** Cache directory for subtitle downloads. */
    fun cacheDir(): File = cacheDirectory

    companion object {
        private fun buildJsonObject(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject {
            return kotlinx.serialization.json.buildJsonObject(block)
        }

        private fun languageLabel(code: String): String = when (code.lowercase()) {
            "en", "eng" -> "English"
            "ja", "jpn" -> "Japanese"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "pt", "pt-br" -> "Portuguese"
            "it" -> "Italian"
            "ru" -> "Russian"
            "ar" -> "Arabic"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            else -> code.uppercase()
        }
    }
}
