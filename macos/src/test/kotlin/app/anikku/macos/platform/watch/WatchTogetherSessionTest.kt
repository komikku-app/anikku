package app.anikku.macos.platform.watch

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
        host.beginHostSync { Triple(hostCtrl.position, !hostCtrl.isPaused, hostCtrl.rate) }

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
        host.beginHostSync { Triple(50.0, true, 1.0) } // always "playing"

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
