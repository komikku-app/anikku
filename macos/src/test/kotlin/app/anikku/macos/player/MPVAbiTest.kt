package app.anikku.macos.player

import com.sun.jna.Memory
import com.sun.jna.Pointer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Deterministic ABI tests for the libmpv 0.41 bindings bundled by the macOS app.
 * These tests do not load or execute native MPV, so they remain portable in CI.
 */
class MPVAbiTest {

    @Test
    fun `event constants match bundled MPV 0 41 client header`() {
        assertEquals(0, MPVLib.MPV_EVENT_NONE)
        assertEquals(1, MPVLib.MPV_EVENT_SHUTDOWN)
        assertEquals(2, MPVLib.MPV_EVENT_LOG_MESSAGE)
        assertEquals(3, MPVLib.MPV_EVENT_GET_PROPERTY_REPLY)
        assertEquals(4, MPVLib.MPV_EVENT_SET_PROPERTY_REPLY)
        assertEquals(5, MPVLib.MPV_EVENT_COMMAND_REPLY)
        assertEquals(6, MPVLib.MPV_EVENT_START_FILE)
        assertEquals(7, MPVLib.MPV_EVENT_END_FILE)
        assertEquals(8, MPVLib.MPV_EVENT_FILE_LOADED)
        assertEquals(16, MPVLib.MPV_EVENT_CLIENT_MESSAGE)
        assertEquals(17, MPVLib.MPV_EVENT_VIDEO_RECONFIG)
        assertEquals(18, MPVLib.MPV_EVENT_AUDIO_RECONFIG)
        assertEquals(20, MPVLib.MPV_EVENT_SEEK)
        assertEquals(21, MPVLib.MPV_EVENT_PLAYBACK_RESTART)
        assertEquals(22, MPVLib.MPV_EVENT_PROPERTY_CHANGE)
        assertEquals(24, MPVLib.MPV_EVENT_QUEUE_OVERFLOW)
        assertEquals(25, MPVLib.MPV_EVENT_HOOK)
    }

    @Test
    fun `end file reason constants match bundled MPV 0 41 client header`() {
        assertEquals(0, MPVLib.END_FILE_REASON_EOF)
        assertEquals(2, MPVLib.END_FILE_REASON_STOP)
        assertEquals(3, MPVLib.END_FILE_REASON_QUIT)
        assertEquals(4, MPVLib.END_FILE_REASON_ERROR)
        assertEquals(5, MPVLib.END_FILE_REASON_REDIRECT)
    }

    @Test
    fun `mpv event eagerly snapshots end file payload`() {
        val endFilePayload = Memory(24).apply {
            setInt(0, MPVLib.END_FILE_REASON_ERROR)
            setInt(4, MPVLib.ERROR_LOADING_FAILED)
            setLong(8, 1234L)
        }
        val nativeEvent = Memory(24).apply {
            setInt(0, MPVLib.MPV_EVENT_END_FILE)
            setInt(4, MPVLib.ERROR_LOADING_FAILED)
            setLong(8, 99L)
            setPointer(16, endFilePayload)
        }

        val event = MPVEvent(nativeEvent)

        assertEquals(MPVLib.MPV_EVENT_END_FILE, event.eventId)
        assertEquals(MPVLib.ERROR_LOADING_FAILED, event.error)
        assertEquals(99L, event.replyUserdata)
        assertEquals(MPVLib.END_FILE_REASON_ERROR, event.endFileReason)
        assertEquals(MPVLib.ERROR_LOADING_FAILED, event.endFileError)
        assertEquals(1234L, event.playlistEntryId)
        assertEquals("end_file", event.eventName())
    }

