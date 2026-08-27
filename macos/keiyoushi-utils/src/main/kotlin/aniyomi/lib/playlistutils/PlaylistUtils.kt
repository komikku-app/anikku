package aniyomi.lib.playlistutils

import android.net.Uri
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.UrlUtils
import keiyoushi.utils.bodyString
import keiyoushi.utils.commonEmptyHeaders
import keiyoushi.utils.parallelMapNotNullBlocking
import keiyoushi.utils.useAsJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import kotlin.math.abs

class PlaylistUtils(private val client: OkHttpClient, private val headers: Headers = commonEmptyHeaders) {

    // ================================ M3U8 ================================

    /**
     * Extracts videos from a .m3u8 file.
     */
    fun extractFromHls(
        playlistUrl: String,
        referer: String = playlistUrl.toDefaultReferer(),
        masterHeaders: Headers,
        videoHeaders: Headers,
        videoNameGen: (String) -> String = { quality -> quality },
        subtitleList: List<Track> = emptyList(),
        audioList: List<Track> = emptyList(),
        toStandardQuality: (String) -> String = { quality ->
            stnQuality(quality)
        },
    ): List<Video> = extractFromHls(
        playlistUrl,
        referer,
        { _, _ -> masterHeaders },
        { _, _, _ -> videoHeaders },
        videoNameGen,
        subtitleList,
        audioList,
        toStandardQuality,
    )

    /**
     * Extracts videos from a .m3u8 file with configurable header generators.
     */
    fun extractFromHls(
        playlistUrl: String,
        referer: String = playlistUrl.toDefaultReferer(),
        masterHeadersGen: (Headers, String) -> Headers = ::generateMasterHeaders,
        videoHeadersGen: (Headers, String, String) -> Headers = { baseHeaders, referer, _ ->
            generateMasterHeaders(baseHeaders, referer)
        },
        videoNameGen: (String) -> String = { quality -> quality },
        subtitleList: List<Track> = emptyList(),
        audioList: List<Track> = emptyList(),
        toStandardQuality: (String) -> String = { quality ->
            stnQuality(quality)
        },
    ): List<Video> {
        val masterHeaders = masterHeadersGen(headers, referer)

        val masterPlaylist = client.newCall(GET(playlistUrl, masterHeaders))
            .execute().bodyString()

        // Check if there isn't multiple streams available
        if (PLAYLIST_SEPARATOR !in masterPlaylist) {
            return listOf(
                Video(
                    playlistUrl,
                    videoNameGen("Video"),
                    playlistUrl,
                    headers = masterHeaders,
                    subtitleTracks = subtitleList,
                    audioTracks = audioList,
                ),
            )
        }

        // Get subtitles
        val subtitleTracks = subtitleList + SUBTITLE_REGEX.findAll(masterPlaylist).mapNotNull {
            Track(
                UrlUtils.fixUrl(it.groupValues[2], playlistUrl) ?: return@mapNotNull null,
                it.groupValues[1],
            )
        }.toList()

        // Get audio tracks
        val audioTracks = audioList + AUDIO_REGEX.findAll(masterPlaylist).mapNotNull {
            Track(
                UrlUtils.fixUrl(it.groupValues[2], playlistUrl) ?: return@mapNotNull null,
                it.groupValues[1],
            )
        }.toList()

        return masterPlaylist.substringAfter(PLAYLIST_SEPARATOR).split(PLAYLIST_SEPARATOR).mapNotNull { stream ->
            val codec = CODECS_REGEX.find(stream)?.groupValues?.get(1)
            if (!codec.isNullOrBlank()) {
                val codecs = codec.split(',')
                if (codecs.all { it.startsWith("mp4a") }) return@mapNotNull null
            }

            val resolution = RESOLUTION_REGEX.find(stream)
                ?.groupValues?.get(1)
                ?.let { resolution ->
                    val standardQuality = QUALITY_REGEX.find(resolution)
                        ?.groupValues?.get(1)
                        ?.let { toStandardQuality(it) }

                    if (!standardQuality.isNullOrBlank()) {
                        "$standardQuality ($resolution)"
                    } else {
                        resolution
                    }
                }
            val bandwidth = BANDWIDTH_REGEX.find(stream)
                ?.groupValues?.get(1)
                ?.toLongOrNull()
            val bandwidthFormatted = bandwidth
                ?.let(::formatBytes)
            val streamName = listOfNotNull(resolution, bandwidthFormatted).joinToString(" - ")
                .takeIf { it.isNotBlank() }
                ?: "Video"

            val videoUrl = stream.substringAfter("\n").substringBefore("\n").let { url ->
                UrlUtils.fixUrl(url, playlistUrl)?.trimEnd()
            } ?: return@mapNotNull null

            bandwidth to Video(
                url = videoUrl,
                quality = videoNameGen(streamName),
                videoUrl = videoUrl,
                headers = videoHeadersGen(headers, referer, videoUrl),
                subtitleTracks = subtitleTracks,
                audioTracks = audioTracks,
            )
        }
            .sortedByDescending { (bandwidth, _) ->
                bandwidth ?: 0L
            }
            .map { (_, video) -> video }
    }

    fun generateMasterHeaders(baseHeaders: Headers, referer: String): Headers = baseHeaders.newBuilder().apply {
        set("Accept", "*/*")
        if (referer.isNotEmpty()) {
            set("Origin", "https://${referer.toHttpUrl().host}")
            set("Referer", referer)
        }
    }.build()

