@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package app.anikku.macos.platform.backup

import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.MacOSCustomAnimeRepository
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

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
        source.preferences.getString("proxy_password", "").set("must-not-leave-keychain-bound-state")
        source.preferences.getString("syncyomi_etag", "").set("machine-specific")

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
        assertFalse(encoded.contains("must-not-leave-keychain-bound-state"))
        assertFalse(encoded.contains("machine-specific"))

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
        assertEquals("", restored.preferences.getString("proxy_password", "").get())

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

    @Test
    fun `restore ignores credential shaped preference keys`(@TempDir tempDir: Path) {
        val fixture = Fixture(tempDir.resolve("state"))
        fixture.preferences.getString("proxy_password", "").set("existing-secret")
        val backup = tempDir.resolve("untrusted${MacOSBackupManager.BACKUP_EXTENSION}").toFile().apply {
            writeText(
                """
                {
                  "version": 2,
                  "preferenceValues": {
                    "proxy_password": "injected-secret",
                    "tracker_token_anilist": "injected-token",
                    "safe_setting": true
                  }
                }
                """.trimIndent(),
            )
        }

        val result = fixture.manager.importFrom(backup)

        assertTrue(result.success, result.error)
        assertEquals("existing-secret", fixture.preferences.getString("proxy_password", "").get())
        assertEquals("", fixture.preferences.getString("tracker_token_anilist", "").get())
        assertTrue(fixture.preferences.getBoolean("safe_setting", false).get())
    }

    @Test
    fun `Android tachibk imports library categories history progress and custom metadata`(@TempDir tempDir: Path) {
        val animeUrl = "/anime/android"
        val episodeUrl = "/episode/2"
        val backup = AndroidFixtureBackup(
            anime = listOf(
                AndroidFixtureAnime(
                    source = 42,
                    url = animeUrl,
                    title = "Original title",
                    author = "Original author",
                    genre = listOf("Action"),
                    status = 1,
                    dateAdded = 1234,
                    episodes = listOf(
                        AndroidFixtureEpisode("/episode/1", "Episode 1", seen = true, episodeNumber = 1f),
                        AndroidFixtureEpisode(
                            episodeUrl,
                            "Episode 2",
                            seen = true,
                            lastSecondSeen = 91,
                            totalSeconds = 120,
                            episodeNumber = 2f,
                        ),
                    ),
                    categories = listOf(7),
                    history = listOf(AndroidFixtureHistory(episodeUrl, lastRead = 5678, readDuration = 88)),
                    lastModifiedAt = 4321,
                    customTitle = "Custom Android title",
                    customGenre = listOf("Drama"),
                    customStatus = 2,
                ),
            ),
            categories = listOf(AndroidFixtureCategory("Imported", order = 7, hidden = true)),
        )
        val backupFile = tempDir.resolve("android.tachibk").toFile()
        GZIPOutputStream(backupFile.outputStream()).use { gzip ->
            gzip.write(ProtoBuf.encodeToByteArray(AndroidFixtureBackup.serializer(), backup))
        }

        val fixture = Fixture(tempDir.resolve("restored-android"))
        val result = fixture.manager.importFrom(backupFile)

        assertTrue(result.success, result.error)
        assertEquals(1, result.libraryCount)
        assertEquals(1, result.historyCount)
        assertEquals(1, result.customAnimeCount)
        val animeId = animeUrl.hashCode().toLong()
        val restored = fixture.library.get(animeId)!!
        assertEquals("Custom Android title", restored.title)
        assertEquals(42, restored.sourceId)
        assertEquals(91, restored.lastSecondSeen)
        assertEquals(120, restored.totalSeconds)
        assertEquals(2.0, restored.latestEpisodeNumber)
        assertEquals("Imported", fixture.library.getCategory(restored.categoryId)?.name)
        assertTrue(fixture.library.getCategory(restored.categoryId)?.hidden == true)
        val restoredHistory = fixture.history.getLatestForAnime(animeId)!!
        assertEquals(episodeUrl, restoredHistory.episodeUrl)
        assertEquals(91, restoredHistory.lastSecondSeen)
        assertEquals("Custom Android title", fixture.customAnime.get(animeId)?.title)
        assertEquals(listOf("Drama"), fixture.customAnime.get(animeId)?.genre)
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

    @Serializable
    private data class AndroidFixtureBackup(
        @ProtoNumber(3) val anime: List<AndroidFixtureAnime>,
        @ProtoNumber(4) val categories: List<AndroidFixtureCategory>,
    )

    @Serializable
    private data class AndroidFixtureAnime(
        @ProtoNumber(1) val source: Long,
        @ProtoNumber(2) val url: String,
        @ProtoNumber(3) val title: String,
        @ProtoNumber(5) val author: String? = null,
        @ProtoNumber(7) val genre: List<String> = emptyList(),
        @ProtoNumber(8) val status: Int = 0,
        @ProtoNumber(13) val dateAdded: Long = 0,
        @ProtoNumber(16) val episodes: List<AndroidFixtureEpisode> = emptyList(),
        @ProtoNumber(17) val categories: List<Long> = emptyList(),
        @ProtoNumber(100) val favorite: Boolean = true,
        @ProtoNumber(104) val history: List<AndroidFixtureHistory> = emptyList(),
        @ProtoNumber(106) val lastModifiedAt: Long = 0,
        @ProtoNumber(602) val customStatus: Int = 0,
        @ProtoNumber(800) val customTitle: String? = null,
        @ProtoNumber(805) val customGenre: List<String>? = null,
    )

    @Serializable
    private data class AndroidFixtureEpisode(
        @ProtoNumber(1) val url: String,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(4) val seen: Boolean = false,
        @ProtoNumber(6) val lastSecondSeen: Long = 0,
        @ProtoNumber(16) val totalSeconds: Long = 0,
        @ProtoNumber(9) val episodeNumber: Float = 0f,
    )

    @Serializable
    private data class AndroidFixtureHistory(
        @ProtoNumber(1) val url: String,
        @ProtoNumber(2) val lastRead: Long,
        @ProtoNumber(3) val readDuration: Long = 0,
    )

    @Serializable
    private data class AndroidFixtureCategory(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val order: Long,
        @ProtoNumber(900) val hidden: Boolean = false,
    )
}