    @Test
    fun `mpv event eagerly snapshots property change payload`() {
        val propertyName = Memory(16).apply { setString(0, "time-pos") }
        val propertyPayload = Memory(24).apply {
            setPointer(0, propertyName)
            setInt(8, MPVLib.FORMAT_DOUBLE)
        }
        val nativeEvent = Memory(24).apply {
            setInt(0, MPVLib.MPV_EVENT_PROPERTY_CHANGE)
            setLong(8, 42L)
            setPointer(16, propertyPayload)
        }

        val event = MPVEvent(nativeEvent)

        assertEquals("time-pos", event.propertyName)
        assertEquals(MPVLib.FORMAT_DOUBLE, event.propertyFormat)
        assertNull(event.endFileReason)
        assertNull(event.logText)
    }

    @Test
    fun `mpv event eagerly snapshots log message payload`() {
        val prefix = Memory(16).apply { setString(0, "vo/libmpv") }
        val level = Memory(16).apply { setString(0, "warn") }
        val text = Memory(32).apply { setString(0, "render target unavailable") }
        val logPayload = Memory(40).apply {
            setPointer(0, prefix)
            setPointer(8, level)
            setPointer(16, text)
            setInt(24, MPVLib.LOG_LEVEL_WARN)
        }
        val nativeEvent = Memory(24).apply {
            setInt(0, MPVLib.MPV_EVENT_LOG_MESSAGE)
            setPointer(16, logPayload)
        }

        val event = MPVEvent(nativeEvent)

        assertEquals("vo/libmpv", event.logPrefix)
        assertEquals("warn", event.logLevelName)
        assertEquals("render target unavailable", event.logText)
        assertEquals(MPVLib.LOG_LEVEL_WARN, event.logLevel)
        assertNull(event.propertyName)
    }

    @Test
    fun `mpv event safely represents null data payloads`() {
        val nativeEvent = Memory(24).apply {
            clear()
            setInt(0, MPVLib.MPV_EVENT_SHUTDOWN)
        }

        val event = MPVEvent(nativeEvent)

        assertEquals(MPVLib.MPV_EVENT_SHUTDOWN, event.eventId)
        assertNull(event.endFileReason)
        assertNull(event.endFileError)
        assertNull(event.playlistEntryId)
        assertNull(event.propertyName)
        assertNull(event.logText)
        assertEquals("shutdown", event.eventName())
    }

    @Test
    fun `render parameter constants and memory layout match MPV render ABI`() {
        assertEquals(0, MPVLib.RENDER_PARAM_INVALID)
        assertEquals(1, MPVLib.RENDER_PARAM_API_TYPE)
        assertEquals(10, MPVLib.RENDER_PARAM_ADVANCED_CONTROL)
        assertEquals(12, MPVLib.RENDER_PARAM_BLOCK_FOR_TARGET_TIME)
        assertEquals(17, MPVLib.RENDER_PARAM_SW_SIZE)
        assertEquals(18, MPVLib.RENDER_PARAM_SW_FORMAT)
        assertEquals(19, MPVLib.RENDER_PARAM_SW_STRIDE)
        assertEquals(20, MPVLib.RENDER_PARAM_SW_POINTER)

        val size = Memory(8)
        val pointer = Memory(16)
        val params = MPVLib.buildRenderParams(
            MPVLib.RENDER_PARAM_SW_SIZE to size,
            MPVLib.RENDER_PARAM_SW_POINTER to pointer,
        )

        // Each mpv_render_param occupies 16 bytes on 64-bit macOS:
        // int type + pointer-sized data + padding.
        assertEquals(MPVLib.RENDER_PARAM_SW_SIZE, params.getInt(0))
        assertEquals(MPVLib.RENDER_PARAM_SW_POINTER, params.getInt(16))
        assertEquals(MPVLib.RENDER_PARAM_INVALID, params.getInt(32))
        assertEquals(Pointer.nativeValue(size), Pointer.nativeValue(params.getPointer(8)))
        assertEquals(Pointer.nativeValue(pointer), Pointer.nativeValue(params.getPointer(24)))
        assertEquals(0L, Pointer.nativeValue(params.getPointer(40)))
        // Offset 4 is the padding between the int type and the 64-bit pointer.
        assertEquals(0, params.getByte(4).toInt())
    }
}
