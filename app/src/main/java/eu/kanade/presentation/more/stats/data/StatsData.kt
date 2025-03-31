package eu.kanade.presentation.more.stats.data

sealed interface StatsData {

    data class AnimeOverview(
        val libraryMangaCount: Int,
        val completedAnimeCount: Int,
        val totalSeenDuration: Long,
    ) : StatsData

    data class AnimeTitles(
        val globalUpdateItemCount: Int,
        val startedAnimeCount: Int,
        val localAnimeCount: Int,
    ) : StatsData

    data class Episodes(
        val totalEpisodeCount: Int,
        val readEpisodeCount: Int,
        val downloadCount: Int,
    ) : StatsData

    data class Trackers(
        val trackedTitleCount: Int,
        val meanScore: Double,
        val trackerCount: Int,
    ) : StatsData
}
