package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.anime.model.MergedAnimeReference

/*
* SY merged manga backup class
 */
@Serializable
data class BackupMergedMangaReference(
    @ProtoNumber(1) var isInfoManga: Boolean,
    @ProtoNumber(2) var getChapterUpdates: Boolean,
    @ProtoNumber(3) var chapterSortMode: Int,
    @ProtoNumber(4) var chapterPriority: Int,
    @ProtoNumber(5) var downloadChapters: Boolean,
    @ProtoNumber(6) var mergeUrl: String,
    @ProtoNumber(7) var mangaUrl: String,
    @ProtoNumber(8) var mangaSourceId: Long,
) {
    fun getMergedMangaReference(): MergedAnimeReference {
        return MergedAnimeReference(
            isInfoAnime = isInfoManga,
            getEpisodeUpdates = getChapterUpdates,
            episodeSortMode = chapterSortMode,
            episodePriority = chapterPriority,
            downloadEpisodes = downloadChapters,
            mergeUrl = mergeUrl,
            animeUrl = mangaUrl,
            animeSourceId = mangaSourceId,
            mergeId = null,
            animeId = null,
            id = -1,
        )
    }
}

val backupMergedMangaReferenceMapper =
    {
            _: Long,
            isInfoManga: Boolean,
            getChapterUpdates: Boolean,
            chapterSortMode: Long,
            chapterPriority: Long,
            downloadChapters: Boolean,
            _: Long,
            mergeUrl: String,
            _: Long?,
            mangaUrl: String,
            mangaSourceId: Long,
        ->
        BackupMergedMangaReference(
            isInfoManga = isInfoManga,
            getChapterUpdates = getChapterUpdates,
            chapterSortMode = chapterSortMode.toInt(),
            chapterPriority = chapterPriority.toInt(),
            downloadChapters = downloadChapters,
            mergeUrl = mergeUrl,
            mangaUrl = mangaUrl,
            mangaSourceId = mangaSourceId,
        )
    }
