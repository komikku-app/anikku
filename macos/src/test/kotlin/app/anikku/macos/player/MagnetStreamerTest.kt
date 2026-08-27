package app.anikku.macos.player

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class MagnetStreamerTest {

    @Test
    fun `silent process is stopped at the configured deadline`() = runBlocking {
        val process = FakeStreamingProcess()
        lateinit var result: MagnetStreamResult

        val elapsed = measureTimeMillis {
            result = MagnetStreamer.startStreaming(
                magnetUrl = "magnet:?xt=urn:btih:test",
                timeoutMillis = 50,
                processFactory = { process },
            )
        }

        val failure = assertInstanceOf(MagnetStreamResult.Failure::class.java, result)
        assertTrue(failure.message.contains("timed out"))
        assertFalse(process.isAlive)
        assertTrue(elapsed < 2_000, "Timeout path took ${elapsed}ms")
    }

    @Test
    fun `server URL is returned while output continues to be drained`() = runBlocking {
        val process = FakeStreamingProcess("Listening on http://localhost:43210/0\n")

        val result = MagnetStreamer.startStreaming(
            magnetUrl = "magnet:?xt=urn:btih:test",
            timeoutMillis = 1_000,
            processFactory = { process },
        )

        val success = assertInstanceOf(MagnetStreamResult.Success::class.java, result)
        assertEquals("http://localhost:43210/0", success.httpUrl)
        assertTrue(process.isAlive)

        MagnetStreamer.stopStreaming(success)
        assertFalse(process.isAlive)
    }

    private class FakeStreamingProcess(initialOutput: String = "") : Process() {
        private val monitor = Object()
        private val stdin = ByteArrayOutputStream()
        private val stdoutWriter = PipedOutputStream()
        private val stdout = PipedInputStream(stdoutWriter)
        private val stderr = ByteArrayInputStream(ByteArray(0))

        @Volatile
        private var running = true

        init {
            if (initialOutput.isNotEmpty()) {
                stdoutWriter.write(initialOutput.toByteArray())
                stdoutWriter.flush()
            }
        }

        override fun getOutputStream(): OutputStream = stdin
        override fun getInputStream(): InputStream = stdout
        override fun getErrorStream(): InputStream = stderr

        override fun waitFor(): Int = synchronized(monitor) {
            while (running) monitor.wait()
            0
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = synchronized(monitor) {
            if (running) monitor.wait(unit.toMillis(timeout).coerceAtLeast(1))
            !running
        }

        override fun exitValue(): Int {
            if (running) throw IllegalThreadStateException("Process is still running")
            return 0
        }

        override fun destroy() {
            synchronized(monitor) {
                if (!running) return
                running = false
                stdoutWriter.close()
                monitor.notifyAll()
            }
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = running
    }
}
