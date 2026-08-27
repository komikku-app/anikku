package app.anikku.macos.platform.torrent

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class TorrentServerNativeIntegrationTest {
    @Test
    fun `pinned helper launches on loopback and serves current JSON API`() = runBlocking {
        val binary = System.getProperty("anikku.test.torrserver.bin")?.let(::File)
        assumeTrue(binary?.isFile == true, "Run the nativeTorrServerTest Gradle task to provision the helper")

        val dataDirectory = Files.createTempDirectory("anikku-torrserver-native-").toFile()
        val bridge = TorrentServerBridge(
            binDirectory = binary!!.parentFile,
            dataDirectory = dataDirectory,
            bundledBinDirectory = null,
        )
        try {
            assertTrue(bridge.isBinaryAvailable)
            assertTrue(bridge.start(), "TorrServer did not become ready")
            assertTrue(bridge.isRunning)
            assertEquals(emptyList<TorrentInfo>(), bridge.listTorrents())
        } finally {
            bridge.stop()
        }
    }
}
