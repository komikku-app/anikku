package app.anikku.macos.platform.data

import androidx.compose.runtime.compositionLocalOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.anikku.macos.platform.storage.MacOSAtomicFile
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.nio.file.Files

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
 * TODO Phase 2+: Implement tachiyomi.domain.anime.repository.CustomAnimeRepository
 * when shared modules are integrated via desktopMain source sets.
 */
class MacOSCustomAnimeRepository(dataDir: File) {

    private val editJson = File(dataDir, "edits.json")
    private val lock = Any()
    private val customAnimeMap: MutableMap<Long, CustomAnimeEntry> = loadFromFile()

    @Synchronized
    fun get(animeId: Long): CustomAnimeEntry? = customAnimeMap[animeId]

    @Synchronized
    fun set(animeId: Long, title: String? = null, author: String? = null,
            artist: String? = null, thumbnailUrl: String? = null,
            description: String? = null, genre: List<String>? = null, status: Long? = null) {
        val existing = customAnimeMap[animeId]
        val entry = CustomAnimeEntry(
            id = animeId,
            title = title ?: existing?.title,
            author = author ?: existing?.author,
            artist = artist ?: existing?.artist,
            thumbnailUrl = thumbnailUrl ?: existing?.thumbnailUrl,
            description = description ?: existing?.description,
            genre = genre ?: existing?.genre,
            status = status ?: existing?.status,
        )
        customAnimeMap[animeId] = entry
        saveToFile()
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
    )

    @Serializable
    private data class AnimeList(val animes: List<CustomAnimeEntry>? = null)
}
