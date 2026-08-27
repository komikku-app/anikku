@file:Suppress("unused")

package eu.kanade.tachiyomi.animeextension.en.nyaasi

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import rx.Observable
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Nyaa.si torrent search source for Anikku macOS.
 *
 * This source scrapes Nyaa.si for anime torrents and returns magnet links
 * as playable video URLs. The player (PlayerViewModel + MagnetStreamer) handles
 * converting magnet links into a playable HTTP stream via webtorrent-cli.
 *
 * Architecture:
 *   Search → torrent results as SAnime items
 *   Details → torrent info page
 *   Episode list → single SEpisode per torrent (magnet link stored in url)
 *   Video list → Video with videoUrl = magnet:?xt=urn:btih:...
 *   Player → MagnetStreamer spawns webtorrent to create local HTTP server
 *         → mpv plays from local HTTP URL
 */
class NyaaSi : AnimeCatalogueSource {

    // ── Source Identity ─────────────────────────────────────────────

    override val id: Long by lazy { generateId() }
    override val name: String = "Nyaa.si"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    private val baseUrl = "https://nyaa.si"
    private val categoryAnime = "1_0" // Anime - All subcategories

    /**
     * Generate a deterministic ID for this source.
     * Uses the same algorithm as AnimeHttpSource.generateId().
     */
    private fun generateId(): Long {
        val key = "nyaa.si/en/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    // ── HTTP Helper ─────────────────────────────────────────────────

    /**
     * Fetch and parse an HTML document from the given URL.
     *
     * Uses java.net.HttpURLConnection (java.base) rather than the JDK's
     * java.net.http client: the packaged macOS runtime is a minimized jlink
     * image that does NOT include the java.net.http module, so referencing
     * java.net.http.HttpClient throws NoClassDefFoundError in the packaged app
     * (seen when opening the Torrents tab). HttpURLConnection is always
     * available and handles redirects + timeouts the same way.
     */
    private fun fetchDocument(url: String): Document {
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
            )
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        try {
            val stream = connection.inputStream
            return try {
                // Nyaa serves UTF-8; Jsoup reads the charset from the content type.
                Jsoup.parse(stream, "UTF-8", url)
            } finally {
                stream.close()
            }
        } finally {
            connection.disconnect()
        }
    }

