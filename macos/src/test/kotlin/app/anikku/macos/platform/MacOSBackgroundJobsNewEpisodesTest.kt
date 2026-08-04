package app.anikku.macos.platform

import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.library.MacOSLibraryUpdateService
import app.anikku.macos.platform.library.NewEpisodeRepository
import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSSecretStore
import app.anikku.macos.platform.sync.GoogleDriveRestClient
import app.anikku.macos.platform.sync.MacOSGoogleDriveService
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Verifies the New Episodes feed path in the background library update:
 * baseline-gated discoveries are persisted to [NewEpisodeRepository] and the
 * feed is left untouched when nothing genuinely new appears.
 */
class MacOSBackgroundJobsNewEpisodesTest {

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
    fun `library update persists discovered episodes into the feed`(@TempDir tempDir: Path) = runBlocking {
        val fixture = fixture(tempDir, episodes = 5)
        fixture.library.add(
            LibraryRepository.LibraryEntry(
                animeId = 1,
                title = "Ongoing",
                sourceId = 99,
                url = "/anime/1",
                latestEpisodeNumber = 3.0, // baseline: episodes 1-3 already known
            ),
        )

        fixture.jobs.configure(BackgroundJobConfiguration(libraryUpdateHours = 1))
        fixture.jobs.onAppFocused()
        awaitTask("background-focus-refresh")

        assertEquals(2, fixture.feed.count(), "two episodes beyond the baseline should land in the feed")
        val rows = fixture.feed.getAll()
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.animeId == 1L })
        // Latest row carries the newest episode number.
        assertEquals(5.0, rows.maxOf { it.episodeNumber })
    }

    @Test
    fun `second run does not re-add already-discovered episodes`(@TempDir tempDir: Path) = runBlocking {
        val fixture = fixture(tempDir, episodes = 5)
        fixture.library.add(
            LibraryRepository.LibraryEntry(
                animeId = 1,
                title = "Ongoing",
                sourceId = 99,
                url = "/anime/1",
                latestEpisodeNumber = 3.0,
            ),
        )
        fixture.jobs.configure(BackgroundJobConfiguration(libraryUpdateHours = 1))
        fixture.jobs.onAppFocused()
        awaitTask("background-focus-refresh")
        val afterFirst = fixture.feed.count()
        assertEquals(2, afterFirst)

        // The service persists latestEpisodeNumber=5, so a second scan sees no
        // gain — and even if it did, feed rows dedupe by (animeId, episode).
        fixture.jobs.onAppFocused()
        awaitTask("background-focus-refresh")
        assertEquals(2, fixture.feed.count())
    }

    @Test
    fun `first-ever scan establishes a baseline without flooding the feed`(@TempDir tempDir: Path) = runBlocking {
        val fixture = fixture(tempDir, episodes = 24)
        fixture.library.add(
            LibraryRepository.LibraryEntry(animeId = 2, title = "New import", sourceId = 99, url = "/anime/2"),
        )

        fixture.jobs.configure(BackgroundJobConfiguration(libraryUpdateHours = 1))
        fixture.jobs.onAppFocused()
        awaitTask("background-focus-refresh")

        assertEquals(0, fixture.feed.count(), "first scan must not report the whole season as new")
        // Baseline is now recorded, so a later real gain will be detected.
        assertEquals(24.0, fixture.library.get(2)!!.latestEpisodeNumber)
    }

    private suspend fun awaitTask(name: String) {
        repeat(200) {
            if (!scheduler.isRunning(name)) return
            delay(10)
        }
        error("Task $name did not finish")
    }

    private fun fixture(tempDir: Path, episodes: Int): Fixture {
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
        val feed = NewEpisodeRepository(data)
        val source = EpisodeCountSource(episodes)
        val jobs = MacOSBackgroundJobs(
            scheduler = scheduler,
            backupManager = backup,
            automaticBackupsDirectory = automatic,
            libraryUpdateService = MacOSLibraryUpdateService(library, history) { if (it == 99L) source else null },
            googleDriveService = google,
            notificationManager = MacOSNotificationManager(),
            preferenceStore = preferences,
            newEpisodeRepository = feed,
            newEpisodeNotificationsEnabled = { true },
        )
        return Fixture(jobs, library, feed)
    }

    private class Fixture(
        val jobs: MacOSBackgroundJobs,
        val library: LibraryRepository,
        val feed: NewEpisodeRepository,
    )

    private object EmptySecretStore : MacOSSecretStore {
        override val isAvailable: Boolean = true
        override val lastError: String? = null
        override fun store(key: String, value: String): Boolean = true
        override fun retrieve(key: String): String? = null
        override fun delete(key: String): Boolean = true
    }

    private class EpisodeCountSource(private val episodes: Int) : AnimeSource {
        override val id: Long = 99
        override val name: String = "Episode Count Source"

        override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime.apply {
            title = "Updated title"
        }

        override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = (1..episodes).map { number ->
            SEpisode.create().apply {
                url = "/episode/$number"
                name = "Episode $number"
                episode_number = number.toFloat()
            }
        }

        override suspend fun getVideoList(episode: SEpisode): List<Video> = emptyList()
    }
}
