package app.anikku.macos.player

import app.anikku.macos.platform.logging.CrashReporter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Result of attempting to start a magnet stream.
 */
sealed class MagnetStreamResult {
    /** Success — the stream is being served at this local HTTP URL. */
    data class Success(
        val httpUrl: String,
        val process: Process,
        val outputThread: Thread? = null,
    ) : MagnetStreamResult()

    /** Failure — webtorrent-cli is not available or streaming failed. */
    data class Failure(val message: String) : MagnetStreamResult()
}

/**
 * Streams magnet links via webtorrent-cli for playback in mpv.
 *
 * Architecture:
 *   magnet:?xt=urn:btih:...  ─→  webtorrent-cli  ─→  local HTTP server
 *                                                       │
 *                                                       ▼
 *                                                   mpv plays
 *                                                  http://localhost:xxxx/0
 *
 * Requirements:
 *   - Node.js with webtorrent-cli installed:
 *       npm install -g webtorrent-cli
 *     Or use npx (auto-downloads if needed):
 *       npx webtorrent-cli <magnet>
 *
 * Flow:
 *   [PlayerViewModel] detects magnet:// URL
 *         │
 *         ▼
 *   [MagnetStreamer.startStreaming(magnetUrl)]
 *         │
 *         ├── Spawns: npx --yes webtorrent-cli "<magnet>" -p 0
 *         │   (-p 0 = pick any available port)
 *         │
 *         ├── Parses stdout for "http://localhost:PORT/INDEX"
 *         │
 *         ├── Returns Success(httpUrl, process)
 *         │   └── PlayerViewModel loads httpUrl into mpv via loadfile
 *         │
 *         └── On shutdown / playback end:
 *             └── Process.destroy() to kill webtorrent
 */
object MagnetStreamer {

    private const val TIMEOUT_SECONDS = 60L

    private sealed interface ProcessOutput {
        data class Line(val value: String) : ProcessOutput
        data class Failed(val error: Throwable) : ProcessOutput
        data object Closed : ProcessOutput
    }

