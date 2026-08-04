package app.anikku.macos.platform.library

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

private val feedLogger = KotlinLogging.logger {}

/**
 * One newly-discovered episode row in the New Episodes feed, deduped by
 * [animeId] + [episodeNumber]. An anime with N new episodes contributes N rows.
 */
@Serializable
data class NewEpisodeEntry(
    val animeId: Long,
    val episodeNumber: Double,
    val animeTitle: String = "",
    val thumbnailUrl: String? = null,
    val sourceId: Long = 0L,
    val animeUrl: String? = null,
    val episodeName: String? = null,
    val discoveredAt: Long = System.currentTimeMillis(),
)

/** Expand a per-anime discovery into per-episode feed rows (highest N numbers). */
fun NewEpisodeInfo.toFeedEntries(): List<NewEpisodeEntry> {
    if (episodeCount <= 0 || latestEpisodeNumber <= 0.0) return emptyList()
    val base = latestEpisodeNumber.toLong() - episodeCount + 1L
    return (0 until episodeCount).map { offset ->
        val number = (base + offset).toDouble()
        NewEpisodeEntry(
            animeId = animeId,
            episodeNumber = number,
            animeTitle = title,
            thumbnailUrl = thumbnailUrl,
            sourceId = sourceId,
            animeUrl = animeUrl,
            episodeName = if (offset == episodeCount - 1) latestEpisodeName else null,
        )
    }
}

/**
 * JSON-backed store for the New Episodes feed (new_episodes.json).
 *
 * Mirrors HistoryRepository's shape: atomic writes, a `revision` StateFlow for
 * Compose recomputation, and dedupe by (animeId, episodeNumber) so re-running a
 * library check never re-adds the same episode.
 */
class NewEpisodeRepository(private val dataDir: File) {

    private val feedFile = File(dataDir, "new_episodes.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private var entries: MutableList<NewEpisodeEntry> = loadFromFile()
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Feed rows, newest first (tie-broken by episode number so same-batch rows are deterministic). */
    @Synchronized
    fun getAll(): List<NewEpisodeEntry> = entries.sortedWith(
        compareByDescending<NewEpisodeEntry> { it.discoveredAt }.thenByDescending { it.episodeNumber },
    )

    /**
     * Add newly discovered rows, skipping any (animeId, episodeNumber) already
     * present. Returns how many rows were actually added. Capped at 200 rows.
     */
    @Synchronized
    fun addDiscovered(items: List<NewEpisodeEntry>): Int {
        if (items.isEmpty()) return 0
        val existingKeys = entries.map { it.animeId to it.episodeNumber }.toSet()
        val fresh = items.filter { (it.animeId to it.episodeNumber) !in existingKeys }
        if (fresh.isEmpty()) return 0
        entries.addAll(fresh)
        if (entries.size > MAX_FEED_ROWS) {
            entries = entries.sortedByDescending { it.discoveredAt }.take(MAX_FEED_ROWS).toMutableList()
        }
        saveToFile()
        _revision.value++
        return fresh.size
    }

    /** Remove every row for an anime (dismissing it from the feed). */
    @Synchronized
    fun removeForAnime(animeId: Long): Boolean {
        val removed = entries.removeAll { it.animeId == animeId }
        if (removed) {
            saveToFile()
            _revision.value++
        }
        return removed
    }

    /** Clear the whole feed; returns how many rows were removed. */
    @Synchronized
    fun clear(): Int {
        val count = entries.size
        if (count > 0) {
            entries.clear()
            saveToFile()
            _revision.value++
        }
        return count
    }

    @Synchronized
    fun count(): Int = entries.size

    private fun loadFromFile(): MutableList<NewEpisodeEntry> {
        if (!feedFile.exists()) return mutableListOf()
        return try {
            val list = json.decodeFromString<FeedList>(feedFile.readText())
            list.entries.toMutableList()
        } catch (error: Exception) {
            val backup = MacOSAtomicFile.preserveMalformed(feedFile)
            feedLogger.warn(error) {
                "New episodes feed JSON is malformed; starting with empty state" +
                    (backup?.let { ", preserved at ${it.name}" } ?: "")
            }
            mutableListOf()
        }
    }

    private fun saveToFile() {
        synchronized(this) {
            MacOSAtomicFile.writeText(feedFile, json.encodeToString(FeedList(entries)))
        }
    }

    @Serializable
    private data class FeedList(val entries: List<NewEpisodeEntry>)

    companion object {
        private const val MAX_FEED_ROWS = 200
    }
}

val LocalNewEpisodeRepository = compositionLocalOf<NewEpisodeRepository?> { null }
