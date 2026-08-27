package app.anikku.macos.platform.torrent

/**
 * Metadata parsed out of a Nyaa torrent filename.
 *
 * Nyaa filenames are release-style, not catalogue-style: one file per episode
 * (or per batch), tagged with the fan-sub group, quality, and release tags.
 * Examples:
 * - `[SubsPlease] Death Note - 01 (1080p) [ABC123]`
 * - `[Erai-raws] Frieren - S2 - 05 [720p][Multiple Subtitle]`
 * - `[Batch] Death Note - 01-24 [1080p]`
 *
 * [title] is the cleaned anime title (group/quality/episode noise removed);
 * the app matches it against AniList and groups releases by
 * `AnimeSourceMatcher.normalizeTitle(title)`.
 */
data class ParsedTorrent(
    val title: String,
    /** Season number when the filename says so (S02E05, "Season 2", "2nd Season"); null = season 1. */
    val season: Int?,
    /** Single episode number; null for batches and unparseable entries. */
    val episode: Int?,
    /** End of an episode range (e.g. "01-24" → 1..24); non-null implies a batch. */
    val episodeEnd: Int?,
    /** Normalized quality label: "2160p", "1080p", "720p", "480p", "4K". */
    val quality: String?,
    /** True for multi-episode/complete releases (Batch/Complete markers or a range). */
    val batch: Boolean,
    /** Fan-sub group from a leading [tag], e.g. "SubsPlease". */
    val group: String?,
    /** True when the filename didn't parse into anything usable and [title] is the raw filename. */
    val unparsed: Boolean,
)

/**
 * Parses Nyaa torrent filenames into [ParsedTorrent]. Pure and deterministic —
 * every pattern is a heuristic; anything unrecognized falls back to
 * [ParsedTorrent.unparsed] = true with the raw filename as the title.
 */
object NyaaTorrentParser {

    // One or more leading [tags] — the first is the fan-sub group.
    private val LEADING_TAGS = Regex("^\\s*(\\[[^\\]]*\\]\\s*)+")
    private val TAG_CONTENT = Regex("\\[([^\\]]*)\\]")

    // Trailing release tags: [ABC123], [Multiple Subtitle], [720p], …
    private val TRAILING_TAGS = Regex("\\s*(\\[[^\\]]*\\]\\s*)+$")

    // Quality tokens, longest first. Matches "(1080p)", "[1080p]" and bare "1080p".
    private val QUALITY = Regex("(?i)\\b(2160p|1440p|1080p|720p|480p|360p|8k|4k|uhd)\\b")

    // Batch markers inside the title proper ("Complete Series", "Batch").
    private val BATCH_WORD = Regex("(?i)\\b(?:batch|complete)\\w*(?:\\s+series)?\\b")

    // Leading tags that are categories, not fan-sub groups.
    private val CATEGORY_TAGS = setOf("batch", "complete", "全集", "合集")

    // Season/episode patterns, applied in a specific order (see parse()).
    private val ORDINAL_SEASON = Regex("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)\\s+season\\b")
    private val SEASON_EPISODE = Regex("(?i)\\bs(\\d{1,2})e(\\d{1,3})\\b")
    private val SEASON_WORD = Regex("(?i)\\b(?:season\\s*(\\d{1,2})|s(\\d{1,2}))\\b")
    private val EPISODE_WORD = Regex("(?i)\\b(?:episode|ep\\.?)\\s*(\\d{1,3})\\b")
    // Ranges like "01-24" or "01 ~ 37" (Nyaa batch releases use both separators).
    private val EPISODE_RANGE = Regex("\\b(\\d{1,3})\\s*(?:-|~)\\s*(\\d{1,3})\\b")
    private val EPISODE_DASH = Regex("(?i)\\s-\\s*(\\d{1,4})(?:v\\d+)?\\s*$")

    // Trailing video container extension, e.g. ".mkv", ".avi".
    private val FILE_EXTENSION = Regex("(?i)\\.(?:mkv|avi|mp4|mov|wmv|flv|ts|m2ts|webm|m4v)\\s*$")

    // Parenthesized noise: years and common release descriptors.
    private val NOISE_PAREN = Regex("(?i)\\((?:\\d{4}|anime|uncensored|tv|bd|blu-?ray|remux|hevc|x264|x265|h\\.?264|h\\.?265|dual audio|dub|subbed|sub|complete|batch)\\s*\\)")
    private val EMPTY_PAREN = Regex("\\s*\\(\\s*\\)")

    // Trailing fansub-group descriptors, e.g. "Death Note TV Fansubs".
    private val FANSUB_SUFFIX = Regex("(?i)\\s+(?:tv\\s+)?fansubs?\\s*$")

