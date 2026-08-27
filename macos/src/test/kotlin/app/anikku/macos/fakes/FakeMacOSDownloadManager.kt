package app.anikku.macos.fakes

import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.download.MacOSDownloadManager
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.network.MacOSNetworkHelper
import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.storage.MacOSStorageProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * A fake [MacOSDownloadManager] for Compose UI tests.
 *
 * Provides hardcoded [DownloadRepository.DownloadEntry] entries via a
 * custom [downloads] StateFlow and no-op implementations of all action
 * methods (pause, resume, cancel, retry, cancelAll).
 *
 * Constructor dependencies use temporary directories so the fake can
 * be instantiated without any real app state.
 */
class FakeMacOSDownloadManager(
    entries: List<DownloadRepository.DownloadEntry>,
) : MacOSDownloadManager(
    repository = DownloadRepository(createTempDir("anikku-fake-repo")),
    extensionManager = MacOSExtensionManager(
        storageProvider = createStorageProvider(createTempDir("anikku-fake-ext")),
        networkHelper = MacOSNetworkHelper(createStorageProvider(createTempDir("anikku-fake-net"))),
    ),
    storageProvider = createStorageProvider(createTempDir("anikku-fake-storage")),
    notifier = MacOSNotificationManager(),
) {
    private val _downloads = MutableStateFlow(entries)
    override val downloads: StateFlow<List<DownloadRepository.DownloadEntry>> = _downloads.asStateFlow()

    override fun pause(id: Long) {
        // No-op for test
    }

    override fun resume(id: Long) {
        // No-op for test
    }

    override fun cancel(id: Long) {
        // No-op for test
    }

    override fun retry(id: Long) {
        // No-op for test
    }

    override fun cancelAll() {
        // No-op for test
    }

    companion object {
        private fun createTempDir(prefix: String): File {
            val dir = kotlin.io.createTempDir(prefix)
            File(dir, "data").mkdirs()
            File(dir, "downloads").mkdirs()
            File(dir, "extensions").mkdirs()
            File(dir, "backups").mkdirs()
            File(dir, "covers").mkdirs()
            File(dir, "logs").mkdirs()
            return dir
        }

        private fun createStorageProvider(dir: File) = object : MacOSStorageProvider() {
            override fun directory(): File = dir
        }

        /** Create hardcoded test download entries for UI tests. */
        fun createTestEntries(): List<DownloadRepository.DownloadEntry> = listOf(
            DownloadRepository.DownloadEntry(
                id = 1L,
                animeId = 10L,
                sourceId = 1L,
                animeTitle = "Attack on Titan",
                episodeName = "Episode 3 - A Dim Light Amid Despair",
                episodeNumber = 3.0,
                status = DownloadRepository.DownloadStatus.DOWNLOADING,
                progress = 0.45f,
            ),
            DownloadRepository.DownloadEntry(
                id = 2L,
                animeId = 20L,
                sourceId = 1L,
                animeTitle = "Jujutsu Kaisen",
                episodeName = "Episode 1092 - A Night to Remember",
                episodeNumber = 1092.0,
                status = DownloadRepository.DownloadStatus.DOWNLOADING,
                progress = 0.72f,
            ),
            DownloadRepository.DownloadEntry(
                id = 3L,
                animeId = 30L,
                sourceId = 1L,
                animeTitle = "One Piece",
                episodeName = "Episode 1024",
                episodeNumber = 1024.0,
                status = DownloadRepository.DownloadStatus.COMPLETED,
                progress = 1f,
            ),
            DownloadRepository.DownloadEntry(
                id = 4L,
                animeId = 40L,
                sourceId = 1L,
                animeTitle = "Demon Slayer",
                episodeName = "Episode 26",
                episodeNumber = 26.0,
                status = DownloadRepository.DownloadStatus.COMPLETED,
                progress = 1f,
            ),
            DownloadRepository.DownloadEntry(
                id = 5L,
                animeId = 50L,
                sourceId = 1L,
                animeTitle = "Spy x Family",
                episodeName = "Episode 12",
                episodeNumber = 12.0,
                status = DownloadRepository.DownloadStatus.PAUSED,
                progress = 0.3f,
            ),
        )
    }
}
