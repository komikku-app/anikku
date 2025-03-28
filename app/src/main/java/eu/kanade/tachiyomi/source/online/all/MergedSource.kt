package eu.kanade.tachiyomi.source.online.all

import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.copy
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.Video
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mihon.domain.episode.interactor.FilterEpisodesForDownload
import okhttp3.Response
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.GetMergedReferencesById
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.MergedAnimeReference
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy

class MergedSource : HttpSource() {
    private val getAnime: GetAnime by injectLazy()
    private val getMergedReferencesById: GetMergedReferencesById by injectLazy()
    private val syncEpisodesWithSource: SyncEpisodesWithSource by injectLazy()
    private val networkToLocalAnime: NetworkToLocalAnime by injectLazy()
    private val updateAnime: UpdateAnime by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val downloadManager: DownloadManager by injectLazy()
    private val filterEpisodesForDownload: FilterEpisodesForDownload by injectLazy()

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
    override fun fetchEpisodeList(anime: SAnime) = throw UnsupportedOperationException()
    override suspend fun getEpisodeList(anime: SAnime) = throw UnsupportedOperationException()

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getVideoUrl(video)"))
    override fun fetchVideoUrl(video: Video) = throw UnsupportedOperationException()
    override suspend fun getVideoUrl(video: Video) = throw UnsupportedOperationException()

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getVideoList(episode)"))
    override fun fetchVideoList(episode: SEpisode) = throw UnsupportedOperationException()
    override suspend fun getVideoList(episode: SEpisode) = throw UnsupportedOperationException()
    override fun videoListParse(response: Response, hoster: Hoster): List<eu.kanade.tachiyomi.animesource.model.Video> {
        TODO("Not yet implemented")
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates(page)"))
    override fun fetchLatestUpdates(page: Int) = throw UnsupportedOperationException()
    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularAnime(page)"))
    override fun fetchPopularAnime(page: Int) = throw UnsupportedOperationException()
    override suspend fun getPopularAnime(page: Int) = throw UnsupportedOperationException()

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return withIOContext {
            val mergedAnime = requireNotNull(getAnime.await(anime.url, id)) { "merged anime not in db" }
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
                getAnime.await(animeUrl, animeSourceId)?.toSAnime()
            }
            (dbAnime ?: mergedAnime.toSAnime()).copy(
                url = anime.url,
            )
        }
    }

    suspend fun fetchEpisodesForMergedAnime(
        anime: Anime,
        downloadEpisodes: Boolean = true,
    ) {
        fetchEpisodesAndSync(anime, downloadEpisodes)
    }

    suspend fun fetchEpisodesAndSync(anime: Anime, downloadEpisodes: Boolean = true): List<Episode> {
        val animeReferences = getMergedReferencesById.await(anime.id)
        require(animeReferences.isNotEmpty()) {
            "Anime references are empty, episodes unavailable, merge is likely corrupted"
        }

        val semaphore = Semaphore(5)
        var exception: Exception? = null
        return supervisorScope {
            animeReferences
                .groupBy(MergedAnimeReference::animeSourceId)
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
                                            syncEpisodesWithSource.await(episodeList, loadedAnime, source)

                                        if (downloadEpisodes && reference.downloadEpisodes) {
                                            val episodesToDownload = filterEpisodesForDownload.await(anime, results)
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

    suspend fun MergedAnimeReference.load(): LoadedAnimeSource {
        var anime = getAnime.await(animeUrl, animeSourceId)
        val source = sourceManager.getOrStub(anime?.source ?: animeSourceId)
        if (anime == null) {
            val newAnime = networkToLocalAnime.await(
                Anime.create().copy(
                    source = animeSourceId,
                    url = animeUrl,
                ),
            )
            updateAnime.awaitUpdateFromSource(newAnime, source.getAnimeDetails(newAnime.toSAnime()), false)
            anime = getAnime.await(newAnime.id)!!
        }
        return LoadedAnimeSource(source, anime, this)
    }

    data class LoadedAnimeSource(val source: Source, val anime: Anime?, val reference: MergedAnimeReference)

    override val lang = "all"
    override val supportsLatest = false
    override val name = "MergedSource"
}
