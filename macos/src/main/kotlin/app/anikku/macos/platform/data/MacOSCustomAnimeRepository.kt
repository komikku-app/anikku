package app.anikku.macos.platform.data

import androidx.compose.runtime.compositionLocalOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.anikku.macos.platform.storage.MacOSAtomicFile
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.nio.file.Files
import tachiyomi.domain.anime.model.CustomAnimeInfo
import tachiyomi.domain.anime.repository.CustomAnimeRepository

private val customAnimeLogger = KotlinLogging.logger {}

/**
 * CompositionLocal providing the [LibraryRepository] to the Compose tree.
 */
private val composeFallbackDataDirectory: File by lazy {
    Files.createTempDirectory("anikku-compose-fallback-").toFile().apply { deleteOnExit() }
}

val LocalLibraryRepository = compositionLocalOf { LibraryRepository(composeFallbackDataDirectory) }

/**
 * CompositionLocal providing the [HistoryRepository] to the Compose tree.
 */
val LocalHistoryRepository = compositionLocalOf { HistoryRepository(composeFallbackDataDirectory) }

/**
 * macOS-specific custom anime info repository.
 *
 * Stores user-defined custom anime metadata edits as JSON.
 * Data file: {dataDirectory}/edits.json
 *
 * Implements the same domain contract as Android while using atomic JSON files
 * in Application Support instead of Android external storage.
 */
class MacOSCustomAnimeRepository(dataDir: File) : CustomAnimeRepository {

    private val editJson = File(dataDir, "edits.json")
    private val lock = Any()
    private val customAnimeMap: MutableMap<Long, CustomAnimeEntry> = loadFromFile()

    @Synchronized
    override fun get(animeId: Long): CustomAnimeInfo? = customAnimeMap[animeId]?.toDomain()

    @Synchronized
    override fun set(animeInfo: CustomAnimeInfo) {
        val normalized = animeInfo.copy(
            title = animeInfo.title?.takeIf(String::isNotBlank),
            status = animeInfo.status?.takeUnless { it == 0L },
        )
        if (normalized.hasNoOverrides()) {
            if (customAnimeMap.remove(normalized.id) != null) saveToFile()
        } else {
            customAnimeMap[normalized.id] = CustomAnimeEntry.fromDomain(normalized)
            saveToFile()
        }
    }

    @Synchronized
    fun getAll(): List<CustomAnimeInfo> = customAnimeMap.values.map(CustomAnimeEntry::toDomain)

    @Synchronized
    fun replaceAll(restored: List<CustomAnimeInfo>) {
        val previous = customAnimeMap.toMap()
        customAnimeMap.clear()
        restored
            .map { value -> value.copy(
                title = value.title?.takeIf(String::isNotBlank),
                status = value.status?.takeUnless { it == 0L },
            ) }
            .filterNot { it.hasNoOverrides() }
            .forEach { value -> customAnimeMap[value.id] = CustomAnimeEntry.fromDomain(value) }
        try {
            saveToFile()
        } catch (error: Exception) {
            customAnimeMap.clear()
            customAnimeMap.putAll(previous)
            throw error
        }
    }

    @Synchronized
    fun set(animeId: Long, title: String? = null, author: String? = null,
            artist: String? = null, thumbnailUrl: String? = null,
            description: String? = null, genre: List<String>? = null, status: Long? = null) {
        val existing = customAnimeMap[animeId]
        set(
            CustomAnimeInfo(
                id = animeId,
                title = title ?: existing?.title,
                author = author ?: existing?.author,
                artist = artist ?: existing?.artist,
                thumbnailUrl = thumbnailUrl ?: existing?.thumbnailUrl,
                description = description ?: existing?.description,
                genre = genre ?: existing?.genre,
                status = status ?: existing?.status,
            ),
        )
    }

    @Synchronized
    fun remove(animeId: Long) {
        if (customAnimeMap.remove(animeId) != null) saveToFile()
    }

    private fun loadFromFile(): MutableMap<Long, CustomAnimeEntry> {
        if (!editJson.exists() || !editJson.isFile) return mutableMapOf()
        return try {
            val list = Json.decodeFromString<AnimeList>(editJson.readText())
            list.animes?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
        } catch (error: Exception) {
            val backup = MacOSAtomicFile.preserveMalformed(editJson)
            customAnimeLogger.warn(error) {
                "Custom anime JSON is malformed; starting with empty state" +
                    (backup?.let { ", preserved at ${it.name}" } ?: "")
            }
            mutableMapOf()
        }
    }

    private fun saveToFile() {
        synchronized(lock) {
            val content = Json.encodeToString(AnimeList(customAnimeMap.values.toList()))
            MacOSAtomicFile.writeText(editJson, content)
        }
    }

    @Serializable
    data class CustomAnimeEntry(
        val id: Long,
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val thumbnailUrl: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Long? = null,
    ) {
        fun toDomain(): CustomAnimeInfo = CustomAnimeInfo(
            id = id,
            title = title?.takeIf(String::isNotBlank),
            author = author,
            artist = artist,
            thumbnailUrl = thumbnailUrl,
            description = description,
            genre = genre,
            status = status?.takeUnless { it == 0L },
        )

        companion object {
            fun fromDomain(value: CustomAnimeInfo): CustomAnimeEntry = CustomAnimeEntry(
                id = value.id,
                title = value.title,
                author = value.author,
                artist = value.artist,
                thumbnailUrl = value.thumbnailUrl,
                description = value.description,
                genre = value.genre,
                status = value.status,
            )
        }
    }

    private fun CustomAnimeInfo.hasNoOverrides(): Boolean =
        title == null && author == null && artist == null && thumbnailUrl == null &&
            description == null && genre == null && status == null

    @Serializable
    private data class AnimeList(val animes: List<CustomAnimeEntry>? = null)
}
