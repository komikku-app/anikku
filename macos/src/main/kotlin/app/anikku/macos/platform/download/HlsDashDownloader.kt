package app.anikku.macos.platform.download

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

private val hlsLogger = KotlinLogging.logger {}

/**
 * True for HLS/DASH manifest URLs — downloading these raw saves a text
 * playlist, not the episode.
 */
internal fun isManifestUrl(url: String): Boolean {
    val lower = url.lowercase()
    if (lower.endsWith(".m3u8") || lower.endsWith(".mpd")) return true
    return lower.contains("/dash/") || lower.contains("/hls/") ||
        lower.contains("/manifest") || lower.contains(".m3u8?") || lower.contains(".mpd?")
}

/**
 * Downloads an HLS (.m3u8) or DASH (.mpd) manifest plus every segment it
 * references, then writes a LOCAL playlist that mpv plays offline.
 *
 * Used by the download manager when a source only offers a manifest URL —
 * saving the manifest itself yields a useless text file. The playlist + a
 * `segments/` subfolder are written under one target directory so the app's
 * local HTTP server can serve them as one unit.
 */
class HlsDashDownloader(private val httpClient: OkHttpClient) {

    /** The local playlist file (references `segments/...` relative to its dir). */
    class Result(val playlistFile: File)

    suspend fun download(
        manifestUrl: String,
        headers: Map<String, String>?,
        targetDir: File,
        onProgress: ((downloaded: Int, total: Int) -> Unit)? = null,
    ): Result? = when {
        isDash(manifestUrl) -> downloadDash(manifestUrl, headers, targetDir, onProgress)
        else -> downloadHls(manifestUrl, headers, targetDir, onProgress)
    }

    // ------------------------------------------------------------------ HLS

    private suspend fun downloadHls(
        manifestUrl: String,
        headers: Map<String, String>?,
        targetDir: File,
        onProgress: ((Int, Int) -> Unit)?,
    ): Result? {
        val first = fetchText(manifestUrl, headers) ?: return null

        // Master playlist → pick the highest-bandwidth variant.
        val playlistUrl = if (first.contains("#EXT-X-STREAM-INF")) {
            pickBestVariant(first, manifestUrl) ?: return null
        } else {
            manifestUrl
        }
        val text = if (playlistUrl == manifestUrl) first
        else fetchText(playlistUrl, headers) ?: return null

        val segmentsDir = File(targetDir, "segments").apply { mkdirs() }
        val out = StringBuilder()
        var segmentIndex = 0
        var keyIndex = 0
        var initIndex = 0
        val pendingExtinf = StringBuilder()

        for (line in text.lines()) {
            when {
                line.isBlank() -> Unit
                line.startsWith("#EXT-X-KEY:") -> {
                    val method = Regex("METHOD=([^,]+)").find(line)?.groupValues?.get(1)
                    val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                    if (uri != null && method?.trim()?.uppercase() != "NONE") {
                        val remote = resolveUrl(playlistUrl, uri)
                        val local = "key_${keyIndex++}.key"
                        if (downloadFile(remote, File(segmentsDir, local), headers)) {
                            out.append(line.replace(Regex("URI=\"[^\"]*\""), "URI=\"segments/$local\"")).append('\n')
                        } else {
                            hlsLogger.warn { "HLS: failed to download key $remote" }
                            return null
                        }
                    } else {
                        out.append(line).append('\n')
                    }
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                    if (uri != null) {
                        val remote = resolveUrl(playlistUrl, uri)
                        val local = "init_${initIndex++}.m4s"
                        if (downloadFile(remote, File(segmentsDir, local), headers)) {
                            out.append(line.replace(Regex("URI=\"[^\"]*\""), "URI=\"segments/$local\"")).append('\n')
                        } else {
                            hlsLogger.warn { "HLS: failed to download init segment $remote" }
                            return null
                        }
                    } else {
                        out.append(line).append('\n')
                    }
                }
                line.startsWith("#EXTINF") -> {
                    pendingExtinf.append(line).append('\n')
                }
                line.startsWith("#") -> {
                    out.append(line).append('\n')
                }
                else -> {
                    // A segment URI line.
                    val remote = resolveUrl(playlistUrl, line.trim())
                    val ext = remote.substringAfterLast('.', "ts").substringBefore('?').substringBefore('#')
                        .takeIf { it.length in 2..4 } ?: "ts"
                    val local = "seg_${segmentIndex.toString().padStart(5, '0')}.$ext"
                    if (downloadFile(remote, File(segmentsDir, local), headers)) {
                        out.append(pendingExtinf)
                        out.append("segments/$local").append('\n')
                        pendingExtinf.clear()
                        segmentIndex++
                        onProgress?.invoke(segmentIndex, -1)
                    } else {
                        hlsLogger.warn { "HLS: failed to download segment $remote — aborting" }
                        return null
                    }
                }
            }
        }
        if (segmentIndex == 0) return null

        val playlistFile = File(targetDir, "playlist.m3u8")
        playlistFile.writeText(out.toString())
        hlsLogger.info { "HLS: downloaded $segmentIndex segments → ${playlistFile.name}" }
        return Result(playlistFile)
    }

