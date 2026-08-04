package app.anikku.macos.platform.download

import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.media.MacOSHttpServer
import app.anikku.macos.platform.network.MacOSNetworkHelper
import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.storage.MacOSStorageProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class MacOSDownloadManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var server: MockWebServer
    private lateinit var manager: TestDownloadManager
    private lateinit var repository: DownloadRepository
    private lateinit var storage: MacOSStorageProvider
    private lateinit var extensionManager: MacOSExtensionManager

    private val payload = ByteArray(32 * 1024) { index -> (index % 251).toByte() }

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start(0) }
        storage = object : MacOSStorageProvider() {
            override fun directory(): File = tempDir.toFile()
        }
        storage.ensureDirectories()
        repository = DownloadRepository(storage.dataDirectory)
        extensionManager = MacOSExtensionManager(storage, MacOSNetworkHelper(storage))
        manager = TestDownloadManager(
            repository = repository,
            extensionManager = extensionManager,
            storageProvider = storage,
            resolver = { Video(videoUrl = server.url("/video.mp4").toString()) },
        )
    }

    @AfterEach
    fun tearDown() {
        manager.close()
        extensionManager.close()
        server.shutdown()
    }

    @Test
    fun `successful download atomically completes and is compatible with local HTTP server`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))

        val entry = manager.enqueue(1L, 2L, "Demo Anime", "Episode 1", 1.0, "episode-1")
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.COMPLETED)

        val completed = repository.get(entry.id)!!
        val file = File(completed.filePath!!)
        assertTrue(file.isFile)
        assertArrayEquals(payload, file.readBytes())
        assertEquals(file.length(), completed.downloadedBytes)
        assertEquals(1f, completed.progress)
        assertFalse(file.name.contains(".part"))
        assertTrue(file.parentFile!!.canonicalFile == File(storage.downloadsDirectory, "videos").canonicalFile)
        assertNoPartFiles()

        val reloaded = DownloadRepository(storage.dataDirectory).get(entry.id)
        assertNotNull(reloaded)
        assertEquals(DownloadRepository.DownloadStatus.COMPLETED, reloaded!!.status)
        assertEquals(file.absolutePath, reloaded.filePath)

        val localServer = MacOSHttpServer(file.parentFile!!).apply { startServer() }
        try {
            val connection = URL(localServer.getStreamUrl(file)!!).openConnection() as HttpURLConnection
            assertEquals(200, connection.responseCode)
            assertArrayEquals(payload, connection.inputStream.use { it.readBytes() })
            connection.disconnect()
        } finally {
            localServer.stopServer()
        }
    }

    @Test
    fun `HTTP failure marks entry error and cleans partial files`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        val entry = manager.enqueue(1L, 2L, "Failure", "Episode 1", 1.0, "episode-1")
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.ERROR)

        assertNoPartFiles()
        assertNullFilePathDoesNotExist(entry.id)
    }

    @Test
    fun `interrupted response marks entry error and removes partial output`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(payload))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        val entry = manager.enqueue(1L, 2L, "Interrupted", "Episode 1", 1.0, "episode-1")
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.ERROR)

        assertNoPartFiles()
        assertNullFilePathDoesNotExist(entry.id)
    }

    @Test
    fun `retry after HTTP failure creates a complete file and clears error state`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))

        val entry = manager.enqueue(1L, 2L, "Retry", "Episode 1", 1.0, "episode-1")
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.ERROR)
        manager.retry(entry.id)
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.COMPLETED)

        assertArrayEquals(payload, File(repository.get(entry.id)!!.filePath!!).readBytes())
        assertEquals(2, server.requestCount)
        assertNoPartFiles()
    }

    @Test
    fun `pause and immediate resume do not leave the entry queued`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }))
                .throttleBody(1024, 100, TimeUnit.MILLISECONDS),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))

        val entry = manager.enqueue(13L, 2L, "Pause", "Episode 1", 1.0, "episode-1")
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.DOWNLOADING)
        awaitRequestCount(1)
        manager.pause(entry.id)
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.PAUSED)
        manager.resume(entry.id)
        awaitRequestCount(2)
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.COMPLETED)
        assertEquals(2, server.requestCount)

        assertArrayEquals(payload, File(repository.get(entry.id)!!.filePath!!).readBytes())
        assertNoPartFiles()
    }

    @Test
    fun `duplicate enqueue returns existing active entry and does not overwrite`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))

        val first = manager.enqueue(7L, 2L, "Duplicate", "Episode 1", 1.0, "episode-1")
        val second = manager.enqueue(7L, 2L, "Duplicate", "Episode 1", 1.0, "episode-1")
        awaitStatus(first.id, DownloadRepository.DownloadStatus.COMPLETED)

        assertEquals(first.id, second.id)
        assertEquals(1, repository.getAll().size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `concurrent episodes use distinct final and temporary paths`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))

        val first = manager.enqueue(8L, 2L, "Concurrent", "Episode 1", 1.0, "episode-1")
        val second = manager.enqueue(8L, 2L, "Concurrent", "Episode 2", 2.0, "episode-2")
        awaitStatus(first.id, DownloadRepository.DownloadStatus.COMPLETED)
        awaitStatus(second.id, DownloadRepository.DownloadStatus.COMPLETED)

        val firstPath = repository.get(first.id)!!.filePath!!
        val secondPath = repository.get(second.id)!!.filePath!!
        assertNotEquals(firstPath, secondPath)
        assertTrue(File(firstPath).isFile)
        assertTrue(File(secondPath).isFile)
        assertNoPartFiles()
    }

    @Test
    fun `cancellation stops the request and removes partial files`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }))
                .throttleBody(1024, 100, TimeUnit.MILLISECONDS),
        )

        val entry = manager.enqueue(9L, 2L, "Cancel", "Episode 1", 1.0, "episode-1")
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.DOWNLOADING)
        awaitRequestCount(1)
        manager.cancel(entry.id)
        delay(100)

        assertTrue(repository.get(entry.id) == null)
        assertNoPartFiles()
        assertTrue(server.requestCount >= 1)
    }

    @Test
    fun `unsafe filename characters are sanitized and remain inside videos directory`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))

        val entry = manager.enqueue(
            10L,
            2L,
            "../unsafe/name:\u0000*?",
            "Episode 1",
            1.0,
            "episode-1",
        )
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.COMPLETED)

        val file = File(repository.get(entry.id)!!.filePath!!)
        assertTrue(file.isFile)
        assertEquals(File(storage.downloadsDirectory, "videos").canonicalFile, file.parentFile!!.canonicalFile)
        assertFalse(file.name.contains("/"))
        assertFalse(file.name.contains(".."))
        assertNoPartFiles()
    }

    @Test
    fun `failed retry cleanup removes stale partial file before retry`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))

        val entry = manager.enqueue(11L, 2L, "Cleanup", "Episode 1", 1.0, "episode-1")
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.ERROR)
        assertNoPartFiles()

        manager.retry(entry.id)
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.COMPLETED)
        assertNoPartFiles()
    }

    @Test
    fun `persisted outside-root path is not served or deleted`() = runBlocking {
        val outside = tempDir.resolve("outside.mp4").toFile().apply { writeBytes(payload) }
        val entry = repository.enqueue(14L, 2L, "Outside", "Episode 1", 1.0, "episode-1")
        repository.update(
            id = entry.id,
            status = DownloadRepository.DownloadStatus.COMPLETED,
            filePath = outside.absolutePath,
            fileName = outside.name,
        )

        assertEquals(null, manager.getLocalFile(14L, 1.0))
        manager.cancel(entry.id)
        assertTrue(outside.isFile)
    }

    @Test
    fun `shutdown cancels active work and rejects new enqueue`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }))
                .throttleBody(1024, 100, TimeUnit.MILLISECONDS),
        )

        val entry = manager.enqueue(12L, 2L, "Close", "Episode 1", 1.0, "episode-1")
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.DOWNLOADING)
        manager.close()
        delay(200)

        assertFalse(repository.get(entry.id)?.status == DownloadRepository.DownloadStatus.COMPLETED)
        assertNoPartFiles()
        assertThrows(IllegalStateException::class.java) {
            manager.enqueue(12L, 2L, "After Close", "Episode 2", 2.0, "episode-2")
        }
        Unit
    }

    @Test
    fun `download sends the source video headers and persists them`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))

        val headersManager = TestDownloadManager(
            repository = repository,
            extensionManager = extensionManager,
            storageProvider = storage,
            resolver = {
                Video(
                    videoUrl = server.url("/video.mp4").toString(),
                    headers = Headers.headersOf(
                        "Referer", "https://source.example/",
                        "User-Agent", "AnikkuTest",
                    ),
                )
            },
        )
        val entry = headersManager.enqueue(21L, 2L, "Headers", "Episode 1", 1.0, "episode-1")
        awaitStatus(entry.id, DownloadRepository.DownloadStatus.COMPLETED)

        val request = server.takeRequest()
        assertEquals("https://source.example/", request.getHeader("Referer"))
        assertEquals("AnikkuTest", request.getHeader("User-Agent"))
        assertEquals(
            "https://source.example/",
            repository.get(entry.id)!!.headers?.get("Referer"),
        )
        headersManager.close()
    }

    @Test
    fun `removeCompleted deletes completed files and entries but not active`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))

        val first = manager.enqueue(22L, 2L, "Clear", "Episode 1", 1.0, "episode-1")
        val second = manager.enqueue(22L, 2L, "Clear", "Episode 2", 2.0, "episode-2")
        awaitStatus(first.id, DownloadRepository.DownloadStatus.COMPLETED)
        awaitStatus(second.id, DownloadRepository.DownloadStatus.COMPLETED)

        assertEquals(2, manager.removeCompleted())
        assertEquals(0, repository.getAll().size)
        assertFalse(File(repository.get(first.id)?.filePath ?: "missing").exists())
        assertNoPartFiles()
    }

    private suspend fun awaitRequestCount(expected: Int) {
        repeat(100) {
            if (server.requestCount >= expected) return
            delay(25)
        }
        assertTrue(server.requestCount >= expected, "Timed out waiting for request $expected")
    }

    private suspend fun awaitStatus(id: Long, expected: DownloadRepository.DownloadStatus) {
        repeat(100) {
            if (repository.get(id)?.status == expected) return
            delay(25)
        }
        assertEquals(expected, repository.get(id)?.status, "Timed out waiting for download $id")
    }

    private fun assertNoPartFiles() {
        val dir = File(storage.downloadsDirectory, "videos")
        val parts = dir.listFiles()?.filter { it.name.endsWith(".part") }.orEmpty()
        assertTrue(parts.isEmpty(), "Orphaned partial files: ${parts.map { it.name }}")
    }

    private fun assertNullFilePathDoesNotExist(id: Long) {
        repository.get(id)?.filePath?.let { path -> assertFalse(File(path).exists()) }
    }

    private class TestDownloadManager(
        repository: DownloadRepository,
        extensionManager: MacOSExtensionManager,
        storageProvider: MacOSStorageProvider,
        private val resolver: suspend (DownloadRepository.DownloadEntry) -> Video,
    ) : MacOSDownloadManager(
        repository = repository,
        extensionManager = extensionManager,
        storageProvider = storageProvider,
        notifier = MacOSNotificationManager(),
    ) {
        override suspend fun resolveVideo(entry: DownloadRepository.DownloadEntry): Video? = resolver(entry)
        override fun notifyDownloadComplete(entry: DownloadRepository.DownloadEntry) = Unit
    }
}
