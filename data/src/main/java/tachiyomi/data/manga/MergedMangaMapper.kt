package tachiyomi.data.manga

import tachiyomi.domain.manga.model.MergedMangaReference

object MergedMangaMapper {
    fun map(
        id: Long,
        isInfoAnime: Boolean,
        getEpisodeUpdates: Boolean,
        episodeSortMode: Long,
        episodePriority: Long,
        downloadEpisodes: Boolean,
        mergeId: Long,
        mergeUrl: String,
        animeId: Long?,
        animeUrl: String,
        animeSourceId: Long,
    ): MergedMangaReference {
        return MergedMangaReference(
            id = id,
            isInfoManga = isInfoAnime,
            getChapterUpdates = getEpisodeUpdates,
            chapterSortMode = episodeSortMode.toInt(),
            chapterPriority = episodePriority.toInt(),
            downloadChapters = downloadEpisodes,
            mergeId = mergeId,
            mergeUrl = mergeUrl,
            mangaId = animeId,
            mangaUrl = animeUrl,
            mangaSourceId = animeSourceId,
        )
    }
}
