package app.anikku.macos.platform.data

import androidx.compose.runtime.compositionLocalOf
import app.anikku.macos.platform.download.MacOSDownloadManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import app.anikku.macos.platform.storage.MacOSAtomicFile

/**
 * CompositionLocal for MacOSDownloadManager.
 * Provide via CompositionLocalProvider in the app root.
 */
val LocalDownloadManager = compositionLocalOf<MacOSDownloadManager?> { null }

/**
 * JSON-backed repository for download states.
 *
 * Persists download queue items so they survive app restarts.
 * Data file: ~/Library/Application Support/Anikku/data/downloads.json
 */
class DownloadRepository(private val dataDir: File) {

    private val downloadFile = File(dataDir, "downloads.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class DownloadEntry(
        val id: Long,
        val animeId: Long,
        val sourceId: Long = 0L,
        val animeTitle: String = "",
        val episodeName: String = "",
        val episodeNumber: Double = 0.0,
        val episodeUrl: String? = null,
        val videoUrl: String? = null,
        val fileName: String? = null,
        val filePath: String? = null,
        val status: DownloadStatus = DownloadStatus.QUEUED,
        val progress: Float = 0f,
        val totalBytes: Long = 0L,
        val downloadedBytes: Long = 0L,
        val createdAt: Long = System.currentTimeMillis(),
        val completedAt: Long? = null,
        /** Stream headers from the source's Video (Referer, ...). Null-safe for legacy entries. */
        val headers: Map<String, String>? = null,
        /**
         * Kept partial file when a download is paused, so resume() can continue
         * from where it stopped via HTTP Range instead of restarting.
         */
        val resumePartialPath: String? = null,
    ) {
        val isActive: Boolean get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED
        val isFinished: Boolean get() = status == DownloadStatus.COMPLETED || status == DownloadStatus.ERROR
    }

    @Serializable
    enum class DownloadStatus {
        QUEUED,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        ERROR,
    }

    private var nextId: Long = 0L
    private var entries: MutableList<DownloadEntry> = loadFromFile()

    init {
        nextId = (entries.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    @Synchronized
    fun getAll(): List<DownloadEntry> = entries.toList()

    @Synchronized
    fun get(id: Long): DownloadEntry? = entries.find { it.id == id }

    @Synchronized
    fun getActive(): List<DownloadEntry> = entries.filter { it.isActive }

    @Synchronized
    fun getCompleted(): List<DownloadEntry> = entries.filter { it.status == DownloadStatus.COMPLETED }

    @Synchronized
    fun getForAnime(animeId: Long): List<DownloadEntry> =
        entries.filter { it.animeId == animeId }

    @Synchronized
    fun getForEpisode(animeId: Long, episodeId: Long): DownloadEntry? =
        entries.find { it.animeId == animeId && it.id == episodeId }

    @Synchronized
    fun isDownloaded(animeId: Long, episodeNumber: Double): Boolean =
        entries.any { it.animeId == animeId && it.episodeNumber == episodeNumber && it.status == DownloadStatus.COMPLETED }

    /**
     * Create a new download entry with QUEUED status.
     */
    @Synchronized
    fun enqueue(
        animeId: Long,
        sourceId: Long,
        animeTitle: String,
        episodeName: String,
        episodeNumber: Double,
        episodeUrl: String?,
    ): DownloadEntry {
        val entry = DownloadEntry(
            id = nextId++,
            animeId = animeId,
            sourceId = sourceId,
            animeTitle = animeTitle,
            episodeName = episodeName,
            episodeNumber = episodeNumber,
            episodeUrl = episodeUrl,
            status = DownloadStatus.QUEUED,
        )
        entries.add(entry)
        saveToFile()
        return entry
    }

    /**
     * Update a download entry's state.
     *
     * [resumePartialPath] records a preserved partial file for pause/resume:
     * pass a path to set it, [CLEAR_RESUME_PARTIAL] to clear it, or omit it
     * (default null) to keep the current value.
     */
    @Synchronized
    fun update(
        id: Long,
        status: DownloadStatus? = null,
        videoUrl: String? = null,
        headers: Map<String, String>? = null,
        progress: Float? = null,
        totalBytes: Long? = null,
        downloadedBytes: Long? = null,
        filePath: String? = null,
        fileName: String? = null,
        resumePartialPath: String? = null,
    ): DownloadEntry? {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return null

        val current = entries[index]
        val updated = current.copy(
            status = status ?: current.status,
            videoUrl = videoUrl ?: current.videoUrl,
            headers = headers ?: current.headers,
            progress = progress ?: current.progress,
            totalBytes = totalBytes ?: current.totalBytes,
            downloadedBytes = downloadedBytes ?: current.downloadedBytes,
            filePath = filePath ?: current.filePath,
            fileName = fileName ?: current.fileName,
            resumePartialPath = when (resumePartialPath) {
                null -> current.resumePartialPath
                CLEAR_RESUME_PARTIAL -> null
                else -> resumePartialPath
            },
            completedAt = if (status == DownloadStatus.COMPLETED) System.currentTimeMillis() else current.completedAt,
        )
        entries[index] = updated
        saveToFile()
        return updated
    }

    @Synchronized
    fun remove(id: Long): Boolean {
        val removed = entries.removeAll { it.id == id }
        if (removed) saveToFile()
        return removed
    }

    @Synchronized
    fun removeAll() {
        entries.clear()
        saveToFile()
    }

    /** Replaces persisted queue metadata, preserving stable backup IDs. */
    @Synchronized
    fun replaceAll(restored: List<DownloadEntry>) {
        val previousEntries = entries
        val previousNextId = nextId
        entries = restored.distinctBy { it.id }.toMutableList()
        nextId = (entries.maxOfOrNull { it.id } ?: 0L) + 1L
        try {
            saveToFile()
        } catch (error: Exception) {
            entries = previousEntries
            nextId = previousNextId
            throw error
        }
    }

    @Synchronized
    fun pruneCompleted(keepCount: Int = 20) {
        val completed = entries.filter { it.status == DownloadStatus.COMPLETED }
            .sortedByDescending { it.completedAt ?: 0L }
        if (completed.size > keepCount) {
            val toRemove = completed.drop(keepCount).map { it.id }.toSet()
            entries.removeAll { it.id in toRemove }
            saveToFile()
        }
    }

    @Synchronized
    fun getDownloadsDir(): File = File(dataDir, "videos")

    private fun loadFromFile(): MutableList<DownloadEntry> {
        if (!downloadFile.exists()) return mutableListOf()
        return try {
            val list = json.decodeFromString<DownloadList>(downloadFile.readText())
            list.entries.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveToFile() {
        MacOSAtomicFile.writeText(downloadFile, json.encodeToString(DownloadList(entries)))
    }

    @Serializable
    private data class DownloadList(val entries: List<DownloadEntry>)

    companion object {
        /**
         * Sentinel for [update]'s [resumePartialPath]: explicitly clear a
         * recorded partial path. Default null keeps the current value; a NUL
         * byte can never appear in a real file path.
         */
        const val CLEAR_RESUME_PARTIAL: String = "\u0000clear-resume-partial"
    }
}
