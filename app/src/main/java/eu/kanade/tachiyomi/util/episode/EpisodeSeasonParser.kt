// ANK -->
package eu.kanade.tachiyomi.util.episode

/**
 * Best-effort extraction of a season number from an episode's display name.
 *
 * Sources rarely expose season metadata directly, so this looks for common
 * naming conventions (e.g. "Season 2", "S2E05", "2nd Season") inside the
 * episode name itself. Returns null when no marker is found.
 */
object EpisodeSeasonParser {

    private val SEASON_WORD_REGEX = Regex("""season\s*0*(\d+)""", RegexOption.IGNORE_CASE)
    private val SEASON_ABBREVIATION_REGEX = Regex(
        """(?<![a-z0-9])s0*(\d{1,3})(?=e\d|ep\d|[^a-z0-9]|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val SEASON_ORDINAL_REGEX = Regex("""(\d+)(?:st|nd|rd|th)\s*season""", RegexOption.IGNORE_CASE)

    fun parseSeason(episodeName: String): Int? {
        return SEASON_ORDINAL_REGEX.find(episodeName)?.groupValues?.get(1)?.toIntOrNull()
            ?: SEASON_WORD_REGEX.find(episodeName)?.groupValues?.get(1)?.toIntOrNull()
            ?: SEASON_ABBREVIATION_REGEX.find(episodeName)?.groupValues?.get(1)?.toIntOrNull()
    }
}
// ANK <--
