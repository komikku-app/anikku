package app.anikku.macos.player

import app.anikku.macos.platform.torrent.TorrentServerBridge
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException

private val torrentLogger = KotlinLogging.logger {}

sealed class TorrentStreamingResult {
    data class Success(
        val httpUrl: String,
        val backend: String,
        internal val cleanup: () -> Unit,
    ) : TorrentStreamingResult()

    data class Failure(val message: String) : TorrentStreamingResult()
}

/** Uses the bundled native server first and retains WebTorrent as a safe fallback. */
class TorrentStreamingCoordinator(
    private val nativeBridge: TorrentServerBridge = TorrentServerBridge.createDefault(),
    private val webTorrentStart: suspend (String) -> MagnetStreamResult = MagnetStreamer::startStreaming,
) {
    suspend fun start(magnetUrl: String): TorrentStreamingResult {
        try {
            if (nativeBridge.start()) {
                val stream = nativeBridge.prepareStream(magnetUrl)
                if (stream != null) {
                    torrentLogger.info { "Native TorrServer selected ${stream.file.path} (${stream.file.length} bytes)" }
                    return TorrentStreamingResult.Success(stream.httpUrl, "TorrServer") {
                        nativeBridge.removeTorrent(stream.torrentHash)
                        nativeBridge.stop()
                    }
                }
                nativeBridge.stop()
                torrentLogger.warn { "Bundled TorrServer could not prepare the magnet; falling back to WebTorrent" }
            }

            return when (val fallback = webTorrentStart(magnetUrl)) {
                is MagnetStreamResult.Success -> TorrentStreamingResult.Success(fallback.httpUrl, "WebTorrent") {
                    MagnetStreamer.stopStreaming(fallback)
                }
                is MagnetStreamResult.Failure -> TorrentStreamingResult.Failure(fallback.message)
            }
        } catch (cancelled: CancellationException) {
            nativeBridge.stop()
            throw cancelled
        } catch (error: Exception) {
            nativeBridge.stop()
            torrentLogger.warn(error) { "Native torrent streaming failed; trying WebTorrent" }
            return when (val fallback = webTorrentStart(magnetUrl)) {
                is MagnetStreamResult.Success -> TorrentStreamingResult.Success(fallback.httpUrl, "WebTorrent") {
                    MagnetStreamer.stopStreaming(fallback)
                }
                is MagnetStreamResult.Failure -> TorrentStreamingResult.Failure(fallback.message)
            }
        }
    }

    fun stop(result: TorrentStreamingResult.Success) {
        runCatching(result.cleanup)
            .onFailure { torrentLogger.warn(it) { "Failed to stop ${result.backend} torrent stream" } }
    }
}
