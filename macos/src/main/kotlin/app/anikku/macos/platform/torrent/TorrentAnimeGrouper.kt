package app.anikku.macos.platform.torrent

import app.anikku.macos.platform.library.AnimeSourceMatcher

/**
 * A single Nyaa torrent release. Everything the app needs to display and play
 * it — the magnet, its detail page, and the size/seeder info shown on Nyaa.
 */
data class TorrentRelease(
    /** Magnet URI handed to the player's torrent engine. */
    val magnetUrl: String,
    /** Nyaa detail page (`/view/{id}`) used as the anime URL for the player. */
    val pageUrl: String,
    /** The raw Nyaa filename. */
    val rawTitle: String,
    val parsed: ParsedTorrent,
    /** Nyaa row info, e.g. "Size: 1.2 GiB ▲123 ▼4 ⬇56". */
    val sizeSeeders: String,
)

/**
 * One episode of a season: the best-quality release plus the alternative
 * releases of the same episode (other sub groups / lower qualities).
 */
data class TorrentEpisodeRow(
    val episode: Int,
    val best: TorrentRelease,
    val alternatives: List<TorrentRelease>,
)

/** One season of one anime, episodes ordered ascending. */
data class TorrentSeason(
    val season: Int,
    val episodes: List<TorrentEpisodeRow>,
)

/**
 * All releases of one anime, grouped by season. Batch (multi-episode) releases
 * and unparseable entries are separated out rather than merged into episode
 * rows, so nothing is ever hidden.
 */
data class TorrentGroup(
    /** Clean human-readable title (from the most common parsed filename). */
    val displayTitle: String,
    /** Grouping key: `AnimeSourceMatcher.normalizeTitle(displayTitle)`. */
    val normalizedKey: String,
    /** Seasons sorted ascending (1-based; unparsed seasons collapse into 1). */
    val seasons: List<TorrentSeason>,
    /** Multi-episode / complete-series releases, best first. */
    val batches: List<TorrentRelease>,
    /** Releases that didn't parse into an episode (movies, specials, odd names). */
    val other: List<TorrentRelease>,
    /** Raw count of releases folded into this group. */
    val totalReleases: Int,
) {
    val episodeCount: Int get() = seasons.sumOf { it.episodes.size }
    val seasonCount: Int get() = seasons.size
}

/**
 * Groups raw Nyaa releases into [TorrentGroup]s. Pure and deterministic.
 *
 * Grouping key is the normalized CLEAN title WITHOUT the season, so every
 * season of a show lands in the same group and the season becomes a sub-list
 * — that's what makes "all seasons, all episodes, in order" possible from the
 * flat per-file Nyaa search results.
 */
object TorrentAnimeGrouper {

    /** Quality rank used to pick the "best" release of an episode. */
    private fun qualityRank(quality: String?): Int = when (quality?.uppercase()) {
        "2160P", "4K", "UHD", "8K" -> 4
        "1440P" -> 3
        "1080P" -> 2
        "720P" -> 1
        else -> 0
    }

    /** Seeder count parsed from the Nyaa description ("Size: X ▲123 …"). */
    private fun seederCount(release: TorrentRelease): Int {
        val match = Regex("▲(\\d+)").find(release.sizeSeeders)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /**
     * Group [releases] into anime groups, sorted by release count (desc) then
     * title (asc) so the most active shows float to the top.
     */
    fun group(releases: List<TorrentRelease>): List<TorrentGroup> {
        val byKey = LinkedHashMap<String, MutableList<TorrentRelease>>()
        for (release in releases) {
            val key = AnimeSourceMatcher.normalizeTitle(release.parsed.title)
            if (key.isEmpty()) continue
            byKey.getOrPut(key) { mutableListOf() }.add(release)
        }

        return byKey.map { (key, groupReleases) ->
            buildGroup(key, groupReleases)
        }.sortedWith(
            compareByDescending<TorrentGroup> { it.totalReleases }
                .thenBy { it.displayTitle.lowercase() },
        )
    }

    private fun buildGroup(key: String, releases: List<TorrentRelease>): TorrentGroup {
        val displayTitle = releases
            .firstOrNull { !it.parsed.title.isBlank() }?.parsed?.title
            ?: releases.first().rawTitle

        // Split into seasons, batches, and unparseable leftovers.
        val seasonsMap = LinkedHashMap<Int, MutableList<TorrentRelease>>()
        val batches = mutableListOf<TorrentRelease>()
        val other = mutableListOf<TorrentRelease>()

        for (release in releases) {
            val p = release.parsed
            when {
                p.unparsed -> other.add(release)
                p.batch -> batches.add(release)
                p.episode != null -> {
                    val season = (p.season ?: 1).coerceAtLeast(1)
                    seasonsMap.getOrPut(season) { mutableListOf() }.add(release)
                }
                else -> other.add(release)
            }
        }

        val seasons = seasonsMap.map { (season, episodeReleases) ->
            TorrentSeason(
                season = season,
                episodes = buildEpisodeRows(episodeReleases),
            )
        }.sortedBy { it.season }

        val batchesSorted = batches.sortedWith(
            compareByDescending<TorrentRelease> { qualityRank(it.parsed.quality) }
                .thenBy { it.parsed.episode ?: Int.MAX_VALUE }
                .thenByDescending { seederCount(it) },
        )
        val otherSorted = other.sortedBy { it.rawTitle.lowercase() }

        return TorrentGroup(
            displayTitle = displayTitle,
            normalizedKey = key,
            seasons = seasons,
            batches = batchesSorted,
            other = otherSorted,
            totalReleases = releases.size,
        )
    }

    /** Merge releases of the same episode into one row: best quality wins. */
    private fun buildEpisodeRows(releases: List<TorrentRelease>): List<TorrentEpisodeRow> {
        val byEpisode = LinkedHashMap<Int, MutableList<TorrentRelease>>()
        for (release in releases) {
            byEpisode.getOrPut(release.parsed.episode!!) { mutableListOf() }.add(release)
        }
        return byEpisode.map { (episode, episodeReleases) ->
            val ranked = episodeReleases.sortedWith(
                compareByDescending<TorrentRelease> { qualityRank(it.parsed.quality) }
                    .thenByDescending { seederCount(it) },
            )
            TorrentEpisodeRow(
                episode = episode,
                best = ranked.first(),
                alternatives = ranked.drop(1),
            )
        }.sortedBy { it.episode }
    }
}