    // ── Popular Anime (browse default) ──────────────────────────────

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        // Nyaa.si home page with anime category, sorted by date (default)
        val url = "$baseUrl/?f=0&c=$categoryAnime&p=$page"
        return parseAnimePage(url)
    }

    // ── Search Anime ────────────────────────────────────────────────

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/?f=0&c=$categoryAnime&q=$encodedQuery&p=$page"
        return parseAnimePage(url)
    }

    // ── Latest Updates ──────────────────────────────────────────────

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        // Same as popular — Nyaa.si sorts by upload date by default
        val url = "$baseUrl/?f=0&c=$categoryAnime&s=id&o=desc&p=$page"
        return parseAnimePage(url)
    }

    // ── Page Parser ─────────────────────────────────────────────────

    /**
     * Fetch a Nyaa.si search/browse page and parse the torrent listing.
     */
    private fun parseAnimePage(url: String): AnimesPage {
        val doc = fetchDocument(url)
        val rows = doc.select("table.torrent-list tbody tr")
        val animes = rows.mapNotNull { row -> parseTorrentRow(row) }
        // Check for next page: Nyaa.si uses pagination with <ul class="pagination">
        // If the page has a next-page link (active page + 1)
        val hasNextPage = doc.select("ul.pagination li.active + li a").isNotEmpty()
        return AnimesPage(animes, hasNextPage)
    }

    /**
     * Parse a single torrent table row into an SAnime item.
     */
    private fun parseTorrentRow(row: org.jsoup.nodes.Element): SAnime? {
        // Title: 2nd column, last <a> tag (skip comment links)
        val titleCell = row.select("td:nth-child(2)")
        val titleLink = titleCell.select("a").last() ?: return null
        val href = titleLink.attr("href")
        val title = titleLink.text().trim()
        if (href.isBlank() || title.isBlank()) return null

        // Magnet link: 3rd column
        val magnetLink = row.select("td:nth-child(3) a[href^=\"magnet:\"]").attr("href")

        // Size, seeders, leechers, downloads
        val size = row.select("td:nth-child(4)").text().trim()
        val seeders = row.select("td:nth-child(5)").text().trim()
        val leechers = row.select("td:nth-child(6)").text().trim()
        val downloads = row.select("td:nth-child(7)").text().trim()

        val anime = SAnime.create()
        anime.url = href // /view/{torrentId}
        anime.title = title
        anime.description = buildString {
            append("Size: $size")
            if (seeders.isNotBlank() && seeders != "0") append(" ▲$seeders")
            if (leechers.isNotBlank() && leechers != "0") append(" ▼$leechers")
            if (downloads.isNotBlank()) append(" ⬇$downloads")
        }
        anime.genre = "Torrent"
        anime.author = magnetLink.ifBlank { null } // Store magnet for getEpisodeList
        return anime
    }

    // ── Anime Details ───────────────────────────────────────────────

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        // Fetch the torrent detail page for more info
        return try {
            val doc = fetchDocument("$baseUrl${anime.url}")
            parseAnimeDetails(doc, anime)
        } catch (_: Exception) {
            // Fall back to search result data
            anime
        }
    }

    /**
     * Parse the torrent detail page for additional information.
     */
    private fun parseAnimeDetails(doc: Document, existing: SAnime): SAnime {
        // Get the magnet link from the detail page
        val magnetLink = doc.select("a[href^=\"magnet:\"]").attr("href")

        // Parse description from the torrent info panel
        val infoRows = doc.select("div.torrent-info-row")
        var size = ""
        var dateStr = ""
        for (row in infoRows) {
            val label = row.select("strong").text().lowercase()
            val value = row.text().substringAfter(":").trim()
            when {
                "size" in label -> size = value
                "date" in label -> dateStr = value
            }
        }

        val anime = SAnime.create()
        anime.url = existing.url
        anime.title = existing.title
        anime.author = magnetLink.ifBlank { existing.author } // Preserve magnet link
        anime.description = buildString {
            val existingDesc = existing.description
            if (!existingDesc.isNullOrBlank()) append(existingDesc)
            if (size.isNotBlank()) {
                if (isNotEmpty()) append(" | ")
                append("Size: $size")
            }
        }
        anime.genre = existing.genre ?: "Torrent"
        anime.thumbnail_url = existing.thumbnail_url
        return anime
    }

    // ── Episode List ────────────────────────────────────────────────

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        // Get magnet link: either from the SAnime (stored in author during search),
        // or refetch the detail page to get it.
        val magnetLink = if (!anime.author.isNullOrBlank()) {
            anime.author
        } else {
            try {
                val doc = fetchDocument("$baseUrl${anime.url}")
                doc.select("a[href^=\"magnet:\"]").attr("href")
            } catch (_: Exception) {
                null
            }
        }

        val episode = SEpisode.create()
        episode.url = magnetLink ?: "" // Store magnet link for getVideoList
        episode.name = anime.title
        episode.episode_number = 1f
        episode.date_upload = System.currentTimeMillis()
        episode.scanlator = "Nyaa.si"
        episode.summary = anime.description
        return listOf(episode)
    }

    // ── Video List ──────────────────────────────────────────────────

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val magnetUrl = episode.url
        if (magnetUrl.isBlank() || !magnetUrl.startsWith("magnet:")) {
            // Fall back to a placeholder — won't work but avoids crash
            return listOf(
                Video(
                    videoUrl = "https://nyaa.si/static/favicon.png",
                    videoTitle = "No magnet link available",
                ),
            )
        }

        return listOf(
            Video(
                videoUrl = magnetUrl,
                videoTitle = "Stream via Torrent",
                resolution = 720,
                headers = null,
                preferred = true,
            ),
            // Second option with different quality label
            Video(
                videoUrl = magnetUrl,
                videoTitle = "Torrent (Best Available)",
                resolution = 1080,
                headers = null,
                preferred = false,
            ),
        )
    }

    // ── Filters ─────────────────────────────────────────────────────

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    // ── RxJava Stubs ────────────────────────────────────────────────

    @Deprecated("Use suspend API", ReplaceWith("getPopularAnime"))
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> =
        throw UnsupportedOperationException("Use suspend API instead")

    @Deprecated("Use suspend API", ReplaceWith("getSearchAnime"))
    override fun fetchSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Observable<AnimesPage> =
        throw UnsupportedOperationException("Use suspend API instead")

    @Deprecated("Use suspend API", ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> =
        throw UnsupportedOperationException("Use suspend API instead")

    @Deprecated("Use suspend API", ReplaceWith("getAnimeDetails"))
    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> =
        throw UnsupportedOperationException("Use suspend API instead")

    @Deprecated("Use suspend API", ReplaceWith("getEpisodeList"))
    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> =
        throw UnsupportedOperationException("Use suspend API instead")

    @Deprecated("Use suspend API", ReplaceWith("getVideoList"))
    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> =
        throw UnsupportedOperationException("Use suspend API instead")
}
