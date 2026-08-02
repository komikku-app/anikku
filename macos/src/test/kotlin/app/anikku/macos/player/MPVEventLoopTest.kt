package app.anikku.macos.player

import com.sun.jna.Memory
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class MPVEventLoopTest {

    @Test
    fun `recoverable poll failure is reported and loop continues through property and shutdown`() = runBlocking {
        val calls = AtomicInteger()
        val propertyEvent = propertyEvent("paused-for-cache", MPVLib.FORMAT_FLAG, 77L)
        val shutdownEvent = event(MPVLib.MPV_EVENT_SHUTDOWN)
        val loop = MPVEventLoop(
            mpvHandle = Pointer.createConstant(1L),
            waitForEvent = { _, _ ->
                when (calls.getAndIncrement()) {
                    0 -> throw UnsatisfiedLinkError("injected transient native failure")
                    1 -> propertyEvent
                    else -> shutdownEvent
                }
            },
        )
        val error = async(start = CoroutineStart.UNDISPATCHED) { loop.errors.first() }
        val property = async(start = CoroutineStart.UNDISPATCHED) { loop.propertyChanges.first() }

        loop.start()

        assertTrue(withTimeout(2_000) { error.await() } is UnsatisfiedLinkError)
        assertEquals(
            PropertyChange("paused-for-cache", MPVLib.FORMAT_FLAG, 77L),
            withTimeout(2_000) { property.await() },
        )
        withTimeout(2_000) {
            while (loop.isRunning) delay(10)
        }
        assertFalse(loop.isRunning)
        assertTrue(calls.get() >= 3, "The loop must continue polling after a recoverable failure")
        loop.stop()
    }

    @Test
    fun `property registration delegates exact id name and format and stop is repeatable`() {
        var observed: List<Any>? = null
        var unobserved: Long? = null
        val loop = MPVEventLoop(
            mpvHandle = Pointer.createConstant(2L),
            waitForEvent = { _, _ -> null },
            observe = { _, id, name, format ->
                observed = listOf(id, name, format)
                0
            },
            unobserve = { _, id ->
                unobserved = id
                0
            },
        )

        loop.observeProperty("duration", MPVLib.FORMAT_DOUBLE)
        loop.unobserveProperty("duration")
        loop.stop()
        loop.stop()

        assertEquals(
            listOf("duration".hashCode().toLong(), "duration", MPVLib.FORMAT_DOUBLE),
            observed,
        )
        assertEquals("duration".hashCode().toLong(), unobserved)
        assertFalse(loop.isRunning)
    }

    private fun event(eventId: Int): MPVEvent = MPVEvent(
        Memory(24).apply {
            clear()
            setInt(0, eventId)
        },
    )

    private fun propertyEvent(name: String, format: Int, replyUserdata: Long): MPVEvent {
        val nameMemory = Memory((name.length + 1).toLong()).apply { setString(0, name) }
        val payload = Memory(24).apply {
            clear()
            setPointer(0, nameMemory)
            setInt(8, format)
        }
        return MPVEvent(
            Memory(24).apply {
                clear()
                setInt(0, MPVLib.MPV_EVENT_PROPERTY_CHANGE)
                setLong(8, replyUserdata)
                setPointer(16, payload)
            },
        )
    }
}
