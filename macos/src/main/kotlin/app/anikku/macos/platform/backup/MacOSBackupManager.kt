package app.anikku.macos.platform.backup

import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.CategoryEntry
import app.anikku.macos.platform.data.MacOSCustomAnimeRepository
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.storage.MacOSAtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import tachiyomi.domain.anime.model.CustomAnimeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages lossless macOS JSON backup export/import and Android `.tachibk`
 * migration imports.
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
    private val customAnimeRepository: MacOSCustomAnimeRepository? = null,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    },
) {

    companion object {
        const val BACKUP_VERSION = 2
        const val BACKUP_EXTENSION = ".anikku_backup.json"
        const val ANDROID_BACKUP_EXTENSION = ".tachibk"
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
            MacOSAtomicFile.writeText(outputFile, json.encodeToString(backup))
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
     * Import app data from a macOS JSON backup or Android `.tachibk` file.
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

        val backup = try {
            if (AndroidBackupCodec.isAndroidBackup(inputFile)) {
                AndroidBackupCodec.decode(inputFile, libraryRepository.getCategories())
            } else {
                json.decodeFromString<BackupData>(inputFile.readText())
            }
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

        // Decode and validate every record before mutating live state.
        val restoredLibrary = backup.library.orEmpty().map(BackupLibraryEntry::toRepository)
        val restoredCategories = backup.categories?.map(BackupCategoryEntry::toRepository)
        val restoredHistory = backup.history.orEmpty().map(BackupHistoryEntry::toRepository)
        val restoredDownloads = try {
            backup.downloads.orEmpty().map(BackupDownloadEntry::toRepository)
        } catch (error: IllegalArgumentException) {
            return ImportResult(success = false, error = "Invalid download status: ${error.message}")
        }
        val restoredCustomAnime = backup.customAnime.orEmpty().map(BackupCustomAnimeEntry::toDomain)
        val restoredPreferences = backup.preferenceValues
            ?: backup.preferences?.mapValues { JsonPrimitive(it.value) }
            ?: emptyMap()
        val safeRestoredPreferences = restoredPreferences.filterKeys(::isSafePreferenceForBackup)

        val previousLibrary = libraryRepository.getAll()
        val previousCategories = libraryRepository.getCategories()
        val previousHistory = historyRepository.getAll()
        val previousDownloads = downloadRepository.getAll()
        val previousPreferences = preferenceStore?.snapshotJson()
        val previousCustomAnime = customAnimeRepository?.getAll()

        return try {
            val mergedLibrary = (previousLibrary + restoredLibrary)
                .associateBy { it.animeId }
                .values
                .toList()
            val mergedCategories = if (restoredCategories == null) {
                previousCategories
            } else {
                (previousCategories + restoredCategories).associateBy { it.id }.values.toList()
            }
            libraryRepository.replaceAll(mergedLibrary, mergedCategories)

            historyRepository.replaceAll(
                (previousHistory + restoredHistory)
                    .associateBy { it.animeId to it.episodeId }
                    .values
                    .toList(),
            )
            downloadRepository.replaceAll(
                (previousDownloads + restoredDownloads).associateBy { it.id }.values.toList(),
            )
            if (safeRestoredPreferences.isNotEmpty()) preferenceStore?.restoreJson(safeRestoredPreferences)
            if (restoredCustomAnime.isNotEmpty()) {
                customAnimeRepository?.replaceAll(
                    (previousCustomAnime.orEmpty() + restoredCustomAnime).associateBy { it.id }.values.toList(),
                )
            }

            ImportResult(
                success = true,
                libraryCount = restoredLibrary.size,
                historyCount = restoredHistory.size,
                downloadsCount = restoredDownloads.size,
                customAnimeCount = restoredCustomAnime.size,
            )
        } catch (error: Exception) {
            // Best-effort rollback keeps an I/O failure from leaving a partial restore.
            runCatching { libraryRepository.replaceAll(previousLibrary, previousCategories) }
            runCatching { historyRepository.replaceAll(previousHistory) }
            runCatching { downloadRepository.replaceAll(previousDownloads) }
            if (previousPreferences != null) runCatching { preferenceStore?.restoreJson(previousPreferences, replace = true) }
            if (previousCustomAnime != null) runCatching { customAnimeRepository?.replaceAll(previousCustomAnime) }
            ImportResult(success = false, error = "Restore failed: ${error.message?.take(200) ?: "Unknown"}")
        }
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

        val categories = libraryRepository.getCategories().map { category ->
            BackupCategoryEntry(
                id = category.id,
                name = category.name,
                order = category.order,
                isDefault = category.isDefault,
                hidden = category.hidden,
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
                animeUrl = entry.animeUrl,
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
                videoUrl = entry.videoUrl,
                fileName = entry.fileName,
                filePath = entry.filePath,
                status = entry.status.name,
                progress = entry.progress,
                totalBytes = entry.totalBytes,
                downloadedBytes = entry.downloadedBytes,
                createdAt = entry.createdAt,
                completedAt = entry.completedAt,
            )
        }

        val customAnime = customAnimeRepository?.getAll()?.map { entry ->
            BackupCustomAnimeEntry(
                id = entry.id,
                title = entry.title,
                author = entry.author,
                artist = entry.artist,
                thumbnailUrl = entry.thumbnailUrl,
                description = entry.description,
                genre = entry.genre,
                status = entry.status,
            )
        }

        return BackupData(
            version = BACKUP_VERSION,
            appName = "Anikku macOS",
            exportedAt = System.currentTimeMillis(),
            library = library,
            categories = categories,
            history = history,
            downloads = downloads,
            preferenceValues = preferenceStore?.snapshotJson()?.filterKeys(::isSafePreferenceForBackup),
            customAnime = customAnime,
        )
    }

    private fun isSafePreferenceForBackup(key: String): Boolean {
        val normalized = key.lowercase(Locale.ROOT)
        return normalized != "syncyomi_host" &&
            !normalized.endsWith("_etag") &&
            !normalized.contains("password") &&
            !normalized.contains("secret") &&
            !normalized.contains("token")
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
        val categories: List<BackupCategoryEntry>? = null,
        val history: List<BackupHistoryEntry>? = null,
        val downloads: List<BackupDownloadEntry>? = null,
        /** Version 0/1 compatibility field. */
        val preferences: Map<String, String>? = null,
        val preferenceValues: Map<String, JsonElement>? = null,
        val customAnime: List<BackupCustomAnimeEntry>? = null,
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
    ) {
        fun toRepository() = LibraryRepository.LibraryEntry(
            animeId, title, sourceId, url, thumbnailUrl, author, artist, description,
            genre, status, categoryId, lastSecondSeen, totalSeconds, latestEpisodeNumber,
            latestEpisodeName, unseenEpisodeCount, addedAt, lastUpdatedAt,
        )
    }

    @Serializable
    data class BackupCategoryEntry(
        val id: Long,
        val name: String,
        val order: Long = 0L,
        val isDefault: Boolean = false,
        val hidden: Boolean = false,
    ) {
        fun toRepository() = CategoryEntry(id, name, order, isDefault, hidden)
    }

    @Serializable
    data class BackupHistoryEntry(
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
    ) {
        fun toRepository() = HistoryRepository.HistoryEntry(
            animeId, episodeId, animeTitle, episodeName, episodeNumber, sourceId,
            animeUrl, episodeUrl, seenAt, watchDuration, lastSecondSeen, totalSeconds,
        )
    }

    @Serializable
    data class BackupDownloadEntry(
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
        val status: String = "QUEUED",
        val progress: Float = 0f,
        val totalBytes: Long = 0L,
        val downloadedBytes: Long = 0L,
        val createdAt: Long = System.currentTimeMillis(),
        val completedAt: Long? = null,
    ) {
        fun toRepository() = DownloadRepository.DownloadEntry(
            id, animeId, sourceId, animeTitle, episodeName, episodeNumber, episodeUrl,
            videoUrl, fileName, filePath, DownloadRepository.DownloadStatus.valueOf(status),
            progress, totalBytes, downloadedBytes, createdAt, completedAt,
        )
    }

    @Serializable
    data class BackupCustomAnimeEntry(
        val id: Long,
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val thumbnailUrl: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Long? = null,
    ) {
        fun toDomain() = CustomAnimeInfo(id, title, author, artist, thumbnailUrl, description, genre, status)
    }
}

/**
 * Result of a backup import operation.
 */
data class ImportResult(
    val success: Boolean,
    val libraryCount: Int = 0,
    val historyCount: Int = 0,
    val downloadsCount: Int = 0,
    val customAnimeCount: Int = 0,
    val error: String? = null,
)
