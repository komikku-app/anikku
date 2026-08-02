package app.anikku.macos.player

import app.anikku.macos.platform.torrent.TorrentServerBridge
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class TorrentStreamingCoordinatorTest {
    @Test
    fun `uses WebTorrent fallback and preserves cleanup when native binary is absent`() = runBlocking {
        val process = FakeProcess()
        var requestedUrl: String? = null
        val coordinator = TorrentStreamingCoordinator(
            nativeBridge = TorrentServerBridge(
                binDirectory = File("/definitely/missing/torrserver"),
                dataDirectory = File("/tmp/anikku-torrserver-test"),
                bundledBinDirectory = null,
            ),
            webTorrentStart = { url ->
                requestedUrl = url
                MagnetStreamResult.Success("http://localhost:32100/0", process)
            },
        )

        val magnet = "magnet:?xt=urn:btih:unit-test"
        val result = assertInstanceOf(
            TorrentStreamingResult.Success::class.java,
            coordinator.start(magnet),
        )

        assertEquals(magnet, requestedUrl)
        assertEquals("WebTorrent", result.backend)
        assertEquals("http://localhost:32100/0", result.httpUrl)
        assertTrue(process.isAlive)

        coordinator.stop(result)
        assertFalse(process.isAlive)
    }

    private class FakeProcess : Process() {
        private val output = ByteArrayOutputStream()
        @Volatile private var running = true

        override fun getOutputStream(): OutputStream = output
        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int { running = false; return 0 }
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = !running
        override fun exitValue(): Int = if (running) throw IllegalThreadStateException() else 0
        override fun destroy() { running = false }
        override fun destroyForcibly(): Process = apply { destroy() }
        override fun isAlive(): Boolean = running
    }
}
