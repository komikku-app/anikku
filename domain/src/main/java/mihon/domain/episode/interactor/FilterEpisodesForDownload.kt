package mihon.domain.episode.interactor

import exh.source.MERGED_SOURCE_ID
import tachiyomi.domain.anime.model.Manga
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.GetMergedEpisodesByAnimeId
import tachiyomi.domain.episode.model.Chapter

/**
 * Interactor responsible for determining which chapters of a manga should be downloaded.
 *
 * @property getEpisodesByAnimeId Interactor for retrieving chapters by manga ID.
 * @property downloadPreferences User preferences related to chapter downloads.
 * @property getCategories Interactor for retrieving categories associated with a manga.
 */
class FilterEpisodesForDownload(
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val getMergedEpisodesByAnimeId: GetMergedEpisodesByAnimeId,
    private val downloadPreferences: DownloadPreferences,
    private val getCategories: GetCategories,
) {

    /**
     * Determines which chapters of a manga should be downloaded based on user preferences.
     * This should check if user preferences & manga's categories allowed to download
     *
     * @param manga The manga for which chapters may be downloaded.
     * @param newChapters The list of new chapters available for the manga.
     * @return A list of chapters that should be downloaded
     */
    suspend fun await(manga: Manga, newChapters: List<Chapter>): List<Chapter> {
        if (
            newChapters.isEmpty() ||
            !downloadPreferences.downloadNewChapters().get() ||
            !manga.shouldDownloadNewChapters()
        ) {
            return emptyList()
        }

        if (!downloadPreferences.downloadNewUnreadChaptersOnly().get()) return newChapters

        // SY -->
        val existingChapters = if (manga.source == MERGED_SOURCE_ID) {
            getMergedEpisodesByAnimeId.await(manga.id)
        } else {
            getEpisodesByAnimeId.await(manga.id)
        }

        val readChapterNumbers = existingChapters
            // SY <--
            .asSequence()
            .filter { it.seen && it.isRecognizedNumber }
            .map { it.episodeNumber }
            .toSet()
        return newChapters.filterNot { it.episodeNumber in readChapterNumbers }
    }

    /**
     * Determines whether new chapters should be downloaded for the manga based on user preferences and the
     * categories to which the manga belongs.
     *
     * @return `true` if chapters of the manga should be downloaded
     */
    private suspend fun Manga.shouldDownloadNewChapters(): Boolean {
        if (!favorite) return false

        val categories = getCategories.await(id).map { it.id }.ifEmpty { listOf(DEFAULT_CATEGORY_ID) }
        val includedCategories = downloadPreferences.downloadNewChapterCategories().get().map { it.toLong() }
        val excludedCategories = downloadPreferences.downloadNewChapterCategoriesExclude().get().map { it.toLong() }

        return when {
            // Default Download from all categories
            includedCategories.isEmpty() && excludedCategories.isEmpty() -> true
            // In excluded category
            categories.any { it in excludedCategories } -> false
            // Included category not selected
            includedCategories.isEmpty() -> true
            // In included category
            else -> categories.any { it in includedCategories }
        }
    }

    companion object {
        private const val DEFAULT_CATEGORY_ID = 0L
    }
}
