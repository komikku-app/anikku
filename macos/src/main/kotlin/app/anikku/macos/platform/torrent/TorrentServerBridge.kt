package app.anikku.macos.platform.torrent

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.ServerSocket
import java.util.Locale
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Owns the bundled TorrServer process and its localhost API.
 *
 * The API calls intentionally follow TorrServer's versioned JSON contract:
 * `POST /torrents` with `add`, `get`, `list`, and `rem` actions. Playback uses
 * `/play/{hash}/{fileId}` after selecting the largest video file in the torrent.
 */
class TorrentServerBridge(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val binDirectory: File,
    private val dataDirectory: File,
    private val bundledBinDirectory: File? = packagedBinDirectory(),
    private val torrServerHost: String = "127.0.0.1",
    private val torrServerPort: Int = availableLoopbackPort(),
) {
    private var serverProcess: Process? = null
    private var processWatcher: Job? = null
    private var outputThread: Thread? = null
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _serverStatus = MutableStateFlow(ServerStatus.STOPPED)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    val isRunning: Boolean get() = _serverStatus.value == ServerStatus.RUNNING
    internal val apiBase: String get() = "http://$torrServerHost:$torrServerPort"

    suspend fun start(timeoutSeconds: Int = 15): Boolean {
        if (isRunning && serverProcess?.isAlive == true) return true

        val binary = findServerBinary() ?: run {
            _serverStatus.value = ServerStatus.ERROR
            logger.info { "Bundled TorrServer binary is unavailable; WebTorrent fallback will be used" }
            return false
        }
        require(timeoutSeconds > 0) { "timeoutSeconds must be positive" }
        require(dataDirectory.exists() || dataDirectory.mkdirs()) {
            "Unable to create TorrServer data directory: ${dataDirectory.path}"
        }

        _serverStatus.value = ServerStatus.STARTING
        return try {
            serverProcess = ProcessBuilder(
                binary.absolutePath,
                "--ip", torrServerHost,
                "--port", torrServerPort.toString(),
                "--path", dataDirectory.absolutePath,
                "--logpath", File(dataDirectory, "logs").absolutePath,
                "--dontkill",
            )
                .directory(binary.parentFile)
                .redirectErrorStream(true)
                .start()
                .also(::drainProcessOutput)

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds.toLong())
            while (System.nanoTime() < deadline && serverProcess?.isAlive == true) {
                if (isServerHealthy()) {
                    _serverStatus.value = ServerStatus.RUNNING
                    startProcessWatcher()
                    logger.info { "TorrServer started from ${binary.name} on $apiBase" }
                    return true
                }
                delay(200)
            }

            logger.warn { "TorrServer failed to become ready within ${timeoutSeconds}s" }
            stop()
            _serverStatus.value = ServerStatus.ERROR
            false
        } catch (error: Exception) {
            logger.warn(error) { "Failed to start bundled TorrServer" }
            stop()
            _serverStatus.value = ServerStatus.ERROR
            false
        }
    }

    fun stop() {
        processWatcher?.cancel()
        processWatcher = null
        serverProcess?.let { process ->
            runCatching {
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
            }
        }
        serverProcess = null
        outputThread?.interrupt()
        outputThread = null
        _serverStatus.value = ServerStatus.STOPPED
    }

    suspend fun restart(timeoutSeconds: Int = 15): Boolean {
        stop()
        return start(timeoutSeconds)
    }

    /** Add a torrent, wait for metadata, and return the best playable file URL. */
    suspend fun prepareStream(
        torrentUrl: String,
        title: String? = null,
        metadataTimeoutSeconds: Int = 60,
    ): TorrentServerStream? {
        if (!isRunning || metadataTimeoutSeconds <= 0) return null

        val added = postTorrentAction(
            JSONObject()
                .put("action", "add")
                .put("link", torrentUrl)
                .put("save_to_db", false)
                .apply { if (!title.isNullOrBlank()) put("title", title) },
        ) ?: return null
        val hash = added.optString("hash").takeIf { it.isNotBlank() } ?: return null

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(metadataTimeoutSeconds.toLong())
        var status = added
        while (System.nanoTime() < deadline) {
            val files = parseFiles(status.optJSONArray("file_stats"))
            if (files.isNotEmpty()) {
                val selected = selectPlayableFile(files)
                return TorrentServerStream(
                    httpUrl = "$apiBase/play/$hash/${selected.id}",
                    torrentHash = hash,
                    file = selected,
                )
            }
            delay(250)
            status = postTorrentAction(
                JSONObject().put("action", "get").put("hash", hash),
            ) ?: continue
        }

        logger.warn { "TorrServer metadata timed out for ${torrentUrl.take(80)}" }
        removeTorrent(hash)
        return null
    }

    fun removeTorrent(torrentId: String): Boolean {
        if (!isRunning || torrentId.isBlank()) return false
        return postTorrentAction(
            JSONObject().put("action", "rem").put("hash", torrentId),
            expectJson = false,
        ) != null
    }

    fun listTorrents(): List<TorrentInfo> {
        if (!isRunning) {
            _torrents.value = emptyList()
            return emptyList()
        }
        val response = postTorrentAction(
            JSONObject().put("action", "list"),
            expectArray = true,
        ) ?: return emptyList()
        val parsed = parseTorrentList(response.optJSONArray("items"))
        _torrents.value = parsed
        return parsed
    }

    /**
     * Latest snapshot of active torrents (progress/seeders). Updated by
     * [listTorrents]; the Torrents tab polls it while a stream is active.
     */
    private val _torrents = MutableStateFlow<List<TorrentInfo>>(emptyList())
    val torrents: StateFlow<List<TorrentInfo>> = _torrents.asStateFlow()

    val isBinaryAvailable: Boolean get() = findServerBinary() != null

    private fun findServerBinary(): File? {
        val architecture = normalizedArchitecture()
        val names = listOf(
            "TorrServer-darwin-$architecture",
            "TorrServer-macOS-$architecture",
            "TorrServer",
        )
        return listOfNotNull(bundledBinDirectory, binDirectory)
            .distinctBy { it.absolutePath }
            .asSequence()
            .flatMap { directory -> names.asSequence().map { File(directory, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    private fun isServerHealthy(): Boolean = runCatching {
        httpClient.newCall(Request.Builder().url("$apiBase/echo").get().build()).execute().use {
            it.isSuccessful && !it.body?.string().isNullOrBlank()
        }
    }.getOrDefault(false)

    /** Wrap an array response so one internal helper can return JSONObject. */
    private fun postTorrentAction(
        payload: JSONObject,
        expectJson: Boolean = true,
        expectArray: Boolean = false,
    ): JSONObject? = runCatching {
        val request = Request.Builder()
            .url("$apiBase/torrents")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            when {
                expectArray -> JSONObject().put("items", JSONArray(body))
                expectJson -> JSONObject(body)
                else -> JSONObject()
            }
        }
    }.onFailure { error ->
        logger.debug(error) { "TorrServer API action failed: ${payload.optString("action")}" }
    }.getOrNull()

    private fun drainProcessOutput(process: Process) {
        outputThread = Thread({
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { logger.debug { "TorrServer: $it" } }
                }
            }
        }, "anikku-torrserver-output").apply {
            isDaemon = true
            start()
        }
    }

    private fun startProcessWatcher() {
        processWatcher?.cancel()
        processWatcher = scope.launch {
            while (isActive) {
                delay(2_000)
                if (serverProcess?.isAlive != true) {
                    _serverStatus.value = ServerStatus.ERROR
                    logger.warn { "TorrServer process exited unexpectedly" }
                    break
                }
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val VIDEO_EXTENSIONS = setOf(
            "3gp", "avi", "flv", "m2ts", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "mts", "ogm", "ts", "webm", "wmv",
        )

        fun createDefault(
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): TorrentServerBridge {
            val base = File(
                System.getProperty("user.home"),
                "Library${File.separator}Application Support${File.separator}Anikku",
            )
            return TorrentServerBridge(
                scope = scope,
                binDirectory = File(base, "torrserver/bin"),
                dataDirectory = File(base, "torrserver/data"),
            )
        }

        internal fun parseFiles(array: JSONArray?): List<TorrentFile> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                runCatching { array.getJSONObject(index) }.getOrNull()?.let { item ->
                    val id = item.optInt("id", -1)
                    val path = item.optString("path")
                    if (id <= 0 || path.isBlank()) null else TorrentFile(id, path, item.optLong("length", 0L))
                }
            }
        }

        internal fun selectPlayableFile(files: List<TorrentFile>): TorrentFile {
            require(files.isNotEmpty()) { "Torrent has no files" }
            val videos = files.filter { file ->
                file.path.substringAfterLast('.', "").lowercase(Locale.ROOT) in VIDEO_EXTENSIONS
            }
            return (videos.ifEmpty { files }).maxBy { it.length }
        }

        private fun parseTorrentList(array: JSONArray?): List<TorrentInfo> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                runCatching { array.getJSONObject(index) }.getOrNull()?.let { item ->
                    TorrentInfo(
                        hash = item.optString("hash"),
                        title = item.optString("title", item.optString("name", "Unknown")),
                        size = item.optLong("torrent_size", 0L),
                        progress = if (item.optLong("torrent_size", 0L) > 0L) {
                            (item.optDouble("loaded_size", 0.0) / item.optDouble("torrent_size", 1.0)).toFloat()
                        } else 0f,
                        status = item.optString("stat_string", "unknown"),
                        seeders = item.optInt("connected_seeders", 0),
                    )
                }
            }
        }

        private fun normalizedArchitecture(): String = when (System.getProperty("os.arch").lowercase(Locale.ROOT)) {
            "aarch64", "arm64" -> "arm64"
            else -> "amd64"
        }

        private fun packagedBinDirectory(): File? = System.getProperty("compose.application.resources.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it, "TorrServer") }

        private fun availableLoopbackPort(): Int = ServerSocket(0).use { it.localPort }
    }
}

enum class ServerStatus { STOPPED, STARTING, RUNNING, ERROR }

data class TorrentServerStream(
    val httpUrl: String,
    val torrentHash: String,
    val file: TorrentFile,
)

data class TorrentFile(val id: Int, val path: String, val length: Long)

data class TorrentInfo(
    val hash: String,
    val title: String,
    val size: Long = 0,
    val progress: Float = 0f,
    val status: String = "unknown",
    val seeders: Int = 0,
)

/**
 * CompositionLocal for the app-scoped TorrServer bridge (player streams +
 * Torrents-tab activity). Null when unavailable (failure-safe).
 */
val LocalTorrentServerBridge = androidx.compose.runtime.compositionLocalOf<TorrentServerBridge?> { null }
