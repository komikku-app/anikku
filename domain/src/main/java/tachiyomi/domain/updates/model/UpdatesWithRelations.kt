package tachiyomi.domain.updates.model

import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.MangaCover
import uy.kohesive.injekt.injectLazy

data class UpdatesWithRelations(
    val animeId: Long,
    // SY -->
    val ogAnimeTitle: String,
    // SY <--
    val episodeId: Long,
    val episodeName: String,
    val scanlator: String?,
    val seen: Boolean,
    val bookmark: Boolean,
    // AM (FILLERMARK) -->
    val fillermark: Boolean,
    // <-- AM (FILLERMARK)
    val lastSecondSeen: Long,
    val totalSeconds: Long,
    val sourceId: Long,
    val dateFetch: Long,
    val coverData: MangaCover,
) {
    // SY -->
    val animeTitle: String = getCustomMangaInfo.get(animeId)?.title ?: ogAnimeTitle

    companion object {
        private val getCustomMangaInfo: GetCustomMangaInfo by injectLazy()
    }
    // SY <--
}
