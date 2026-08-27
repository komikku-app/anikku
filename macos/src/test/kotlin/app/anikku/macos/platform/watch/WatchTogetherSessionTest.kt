package app.anikku.macos.platform.watch

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * End-to-end room sync over loopback: a real [WatchTogetherServer] with two
 * [WatchTogetherSession]s (host + guest) and recording controllers, mirroring
 * the PlayerScreen wiring.
 */
class WatchTogetherSessionTest {

    private lateinit var server: WatchTogetherServer
    private var host: WatchTogetherSession? = null
    private var guest: WatchTogetherSession? = null

    @BeforeEach
    fun setUp() {
        server = WatchTogetherServer(preferredPort = 0)
        server.startServer()
    }

    @AfterEach
    fun tearDown() {
        host?.close()
        guest?.close()
        server.stopServer()
    }

    @Test
    fun `guest pause propagates to the host and everyone converges`() {
        val hostCtrl = RecordingController(isPaused = false) // host is playing
        val guestCtrl = RecordingController()
        val host = session("Host", hostCtrl)
        val guest = session("Bob", guestCtrl)

        host.startRoom(episode(), WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"), server)
        val code = host.roomCode.value ?: fail("room did not start")
        guest.joinRoomAt("127.0.0.1", server.actualPort, code)

        // Guest pauses — the relayed Pause reaches the host and pauses it.
        guest.sendControl(WtMessage.Pause())
        hostCtrl.awaitCall { it == "togglePause" }
        assertTrue(hostCtrl.isPaused, "host should be paused after guest paused")

        // Guest resumes — host resumes too.
        guest.sendControl(WtMessage.Play())
        hostCtrl.awaitCall { it == "togglePause" && !hostCtrl.isPaused }
        assertTrue(!hostCtrl.isPaused, "host should resume after guest played")
    }

    @Test
    fun `guest seek is applied by the host`() {
        val hostCtrl = RecordingController()
        val guest = session("Bob", RecordingController())
        val host = session("Host", hostCtrl)

        host.startRoom(episode(), WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"), server)
        val code = host.roomCode.value ?: fail("room did not start")
        guest.joinRoomAt("127.0.0.1", server.actualPort, code)

        guest.sendControl(WtMessage.Seek(42.0))
        hostCtrl.awaitCall { it == "seek:42.0" }
    }

    @Test
    fun `first host sync applies as baseline, then drift is corrected`() {
        val hostCtrl = RecordingController(position = 100.0)
        val guestCtrl = RecordingController()
        val host = session("Host", hostCtrl)
        val guest = session("Bob", guestCtrl)

        host.startRoom(episode(), WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"), server)
        val code = host.roomCode.value ?: fail("room did not start")
        guest.joinRoomAt("127.0.0.1", server.actualPort, code)
        host.beginHostSync { WatchTogetherSession.SyncSnapshot(hostCtrl.position, !hostCtrl.isPaused, hostCtrl.rate, 1440.0) }

        // The baseline sync (and subsequent drift corrections) seek the guest.
        guestCtrl.awaitCall { it.startsWith("seek:100.0") }
        assertEquals(100.0, guestCtrl.position)
    }

    @Test
    fun `stale sync does not resurrect a fresh local action`() {
        // A "laggy host" whose sync keeps reporting playing=true even after
        // the guest paused — exactly the fight the guard must prevent.
        val hostCtrl = RecordingController()
        val guestCtrl = RecordingController()
        val host = session("Host", hostCtrl)
        // Long guard window so the test observes both the suppressed and the
        // (after expiry) applied sync.
        val guest = WatchTogetherSession(
            httpClient = okHttp(),
            localActionGuardMillis = 1_500L,
            sessionName = "Bob",
        )
        wire(guest, guestCtrl)

        host.startRoom(episode(), WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"), server)
        val code = host.roomCode.value ?: fail("room did not start")
        guest.joinRoomAt("127.0.0.1", server.actualPort, code)
        host.beginHostSync { WatchTogetherSession.SyncSnapshot(50.0, true, 1.0, 1440.0) } // always "playing"

        // Baseline sync applies (guest starts paused → sync says playing → resume).
        guestCtrl.awaitCall { it == "togglePause" }
        val baselineCount = guestCtrl.calls.size

        // Local pause bumps the guard and (like the player) toggles locally.
        guest.sendControl(WtMessage.Pause())
        guestCtrl.togglePause()
        val afterLocalAction = guestCtrl.calls.size
        // A stale sync arrives within the guard window — it must NOT resume.
        Thread.sleep(900)
        assertEquals(afterLocalAction, guestCtrl.calls.size, "stale sync must not fight the local pause; calls=${guestCtrl.calls}")

        // After the guard window expires, the next sync applies again.
        guestCtrl.awaitCall { it == "togglePause" && baselineCount < guestCtrl.calls.size }
    }

    @Test
    fun `member names appear on the other side`() {
        val host = session("Host", RecordingController())
        val guest = session("Bob", RecordingController())

        host.startRoom(episode(), WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"), server)
        val code = host.roomCode.value ?: fail("room did not start")
        guest.joinRoomAt("127.0.0.1", server.actualPort, code)

        awaitValue(flow = host.memberNames) { it.contains("Bob") }
        assertEquals(2, host.memberCount.value)
    }

    @Test
    fun `guest episode push carries the room media url`() {
        val host = session("Host", RecordingController())
        val guest = session("Bob", RecordingController())
        var pushed: WtMessage.Episode? = null
        guest.onEpisode = { pushed = it }

        host.startRoom(episode(title = "Frieren"), WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"), server)
        val code = host.roomCode.value ?: fail("room did not start")
        guest.joinRoomAt("127.0.0.1", server.actualPort, code)

        val deadline = System.currentTimeMillis() + 5_000
        while (pushed == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val episode = pushed ?: fail("guest never received the episode")
        assertEquals("Frieren", episode.title)
        assertNotNull(episode.mediaUrl)
        assertTrue(episode.mediaUrl!!.contains("/media/$code/"))
    }

    @Test
    fun `host leaving closes the room for guests with a message`() {
        val host = session("Host", RecordingController())
        val guest = session("Bob", RecordingController())
        host.startRoom(episode(), WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"), server)
        val code = host.roomCode.value ?: fail("room did not start")
        guest.joinRoomAt("127.0.0.1", server.actualPort, code)
        // Wait until the guest's websocket actually completed the handshake
        // (role flips optimistically at join — membership only after onOpen).
        awaitValue(flow = host.memberCount) { it == 2 }

        host.leave()

        awaitValue(flow = guest.role) { it == WatchTogetherSession.Role.NONE }
        assertEquals("The host closed the room", guest.status.value)
    }

    @Test
    fun `guest captures the host position from the join-time sync replay`() {
        val hostCtrl = RecordingController(position = 600.0)
        val guestCtrl = RecordingController()
        val host = session("Host", hostCtrl)
        val guest = session("Bob", guestCtrl)

        host.startRoom(episode(), WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"), server)
        val code = host.roomCode.value ?: fail("room did not start")
        // The host is paused at 10:00 and already broadcasting when the guest joins.
        host.beginHostSync { WatchTogetherSession.SyncSnapshot(600.0, false, 1.0, 1440.0) }

        // Wait until the server stored the host's broadcast for replay.
        val storedDeadline = System.currentTimeMillis() + 5_000
        while (server.room(code)?.lastSync == null && System.currentTimeMillis() < storedDeadline) Thread.sleep(20)
        assertNotNull(server.room(code)?.lastSync)

        guest.joinRoomAt("127.0.0.1", server.actualPort, code)

        // The join-time sync anchors the guest's start position…
        val anchorDeadline = System.currentTimeMillis() + 5_000
        while (guest.joinStartPosition == 0.0 && System.currentTimeMillis() < anchorDeadline) Thread.sleep(20)
        assertEquals(600.0, guest.joinStartPosition)
        // …and is applied like any baseline sync (seek straight to 10:00).
        guestCtrl.awaitCall { it == "seek:600.0" }
    }

    @Test
    fun `renaming broadcasts the new name to the other members`() {
        val host = session("Host", RecordingController())
        val guest = session("Bob", RecordingController())

        host.startRoom(episode(), WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"), server)
        val code = host.roomCode.value ?: fail("room did not start")
        guest.joinRoomAt("127.0.0.1", server.actualPort, code)

        awaitValue(flow = host.memberNames) { it.contains("Bob") }
        // Emojis are welcome in names.
        guest.rename("Sakura\uD83D\uDC31")
        awaitValue(flow = host.memberNames) { it.contains("Sakura\uD83D\uDC31") }
        assertFalse(host.memberNames.value.contains("Bob"))
    }

    // -----------------------------------------------------------------------
    // Internet rooms — shared links and the Cloudflare tunnel base
    // -----------------------------------------------------------------------

    @Test
    fun `guest joins a room through a shared link`() {
        val hostCtrl = RecordingController(isPaused = false)
        val host = session("Host", hostCtrl)
        val guest = session("Bob", RecordingController())

        host.startRoom(episode(title = "Frieren"), WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"), server)
        val code = host.roomCode.value ?: fail("room did not start")
        // The link form of a room — over plain http the join goes through ws at the given port.
        guest.joinRoom("http://127.0.0.1:${server.actualPort}/room/$code")

        awaitValue(flow = host.memberCount) { it == 2 }
        guest.sendControl(WtMessage.Pause())
        hostCtrl.awaitCall { it == "togglePause" }
    }

    @Test
    fun `tunnel-hosted room shares the public media and join urls`() {
        val host = session("Host", RecordingController())
        var pushed: WtMessage.Episode? = null
        val guest = session("Bob", RecordingController())
        guest.onEpisode = { pushed = it }

        host.startRoom(
            episode(title = "Frieren"),
            WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"),
            server,
            tunnelUrl = "https://anime-night-2026.trycloudflare.com",
        )
        val code = host.roomCode.value ?: fail("room did not start")
        assertEquals("https://anime-night-2026.trycloudflare.com/room/$code", host.joinUrl.value)

        guest.joinRoomAt("127.0.0.1", server.actualPort, code)
        val deadline = System.currentTimeMillis() + 5_000
        while (pushed == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val episode = pushed ?: fail("guest never received the episode")
        assertEquals("Frieren", episode.title)
        assertTrue(episode.mediaUrl!!.startsWith("https://anime-night-2026.trycloudflare.com/media/$code/"))
    }

    @Test
    fun `room media updates keep the tunnel base in the shared url`() {
        val host = session("Host", RecordingController())
        var pushed: WtMessage.Episode? = null
        val guest = session("Bob", RecordingController())
        guest.onEpisode = { pushed = it }

        host.startRoom(
            episode(),
            WatchTogetherSession.MediaSpec.Url("http://example.com/v1.mp4"),
            server,
            tunnelUrl = "https://anime-night-2026.trycloudflare.com",
        )
        val code = host.roomCode.value ?: fail("room did not start")
        guest.joinRoomAt("127.0.0.1", server.actualPort, code)
        // Consume the join-time episode replay before the update arrives.
        val replayDeadline = System.currentTimeMillis() + 5_000
        while (pushed == null && System.currentTimeMillis() < replayDeadline) Thread.sleep(20)
        pushed = null

        host.updateRoomMedia(
            episode(title = "Frieren"),
            WatchTogetherSession.MediaSpec.Url("http://example.com/v2.mp4"),
            server,
        )
        val updateDeadline = System.currentTimeMillis() + 5_000
        while (pushed == null && System.currentTimeMillis() < updateDeadline) Thread.sleep(20)
        val updated = pushed ?: fail("guest never received the updated episode")
        assertEquals("Frieren", updated.title)
        assertTrue(updated.mediaUrl!!.startsWith("https://anime-night-2026.trycloudflare.com/media/$code/"))
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun session(name: String, ctrl: RecordingController): WatchTogetherSession {
        val s = WatchTogetherSession(httpClient = okHttp(), sessionName = name)
        wire(s, ctrl)
        return s
    }

    private fun wire(s: WatchTogetherSession, ctrl: RecordingController) {
        s.onControl = { message ->
            when (message) {
                is WtMessage.Sync -> {
                    if (message.playing != !ctrl.isPaused) ctrl.togglePause()
                    if (kotlin.math.abs(message.pos - ctrl.position) > 0.5) ctrl.seekTo(message.pos)
                    if (kotlin.math.abs(message.rate - ctrl.rate) > 0.05) ctrl.setSpeed(message.rate)
                }
                is WtMessage.Play -> if (ctrl.isPaused) ctrl.togglePause()
                is WtMessage.Pause -> if (!ctrl.isPaused) ctrl.togglePause()
                is WtMessage.Seek -> ctrl.seekTo(message.pos)
                else -> Unit
            }
        }
    }

    private fun okHttp() = okhttp3.OkHttpClient()

    private fun episode(title: String = "Frieren") = WtMessage.Episode(
        title = title,
        name = "Ep 3",
        number = 3.0,
        kind = "direct",
        duration = 1440.0,
    )

    private class RecordingController(
        var isPaused: Boolean = true,
        var position: Double = 0.0,
        var rate: Double = 1.0,
    ) {
        val calls = CopyOnWriteArrayList<String>()

        fun togglePause() {
            calls += "togglePause"
            isPaused = !isPaused
        }

        fun seekTo(pos: Double) {
            calls += "seek:$pos"
            position = pos
        }

        fun setSpeed(newRate: Double) {
            calls += "rate:$newRate"
            rate = newRate
        }

        fun awaitCall(timeoutMs: Long = 5_000, predicate: (String) -> Boolean): String {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                calls.firstOrNull(predicate)?.let { return it }
                Thread.sleep(20)
            }
            return fail("No call matched; calls=$calls")
        }
    }

    private fun <T> awaitValue(
        timeoutMs: Long = 5_000,
        flow: kotlinx.coroutines.flow.StateFlow<T>,
        predicate: (T) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(flow.value)) return
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for state value (current=${flow.value})")
    }
}
