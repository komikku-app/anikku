package eu.kanade.domain.chapter.model

import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.source.local.isLocal

/**
 * Applies the view filters to the list of episodes obtained from the database.
 * @return an observable of the list of episodes filtered and sorted.
 */
fun List<Chapter>.applyFilters(
    manga: Manga,
    downloadManager: DownloadManager, /* SY --> */
    mergedManga: Map<Long, Manga>, /* SY <-- */
): List<Chapter> {
    val isLocalAnime = manga.isLocal()
    val unseenFilter = manga.unseenFilter
    val downloadedFilter = manga.downloadedFilter
    val bookmarkedFilter = manga.bookmarkedFilter
    // AM (FILLERMARK) -->
    val fillermarkedFilter = manga.fillermarkedFilter
    // <-- AM (FILLERMARK)

    return asSequence().filter { episode -> applyFilter(unseenFilter) { !episode.seen } }
        .filter { episode -> applyFilter(bookmarkedFilter) { episode.bookmark } }
        // AM (FILLERMARK) -->
        .filter { episode -> applyFilter(fillermarkedFilter) { episode.fillermark } }
        // <-- AM (FILLERMARK)
        .filter { episode ->
            // SY -->
            @Suppress("NAME_SHADOWING")
            val anime = mergedManga.getOrElse(episode.animeId) { manga }
            // SY <--
            applyFilter(downloadedFilter) {
                val downloaded = downloadManager.isEpisodeDownloaded(
                    episode.name,
                    episode.scanlator,
                    // SY -->
                    anime.ogTitle,
                    // SY <--
                    anime.source,
                )
                downloaded || isLocalAnime
            }
        }
        .sortedWith(getChapterSort(manga)).toList()
}

/**
 * Applies the view filters to the list of episodes obtained from the database.
 * @return an observable of the list of episodes filtered and sorted.
 */
fun List<ChapterList.Item>.applyFilters(manga: Manga): Sequence<ChapterList.Item> {
    val isLocalAnime = manga.isLocal()
    val unseenFilter = manga.unseenFilter
    val downloadedFilter = manga.downloadedFilter
    val bookmarkedFilter = manga.bookmarkedFilter
    // AM (FILLERMARK) -->
    val fillermarkedFilter = manga.fillermarkedFilter
    // <-- AM (FILLERMARK)
    return asSequence()
        .filter { (episode) -> applyFilter(unseenFilter) { !episode.seen } }
        .filter { (episode) -> applyFilter(bookmarkedFilter) { episode.bookmark } }
        // AM (FILLERMARK) -->
        .filter { (episode) -> applyFilter(fillermarkedFilter) { episode.fillermark } }
        // <-- AM (FILLERMARK)
        .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalAnime } }
        .sortedWith { (episode1), (episode2) -> getChapterSort(manga).invoke(episode1, episode2) }
}
