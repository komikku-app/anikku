package exh.source

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

@Suppress("OverridingDeprecatedMember", "DEPRECATION")
abstract class DelegatedHttpSource(val delegate: HttpSource) : HttpSource() {
    /**
     * Returns the request for the popular anime given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun popularAnimeRequest(page: Int) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun popularAnimeParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Returns the request for the search anime given the page.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    override fun searchAnimeRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun searchAnimeParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Returns the request for latest anime given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns the details of a anime.
     *
     * @param response the response from the site.
     */
    override fun animeDetailsParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a list of episodes.
     *
     * @param response the response from the site.
     */
    override fun episodeListParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a SChapter Object.
     *
     * @param response the response from the site.
     */
    override fun episodeVideoParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a list of videos.
     *
     * @param response the response from the site.
     */
    override fun videoListParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns the absolute url to the source video.
     *
     * @param response the response from the site.
     */
    override fun videoUrlParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    override val baseUrl get() = delegate.baseUrl

    /**
     * Headers used for requests.
     */
    override val headers get() = delegate.headers

    /**
     * Whether the source has support for latest updates.
     */
    override val supportsLatest get() = delegate.supportsLatest

    /**
     * Name of the source.
     */
    final override val name get() = delegate.name

    // ===> OPTIONAL FIELDS

    /**
     * Id of the source. By default it uses a generated id using the first 16 characters (64 bits)
     * of the MD5 of the string: sourcename/language/versionId
     * Note the generated id sets the sign bit to 0.
     */
    override val id get() = delegate.id

    /**
     * Default network client for doing requests.
     */
    final override val client get() = delegate.client

    /**
     * You must NEVER call super.client if you override this!
     */
    open val baseHttpClient: OkHttpClient? = null
    open val networkHttpClient: OkHttpClient get() = network.client

    /**
     * Visible name of the source.
     */
    override fun toString() = delegate.toString()

    /**
     * Returns an observable containing a page with a list of anime. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     */
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularAnime(page)"))
    override fun fetchPopularAnime(page: Int): Observable<MangasPage> {
        ensureDelegateCompatible()
        return delegate.fetchPopularAnime(page)
    }

    override suspend fun getPopularAnime(page: Int): MangasPage {
        ensureDelegateCompatible()
        return delegate.getPopularAnime(page)
    }

    /**
     * Returns an observable containing a page with a list of anime. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchAnime(page, query, filters)"))
    override fun fetchSearchAnime(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        ensureDelegateCompatible()
        return delegate.fetchSearchAnime(page, query, filters)
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: FilterList): MangasPage {
        ensureDelegateCompatible()
        return delegate.getSearchAnime(page, query, filters)
    }

    /**
     * Returns an observable containing a page with a list of latest anime updates.
     *
     * @param page the page number to retrieve.
     */
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates(page)"))
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        ensureDelegateCompatible()
        return delegate.fetchLatestUpdates(page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        ensureDelegateCompatible()
        return delegate.getLatestUpdates(page)
    }

    /**
     * Returns an observable with the updated details for a anime. Normally it's not needed to
     * override this method.
     *
     * @param anime the anime to be updated.
     */
    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getAnimeDetails(anime)"))
    override fun fetchAnimeDetails(anime: SManga): Observable<SManga> {
        ensureDelegateCompatible()
        return delegate.fetchAnimeDetails(anime)
    }

    /**
     * [1.x API] Get the updated details for a anime.
     */
    override suspend fun getAnimeDetails(anime: SManga): SManga {
        ensureDelegateCompatible()
        return delegate.getAnimeDetails(anime)
    }

    /**
     * Returns the request for the details of a anime. Override only if it's needed to change the
     * url, send different headers or request method like POST.
     *
     * @param anime the anime to be updated.
     */
    override fun animeDetailsRequest(anime: SManga): Request {
        ensureDelegateCompatible()
        return delegate.animeDetailsRequest(anime)
    }

    /**
     * Returns an observable with the updated episode list for a anime. Normally it's not needed to
     * override this method.  If a anime is licensed an empty episode list observable is returned
     *
     * @param anime the anime to look for episodes.
     */
    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getEpisodeList(anime)"))
    override fun fetchEpisodeList(anime: SManga): Observable<List<SChapter>> {
        ensureDelegateCompatible()
        return delegate.fetchEpisodeList(anime)
    }

    /**
     * [1.x API] Get all the available episodes for a anime.
     */
    override suspend fun getEpisodeList(anime: SManga): List<SChapter> {
        ensureDelegateCompatible()
        return delegate.getEpisodeList(anime)
    }

    /**
     * Returns an observable with the video list for a episode.
     *
     * @param episode the episode whose video list has to be fetched.
     */
    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getVideoList(episode)"))
    override fun fetchVideoList(episode: SChapter): Observable<List<Video>> {
        ensureDelegateCompatible()
        return delegate.fetchVideoList(episode)
    }

    /**
     * [1.x API] Get the list of videos a episode has.
     */
    override suspend fun getVideoList(episode: SChapter): List<Video> {
        ensureDelegateCompatible()
        return delegate.getVideoList(episode)
    }

    /**
     * Returns an observable with the video containing the source url of the video. If there's any
     * error, it will return null instead of throwing an exception.
     *
     * @param video the video whose source video has to be fetched.
     */
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getVideoUrl(video)"))
    override fun fetchVideoUrl(video: Video): Observable<String> {
        ensureDelegateCompatible()
        return delegate.fetchVideoUrl(video)
    }

    override suspend fun getVideoUrl(video: Video): String {
        ensureDelegateCompatible()
        return delegate.getVideoUrl(video)
    }

    /**
     * Returns the url of the provided anime
     *
     * @since extensions-lib 1.4
     * @param anime the anime
     * @return url of the anime
     */
    override fun getAnimeUrl(anime: SManga): String {
        ensureDelegateCompatible()
        return delegate.getAnimeUrl(anime)
    }

    /**
     * Returns the url of the provided episode
     *
     * @since extensions-lib 1.4
     * @param episode the episode
     * @return url of the episode
     */
    override fun getEpisodeUrl(episode: SChapter): String {
        ensureDelegateCompatible()
        return delegate.getEpisodeUrl(episode)
    }

    /**
     * Called before inserting a new episode into database. Use it if you need to override episode
     * fields, like the title or the episode number. Do not change anything to [anime].
     *
     * @param episode the episode to be added.
     * @param anime the anime of the episode.
     */
    override fun prepareNewEpisode(episode: SChapter, anime: SManga) {
        ensureDelegateCompatible()
        return delegate.prepareNewEpisode(episode, anime)
    }

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList() = delegate.getFilterList()

    protected open fun ensureDelegateCompatible() {
        if (versionId != delegate.versionId || lang != delegate.lang) {
            throw IncompatibleDelegateException(
                "Delegate source is not compatible (" +
                    "versionId: $versionId <=> ${delegate.versionId}, lang: $lang <=> ${delegate.lang}" +
                    ")!",
            )
        }
    }

    class IncompatibleDelegateException(message: String) : RuntimeException(message)

    init {
        delegate.bindDelegate(this)
    }
}
