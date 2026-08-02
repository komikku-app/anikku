package app.anikku.macos.platform.backup

import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.MacOSCustomAnimeRepository
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MacOSBackupManagerRoundTripTest {

    @Test
    fun `version two backup round trips typed state and complete metadata`(@TempDir tempDir: Path) {
        val source = Fixture(tempDir.resolve("source"))
        source.preferences.getBoolean("boolean", false).set(true)
        source.preferences.getInt("integer", 0).set(17)
        source.preferences.getLong("long", 0).set(9_000_000_000L)
        source.preferences.getFloat("float", 0f).set(1.25f)
        source.preferences.getString("string", "").set("value")
        source.preferences.getStringSet("set", emptySet()).set(setOf("en", "ja"))

        val category = source.library.addCategory("Favorites")
        source.library.add(
            LibraryRepository.LibraryEntry(
                animeId = 10,
                title = "Backup anime",
                sourceId = 20,
                url = "/anime/10",
                categoryId = category.id,
                latestEpisodeNumber = 12.5,
                unseenEpisodeCount = 3,
            ),
        )
        source.history.add(
            HistoryRepository.HistoryEntry(
                animeId = 10,
                episodeId = 11,
                animeTitle = "Backup anime",
                episodeName = "Episode 1",
                sourceId = 20,
                animeUrl = "/anime/10",
                episodeUrl = "/episode/11",
                lastSecondSeen = 90,
                totalSeconds = 120,
            ),
        )
        source.downloads.replaceAll(
            listOf(
                DownloadRepository.DownloadEntry(
                    id = 44,
                    animeId = 10,
                    sourceId = 20,
                    animeTitle = "Backup anime",
                    episodeName = "Episode 1",
                    episodeNumber = 1.0,
                    episodeUrl = "/episode/11",
                    videoUrl = "https://example.test/video.m3u8",
                    fileName = "episode.mp4",
                    filePath = "/tmp/episode.mp4",
                    status = DownloadRepository.DownloadStatus.PAUSED,
                    progress = 0.5f,
                    totalBytes = 1_000,
                    downloadedBytes = 500,
                    createdAt = 123,
                ),
            ),
        )
        source.customAnime.set(10, title = "Custom title", genre = listOf("Drama"), status = 2)

        val backupFile = tempDir.resolve("round-trip${MacOSBackupManager.BACKUP_EXTENSION}").toFile()
        assertTrue(source.manager.exportTo(backupFile))
        val encoded = backupFile.readText()
        assertTrue(encoded.contains("\"version\": 2"))
        assertTrue(encoded.contains("\"preferenceValues\""))
        assertFalse(encoded.contains("\"preferences\":"), "Legacy string-only field must not be emitted")

        val restored = Fixture(tempDir.resolve("restored"))
        restored.preferences.getString("newer-setting", "").set("preserved")
        val result = restored.manager.importFrom(backupFile)

        assertTrue(result.success, result.error)
        assertEquals(1, result.libraryCount)
        assertEquals(1, result.historyCount)
        assertEquals(1, result.downloadsCount)
        assertEquals(1, result.customAnimeCount)
        assertEquals("Favorites", restored.library.getCategory(category.id)?.name)
        assertEquals(category.id, restored.library.get(10)?.categoryId)
        assertEquals("/anime/10", restored.history.getLatestForAnime(10)?.animeUrl)
        val download = restored.downloads.get(44)!!
        assertEquals("https://example.test/video.m3u8", download.videoUrl)
        assertEquals(DownloadRepository.DownloadStatus.PAUSED, download.status)
        assertEquals(500, download.downloadedBytes)
        assertEquals("Custom title", restored.customAnime.get(10)?.title)
        assertEquals(listOf("Drama"), restored.customAnime.get(10)?.genre)
        assertTrue(restored.preferences.getBoolean("boolean", false).get())
        assertEquals(17, restored.preferences.getInt("integer", 0).get())
        assertEquals(9_000_000_000L, restored.preferences.getLong("long", 0).get())
        assertEquals(1.25f, restored.preferences.getFloat("float", 0f).get())
        assertEquals("value", restored.preferences.getString("string", "").get())
        assertEquals(setOf("en", "ja"), restored.preferences.getStringSet("set", emptySet()).get())
        assertEquals("preserved", restored.preferences.getString("newer-setting", "").get())

        val restarted = Fixture(tempDir.resolve("restored"))
        assertEquals("Backup anime", restarted.library.get(10)?.title)
        assertEquals("Custom title", restarted.customAnime.get(10)?.title)
        assertEquals(DownloadRepository.DownloadStatus.PAUSED, restarted.downloads.get(44)?.status)
        assertTrue(restarted.preferences.getBoolean("boolean", false).get())
    }

    @Test
    fun `invalid backup record is rejected before any repository changes`(@TempDir tempDir: Path) {
        val fixture = Fixture(tempDir.resolve("state"))
        fixture.library.add(LibraryRepository.LibraryEntry(animeId = 1, title = "Existing"))
        val backup = tempDir.resolve("invalid${MacOSBackupManager.BACKUP_EXTENSION}").toFile().apply {
            writeText(
                """
                {
                  "version": 2,
                  "library": [{"animeId": 2, "title": "Must not restore"}],
                  "downloads": [{"id": 1, "animeId": 2, "status": "NOT_A_STATUS"}]
                }
                """.trimIndent(),
            )
        }

        val result = fixture.manager.importFrom(backup)

        assertFalse(result.success)
        assertEquals("Existing", fixture.library.get(1)?.title)
        assertEquals(null, fixture.library.get(2))
    }

    private class Fixture(root: Path) {
        private val dataDir = root.toFile().apply { mkdirs() }
        val preferences = MacOSPreferenceStore(dataDir.resolve("preferences.json"))
        val library = LibraryRepository(dataDir)
        val history = HistoryRepository(dataDir)
        val downloads = DownloadRepository(dataDir)
        val customAnime = MacOSCustomAnimeRepository(dataDir)
        val manager = MacOSBackupManager(
            libraryRepository = library,
            historyRepository = history,
            downloadRepository = downloads,
            preferenceStore = preferences,
            customAnimeRepository = customAnime,
        )
    }
}
