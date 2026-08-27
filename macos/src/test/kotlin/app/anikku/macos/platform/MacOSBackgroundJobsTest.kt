package app.anikku.macos.platform

import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.library.MacOSLibraryUpdateService
import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSSecretStore
import app.anikku.macos.platform.sync.GoogleDriveRestClient
import app.anikku.macos.platform.sync.MacOSGoogleDriveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MacOSBackgroundJobsTest {
    private lateinit var scope: CoroutineScope
    private lateinit var scheduler: BackgroundTaskScheduler

    @BeforeEach
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scheduler = BackgroundTaskScheduler(scope)
    }

    @AfterEach
    fun tearDown() {
        scheduler.cancelAll()
        scope.cancel()
    }

    @Test
    fun `configure creates and removes all periodic jobs`(@TempDir tempDir: Path) {
        val fixture = fixture(tempDir)

        fixture.jobs.configure(BackgroundJobConfiguration(12, 4, 6, 8))

        assertTrue(scheduler.isRunning("automatic-backup-periodic"))
        assertTrue(scheduler.isRunning("library-update-periodic"))
        assertTrue(scheduler.isRunning("google-drive-sync-periodic"))
        assertTrue(scheduler.isRunning("syncyomi-periodic"))

        fixture.jobs.configure(BackgroundJobConfiguration(0, 0, 0, 0))
        assertFalse(scheduler.isRunning("automatic-backup-periodic"))
        assertFalse(scheduler.isRunning("library-update-periodic"))
        assertFalse(scheduler.isRunning("google-drive-sync-periodic"))
        assertFalse(scheduler.isRunning("syncyomi-periodic"))
    }

    @Test
    fun `focus runs only due automatic backup and records success`(@TempDir tempDir: Path) = runBlocking {
        val fixture = fixture(tempDir)
        fixture.jobs.configure(BackgroundJobConfiguration(automaticBackupHours = 1))

        fixture.jobs.onAppFocused()
        awaitTask("background-focus-refresh")
        assertEquals(1, fixture.automaticDir.listFiles().orEmpty().size)
        assertEquals(fixture.now, fixture.preferences.getLong("background_last_auto_backup", 0).get())

        fixture.jobs.onAppFocused()
        awaitTask("background-focus-refresh")
        assertEquals(1, fixture.automaticDir.listFiles().orEmpty().size, "Rate limit should suppress a second focus backup")
    }

    @Test
    fun `automatic backup retention keeps newest five files`(@TempDir tempDir: Path) = runBlocking {
        val fixture = fixture(tempDir)

        repeat(7) {
            fixture.now += 1_000
            fixture.jobs.createBackupNow().join()
        }

        val backups = fixture.automaticDir.listFiles().orEmpty()
            .filter { it.name.endsWith(MacOSBackupManager.BACKUP_EXTENSION) }
        assertEquals(5, backups.size)
        assertFalse(backups.any { it.name.contains("1700000001000") })
        assertTrue(fixture.jobs.status.value.lastError == null)
    }

    private suspend fun awaitTask(name: String) {
        repeat(200) {
            if (!scheduler.isRunning(name)) return
            delay(10)
        }
        error("Task $name did not finish")
    }

    private fun fixture(tempDir: Path): Fixture {
        val data = tempDir.resolve("data").toFile().apply { mkdirs() }
        val automatic = tempDir.resolve("automatic").toFile()
        val library = LibraryRepository(data)
        val history = HistoryRepository(data)
        val downloads = DownloadRepository(data)
        val preferences = MacOSPreferenceStore(File(data, "preferences.json"))
        val backup = MacOSBackupManager(library, history, downloads, preferences)
        val google = MacOSGoogleDriveService(
            driveClient = GoogleDriveRestClient(OkHttpClient()),
            backupManager = backup,
            backupsDirectory = tempDir.resolve("backups").toFile(),
            secretStore = EmptySecretStore,
        )
        var now = 1_700_000_000_000L
        val jobs = MacOSBackgroundJobs(
            scheduler = scheduler,
            backupManager = backup,
            automaticBackupsDirectory = automatic,
            libraryUpdateService = MacOSLibraryUpdateService(library, history) { null },
            googleDriveService = google,
            notificationManager = MacOSNotificationManager(),
            preferenceStore = preferences,
            clockMillis = { now },
        )
        return Fixture(jobs, automatic, preferences, now).also { fixture ->
            fixture.setNow = { value -> now = value }
            fixture.readNow = { now }
        }
    }

    private class Fixture(
        val jobs: MacOSBackgroundJobs,
        val automaticDir: File,
        val preferences: MacOSPreferenceStore,
        initialNow: Long,
    ) {
        var setNow: (Long) -> Unit = {}
        var readNow: () -> Long = { initialNow }
        var now: Long
            get() = readNow()
            set(value) = setNow(value)
    }

    private object EmptySecretStore : MacOSSecretStore {
        override val isAvailable: Boolean = true
        override val lastError: String? = null
        override fun store(key: String, value: String): Boolean = true
        override fun retrieve(key: String): String? = null
        override fun delete(key: String): Boolean = true
    }
}
