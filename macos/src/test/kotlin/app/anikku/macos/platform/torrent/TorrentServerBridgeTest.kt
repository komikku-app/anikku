package app.anikku.macos.platform.torrent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.json.JSONArray
import java.io.File
import kotlinx.coroutines.runBlocking

class TorrentServerBridgeTest {

    @Test
    fun `initial status is stopped`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/tmp"),
            dataDirectory = File("/tmp"),
        )
        assertEquals(ServerStatus.STOPPED, bridge.serverStatus.value)
    }

    @Test
    fun `isRunning returns false initially`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/tmp"),
            dataDirectory = File("/tmp"),
        )
        assertFalse(bridge.isRunning)
    }

    @Test
    fun `isBinaryAvailable returns false when no binary`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/nonexistent"),
            dataDirectory = File("/tmp"),
        )
        assertFalse(bridge.isBinaryAvailable)
    }

    @Test
    fun `stop is safe when not running`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/tmp"),
            dataDirectory = File("/tmp"),
        )
        bridge.stop() // Should not throw
        assert(true)
    }

    @Test
    fun `listTorrents returns empty when not running`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/tmp"),
            dataDirectory = File("/tmp"),
        )
        val torrents = bridge.listTorrents()
        assertEquals(0, torrents.size)
    }

    @Test
    fun `prepareStream returns null when not running`() = runBlocking {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/tmp"),
            dataDirectory = File("/tmp"),
        )
        val url = bridge.prepareStream("magnet:?xt=urn:btih:test")
        assertEquals(null, url)
    }

    @Test
    fun `removeTorrent returns false when not running`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/tmp"),
            dataDirectory = File("/tmp"),
        )
        assertFalse(bridge.removeTorrent("test_hash"))
    }

    @Test
    fun `largest recognized video is selected over archives and samples`() {
        val files = TorrentServerBridge.parseFiles(
            JSONArray(
                """[
                    {"id":1,"path":"release.zip","length":9000000000},
                    {"id":2,"path":"sample.mkv","length":10000000},
                    {"id":3,"path":"Episode 01.MP4","length":1400000000}
                ]""",
            ),
        )

        assertEquals(TorrentFile(3, "Episode 01.MP4", 1_400_000_000), TorrentServerBridge.selectPlayableFile(files))
    }

    @Test
    fun `largest file is used when torrent has no recognized video extension`() {
        val files = listOf(
            TorrentFile(1, "one.bin", 10),
            TorrentFile(2, "two.unknown", 20),
        )

        assertEquals(files[1], TorrentServerBridge.selectPlayableFile(files))
    }
}
