package app.anikku.macos.platform.local

import app.anikku.macos.platform.library.AnimeSourceMatcher

/**
 * One season of a locally-imported anime, episodes ordered ascending.
 */
data class LocalSeason(
    val season: Int,
    val episodes: List<LocalVideoEntry>,
)

/**
 * All locally-imported files of one anime, grouped by season. Entries that
 * didn't parse into an episode (batches, movies, odd names) go to [other].
 */
data class LocalAnimeGroup(
    val displayTitle: String,
    val normalizedKey: String,
    val seasons: List<LocalSeason>,
    val other: List<LocalVideoEntry>,
    val totalFiles: Int,
) {
    val episodeCount: Int get() = seasons.sumOf { it.episodes.size }
    val seasonCount: Int get() = seasons.size
}

/**
 * Groups imported local files into anime groups (same keying convention as the
 * torrent grouping: normalized title without season). Pure and deterministic.
 */
object LocalVideoGrouper {

    fun group(entries: List<LocalVideoEntry>): List<LocalAnimeGroup> {
        val byKey = LinkedHashMap<String, MutableList<LocalVideoEntry>>()
        for (entry in entries) {
            val key = AnimeSourceMatcher.normalizeTitle(entry.title)
            if (key.isEmpty()) continue
            byKey.getOrPut(key) { mutableListOf() }.add(entry)
        }

        return byKey.map { (key, groupEntries) -> buildGroup(key, groupEntries) }
            .sortedWith(
                compareByDescending<LocalAnimeGroup> { it.totalFiles }
                    .thenBy { it.displayTitle.lowercase() },
            )
    }

    private fun buildGroup(key: String, entries: List<LocalVideoEntry>): LocalAnimeGroup {
        val displayTitle = entries.first().title
        val seasonsMap = LinkedHashMap<Int, MutableList<LocalVideoEntry>>()
        val other = mutableListOf<LocalVideoEntry>()

        for (entry in entries) {
            if (entry.episode > 0) {
                seasonsMap.getOrPut(entry.season) { mutableListOf() }.add(entry)
            } else {
                other.add(entry)
            }
        }

        val seasons = seasonsMap.map { (season, seasonEntries) ->
            LocalSeason(
                season = season,
                episodes = seasonEntries.sortedBy { it.episode },
            )
        }.sortedBy { it.season }

        return LocalAnimeGroup(
            displayTitle = displayTitle,
            normalizedKey = key,
            seasons = seasons,
            other = other.sortedBy { it.fileName.lowercase() },
            totalFiles = entries.size,
        )
    }
}
