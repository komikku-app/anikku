package app.anikku.macos.platform.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.anikku.macos.platform.storage.MacOSAtomicFile
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val historyLogger = KotlinLogging.logger {}

/**
 * JSON-backed repository for the user's episode watch history.
 *
 * Records episodes the user has watched, including duration and timestamp.
 * Data file: ~/Library/Application Support/Anikku/data/history.json
 */
class HistoryRepository(private val dataDir: File) {

    private val historyFile = File(dataDir, "history.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    data class HistoryEntry(
        val animeId: Long,
        val episodeId: Long,
        val animeTitle: String = "",
        val episodeName: String = "",
        val episodeNumber: Double = 0.0,
        val sourceId: Long = 0L,
        val animeUrl: String? = null,
        val episodeUrl: String? = null,
        val seenAt: Long = System.currentTimeMillis(),
        val watchDuration: Long = 0L,
        val lastSecondSeen: Long = 0L,
        val totalSeconds: Long = 0L,
        /** Cover image URL for list rows (best-effort; null for legacy entries). */
        val coverUrl: String? = null,
    )

    private var entries: MutableList<HistoryEntry> = loadFromFile()
    private val _revision = MutableStateFlow(0L)
    /** Incremented on every mutation so UI can recompute derived data (e.g. last-watched). */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun getAll(): List<HistoryEntry> = entries.toList()

    fun getLatest(): HistoryEntry? = entries.maxByOrNull { it.seenAt }

    @Synchronized
    fun add(entry: HistoryEntry) {
        // Remove duplicate entry for same episode if exists (replace with latest)
        entries.removeAll { it.episodeId == entry.episodeId && it.animeId == entry.animeId }
        entries.add(entry)
        // Keep only last 500 entries to prevent unbounded growth
        if (entries.size > 500) {
            entries = entries.sortedByDescending { it.seenAt }.take(500).toMutableList()
        }
        saveToFile()
        _revision.value++
    }

    @Synchronized
    fun clearAll() {
        entries.clear()
        saveToFile()
        _revision.value++
    }

    /** Remove one episode's history entry (same dedupe key as [add]). */
    @Synchronized
    fun removeForEpisode(animeId: Long, episodeId: Long) {
        val removed = entries.removeAll { it.animeId == animeId && it.episodeId == episodeId }
        if (removed) {
            saveToFile()
            _revision.value++
        }
    }

    /** Remove all history for an anime (clears it from Continue Watching). */
    @Synchronized
    fun removeForAnime(animeId: Long) {
        val removed = entries.removeAll { it.animeId == animeId }
        if (removed) {
            saveToFile()
            _revision.value++
        }
    }

    fun count(): Int = entries.size

    /** Replaces persisted history for transactional backup restore. */
    @Synchronized
    fun replaceAll(restored: List<HistoryEntry>) {
        val previous = entries
        entries = restored
            .distinctBy { it.animeId to it.episodeId }
            .sortedByDescending { it.seenAt }
            .take(500)
            .toMutableList()
        try {
            saveToFile()
        } catch (error: Exception) {
            entries = previous
            throw error
        }
        _revision.value++
    }

    fun getForAnime(animeId: Long): List<HistoryEntry> =
        entries.filter { it.animeId == animeId }.sortedByDescending { it.seenAt }

    /**
     * Get the most recent history entry for an anime, for resume position.
     */
    fun getLatestForAnime(animeId: Long): HistoryEntry? =
        entries.filter { it.animeId == animeId }.maxByOrNull { it.seenAt }

    /**
     * Get the most recent history entry for a specific episode, so the player
     * can resume from the exact position the user last stopped at.
     */
    @Synchronized
    fun getForEpisode(animeId: Long, episodeId: Long): HistoryEntry? =
        entries.filter { it.animeId == animeId && it.episodeId == episodeId }
            .maxByOrNull { it.seenAt }

    /**
     * Resume fallback keyed by episode NUMBER rather than hashed episode id.
     * Episode IDs are derived from source URLs, which can change between
     * sessions (signed URLs, query params), while numbers stay stable — so
     * resume would silently miss for those shows.
     */
    @Synchronized
    fun getLatestForEpisodeNumber(animeId: Long, episodeNumber: Double): HistoryEntry? =
        entries.filter { it.animeId == animeId && it.episodeNumber == episodeNumber }
            .maxByOrNull { it.seenAt }

    /**
     * Entries for a "Continue Watching" row: the most recent in-progress
     * episode per anime. An episode counts as in-progress when the user has
     * actually advanced into it (lastSecondSeen > 0) but hasn't finished it
     * (lastSecondSeen is more than a few seconds before the end, or the
     * duration is unknown). Sorted by most recently watched, capped at
     * [limit] entries.
     */
    @Synchronized
    fun getContinueWatching(limit: Int = 12): List<HistoryEntry> {
        val inProgress = entries.filter { entry ->
            entry.lastSecondSeen > 0 &&
                (entry.totalSeconds <= 0 || entry.lastSecondSeen < entry.totalSeconds - 5)
        }
        return inProgress
            .groupBy { it.animeId }
            .map { (_, list) -> list.maxByOrNull { it.seenAt }!! }
            .sortedByDescending { it.seenAt }
            .take(limit.coerceAtLeast(0))
    }

    private fun loadFromFile(): MutableList<HistoryEntry> {
        if (!historyFile.exists()) return mutableListOf()
        return try {
            val list = json.decodeFromString<HistoryList>(historyFile.readText())
            list.entries.toMutableList()
        } catch (error: Exception) {
            val backup = MacOSAtomicFile.preserveMalformed(historyFile)
            historyLogger.warn(error) {
                "History JSON is malformed; starting with empty state" +
                    (backup?.let { ", preserved at ${it.name}" } ?: "")
            }
            mutableListOf()
        }
    }

    private fun saveToFile() {
        synchronized(this) {
            MacOSAtomicFile.writeText(historyFile, json.encodeToString(HistoryList(entries)))
        }
    }

    @Serializable
    private data class HistoryList(val entries: List<HistoryEntry>)
}
