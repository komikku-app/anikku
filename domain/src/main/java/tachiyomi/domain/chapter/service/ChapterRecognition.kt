package tachiyomi.domain.chapter.service

/**
 * -R> = regex conversion.
 */
object ChapterRecognition {

    private const val NUMBER_PATTERN = """([0-9]+)(\.[0-9]+)?(\.?[a-z]+)?"""

    /**
     * All cases with e.xx, exx, episode xx, or ep xx
     * kaguya-sama wa kokurasetai - s01e01v2 (BD 1080p HEVC) -R> 01
     */
    private val basic = Regex("""(?<=\be\.|\be|episode|\bep) *$NUMBER_PATTERN""")

    /**
     * Example: Bleach 567: Down With Snowwhite -R> 567
     */
    private val number = Regex(NUMBER_PATTERN)

    // ANK -->
    /**
     * Regex to remove tags
     * Example: [flugel] kaguya-sama wa kokurasetai - s01e01v2 (bd 1080p hevc) [multi audio] [80ac7b2e]
     * -> kaguya-sama wa kokurasetai - s01e01v2
     */
    internal val tagRegex = Regex("""^\s*(\[[^]]+]\s*|\([^)]+\)\s*)+|\s*(\[[^]]+]\s*|\([^)]+\)\s*)+$""")
    // ANK <--

    /**
     * Regex used to remove unwanted tags
     * Example kaguya-sama wa kokurasetai - s01e01v2 1080p -R> kaguya-sama wa kokurasetai - e01v2
     */
    private val unwanted = Regex("""\b(?:v|ver|version|season|s)[^a-z]?[0-9]+|\b\d+p\b|hi10""")

    /**
     * Regex used to remove unwanted whitespace
     * Example One Piece 12 special -R> One Piece 12special
     */
    private val unwantedWhiteSpace = Regex("""\s(?=extra|special|omake)""")

    fun parseEpisodeNumber(
        animeTitle: String,
        episodeName: String,
        episodeNumber: Double? = null,
    ) = parseChapterNumber(animeTitle, episodeName, episodeNumber)

    fun parseChapterNumber(
        mangaTitle: String,
        chapterName: String,
        chapterNumber: Double? = null,
    ): Double {
        // If chapter number is known return.
        if (chapterNumber != null && (chapterNumber == -2.0 || chapterNumber > -1.0)) {
            return chapterNumber
        }

        // Get chapter title with lower case
        val cleanChapterName = chapterName.lowercase()
            // Remove manga title from chapter title.
            .replace(mangaTitle.lowercase(), "").trim()
            // Remove comma's or hyphens.
            .replace(',', '.')
            .replace('-', '.')
            // Remove unwanted white spaces.
            .replace(unwantedWhiteSpace, "")
            // ANK -->
            .replace(tagRegex, "").trim()
        // ANK <--

        val numberMatch = number.findAll(cleanChapterName)

        when {
            numberMatch.none() -> {
                return chapterNumber ?: -1.0
            }
            numberMatch.count() > 1 -> {
                // Remove unwanted tags.
                unwanted.replace(cleanChapterName, "").let { name ->
                    // Check base case ep.xx
                    basic.find(name)?.let { return getChapterNumberFromMatch(it) }

                    // need to find again first number might already removed
                    number.find(name)?.let { return getChapterNumberFromMatch(it) }
                }
            }
        }

        // return the first number encountered
        return getChapterNumberFromMatch(numberMatch.first())
    }

    /**
     * Check if chapter number is found and return it
     * @param match result of regex
     * @return chapter number if found else null
     */
    private fun getChapterNumberFromMatch(match: MatchResult): Double {
        return match.let {
            val initial = it.groups[1]?.value?.toDouble()!!
            val subChapterDecimal = it.groups[2]?.value
            val subChapterAlpha = it.groups[3]?.value
            val addition = checkForDecimal(subChapterDecimal, subChapterAlpha)
            initial.plus(addition)
        }
    }

    /**
     * Check for decimal in received strings
     * @param decimal decimal value of regex
     * @param alpha alpha value of regex
     * @return decimal/alpha float value
     */
    private fun checkForDecimal(decimal: String?, alpha: String?): Double {
        if (!decimal.isNullOrEmpty()) {
            return decimal.toDouble()
        }

        if (!alpha.isNullOrEmpty()) {
            if (alpha.contains("extra")) {
                return 0.99
            }

            if (alpha.contains("omake")) {
                return 0.98
            }

            if (alpha.contains("special")) {
                return 0.97
            }

            val trimmedAlpha = alpha.trimStart('.')
            if (trimmedAlpha.length == 1) {
                return parseAlphaPostFix(trimmedAlpha[0])
            }
        }

        return 0.0
    }

    /**
     * x.a -> x.1, x.b -> x.2, etc
     */
    private fun parseAlphaPostFix(alpha: Char): Double {
        val number = alpha.code - ('a'.code - 1)
        if (number >= 10) return 0.0
        return number / 10.0
    }
}
