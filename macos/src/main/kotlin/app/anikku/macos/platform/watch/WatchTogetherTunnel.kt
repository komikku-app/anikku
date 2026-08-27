package app.anikku.macos.platform.watch

import androidx.compose.runtime.compositionLocalOf
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Owns the bundled `cloudflared` process and exposes the host's local Watch
 * Together server to the public internet through a Cloudflare quick tunnel.
 *
 * Running `cloudflared tunnel --url http://127.0.0.1:<port>` yields a random
 * `https://<name>.trycloudflare.com` base (no account, no domain, no VPS).
 * Guests anywhere reach the room server — WebSocket sync AND proxied media —
 * through that URL. The tunnel is app-scoped (one stable base per launch) and
 * starts lazily when the host starts a room; when the binary is missing or the
 * tunnel cannot be established, rooms fall back to the LAN-only flow.
 */
class WatchTogetherTunnel(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val bundledBinDirectory: File? = packagedBinDirectory(),
    private val binDirectory: File? = null,
    /** Exact binary path override (native E2E tests point at the build-time helper). */
    private val binary: File? = null,
) : AutoCloseable {

    enum class Status { STOPPED, STARTING, RUNNING, ERROR }

    private val _status = MutableStateFlow(Status.STOPPED)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** The public `https://<name>.trycloudflare.com` base, null until established. */
    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url.asStateFlow()

    private var tunnelProcess: Process? = null
    private var processWatcher: Job? = null
    private var outputThread: Thread? = null

    val isRunning: Boolean get() = _status.value == Status.RUNNING

    val isBinaryAvailable: Boolean get() = findTunnelBinary() != null

    /**
     * Establish a quick tunnel to [port] and return the public base URL, or
     * null when the binary is missing, the process fails, or the URL does not
     * appear within [timeoutSeconds]. Idempotent: an already-running tunnel
     * returns its existing URL.
     */
    suspend fun start(port: Int, timeoutSeconds: Int = 25): String? {
        if (isRunning && tunnelProcess?.isAlive == true) return _url.value

        val binary = findTunnelBinary() ?: run {
            _status.value = Status.ERROR
            logger.info { "Bundled cloudflared binary is unavailable; rooms stay LAN-only" }
            return null
        }

        _status.value = Status.STARTING
        _url.value = null
        return try {
            val metricsPort = availableLoopbackPort()
            tunnelProcess = ProcessBuilder(
                binary.absolutePath,
                "tunnel",
                "--url", "http://127.0.0.1:$port",
                "--no-autoupdate",
                "--protocol", "http2",
                "--metrics", "127.0.0.1:$metricsPort",
                "--grace-period", "0",
                "--loglevel", "info",
            )
                .directory(binary.parentFile)
                .redirectErrorStream(true)
                .start()
                .also(::drainProcessOutput)

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds.toLong())
            while (System.nanoTime() < deadline && tunnelProcess?.isAlive == true) {
                val established = _url.value
                if (established != null) {
                    _status.value = Status.RUNNING
                    startProcessWatcher()
                    logger.info { "Watch Together tunnel established at $established" }
                    return established
                }
                delay(200)
            }

            logger.warn { "cloudflared did not establish a tunnel within ${timeoutSeconds}s" }
            stop()
            _status.value = Status.ERROR
            null
        } catch (error: Exception) {
            logger.warn(error) { "Failed to start cloudflared tunnel" }
            stop()
            _status.value = Status.ERROR
            null
        }
    }

    fun stop() {
        processWatcher?.cancel()
        processWatcher = null
        tunnelProcess?.let { process ->
            runCatching {
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
            }
        }
        tunnelProcess = null
        outputThread?.interrupt()
        outputThread = null
        _url.value = null
        _status.value = Status.STOPPED
    }

    /**
     * Emergency kill without waiting or locking — used by the shutdown
     * watchdog when a normal [stop] is wedged.
     */
    fun forceKill() {
        tunnelProcess?.let { runCatching { it.destroyForcibly() } }
    }

    override fun close() {
        stop()
        scope.cancel()
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    /** Reap the process if it dies on its own; drop the URL so rooms know. */
    private fun startProcessWatcher() {
        processWatcher?.cancel()
        processWatcher = scope.launch {
            val process = tunnelProcess ?: return@launch
            process.waitFor()
            if (_status.value != Status.STOPPED) {
                logger.warn { "cloudflared exited unexpectedly (code ${process.exitValue()})" }
                _status.value = Status.ERROR
                _url.value = null
            }
        }
    }

    private fun drainProcessOutput(process: Process) {
        outputThread = Thread({
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (_url.value == null) {
                            parseTunnelUrl(line)?.let { _url.value = it }
                        }
                        logger.debug { "cloudflared: $line" }
                    }
                }
            }
        }, "anikku-cloudflared-output").apply {
            isDaemon = true
            start()
        }
    }

    private fun findTunnelBinary(): File? {
        binary?.takeIf { it.isFile && it.canExecute() }?.let { return it }
        val architecture = normalizedArchitecture()
        val names = listOf(
            "cloudflared-darwin-$architecture",
            "cloudflared",
        )
        return listOfNotNull(bundledBinDirectory, binDirectory)
            .distinctBy { it.absolutePath }
            .asSequence()
            .flatMap { directory -> names.asSequence().map { File(directory, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    companion object {
        /** The resources directory Compose packages the staged binary into. */
        fun packagedBinDirectory(): File? =
            System.getProperty("compose.application.resources.dir")
                ?.let { File(it, "Cloudflared") }

        private fun normalizedArchitecture(): String = when (System.getProperty("os.arch").lowercase()) {
            "aarch64", "arm64" -> "arm64"
            else -> "amd64"
        }

        private fun availableLoopbackPort(): Int =
            ServerSocket(0).use { it.localPort }

        /**
         * Extract the quick-tunnel URL from a cloudflared log line.
         * cloudflared prints `Your quick Tunnel has been created! Visit it at
         * (trycloudflare.com) https://<name>.trycloudflare.com` once connected;
         * connection-established lines also repeat the URL.
         */
        internal fun parseTunnelUrl(line: String): String? =
            TUNNEL_URL_REGEX.find(line)?.value?.trimEnd('/')

        private val TUNNEL_URL_REGEX = Regex("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com")
    }
}

/** CompositionLocal for the app-wide [WatchTogetherTunnel] (null when unavailable). */
val LocalWatchTogetherTunnel = compositionLocalOf<WatchTogetherTunnel?> { null }
