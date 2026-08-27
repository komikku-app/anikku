package app.anikku.macos.platform.sync

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class GoogleDriveRestClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: GoogleDriveRestClient

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        client = GoogleDriveRestClient(
            client = okhttp3.OkHttpClient.Builder().build(),
            driveApiBase = "$baseUrl/drive/v3",
            uploadApiBase = "$baseUrl/upload/drive/v3",
            oauthTokenUrl = "$baseUrl/oauth2/token",
        )
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `is not authenticated initially`() {
        assertFalse(client.isAuthenticated)
    }

    @Test
    fun `authenticate sets the token`() {
        client.authenticate("test_token")
        assertTrue(client.isAuthenticated)
    }

    @Test
    fun `logout clears authentication`() {
        client.authenticate("test_token")
        assertTrue(client.isAuthenticated)
        client.logout()
        assertFalse(client.isAuthenticated)
    }

    @Test
    fun `exchange code returns token on success`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{
                    "access_token": "ya29.test_access",
                    "refresh_token": "1//test_refresh",
                    "expires_in": 3600,
                    "scope": "https://www.googleapis.com/auth/drive.file"
                }"""),
        )

        val token = client.exchangeCode(
            code = "4/0_test_code",
            clientId = "test.apps.googleusercontent.com",
            clientSecret = "GOCSPX-test_secret",
            redirectUri = "http://127.0.0.1:8080/callback",
        )

        assertNotNull(token)
        assertEquals("ya29.test_access", token?.accessToken)
        assertEquals("1//test_refresh", token?.refreshToken)
        val request = mockServer.takeRequest()
        assertEquals("/oauth2/token", request.path)
        assertTrue(request.body.readUtf8().contains("code=4%2F0_test_code"))
    }

    @Test
    fun `exchange code returns null on failure`() {
        mockServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"error": "invalid_grant"}"""))

        val token = client.exchangeCode(
            code = "bad_code",
            clientId = "test.apps.googleusercontent.com",
            clientSecret = "GOCSPX-test_secret",
            redirectUri = "http://127.0.0.1:8080/callback",
        )

        assertNull(token)
    }

    @Test
    fun `list files returns empty list without authentication`() {
        val files = client.listFiles()
        assertTrue(files.isEmpty())
    }

    @Test
    fun `list files returns files when authenticated`() {
        client.authenticate("test_token")

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{
                    "files": [
                        {"id": "file1", "name": "backup.json", "mimeType": "application/json", "size": 1024},
                        {"id": "file2", "name": "backup2.json", "mimeType": "application/json", "size": 2048}
                    ]
                }"""),
        )

        val files = client.listFiles()
        assertEquals(2, files.size)
        assertEquals("file1", files[0].id)
        assertEquals("backup.json", files[0].name)
    }

    @Test
    fun `upload file returns null without authentication`() {
        val fileId = client.uploadFile(File("test.txt"))
        assertNull(fileId)
    }

    @Test
    fun `getOrCreateBackupFolder finds existing folder`() {
        client.authenticate("test_token")

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{
                    "files": [
                        {"id": "backup_folder_id", "name": "Anikku Backups", "mimeType": "application/vnd.google-apps.folder"}
                    ]
                }"""),
        )

        val folderId = client.getOrCreateBackupFolder()
        assertEquals("backup_folder_id", folderId)
    }

    @Test
    fun `PKCE exchange sends verifier and omits empty desktop client secret`() {
        mockServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access_token":"access","refresh_token":"refresh","expires_in":3600}""",
            ),
        )

        assertNotNull(
            client.exchangeCode(
                code = "code with symbols/+",
                clientId = "desktop.apps.googleusercontent.com",
                redirectUri = "http://127.0.0.1:54321/oauth/google-drive",
                codeVerifier = "verifier_-~.123",
            ),
        )

        val body = mockServer.takeRequest().body.readUtf8()
        val decodedBody = java.net.URLDecoder.decode(body, Charsets.UTF_8)
        assertTrue(decodedBody.contains("code=code with symbols/+"))
        assertTrue(decodedBody.contains("code_verifier=verifier_-~.123"))
        assertFalse(body.contains("client_secret"))
    }

    @Test
    fun `upload streams file through resumable session with escaped metadata`() {
        client.authenticate("test_token")
        val file = Files.createTempFile("drive-quote-\"", ".json").toFile().apply {
            writeText("payload")
            deleteOnExit()
        }
        mockServer.enqueue(
            MockResponse().setResponseCode(200).addHeader("Location", mockServer.url("/upload-session")),
        )
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"uploaded-id"}"""))

        assertEquals("uploaded-id", client.uploadFile(file, "application/json", "folder-id"))

        val session = mockServer.takeRequest()
        assertEquals("Bearer test_token", session.getHeader("Authorization"))
        assertTrue(session.body.readUtf8().contains("\\\""), "Filename must be JSON escaped")
        val upload = mockServer.takeRequest()
        assertEquals("payload", upload.body.readUtf8())
    }

    @Test
    fun `failed download preserves destination and removes partial file`() {
        client.authenticate("test_token")
        val directory = Files.createTempDirectory("drive-download-").toFile().apply { deleteOnExit() }
        val destination = File(directory, "backup.json").apply { writeText("existing") }
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("partial"))

        assertFalse(client.downloadFile("valid_file-id", destination))
        assertEquals("existing", destination.readText())
        assertTrue(directory.listFiles().orEmpty().none { it.name.contains(".part-") })

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("replacement"))
        assertTrue(client.downloadFile("valid_file-id", destination))
        assertEquals("replacement", destination.readText())
    }

    @Test
    fun `invalid Drive file identifiers are rejected before network access`() {
        client.authenticate("test_token")
        val destination = Files.createTempFile("drive-invalid-", ".json").toFile()

        assertFalse(client.downloadFile("../../escape", destination))
        assertFalse(client.deleteFile("bad/id"))
        assertEquals(0, mockServer.requestCount)
    }
}