    // ================================ DASH ================================

    @Suppress("unused")
    fun extractFromDash(
        mpdUrl: String,
        videoNameGen: (String) -> String,
        mpdHeaders: Headers,
        videoHeaders: Headers,
        referer: String = mpdUrl.toDefaultReferer(),
        subtitleList: List<Track> = emptyList(),
        audioList: List<Track> = emptyList(),
        toStandardQuality: (String) -> String = { quality ->
            stnQuality(quality)
        },
    ): List<Video> = extractFromDash(
        mpdUrl,
        { videoRes, bandwidth ->
            videoNameGen(videoRes) + " - ${formatBytes(bandwidth.toLongOrNull())}"
        },
        referer,
        { _, _ -> mpdHeaders },
        { _, _, _ -> videoHeaders },
        subtitleList,
        audioList,
        toStandardQuality,
    )

    @Suppress("MemberVisibilityCanBePrivate")
    fun extractFromDash(
        mpdUrl: String,
        videoNameGen: (String, String) -> String,
        referer: String = mpdUrl.toDefaultReferer(),
        mpdHeadersGen: (Headers, String) -> Headers = ::generateMasterHeaders,
        videoHeadersGen: (Headers, String, String) -> Headers = { baseHeaders, referer, _ ->
            generateMasterHeaders(baseHeaders, referer)
        },
        subtitleList: List<Track> = emptyList(),
        audioList: List<Track> = emptyList(),
        toStandardQuality: (String) -> String = { quality ->
            stnQuality(quality)
        },
    ): List<Video> {
        val mpdHeaders = mpdHeadersGen(headers, referer)

        val doc = client.newCall(GET(mpdUrl, mpdHeaders))
            .execute().useAsJsoup()

        // Get audio tracks
        val audioTracks = audioList + doc.select("Representation[mimetype~=audio]").map { audioSrc ->
            val bandwidth = audioSrc.attr("bandwidth").toLongOrNull()
            Track(audioSrc.text(), formatBytes(bandwidth))
        }

        return doc.select("Representation[mimetype~=video]").map { videoSrc ->
            val bandwidth = videoSrc.attr("bandwidth")
            val res = videoSrc.attr("height")
                .let(toStandardQuality)
                .let { "$it (${videoSrc.attr("width")}x${videoSrc.attr("height")})" }
            val videoUrl = videoSrc.text()

            Video(
                videoUrl,
                videoNameGen(res, bandwidth),
                videoUrl,
                audioTracks = audioTracks,
                subtitleTracks = subtitleList,
                headers = videoHeadersGen(headers, referer, videoUrl),
            )
        }
    }

    private fun formatBytes(bytes: Long?): String = when {
        bytes == null -> ""
        bytes >= 1_000_000_000 -> "%.2f GB/s".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.2f MB/s".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.2f KB/s".format(bytes / 1_000.0)
        bytes > 1 -> "$bytes bytes/s"
        bytes == 1L -> "$bytes byte/s"
        else -> ""
    }

    // ============================= Utilities ==============================

    private fun String.toDefaultReferer(): String = try {
        toHttpUrl().run { "$scheme://$host/" }
    } catch (_: IllegalArgumentException) {
        ""
    }

    private fun stnQuality(quality: String): String {
        val intQuality = quality.trim().toIntOrNull() ?: return quality
        val result = STANDARD_QUALITIES.minByOrNull { abs(it - intQuality) } ?: intQuality
        return "${result}p"
    }

    private fun cleanSubtitleData(matchResult: MatchResult): String {
        val lineCount = matchResult.groupValues[1].count { it == '\n' }
        return "\n" + "&nbsp;\n".repeat(lineCount - 1)
    }

    fun fixSubtitles(subtitleList: List<Track>): List<Track> = subtitleList.parallelMapNotNullBlocking {
        runCatching {
            val subData = client.newCall(GET(it.url))
                .awaitSuccess().bodyString()

            val file = File.createTempFile("subs", "vtt")
                .also(File::deleteOnExit)

            file.writeText(FIX_SUBTITLE_REGEX.replace(subData, ::cleanSubtitleData))
            val uri = Uri.fromFile(file)

            Track(uri.toString(), it.lang)
        }.getOrNull()
    }

    companion object {
        private val FIX_SUBTITLE_REGEX = Regex("""$(\n{2,})(?!(?:\d+:)*\d+(?:\.\d+)?\s-+>\s(?:\d+:)*\d+(?:\.\d+)?)""", RegexOption.MULTILINE)

        private const val PLAYLIST_SEPARATOR = "#EXT-X-STREAM-INF:"

        private val SUBTITLE_REGEX by lazy { Regex("""#EXT-X-MEDIA:TYPE=SUBTITLES.*?NAME="(.*?)".*?URI="(.*?)"""") }
        private val AUDIO_REGEX by lazy { Regex("""#EXT-X-MEDIA:TYPE=AUDIO.*?NAME="(.*?)".*?URI="(.*?)"""") }

        private val CODECS_REGEX by lazy { Regex("""CODECS="([^"]+)"""") }
        private val RESOLUTION_REGEX by lazy { Regex("""RESOLUTION=([xX\d]+)""") }
        private val QUALITY_REGEX by lazy { Regex("""[xX](\d+)""") }
        private val BANDWIDTH_REGEX by lazy { Regex("""BANDWIDTH=(\d+)""") }

        private val STANDARD_QUALITIES = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160)
    }
}
