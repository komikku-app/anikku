package app.anikku.macos.platform

import androidx.compose.runtime.compositionLocalOf
import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.platform.library.MacOSLibraryUpdateService
import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.sync.MacOSGoogleDriveService
import app.anikku.macos.platform.sync.MacOSSyncYomiService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlin.time.Duration.Companion.hours

private val backgroundLogger = KotlinLogging.logger {}

data class BackgroundJobConfiguration(
    val automaticBackupHours: Int = 12,
    val libraryUpdateHours: Int = 0,
    val googleDriveSyncHours: Int = 0,
    val syncYomiSyncHours: Int = 0,
)

data class BackgroundJobStatus(
    val runningTask: String? = null,
    val message: String? = null,
    val completed: Int = 0,
    val total: Int = 0,
    val lastError: String? = null,
)

/** Configures the WorkManager-equivalent jobs that run while Anikku is open. */
class MacOSBackgroundJobs(
    private val scheduler: BackgroundTaskScheduler,
    private val backupManager: MacOSBackupManager,
    private val automaticBackupsDirectory: File,
    private val libraryUpdateService: MacOSLibraryUpdateService,
    private val googleDriveService: MacOSGoogleDriveService,
    private val notificationManager: MacOSNotificationManager,
    private val preferenceStore: MacOSPreferenceStore,
    private val syncYomiService: MacOSSyncYomiService? = null,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val backupMutex = Mutex()
    private val _status = MutableStateFlow(BackgroundJobStatus())
    val status: StateFlow<BackgroundJobStatus> = _status.asStateFlow()

    @Volatile
    private var configuration = BackgroundJobConfiguration()
    @Volatile
    private var configured = false

    fun configure(value: BackgroundJobConfiguration) {
        val normalized = value.copy(
            automaticBackupHours = value.automaticBackupHours.coerceIn(0, MAX_INTERVAL_HOURS),
            libraryUpdateHours = value.libraryUpdateHours.coerceIn(0, MAX_INTERVAL_HOURS),
            googleDriveSyncHours = value.googleDriveSyncHours.coerceIn(0, MAX_INTERVAL_HOURS),
            syncYomiSyncHours = value.syncYomiSyncHours.coerceIn(0, MAX_INTERVAL_HOURS),
        )
        if (normalized == configuration && configured) return
        configuration = normalized
        configurePeriodic(
            name = AUTO_BACKUP_PERIODIC,
            intervalHours = normalized.automaticBackupHours,
            task = ::createAutomaticBackup,
        )
        configurePeriodic(
            name = LIBRARY_UPDATE_PERIODIC,
            intervalHours = normalized.libraryUpdateHours,
            task = ::updateLibrary,
        )
        configurePeriodic(
            name = DRIVE_SYNC_PERIODIC,
            intervalHours = normalized.googleDriveSyncHours,
            task = ::syncGoogleDrive,
        )
        configurePeriodic(
            name = SYNCYOMI_PERIODIC,
            intervalHours = normalized.syncYomiSyncHours,
            task = ::syncSyncYomi,
        )
        configured = true
    }

    /** Trigger due jobs after focus without resetting their periodic timers. */
    fun onAppFocused() {
        if (!configured) return
        scheduler.runOnce(FOCUS_REFRESH) {
            val config = configuration
            if (isDue(LAST_AUTO_BACKUP, config.automaticBackupHours)) createAutomaticBackup()
            if (isDue(LAST_LIBRARY_UPDATE, config.libraryUpdateHours)) updateLibrary()
            if (isDue(LAST_DRIVE_SYNC, config.googleDriveSyncHours)) syncGoogleDrive()
            if (isDue(LAST_SYNCYOMI, config.syncYomiSyncHours)) syncSyncYomi()
        }
    }

    fun updateLibraryNow() = scheduler.runOnce(LIBRARY_UPDATE_MANUAL, ::updateLibrary)
    fun createBackupNow() = scheduler.runOnce(AUTO_BACKUP_MANUAL, ::createAutomaticBackup)
    fun syncGoogleDriveNow() = scheduler.runOnce(DRIVE_SYNC_MANUAL, ::syncGoogleDrive)
    fun syncYomiNow() = scheduler.runOnce(SYNCYOMI_MANUAL, ::syncSyncYomi)

    private fun configurePeriodic(name: String, intervalHours: Int, task: suspend () -> Unit) {
        if (intervalHours <= 0) {
            scheduler.cancelTask(name)
        } else {
            scheduler.schedulePeriodic(name, intervalHours.hours, runImmediately = false, task = task)
        }
    }

    private suspend fun createAutomaticBackup() = backupMutex.withLock {
        runStatus("Automatic backup", "Creating backup…") {
            if (!automaticBackupsDirectory.exists() && !automaticBackupsDirectory.mkdirs()) {
                error("Could not create automatic backup directory")
            }
            val backup = backupManager.exportToDir(automaticBackupsDirectory, "anikku_auto_${clockMillis()}")
                ?: error("Backup export failed")
            backup.setLastModified(clockMillis())
            automaticBackupsDirectory.listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith(MacOSBackupManager.BACKUP_EXTENSION) }
                .sortedByDescending(File::lastModified)
                .drop(AUTOMATIC_BACKUP_RETENTION)
                .forEach { stale ->
                    if (!stale.delete()) backgroundLogger.warn { "Could not remove stale backup ${stale.name}" }
                }
            markSuccess(LAST_AUTO_BACKUP)
            "Backup created: ${backup.name}"
        }
    }

    private suspend fun updateLibrary() {
        runStatus("Library update", "Checking library…") {
            val result = libraryUpdateService.updateAll()
            if (result.newlyDiscoveredEpisodes > 0) {
                notificationManager.showLibraryUpdate(result.newlyDiscoveredEpisodes)
            }
            markSuccess(LAST_LIBRARY_UPDATE)
            "Updated ${result.updated}/${result.scanned} titles" +
                if (result.failures.isNotEmpty()) " (${result.failures.size} failed)" else ""
        }
    }

    private suspend fun syncGoogleDrive() {
        if (!googleDriveService.isConnected) {
            val restored = googleDriveService.restoreSession()
            if (!restored.success) {
                backgroundLogger.debug { "Skipping Drive sync: ${restored.error}" }
                return
            }
        }
        runStatus("Google Drive sync", "Uploading backup…") {
            val result = googleDriveService.uploadBackup()
            if (!result.success) error(result.error ?: "Google Drive upload failed")
            markSuccess(LAST_DRIVE_SYNC)
            "Uploaded ${result.value?.name ?: "backup"}"
        }
    }

    private suspend fun syncSyncYomi() {
        val service = syncYomiService ?: return
        if (!service.isConfigured && !service.restoreConfiguration()) {
            backgroundLogger.debug { "Skipping SyncYomi sync: not configured" }
            return
        }
        runStatus("SyncYomi", "Synchronizing library…") {
            val result = service.sync()
            if (!result.success) error(result.error ?: "SyncYomi failed")
            markSuccess(LAST_SYNCYOMI)
            when (result.outcome) {
                app.anikku.macos.platform.sync.SyncYomiOutcome.MERGED -> "Merged SyncYomi data"
                app.anikku.macos.platform.sync.SyncYomiOutcome.UPLOADED -> "Uploaded SyncYomi data"
                app.anikku.macos.platform.sync.SyncYomiOutcome.NOT_MODIFIED -> "SyncYomi is up to date"
                else -> "SyncYomi complete"
            }
        }
    }

    private suspend fun runStatus(taskName: String, initialMessage: String, task: suspend () -> String) {
        _status.value = BackgroundJobStatus(runningTask = taskName, message = initialMessage)
        try {
            val message = task()
            _status.value = BackgroundJobStatus(message = message)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = error.message?.take(180) ?: error::class.simpleName.orEmpty()
            backgroundLogger.warn(error) { "$taskName failed" }
            _status.value = BackgroundJobStatus(message = "$taskName failed", lastError = message)
        }
    }

    private fun markSuccess(key: String) {
        preferenceStore.getLong(key, 0L).set(clockMillis())
    }

    private fun isDue(key: String, intervalHours: Int): Boolean {
        if (intervalHours <= 0) return false
        val lastRun = preferenceStore.getLong(key, 0L).get()
        return clockMillis() - lastRun >= intervalHours.hours.inWholeMilliseconds
    }

    companion object {
        private const val MAX_INTERVAL_HOURS = 24 * 30
        private const val AUTOMATIC_BACKUP_RETENTION = 5
        private const val AUTO_BACKUP_PERIODIC = "automatic-backup-periodic"
        private const val LIBRARY_UPDATE_PERIODIC = "library-update-periodic"
        private const val DRIVE_SYNC_PERIODIC = "google-drive-sync-periodic"
        private const val SYNCYOMI_PERIODIC = "syncyomi-periodic"
        private const val FOCUS_REFRESH = "background-focus-refresh"
        private const val AUTO_BACKUP_MANUAL = "automatic-backup-manual"
        private const val LIBRARY_UPDATE_MANUAL = "library-update-manual"
        private const val DRIVE_SYNC_MANUAL = "google-drive-sync-manual"
        private const val SYNCYOMI_MANUAL = "syncyomi-manual"
        private const val LAST_AUTO_BACKUP = "background_last_auto_backup"
        private const val LAST_LIBRARY_UPDATE = "background_last_library_update"
        private const val LAST_DRIVE_SYNC = "background_last_drive_sync"
        private const val LAST_SYNCYOMI = "background_last_syncyomi"
    }
}

val LocalBackgroundJobs = compositionLocalOf<MacOSBackgroundJobs?> { null }
