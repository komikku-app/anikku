package app.anikku.macos.platform.sync

import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.security.MacOSSecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MacOSGoogleDriveServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var restClient: GoogleDriveRestClient
    private val httpClient = OkHttpClient()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        restClient = GoogleDriveRestClient(
            client = httpClient,
            driveApiBase = "$base/drive/v3",
            uploadApiBase = "$base/upload/drive/v3",
            oauthTokenUrl = "$base/oauth2/token",
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `desktop OAuth uses loopback state PKCE and stores session in Keychain`(@TempDir tempDir: Path) = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access_token":"access-1","refresh_token":"refresh-1","expires_in":3600}""",
            ),
        )
        val launchedUrls = LinkedBlockingQueue<String>()
        val secrets = FakeSecretStore()
        val service = service(tempDir, secrets, launcher = { url -> launchedUrls.put(url); true })

        val connection = async(Dispatchers.Default) {
            service.connect("1234567890-desktop.apps.googleusercontent.com", timeoutSeconds = 15)
        }
        val authorizationUrl = launchedUrls.poll(5, TimeUnit.SECONDS)
        assertNotNull(authorizationUrl)
        val parsed = authorizationUrl!!.toHttpUrl()
        val redirectUri = parsed.queryParameter("redirect_uri")!!
        val state = parsed.queryParameter("state")!!
        assertEquals("S256", parsed.queryParameter("code_challenge_method"))
        assertTrue(parsed.queryParameter("code_challenge")!!.length >= 43)
        assertEquals("https://www.googleapis.com/auth/drive.file", parsed.queryParameter("scope"))
        assertEquals("offline", parsed.queryParameter("access_type"))
        assertTrue(redirectUri.startsWith("http://127.0.0.1:"))

        val callback = redirectUri.toHttpUrl().newBuilder()
            .addQueryParameter("code", "authorization-code")
            .addQueryParameter("state", state)
            .build()
        httpClient.newCall(Request.Builder().url(callback).build()).execute().use { assertTrue(it.isSuccessful) }

        assertTrue(connection.await().success)
        assertEquals(GoogleDriveConnectionState.CONNECTED, service.connectionState.value)
        assertTrue(secrets.values.values.single().contains("refresh-1"))
        val tokenRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        val tokenBody = tokenRequest.body.readUtf8()
        assertTrue(tokenBody.contains("code_verifier="))
        assertFalse(tokenBody.contains("client_secret"))
    }

    @Test
    fun `OAuth rejects mismatched state without exchanging a token`(@TempDir tempDir: Path) = runBlocking {
        val launchedUrls = LinkedBlockingQueue<String>()
        val service = service(
            tempDir,
            FakeSecretStore(),
            launcher = { url -> launchedUrls.put(url); true },
        )

        val connection = async(Dispatchers.Default) {
            service.connect("1234567890-desktop.apps.googleusercontent.com", timeoutSeconds = 15)
        }
        val redirectUri = launchedUrls.poll(5, TimeUnit.SECONDS)!!.toHttpUrl().queryParameter("redirect_uri")!!
        val callback = redirectUri.toHttpUrl().newBuilder()
            .addQueryParameter("code", "stolen-code")
            .addQueryParameter("state", "wrong-state")
            .build()
        httpClient.newCall(Request.Builder().url(callback).build()).execute().use { assertTrue(it.isSuccessful) }

        val result = connection.await()
        assertFalse(result.success)
        assertTrue(result.error!!.contains("state"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `expired Keychain session refreshes and remains connected`(@TempDir tempDir: Path) = runBlocking {
        val now = 1_700_000_000_000L
        val secrets = FakeSecretStore().apply {
            values["google-drive-session-v1"] = Json.encodeToString(
                MacOSGoogleDriveService.StoredGoogleDriveSession(
                    clientId = "1234567890-desktop.apps.googleusercontent.com",
                    accessToken = "expired-access",
                    refreshToken = "refresh-token",
                    expiresAtMillis = now - 1,
                ),
            )
        }
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access_token":"fresh-access","expires_in":3600}""",
            ),
        )
        val service = service(tempDir, secrets, clockMillis = { now })

        assertTrue(service.restoreSession().success)
        assertTrue(service.isConnected)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("refresh_token=refresh-token"))
        assertFalse(body.contains("client_secret"))
        assertTrue(secrets.values.values.single().contains("fresh-access"))
    }

    @Test
    fun `backup upload exports locally and streams into Drive folder`(@TempDir tempDir: Path) = runBlocking {
        val secrets = validSessionStore()
        val service = service(tempDir, secrets)
        assertTrue(service.restoreSession().success)
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"files":[{"id":"folder-id","name":"Anikku Backups","mimeType":"application/vnd.google-apps.folder"}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).addHeader("Location", server.url("/upload-session")),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"cloud-backup-id"}"""))

        val result = service.uploadBackup()

        assertTrue(result.success, result.error)
        assertEquals("cloud-backup-id", result.value?.id)
        assertTrue(tempDir.resolve("backups").toFile().listFiles().orEmpty().any {
            it.name.endsWith(MacOSBackupManager.BACKUP_EXTENSION)
        })
        server.takeRequest()
        server.takeRequest()
        val upload = server.takeRequest()
        assertTrue(upload.body.size > 0)
    }

    @Test
    fun `cloud download sanitizes remote filename and stays in backup directory`(@TempDir tempDir: Path) = runBlocking {
        val service = service(tempDir, validSessionStore())
        assertTrue(service.restoreSession().success)
        server.enqueue(MockResponse().setResponseCode(200).setBody("backup-data"))

        val result = service.downloadBackup(
            GoogleDriveFile(
                id = "safe-file-id",
                name = "../../outside backup",
                mimeType = "application/json",
            ),
        )

        assertTrue(result.success, result.error)
        val downloaded = result.value!!.canonicalFile
        assertEquals(tempDir.resolve("backups").toFile().canonicalFile, downloaded.parentFile)
        assertEquals("backup-data", downloaded.readText())
        assertFalse(tempDir.resolve("outside backup${MacOSBackupManager.BACKUP_EXTENSION}").toFile().exists())
    }

    private fun service(
        tempDir: Path,
        secretStore: FakeSecretStore,
        launcher: (String) -> Boolean = { true },
        clockMillis: () -> Long = System::currentTimeMillis,
    ): MacOSGoogleDriveService {
        val dataDir = tempDir.resolve("data").toFile().apply { mkdirs() }
        val backupDir = tempDir.resolve("backups").toFile().apply { mkdirs() }
        val manager = MacOSBackupManager(
            libraryRepository = LibraryRepository(dataDir),
            historyRepository = HistoryRepository(dataDir),
            downloadRepository = DownloadRepository(dataDir),
        )
        return MacOSGoogleDriveService(
            driveClient = restClient,
            backupManager = manager,
            backupsDirectory = backupDir,
            secretStore = secretStore,
            browserLauncher = launcher,
            clockMillis = clockMillis,
        )
    }

    private fun validSessionStore(): FakeSecretStore = FakeSecretStore().apply {
        values["google-drive-session-v1"] = Json.encodeToString(
            MacOSGoogleDriveService.StoredGoogleDriveSession(
                clientId = "1234567890-desktop.apps.googleusercontent.com",
                accessToken = "valid-access",
                refreshToken = "valid-refresh",
                expiresAtMillis = System.currentTimeMillis() + 3_600_000,
            ),
        )
    }

    private class FakeSecretStore : MacOSSecretStore {
        val values = mutableMapOf<String, String>()
        override val isAvailable: Boolean = true
        override val lastError: String? = null
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