    /**
     * Check if webtorrent-cli is available on this system.
     *
     * Only checks for a global install via `which webtorrent`.
     * The `npx` fallback is NOT used here because it would download the
     * entire npm package just for an availability check, which is very slow.
     * If not globally installed, `npx` will be used automatically when
     * [startStreaming] is called (which handles the download then).
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val which = ProcessBuilder("which", "webtorrent")
                .redirectErrorStream(true)
                .start()
            which.waitFor(5, TimeUnit.SECONDS) && which.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Start streaming a magnet link via webtorrent-cli.
     *
     * This spawns a webtorrent HTTP server for the magnet link and returns
     * the local URL (e.g., http://localhost:50981/0) that serves the first
     * media file in the torrent.
     *
     * @param magnetUrl The magnet:?xt=urn:btih:... link
     * @return [MagnetStreamResult.Success] with the HTTP URL and managed process,
     *         or [MagnetStreamResult.Failure] with an error message.
     */
    suspend fun startStreaming(magnetUrl: String): MagnetStreamResult = startStreaming(
        magnetUrl = magnetUrl,
        timeoutMillis = TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS),
        processFactory = { url ->
            ProcessBuilder(
                "npx", "--yes", "webtorrent-cli",
                url,
                "-p", "0",
            )
                .redirectErrorStream(true)
                .start()
        },
    )

    internal suspend fun startStreaming(
        magnetUrl: String,
        timeoutMillis: Long,
        processFactory: (String) -> Process,
    ): MagnetStreamResult = withContext(Dispatchers.IO) {
        startStreamingBlocking(magnetUrl, timeoutMillis, processFactory)
    }

    private fun startStreamingBlocking(
        magnetUrl: String,
        timeoutMillis: Long,
        processFactory: (String) -> Process,
    ): MagnetStreamResult {
        logger.info { "🧲 MAGNET_STREAM: Starting torrent stream: ${magnetUrl.take(80)}..." }
        CrashReporter.logEvent("Magnet stream start", "url=${magnetUrl.take(80)}")

        try {
            require(timeoutMillis > 0) { "timeoutMillis must be positive" }
            val process = processFactory(magnetUrl)
            val output = LinkedBlockingQueue<ProcessOutput>()
            val outputThread = Thread({
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { output.offer(ProcessOutput.Line(it)) }
                    }
                } catch (error: Throwable) {
                    if (process.isAlive) output.offer(ProcessOutput.Failed(error))
                } finally {
                    output.offer(ProcessOutput.Closed)
                }
            }, "anikku-webtorrent-output").apply {
                // The process pipe can ignore interruption until the child exits.
                // Keeping this daemonized prevents a broken CLI from pinning the JVM.
                isDaemon = true
                start()
            }

            val httpUrlPattern = Regex("http://localhost:\\d+/\\d+")
            val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)

            while (true) {
                val remainingNanos = deadlineNanos - System.nanoTime()
                if (remainingNanos <= 0L) {
                    stopProcess(process, outputThread)
                    val msg = "Magnet stream timed out after ${timeoutMillis}ms"
                    logger.warn { "🧲 MAGNET_STREAM: $msg" }
                    CrashReporter.logEvent("Magnet timeout", msg)
                    return MagnetStreamResult.Failure(msg)
                }

                val event = output.poll(
                    minOf(remainingNanos, TimeUnit.MILLISECONDS.toNanos(250)),
                    TimeUnit.NANOSECONDS,
                ) ?: continue

                if (event is ProcessOutput.Closed) {
                    stopProcess(process, outputThread)
                    val msg = "Could not find HTTP server URL in webtorrent output"
                    logger.warn { "🧲 MAGNET_STREAM: $msg" }
                    CrashReporter.logEvent("Magnet error", msg)
                    return MagnetStreamResult.Failure(msg)
                }

                if (event is ProcessOutput.Failed) {
                    stopProcess(process, outputThread)
                    throw event.error
                }

                val line = (event as ProcessOutput.Line).value
                logger.debug { "🧲 MAGNET_STREAM: $line" }

                val match = httpUrlPattern.find(line)
                if (match != null) {
                    val serverUrl = match.value
                    logger.info { "🧲 MAGNET_STREAM: Found server URL: $serverUrl" }
                    logger.info { "🧲 MAGNET_STREAM: Success! Stream ready at $serverUrl" }
                    CrashReporter.logEvent("Magnet success", "url=$serverUrl")
                    return MagnetStreamResult.Success(serverUrl, process, outputThread)
                }

                val lowerLine = line.lowercase()
                when {
                    "error" in lowerLine && ("not found" in lowerLine || "cant find" in lowerLine) -> {
                        stopProcess(process, outputThread)
                        val msg = "webtorrent-cli not found. Install: npm install -g webtorrent-cli"
                        logger.warn { "🧲 MAGNET_STREAM: $msg" }
                        CrashReporter.logEvent("Magnet error", msg)
                        return MagnetStreamResult.Failure(msg)
                    }
                    "no peers" in lowerLine && "no tmp" in lowerLine -> {
                        // Still trying to connect — keep waiting
                        logger.debug { "🧲 MAGNET_STREAM: Waiting for peers..." }
                    }
                }
            }
        } catch (e: Exception) {
            val msg = "Magnet stream failed: ${e.message ?: "Unknown error"}"
            logger.error(e) { "🧲 MAGNET_STREAM: $msg" }
            CrashReporter.logError("MagnetStream", msg, e)
            return MagnetStreamResult.Failure(msg)
        }
    }

    /**
     * Stop a running webtorrent process and clean up resources.
     */
    fun stopStreaming(result: MagnetStreamResult.Success) {
        stopProcess(result.process, result.outputThread)
        logger.info { "🧲 MAGNET_STREAM: Process stopped" }
    }

    private fun stopProcess(process: Process, outputThread: Thread?) {
        try {
            val descendants = try {
                process.descendants().toList()
            } catch (_: UnsupportedOperationException) {
                emptyList()
            }
            descendants.asReversed().forEach { it.destroy() }
            process.destroy()
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
            }
            descendants.asReversed().filter { it.isAlive }.forEach { it.destroyForcibly() }
            outputThread?.join(1_000)
        } catch (e: Exception) {
            logger.warn(e) { "🧲 MAGNET_STREAM: Error stopping process" }
        }
    }
}
