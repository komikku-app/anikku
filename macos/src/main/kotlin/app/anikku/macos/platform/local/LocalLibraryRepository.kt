package app.anikku.macos.platform.local

import androidx.compose.runtime.compositionLocalOf
import app.anikku.macos.platform.storage.MacOSAtomicFile
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val localLogger = KotlinLogging.logger {}

/**
 * Composition local for the local video collection (folder-imported files).
 */
val LocalLocalLibraryRepository = compositionLocalOf<LocalLibraryRepository?> { null }

/**
 * One locally-imported video file, parsed into anime/episode metadata.
 *
 * [animeId] is a stable per-anime id minted from the normalized title (same
 * convention as the torrent grouping), [season] is 1-based, [episode] is the
 * parsed episode number (0 for entries that didn't parse, e.g. batches or
 * movies — those render under "Other").
 */
@Serializable
data class LocalVideoEntry(
    val animeId: Long,
    val title: String,
    val season: Int,
    val episode: Int,
    val filePath: String,
    val fileName: String,
    val sizeBytes: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * JSON-backed store of the user's local video collection.
 * Data file: ~/Library/Application Support/Anikku/data/local_library.json
 */
class LocalLibraryRepository(private val dataDir: File) {

    private val localFile = File(dataDir, "local_library.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private var entries: MutableList<LocalVideoEntry> = loadFromFile()
    private val _revision = MutableStateFlow(0L)
    /** Incremented on every mutation so UI can recompute grouped views. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun getAll(): List<LocalVideoEntry> = entries.toList()

    fun count(): Int = entries.size

    /** Add scanned files, deduped by absolute file path. */
    @Synchronized
    fun add(newEntries: List<LocalVideoEntry>) {
        if (newEntries.isEmpty()) return
        val existingPaths = entries.map { canonical(it.filePath) }.toSet()
        val fresh = newEntries.filter { canonical(it.filePath) !in existingPaths }
        if (fresh.isEmpty()) return
        entries.addAll(fresh)
        saveToFile()
        _revision.value++
    }

    /** Remove every file belonging to one anime (animeId = normalized-title hash). */
    @Synchronized
    fun removeAnime(animeId: Long) {
        val removed = entries.removeAll { it.animeId == animeId }
        if (removed) {
            saveToFile()
            _revision.value++
        }
    }

    /** Remove one file by path (used by the per-file overflow action). */
    @Synchronized
    fun remove(filePath: String) {
        val target = canonical(filePath)
        val removed = entries.removeAll { canonical(it.filePath) == target }
        if (removed) {
            saveToFile()
            _revision.value++
        }
    }

    /** Replaces persisted entries for transactional backup restore. */
    @Synchronized
    fun replaceAll(restored: List<LocalVideoEntry>) {
        val previous = entries
        entries = restored.distinctBy { canonical(it.filePath) }.toMutableList()
        try {
            saveToFile()
        } catch (error: Exception) {
            entries = previous
            throw error
        }
        _revision.value++
    }

    @Serializable
    private data class LocalVideoList(val entries: List<LocalVideoEntry>)

    private fun saveToFile() {
        MacOSAtomicFile.writeText(localFile, json.encodeToString(LocalVideoList(entries)))
    }

    private fun loadFromFile(): MutableList<LocalVideoEntry> {
        if (!localFile.exists()) return mutableListOf()
        return try {
            json.decodeFromString<LocalVideoList>(localFile.readText()).entries.toMutableList()
        } catch (error: Exception) {
            localLogger.warn(error) { "LOCAL: corrupted local_library.json — preserving as backup" }
            MacOSAtomicFile.preserveMalformed(localFile)
            mutableListOf()
        }
    }

    private fun canonical(path: String): String = runCatching { File(path).canonicalPath }.getOrElse { path }
}
