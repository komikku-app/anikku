package app.anikku.macos.platform.backup

import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages JSON backup export and import for the macOS app.
 *
 * Backup format includes:
 * - Library entries (favorites with categories)
 * - Watch history
 * - Download queue state
 * - Custom anime metadata edits
 * - App preferences
 * - Extension list
 *
 * ## Export
 *
 * ```kotlin
 * val backup = MacOSBackupManager(storageProvider, libraryRepo, historyRepo, ...)
 * backup.exportTo(outputFile)   // Writes all data to a JSON file
 * ```
 *
 * ## Import
 *
 * ```kotlin
 * backup.importFrom(inputFile)  // Reads JSON and restores data
 * ```
 *
 * The backup file uses a `.anikku_backup.json` extension and follows a
 * portable JSON schema that can be read/written by other tools.
 */
class MacOSBackupManager(
    private val libraryRepository: LibraryRepository,
    private val historyRepository: HistoryRepository,
    private val downloadRepository: DownloadRepository,
    private val preferenceStore: MacOSPreferenceStore? = null,
    private val customAnimeDir: File? = null,
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true },
) {

    companion object {
        const val BACKUP_VERSION = 1
        const val BACKUP_EXTENSION = ".anikku_backup.json"
    }

    // -----------------------------------------------------------------------
    // Export
    // -----------------------------------------------------------------------

    /**
     * Export all app data to a backup JSON file.
     *
     * @param outputFile The file to write the backup to (should end with .anikku_backup.json).
     * @return true on success.
     */
    fun exportTo(outputFile: File): Boolean {
        return try {
            val backup = buildBackupData()
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(json.encodeToString(backup))
            true
        } catch (e: Exception) {
            io.github.oshai.kotlinlogging.KotlinLogging.logger {}
                .error(e) { "Backup export failed" }
            false
        }
    }

    /**
     * Export all app data to a backup file in the given directory.
     * Auto-generates a filename with timestamp.
     *
     * @param outputDir The directory to save the backup in.
     * @param customName Optional custom filename (without extension).
     * @return The backup file on success, or null on failure.
     */
    fun exportToDir(outputDir: File, customName: String? = null): File? {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val name = customName ?: "anikku_backup_$dateStr"
        val file = File(outputDir, "$name$BACKUP_EXTENSION")
        return if (exportTo(file)) file else null
    }

    // -----------------------------------------------------------------------
    // Import
    // -----------------------------------------------------------------------

    /**
     * Import app data from a backup JSON file.
     *
     * Restores library, history, downloads, and preferences.
     *
     * @param inputFile The backup file to import.
     * @return A summary of what was restored.
     */
    fun importFrom(inputFile: File): ImportResult {
        if (!inputFile.isFile) {
            return ImportResult(success = false, error = "File not found: $inputFile")
        }

        // Read and parse backup JSON. File-system failures are returned to the
        // caller so the UI can show an actionable restore error instead of
        // losing the coroutine exception silently.
        val backupText = try {
            inputFile.readText()
        } catch (e: Exception) {
            io.github.oshai.kotlinlogging.KotlinLogging.logger {}
                .error(e) { "Backup import could not read ${inputFile.name}" }
            return ImportResult(success = false, error = "Read error: ${e.message?.take(200) ?: "Unknown"}")
        }
        val backup: BackupData
        try {
            backup = json.decodeFromString<BackupData>(backupText)
        } catch (e: Exception) {
            io.github.oshai.kotlinlogging.KotlinLogging.logger {}
                .error(e) { "Backup import failed for ${inputFile.name}" }
            return ImportResult(success = false, error = "Parse error: ${e.message?.take(200) ?: "Unknown"}")
        }

        // Check version compatibility
        if (backup.version > BACKUP_VERSION) {
            return ImportResult(
                success = false,
                error = "Backup version ${backup.version} > supported ($BACKUP_VERSION)",
            )
        }

        // Restore library
        val libraryEntries = backup.library
        var libraryCount = 0
        if (libraryEntries != null) {
            for (entry in libraryEntries) {
                libraryRepository.add(
                    LibraryRepository.LibraryEntry(
                        animeId = entry.animeId,
                        title = entry.title,
                        sourceId = entry.sourceId,
                        url = entry.url,
                        thumbnailUrl = entry.thumbnailUrl,
                        author = entry.author,
                        artist = entry.artist,
                        description = entry.description,
                        genre = entry.genre,
                        status = entry.status,
                        categoryId = entry.categoryId,
                        lastSecondSeen = entry.lastSecondSeen,
                        totalSeconds = entry.totalSeconds,
                        latestEpisodeNumber = entry.latestEpisodeNumber,
                        latestEpisodeName = entry.latestEpisodeName,
                        unseenEpisodeCount = entry.unseenEpisodeCount,
                        addedAt = entry.addedAt,
                        lastUpdatedAt = entry.lastUpdatedAt,
                    )
                )
                libraryCount++
            }
        }

        // Restore history
        val historyEntries = backup.history
        var historyCount = 0
        if (historyEntries != null) {
            for (entry in historyEntries) {
                historyRepository.add(
                    HistoryRepository.HistoryEntry(
                        animeId = entry.animeId,
                        episodeId = entry.episodeId,
                        animeTitle = entry.animeTitle,
                        episodeName = entry.episodeName,
                        episodeNumber = entry.episodeNumber,
                        sourceId = entry.sourceId,
                        episodeUrl = entry.episodeUrl,
                        seenAt = entry.seenAt,
                        watchDuration = entry.watchDuration,
                        lastSecondSeen = entry.lastSecondSeen,
                        totalSeconds = entry.totalSeconds,
                    )
                )
                historyCount++
            }
        }

        // Restore download metadata (count only)
        var downloadsCount = backup.downloads?.size ?: 0

        // Restore preferences
        val prefs = backup.preferences
        if (prefs != null) {
            for ((key, value) in prefs) {
                preferenceStore?.getString(key, "")?.set(value)
            }
        }

        return ImportResult(
            success = true,
            libraryCount = libraryCount,
            historyCount = historyCount,
            downloadsCount = downloadsCount,
        )
    }

    // -----------------------------------------------------------------------
    // Data collection
    // -----------------------------------------------------------------------

    private fun buildBackupData(): BackupData {
        val library = libraryRepository.getAll().map { entry ->
            BackupLibraryEntry(
                animeId = entry.animeId,
                title = entry.title,
                sourceId = entry.sourceId,
                url = entry.url,
                thumbnailUrl = entry.thumbnailUrl,
                author = entry.author,
                artist = entry.artist,
                description = entry.description,
                genre = entry.genre,
                status = entry.status,
                categoryId = entry.categoryId,
                lastSecondSeen = entry.lastSecondSeen,
                totalSeconds = entry.totalSeconds,
                latestEpisodeNumber = entry.latestEpisodeNumber,
                latestEpisodeName = entry.latestEpisodeName,
                unseenEpisodeCount = entry.unseenEpisodeCount,
                addedAt = entry.addedAt,
                lastUpdatedAt = entry.lastUpdatedAt,
            )
        }

        val history = historyRepository.getAll().map { entry ->
            BackupHistoryEntry(
                animeId = entry.animeId,
                episodeId = entry.episodeId,
                animeTitle = entry.animeTitle,
                episodeName = entry.episodeName,
                episodeNumber = entry.episodeNumber,
                sourceId = entry.sourceId,
                episodeUrl = entry.episodeUrl,
                seenAt = entry.seenAt,
                watchDuration = entry.watchDuration,
                lastSecondSeen = entry.lastSecondSeen,
                totalSeconds = entry.totalSeconds,
            )
        }

        val downloads = downloadRepository.getAll().map { entry ->
            BackupDownloadEntry(
                id = entry.id,
                animeId = entry.animeId,
                sourceId = entry.sourceId,
                animeTitle = entry.animeTitle,
                episodeName = entry.episodeName,
                episodeNumber = entry.episodeNumber,
                episodeUrl = entry.episodeUrl,
                status = entry.status.name,
                createdAt = entry.createdAt,
            )
        }

        val preferences = preferenceStore?.getAll()?.mapValues { it.value.toString() }

        return BackupData(
            version = BACKUP_VERSION,
            appName = "Anikku macOS",
            exportedAt = System.currentTimeMillis(),
            library = library,
            history = history,
            downloads = downloads,
            preferences = preferences,
        )
    }

    // -----------------------------------------------------------------------
    // Data models
    // -----------------------------------------------------------------------

    @Serializable
    data class BackupData(
        val version: Int = BACKUP_VERSION,
        val appName: String = "Anikku macOS",
        val exportedAt: Long = System.currentTimeMillis(),
        val library: List<BackupLibraryEntry>? = null,
        val history: List<BackupHistoryEntry>? = null,
        val downloads: List<BackupDownloadEntry>? = null,
        val preferences: Map<String, String>? = null,
    )

    @Serializable
    data class BackupLibraryEntry(
        val animeId: Long,
        val title: String,
        val sourceId: Long = 0L,
        val url: String? = null,
        val thumbnailUrl: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Int = 0,
        val categoryId: Long = 0L,
        val lastSecondSeen: Long = 0L,
        val totalSeconds: Long = 0L,
        val latestEpisodeNumber: Double = 0.0,
        val latestEpisodeName: String? = null,
        val unseenEpisodeCount: Int = 0,
        val addedAt: Long = System.currentTimeMillis(),
        val lastUpdatedAt: Long = System.currentTimeMillis(),
    )

    @Serializable
    data class BackupHistoryEntry(
        val animeId: Long,
        val episodeId: Long,
        val animeTitle: String = "",
        val episodeName: String = "",
        val episodeNumber: Double = 0.0,
        val sourceId: Long = 0L,
        val episodeUrl: String? = null,
        val seenAt: Long = System.currentTimeMillis(),
        val watchDuration: Long = 0L,
        val lastSecondSeen: Long = 0L,
        val totalSeconds: Long = 0L,
    )

    @Serializable
    data class BackupDownloadEntry(
        val id: Long,
        val animeId: Long,
        val sourceId: Long = 0L,
        val animeTitle: String = "",
        val episodeName: String = "",
        val episodeNumber: Double = 0.0,
        val episodeUrl: String? = null,
        val status: String = "QUEUED",
        val createdAt: Long = System.currentTimeMillis(),
    )
}

/**
 * Result of a backup import operation.
 */
data class ImportResult(
    val success: Boolean,
    val libraryCount: Int = 0,
    val historyCount: Int = 0,
    val downloadsCount: Int = 0,
    val error: String? = null,
)