    /**
     * Parse a Nyaa filename. Never throws — worst case returns an unparsed
     * fallback with the raw filename as [ParsedTorrent.title].
     */
    fun parse(filename: String): ParsedTorrent {
        val original = filename.trim()
        if (original.isEmpty()) {
            return ParsedTorrent("", null, null, null, null, false, null, true)
        }

        var text = original
        var group: String? = null
        var categoryBatch = false

        // 1. Leading [tags]: first tag is the group unless it's a category marker.
        LEADING_TAGS.find(text)?.let { lead ->
            val tags = TAG_CONTENT.findAll(lead.value).map { it.groupValues[1].trim() }.toList()
            text = text.removeRange(lead.range).trim()
            val first = tags.firstOrNull { it.isNotBlank() }
            if (first != null) {
                if (first.lowercase() in CATEGORY_TAGS) {
                    categoryBatch = true
                } else {
                    group = first
                }
            }
        }

        // 2. Batch marker words anywhere in the title — and remove them so
        //    "Complete Series" doesn't leak into the cleaned title.
        val batchWord = BATCH_WORD.containsMatchIn(text)
        text = BATCH_WORD.replace(text, "").trim()

        // 3. Multi-title rows ("Release | Alternative Title") keep only the
        //    first segment — the rest is an alternate listing, not the title.
        text = text.substringBefore(" | ").trim()

        // 3. Quality — scan before stripping bracket tags so "[1080p]" counts;
        //    then remove the token so it can't confuse episode parsing.
        var quality: String? = QUALITY.find(text)?.value
        text = QUALITY.replace(text, "").trim()

        // 4. Strip file extensions first (so trailing release tags after them
        //    can be removed), then trailing release tags and parenthesized noise.
        text = text.replace(FILE_EXTENSION, "")
            .replace(TRAILING_TAGS, "")
            .replace(NOISE_PAREN, "")
            .replace(EMPTY_PAREN, "")
            .replace(FANSUB_SUFFIX, "")
            .trim()

        // 5. Season extraction (ordinal first, then SxxExx, then word form).
        var season: Int? = null
        var episode: Int? = null
        var episodeEnd: Int? = null

        ORDINAL_SEASON.find(text)?.let { m ->
            season = m.groupValues[1].toInt()
            text = text.removeRange(m.range).trim()
        }
        if (season == null) {
            SEASON_EPISODE.find(text)?.let { m ->
                season = m.groupValues[1].toInt()
                episode = m.groupValues[2].toInt()
                text = text.removeRange(m.range).trim()
            }
        }
        if (season == null) {
            SEASON_WORD.find(text)?.let { m ->
                val n = m.groupValues[1].ifBlank { m.groupValues[2] }
                if (n.isNotBlank()) {
                    season = n.toInt()
                    text = text.removeRange(m.range).trim()
                }
            }
        }

        // 6. Episode extraction (only if SxxExx didn't set it).
        if (episode == null) {
            EPISODE_WORD.find(text)?.let { m ->
                episode = m.groupValues[1].toInt()
                text = text.removeRange(m.range).trim()
            }
        }
        if (episode == null) {
            EPISODE_RANGE.find(text)?.let { m ->
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                // Sanity: ascending and range-like (not a date such as 2024-01-24).
                if (b > a && b - a <= 200) {
                    episode = a
                    episodeEnd = b
                    text = text.removeRange(m.range).trim()
                }
            }
        }
        if (episode == null) {
            EPISODE_DASH.find(text)?.let { m ->
                val n = m.groupValues[1].toInt()
                // A dash-number is an episode unless it's a 4-digit year
                // (e.g. "Death Note (2006) - 01" is handled earlier; " - 2024"
                // would be a year). 1-3 digit numbers are always episodes;
                // 4-digit ones like One Piece's 1085 are episodes too, only
                // 1900-2030 is treated as a year.
                if (n !in 1900..2030) {
                    episode = n
                    text = text.removeRange(m.range).trim()
                }
            }
        }

        // 7. Clean the remaining text into a title.
        var title = cleanTitle(text)
        val unparsed = title.isBlank()
        if (unparsed) title = original

        val batch = categoryBatch || batchWord || episodeEnd != null

        return ParsedTorrent(
            title = title,
            season = season,
            episode = episode,
            episodeEnd = episodeEnd,
            quality = quality,
            batch = batch,
            group = group,
            unparsed = unparsed,
        )
    }

    /** Collapse whitespace and trim dangling separators from the title. */
    private fun cleanTitle(text: String): String {
        var t = text.trim()
        t = t.replace(Regex("\\s{2,}"), " ").trim()
        t = t.trim(' ', '-', ':', '.', '_')
        return t
    }
}
