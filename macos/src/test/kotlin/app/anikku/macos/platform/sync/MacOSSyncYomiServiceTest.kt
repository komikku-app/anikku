package app.anikku.macos.platform.sync

import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.MacOSCustomAnimeRepository
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSSecretStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MacOSSyncYomiServiceTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `configuration keeps API token in Keychain and rejects plaintext remote host`(@TempDir tempDir: Path) {
        val fixture = Fixture(tempDir.resolve("state"))

        val rejected = fixture.service.configure("http://example.test", "secret-token")
        assertFalse(rejected.success)
        assertNull(fixture.secrets.values["api-token"])

        val configured = fixture.service.configure(server.url("/").toString(), " secret-token ")
        assertTrue(configured.success)
        assertEquals("secret-token", fixture.secrets.values["api-token"])
        assertTrue(fixture.service.restoreConfiguration())
        assertTrue(fixture.preferences.snapshotJson().values.none { it.toString().contains("secret-token") })
    }

    @Test
    fun `missing remote uploads local backup and later changes use saved ETag`(@TempDir tempDir: Path) = runBlocking {
        val fixture = Fixture(tempDir.resolve("state"))
        fixture.library.add(LibraryRepository.LibraryEntry(animeId = 1, title = "Local"))
        assertTrue(fixture.service.configure(server.url("/").toString(), "token").success)
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(200).addHeader("ETag", "\"one\""))

        val uploaded = fixture.service.sync()

        assertTrue(uploaded.success, uploaded.error)
        assertEquals(SyncYomiOutcome.UPLOADED, uploaded.outcome)
        val get = server.takeRequest()
        val put = server.takeRequest()
        assertEquals("GET", get.method)
        assertEquals("PUT", put.method)
        assertEquals("/api/sync/content", put.path)
        assertEquals("token", put.getHeader("X-API-Token"))
        assertTrue(put.body.readUtf8().contains("\"version\": 2"))

        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(MockResponse().setResponseCode(200).addHeader("ETag", "\"two\""))
        val conditionalUpload = fixture.service.sync()
        assertTrue(conditionalUpload.success)
        assertEquals(SyncYomiOutcome.UPLOADED, conditionalUpload.outcome)
        val conditionalGet = server.takeRequest()
        assertEquals("\"one\"", conditionalGet.getHeader("If-None-Match"))
        val secondPut = server.takeRequest()
        assertEquals("\"one\"", secondPut.getHeader("If-Match"))
    }

    @Test
    fun `remote backup merges into live state and conditional upload contains both sides`(@TempDir tempDir: Path) = runBlocking {
        val local = Fixture(tempDir.resolve("local"))
        local.library.add(LibraryRepository.LibraryEntry(animeId = 1, title = "Local"))
        assertTrue(local.service.configure(server.url("/").toString(), "token").success)

        val remote = Fixture(tempDir.resolve("remote"))
        remote.library.add(LibraryRepository.LibraryEntry(animeId = 2, title = "Remote"))
        val remoteFile = tempDir.resolve("remote${MacOSBackupManager.BACKUP_EXTENSION}").toFile()
        assertTrue(remote.backupManager.exportTo(remoteFile))

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("ETag", "\"remote\"")
                .setBody(remoteFile.readText()),
        )
        server.enqueue(MockResponse().setResponseCode(200).addHeader("ETag", "\"merged\""))

        val result = local.service.sync()

        assertTrue(result.success, result.error)
        assertEquals(SyncYomiOutcome.MERGED, result.outcome)
        assertEquals("Remote", local.library.get(2)?.title)
        server.takeRequest()
        val put = server.takeRequest()
        assertEquals("\"remote\"", put.getHeader("If-Match"))

        val uploadedFile = tempDir.resolve("uploaded${MacOSBackupManager.BACKUP_EXTENSION}").toFile()
        uploadedFile.writeText(put.body.readUtf8())
        val verifier = Fixture(tempDir.resolve("verifier"))
        val imported = verifier.backupManager.importFrom(uploadedFile)
        assertTrue(imported.success, imported.error)
        assertEquals("Local", verifier.library.get(1)?.title)
        assertEquals("Remote", verifier.library.get(2)?.title)
    }

    private class Fixture(root: Path) {
        private val dataDir = root.resolve("data").toFile().apply { mkdirs() }
        private val cacheDir = root.resolve("cache").toFile().apply { mkdirs() }
        val preferences = MacOSPreferenceStore(dataDir.resolve("preferences.json"))
        val library = LibraryRepository(dataDir)
        private val history = HistoryRepository(dataDir)
        private val downloads = DownloadRepository(dataDir)
        private val customAnime = MacOSCustomAnimeRepository(dataDir)
        val secrets = FakeSecretStore()
        val backupManager = MacOSBackupManager(library, history, downloads, preferences, customAnime)
        val service = MacOSSyncYomiService(
            httpClient = OkHttpClient(),
            backupManager = backupManager,
            cacheDirectory = cacheDir,
            secretStore = secrets,
            preferenceStore = preferences,
        )
    }

    private class FakeSecretStore : MacOSSecretStore {
        override val isAvailable = true
        override var lastError: String? = null
        val values = mutableMapOf<String, String>()
        override fun store(key: String, value: String): Boolean {
            values[key] = value
            return true
        }
        override fun retrieve(key: String): String? = values[key]
        override fun delete(key: String): Boolean {
            values.remove(key)
            return true
        }
    }
}
