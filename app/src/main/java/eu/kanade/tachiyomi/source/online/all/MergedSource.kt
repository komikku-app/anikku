package eu.kanade.tachiyomi.source.online.all

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSAnime
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.model.copy
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import okhttp3.Response
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy

class MergedSource : HttpSource() {
    private val getManga: GetManga by injectLazy()
    private val getMergedReferencesById: GetMergedReferencesById by injectLazy()
    private val syncChaptersWithSource: SyncChaptersWithSource by injectLazy()
    private val networkToLocalManga: NetworkToLocalManga by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val downloadManager: DownloadManager by injectLazy()
    private val filterChaptersForDownload: FilterChaptersForDownload by injectLazy()

    override val id: Long = MERGED_SOURCE_ID

    override val baseUrl = ""

    override fun popularAnimeRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularAnimeParse(response: Response) = throw UnsupportedOperationException()
    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ) = throw UnsupportedOperationException()
    override fun searchAnimeParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun animeDetailsParse(response: Response) = throw UnsupportedOperationException()
    override fun episodeListParse(response: Response) = throw UnsupportedOperationException()
    override fun episodeVideoParse(response: Response) = throw UnsupportedOperationException()

    override fun hosterListParse(response: Response): List<Hoster> = throw UnsupportedOperationException()

    override fun videoListParse(response: Response) = throw UnsupportedOperationException()
    override fun videoUrlParse(response: Response) = throw UnsupportedOperationException()

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getEpisodeList(anime)"))
    override fun fetchEpisodeList(anime: SManga) = throw UnsupportedOperationException()
    override suspend fun getEpisodeList(anime: SManga) = throw UnsupportedOperationException()

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getVideoUrl(video)"))
    override fun fetchVideoUrl(video: Video) = throw UnsupportedOperationException()
    override suspend fun getVideoUrl(video: Video) = throw UnsupportedOperationException()

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getVideoList(episode)"))
    override fun fetchVideoList(episode: SChapter) = throw UnsupportedOperationException()
    override suspend fun getVideoList(episode: SChapter) = throw UnsupportedOperationException()
    override fun videoListParse(response: Response, hoster: Hoster): List<Video> {
        TODO("Not yet implemented")
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates(page)"))
    override fun fetchLatestUpdates(page: Int) = throw UnsupportedOperationException()
    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularAnime(page)"))
    override fun fetchPopularAnime(page: Int) = throw UnsupportedOperationException()
    override suspend fun getPopularAnime(page: Int) = throw UnsupportedOperationException()

    override suspend fun getAnimeDetails(anime: SManga): SManga {
        return withIOContext {
            val mergedAnime = requireNotNull(getManga.await(anime.url, id)) { "merged anime not in db" }
            val animeReferences = getMergedReferencesById.await(mergedAnime.id)
                .apply {
                    require(isNotEmpty()) { "Anime references are empty, info unavailable, merge is likely corrupted" }
                    require(!(size == 1 && first().animeSourceId == MERGED_SOURCE_ID)) {
                        "Anime references contain only the merged reference, merge is likely corrupted"
                    }
                }

            val animeInfoReference = animeReferences.firstOrNull { it.isInfoAnime }
                ?: animeReferences.firstOrNull { it.animeId != it.mergeId }
            val dbAnime = animeInfoReference?.run {
                getManga.await(animeUrl, animeSourceId)?.toSAnime()
            }
            (dbAnime ?: mergedAnime.toSAnime()).copy(
                url = anime.url,
            )
        }
    }

    suspend fun fetchEpisodesForMergedAnime(
        manga: Manga,
        downloadEpisodes: Boolean = true,
    ) {
        fetchEpisodesAndSync(manga, downloadEpisodes)
    }

    private suspend fun fetchEpisodesAndSync(manga: Manga, downloadEpisodes: Boolean = true): List<Chapter> {
        val animeReferences = getMergedReferencesById.await(manga.id)
        require(animeReferences.isNotEmpty()) {
            "Anime references are empty, episodes unavailable, merge is likely corrupted"
        }

        val semaphore = Semaphore(5)
        var exception: Exception? = null
        return supervisorScope {
            animeReferences
                .groupBy(MergedMangaReference::animeSourceId)
                .minus(MERGED_SOURCE_ID)
                .map { (_, values) ->
                    async {
                        semaphore.withPermit {
                            values.flatMap {
                                try {
                                    val (source, loadedAnime, reference) = it.load()
                                    if (loadedAnime != null && reference.getEpisodeUpdates) {
                                        val episodeList = source.getEpisodeList(loadedAnime.toSAnime())
                                        val results =
                                            syncChaptersWithSource.await(episodeList, loadedAnime, source)

                                        if (downloadEpisodes && reference.downloadEpisodes) {
                                            val episodesToDownload = filterChaptersForDownload.await(manga, results)
                                            if (episodesToDownload.isNotEmpty()) {
                                                downloadManager.downloadEpisodes(
                                                    loadedAnime,
                                                    episodesToDownload,
                                                )
                                            }
                                        }
                                        results
                                    } else {
                                        emptyList()
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    exception = e
                                    emptyList()
                                }
                            }
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }.also {
            exception?.let { throw it }
        }
    }

    suspend fun MergedMangaReference.load(): LoadedAnimeSource {
        var anime = getManga.await(animeUrl, animeSourceId)
        val source = sourceManager.getOrStub(anime?.source ?: animeSourceId)
        if (anime == null) {
            val newManga = networkToLocalManga.await(
                Manga.create().copy(
                    source = animeSourceId,
                    url = animeUrl,
                ),
            )
            updateManga.awaitUpdateFromSource(newManga, source.getAnimeDetails(newManga.toSAnime()), false)
            anime = getManga.await(newManga.id)!!
        }
        return LoadedAnimeSource(source, anime, this)
    }

    suspend fun getMergedReferenceSources(manga: Manga?): List<Source> {
        if (manga == null) return emptyList()
        val animeReferences = getMergedReferencesById.await(manga.id)
        require(animeReferences.isNotEmpty()) {
            "Anime references are empty, episodes unavailable, merge is likely corrupted"
        }

        return animeReferences
            .groupBy(MergedMangaReference::animeSourceId)
            .minus(MERGED_SOURCE_ID)
            .values
            .flatten()
            .map {
                val referenceAnime = getManga.await(it.animeUrl, it.animeSourceId)
                sourceManager.getOrStub(referenceAnime?.source ?: it.animeSourceId)
            }
    }

    data class LoadedAnimeSource(val source: Source, val manga: Manga?, val reference: MergedMangaReference)

    override val lang = "all"
    override val supportsLatest = false
    override val name = "MergedSource"
}
