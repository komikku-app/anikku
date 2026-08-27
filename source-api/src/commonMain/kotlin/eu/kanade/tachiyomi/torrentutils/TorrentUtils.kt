package eu.kanade.tachiyomi.torrentutils

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.torrentServer.TorrentHelpers
import eu.kanade.tachiyomi.torrentServer.TorrentServerApi
import eu.kanade.tachiyomi.torrentServer.model.Torrent
import eu.kanade.tachiyomi.torrentutils.model.DeadTorrentException
import eu.kanade.tachiyomi.torrentutils.model.TorrentFile
import eu.kanade.tachiyomi.torrentutils.model.TorrentInfo
import uy.kohesive.injekt.injectLazy
import java.net.SocketTimeoutException

object TorrentUtils {
    private val network: NetworkHelper by injectLazy()

    fun getTorrentInfo(
        url: String,
        title: String,
    ): TorrentInfo {
        val torrent: Torrent = if (url.startsWith("magnet")) {
            // Magnet links need to be added to the torrent server to retrieve their
            // information
            @Suppress("SwallowedException")
            try {
                TorrentServerApi.addTorrent(url, title, "", "", false)
            } catch (e: SocketTimeoutException) {
                throw DeadTorrentException()
            }
        } else {
            // For torrent files we can parse the information out of the file itself
            // without starting the torrent server
            network.client.newCall(GET(url)).execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpException(response.code)
                }
                TorrentHelpers.parseTorrentDetailsFromTorrentFileContent(response.body.byteStream())
            }
        }
        return torrentToTorrentInfo(torrent, title)
    }

    private fun torrentToTorrentInfo(torrent: Torrent, overrideTitle: String?): TorrentInfo {
        return TorrentInfo(
            overrideTitle ?: torrent.title,
            torrent.file_stats?.map { file ->
                TorrentFile(file.path, file.id ?: 0, file.length, torrent.hash!!, torrent.trackers ?: emptyList())
            } ?: emptyList(),
            torrent.hash!!,
            torrent.torrent_size!!,
            torrent.trackers ?: emptyList(),
        )
    }
}