    private fun pickBestVariant(master: String, masterUrl: String): String? {
        val lines = master.lines()
        var best: Pair<Long, String>? = null // (bandwidth, url)
        var pendingBandwidth: Long = -1
        for (line in lines) {
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                pendingBandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: -1
            } else if (!line.startsWith("#") && line.isNotBlank()) {
                val url = resolveUrl(masterUrl, line.trim())
                if (pendingBandwidth > (best?.first ?: -1L)) {
                    best = pendingBandwidth to url
                }
                pendingBandwidth = -1
            }
        }
        return best?.second
    }

    // ----------------------------------------------------------------- DASH

    private fun isDash(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mpd") || lower.contains(".mpd?") || lower.contains("/dash/")
    }

    private suspend fun downloadDash(
        mpdUrl: String,
        headers: Map<String, String>?,
        targetDir: File,
        onProgress: ((Int, Int) -> Unit)?,
    ): Result? {
        val xml = fetchText(mpdUrl, headers) ?: return null
        val doc = try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(xml.byteInputStream())
        } catch (e: Exception) {
            return null
        }

        val baseDir = mpdUrl.substringBeforeLast('/')
        val reps = doc.getElementsByTagName("Representation")
        // Pick the video representation with the highest bandwidth.
        var best: Element? = null
        var bestBandwidth = -1L
        for (i in 0 until reps.length) {
            val rep = reps.item(i) as Element
            val mime = rep.getAttribute("mimeType")
            if (mime.startsWith("video")) {
                val bw = rep.getAttribute("bandwidth").toLongOrNull() ?: -1L
                if (bw > bestBandwidth) {
                    bestBandwidth = bw
                    best = rep
                }
            }
        }
        val videoRep = best ?: run {
            return null
        }
        val repId = videoRep.getAttribute("id")

        // Walk up to the SegmentTemplate + any BaseURL (parents first).
        var node: Element? = videoRep
        var segmentTemplate: Element? = null
        var baseUrlHref: String? = null
        while (node != null) {
            if (segmentTemplate == null) {
                node.getElementsByTagName("SegmentTemplate")?.let { list ->
                    if (list.length > 0) segmentTemplate = list.item(0) as Element
                }
            }
            if (baseUrlHref == null) {
                node.getElementsByTagName("BaseURL")?.let { list ->
                    if (list.length > 0) baseUrlHref = list.item(0).textContent.trim()
                }
            }
            node = node.parentNode as? Element
        }
        val tpl = segmentTemplate ?: run {
            return null
        }
        val initTemplate = tpl.getAttribute("initialization")
        val mediaTemplate = tpl.getAttribute("media")
        if (mediaTemplate.isBlank()) {
            return null
        }
        val startNumber = tpl.getAttribute("startNumber").toLongOrNull() ?: 1L

        // Segment count from the SegmentTimeline (S elements + repeat counts).
        var segmentCount = 0
        val durationsSeconds = mutableListOf<Double>()
        val timeline = tpl.getElementsByTagName("SegmentTimeline")
        val timescale = tpl.getAttribute("timescale").toLongOrNull() ?: 1L
        if (timeline.length > 0) {
            val sNodes = (timeline.item(0) as Element).getElementsByTagName("S")
            for (i in 0 until sNodes.length) {
                val s = sNodes.item(i) as Element
                val d = s.getAttribute("d").toLongOrNull() ?: 0L
                val repeat = s.getAttribute("r").toLongOrNull() ?: 0L
                val count = (repeat + 1).coerceAtLeast(1)
                segmentCount += count.toInt()
                for (r in 0 until count.toInt()) {
                    durationsSeconds.add(if (timescale > 0) d / timescale.toDouble() else 0.0)
                }
            }
        }
        if (segmentCount <= 0) {
            return null
        }

        fun formatTemplate(template: String, number: Long, time: Long?): String {
            var t = template.replace("\$RepresentationID\$", repId)
                .replace("\$Bandwidth\$", bestBandwidth.toString())
            // $Number$ or $Number%05d$
            t = Regex("\\\$Number(?:%0(\\d+)d)?\\\$").replace(t) { m ->
                val width = m.groupValues[1].ifBlank { "0" }.toInt()
                if (width > 0) number.toString().padStart(width, '0') else number.toString()
            }
            if (time != null) {
                t = t.replace("\$Time\$", time.toString())
            }
            return t
        }

        val segmentsDir = File(targetDir, "segments").apply { mkdirs() }
        val baseHref = baseUrlHref?.trim()?.takeIf { it.isNotBlank() }
        val segmentBase = if (baseHref != null && baseHref.startsWith("http")) {
            baseHref
        } else {
            // Some sources (e.g. av1encodes) serve the MPD from a token PATH
            // with no file extension — the token is a directory, and segments
            // resolve against "<token>/". Standard "…/manifest.mpd" URLs are
            // files, so segments resolve against the parent directory instead.
            val lastSegment = mpdUrl.substringAfterLast('/')
            if (Regex("""\.[A-Za-z0-9]{2,5}$""").containsMatchIn(lastSegment)) baseDir else "$mpdUrl/"
        }

        val segmentBaseDir = if (segmentBase.endsWith('/')) segmentBase else "$segmentBase/"
        // Download the init segment (if the template defines one).
        if (initTemplate.isNotBlank()) {
            val initUrl = resolveToFull(segmentBaseDir, formatTemplate(initTemplate, startNumber, null))
            val localInit = File(segmentsDir, "init.m4s")
            if (!downloadFile(initUrl, localInit, headers)) {
                return null
            }
        }

        // Download each numbered segment (stop after a run of 404s).
        val out = StringBuilder()
        out.append("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:10\n")
        if (initTemplate.isNotBlank()) {
            out.append("#EXT-X-MAP:URI=\"segments/init.m4s\"\n")
        }
        var downloaded = 0
        var missingStreak = 0
        for (n in 0 until segmentCount) {
            currentCoroutineContext().ensureActive()
            val number = startNumber + n
            val segUrl = resolveToFull(segmentBaseDir, formatTemplate(mediaTemplate, number, null))
            val local = File(segmentsDir, "seg_${n.toString().padStart(5, '0')}.m4s")
            if (downloadFile(segUrl, local, headers)) {
                val dur = durationsSeconds.getOrNull(n) ?: 10.0
                out.append("#EXTINF:${dur},\nsegments/${local.name}\n")
                downloaded++
                missingStreak = 0
                onProgress?.invoke(downloaded, segmentCount)
            } else {
                missingStreak++
                if (missingStreak >= 2) break // segment count over-estimated
            }
        }
        if (downloaded == 0) {
            return null
        }
        out.append("#EXT-X-ENDLIST\n")
        val playlistFile = File(targetDir, "playlist.m3u8")
        playlistFile.writeText(out.toString())
        hlsLogger.info { "DASH: downloaded $downloaded segments → ${playlistFile.name}" }
        return Result(playlistFile)
    }

    private fun resolveToFull(base: String, url: String): String = resolveUrl(base, url)

    // ------------------------------------------------------------- helpers

    private fun resolveUrl(baseUrl: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return try {
            URI(baseUrl).resolve(URLDecoder.decode(ref, "UTF-8").replace(" ", "%20")).toString()
        } catch (e: Exception) {
            "$baseUrl/$ref"
        }
    }

    private suspend fun fetchText(url: String, headers: Map<String, String>?): String? {
        val call = newCall(url, headers)
        return try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (e: Exception) {
            hlsLogger.warn(e) { "HLS: fetch failed $url" }
            null
        }
    }

    private suspend fun downloadFile(url: String, target: File, headers: Map<String, String>?): Boolean {
        currentCoroutineContext().ensureActive()
        return try {
            newCall(url, headers).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@use false
                }
                resp.body?.byteStream()?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: false
                target.isFile && target.length() > 0
            }
        } catch (e: Exception) {
            hlsLogger.warn(e) { "HLS: segment download failed $url" }
            false
        }
    }

    private fun newCall(url: String, headers: Map<String, String>?): okhttp3.Call {
        val rb = Request.Builder().url(url)
        headers?.forEach { (k, v) -> rb.header(k, v) }
        rb.header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/129.0.0.0 Safari/537.36")
        return httpClient.newCall(rb.build())
    }
}
