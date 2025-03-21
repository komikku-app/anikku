package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.AnimeRepository

class SetAnimeEpisodeFlags(
    private val animeRepository: AnimeRepository,
) {

    suspend fun awaitSetDownloadedFilter(manga: Anime, flag: Long): Boolean {
        return animeRepository.update(
            AnimeUpdate(
                id = manga.id,
                episodeFlags = manga.episodeFlags.setFlag(flag, Anime.EPISODE_DOWNLOADED_MASK),
            ),
        )
    }

    suspend fun awaitSetUnreadFilter(manga: Anime, flag: Long): Boolean {
        return animeRepository.update(
            AnimeUpdate(
                id = manga.id,
                episodeFlags = manga.episodeFlags.setFlag(flag, Anime.EPISODE_UNSEEN_MASK),
            ),
        )
    }

    suspend fun awaitSetBookmarkFilter(manga: Anime, flag: Long): Boolean {
        return animeRepository.update(
            AnimeUpdate(
                id = manga.id,
                episodeFlags = manga.episodeFlags.setFlag(flag, Anime.EPISODE_BOOKMARKED_MASK),
            ),
        )
    }

    // AM (FILLERMARK) -->
    suspend fun awaitSetFillermarkFilter(manga: Anime, flag: Long): Boolean {
        return animeRepository.update(
            AnimeUpdate(
                id = manga.id,
                episodeFlags = manga.episodeFlags.setFlag(flag, Anime.EPISODE_FILLERMARKED_MASK),
            ),
        )
    }
    // <-- AM (FILLERMARK)

    suspend fun awaitSetDisplayMode(manga: Anime, flag: Long): Boolean {
        return animeRepository.update(
            AnimeUpdate(
                id = manga.id,
                episodeFlags = manga.episodeFlags.setFlag(flag, Anime.EPISODE_DISPLAY_MASK),
            ),
        )
    }

    suspend fun awaitSetSortingModeOrFlipOrder(manga: Anime, flag: Long): Boolean {
        val newFlags = manga.episodeFlags.let {
            if (manga.sorting == flag) {
                // Just flip the order
                val orderFlag = if (manga.sortDescending()) {
                    Anime.EPISODE_SORT_ASC
                } else {
                    Anime.EPISODE_SORT_DESC
                }
                it.setFlag(orderFlag, Anime.EPISODE_SORT_DIR_MASK)
            } else {
                // Set new flag with ascending order
                it
                    .setFlag(flag, Anime.EPISODE_SORTING_MASK)
                    .setFlag(Anime.EPISODE_SORT_ASC, Anime.EPISODE_SORT_DIR_MASK)
            }
        }
        return animeRepository.update(
            AnimeUpdate(
                id = manga.id,
                episodeFlags = newFlags,
            ),
        )
    }

    suspend fun awaitSetAllFlags(
        animeId: Long,
        unseenFilter: Long,
        downloadedFilter: Long,
        bookmarkedFilter: Long,
        // AM (FILLERMARK) -->
        fillermarkedFilter: Long,
        // <-- AM (FILLERMARK)
        sortingMode: Long,
        sortingDirection: Long,
        displayMode: Long,
    ): Boolean {
        return animeRepository.update(
            AnimeUpdate(
                id = animeId,
                episodeFlags = 0L.setFlag(unseenFilter, Anime.EPISODE_UNSEEN_MASK)
                    .setFlag(downloadedFilter, Anime.EPISODE_DOWNLOADED_MASK)
                    .setFlag(bookmarkedFilter, Anime.EPISODE_BOOKMARKED_MASK)
                    // AM (FILLERMARK) -->
                    .setFlag(fillermarkedFilter, Anime.EPISODE_FILLERMARKED_MASK)
                    // <-- AM (FILLERMARK)
                    .setFlag(sortingMode, Anime.EPISODE_SORTING_MASK)
                    .setFlag(sortingDirection, Anime.EPISODE_SORT_DIR_MASK)
                    .setFlag(displayMode, Anime.EPISODE_DISPLAY_MASK),
            ),
        )
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }
}
