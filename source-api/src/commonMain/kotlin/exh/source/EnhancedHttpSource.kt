package exh.source

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.source.model.AnimesPage
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.Video
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.pref.DelegateSourcePreferences
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Suppress("OverridingDeprecatedMember", "DEPRECATION")
class EnhancedHttpSource(
    val originalSource: HttpSource,
    val enhancedSource: HttpSource,
) : HttpSource() {

    /**
     * Returns the request for the popular anime given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun popularAnimeRequest(page: Int) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a [AnimesPage] object.
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
     * Parses the response from the site and returns a [AnimesPage] object.
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
     * Parses the response from the site and returns a [AnimesPage] object.
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
     * Parses the response from the site and returns a SEpisode Object.
     *
     * @param response the response from the site.
     */
    override fun episodeVideoParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    override fun hosterListParse(response: Response) =
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
    override val baseUrl get() = source().baseUrl

    /**
     * Headers used for requests.
     */
    override val headers get() = source().headers

    /**
     * Whether the source has support for latest updates.
     */
    override val supportsLatest get() = source().supportsLatest

    /**
     * Name of the source.
     */
    override val name get() = source().name

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang get() = source().lang

    // ===> OPTIONAL FIELDS

    /**
     * Id of the source. By default it uses a generated id using the first 16 characters (64 bits)
     * of the MD5 of the string: sourcename/language/versionId
     * Note the generated id sets the sign bit to 0.
     */
    override val id get() = source().id

    /**
     * Default network client for doing requests.
     */
    override val client get() = originalSource.client // source().client

    /**
     * Visible name of the source.
     */
    override fun toString() = source().toString()

    /**
     * Returns an observable containing a page with a list of anime. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     */
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularAnime(page)"))
    override fun fetchPopularAnime(page: Int) = source().fetchPopularAnime(page)

    override suspend fun getPopularAnime(page: Int) = source().getPopularAnime(page)

    /**
     * Returns an observable containing a page with a list of anime. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchAnime(page, query, filters)"))
    override fun fetchSearchAnime(page: Int, query: String, filters: FilterList) =
        source().fetchSearchAnime(page, query, filters)

    override suspend fun getSearchAnime(page: Int, query: String, filters: FilterList) =
        source().getSearchAnime(page, query, filters)

    /**
     * Returns an observable containing a page with a list of latest anime updates.
     *
     * @param page the page number to retrieve.
     */
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates(page)"))
    override fun fetchLatestUpdates(page: Int) = source().fetchLatestUpdates(page)

    override suspend fun getLatestUpdates(page: Int) = source().getLatestUpdates(page)

    /**
     * Returns an observable with the updated details for a anime. Normally it's not needed to
     * override this method.
     *
     * @param anime the anime to be updated.
     */
    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getAnimeDetails(anime)"))
    override fun fetchAnimeDetails(anime: SAnime) = source().fetchAnimeDetails(anime)

    /**
     * [1.x API] Get the updated details for a anime.
     */
    override suspend fun getAnimeDetails(anime: SAnime): SAnime = source().getAnimeDetails(anime)

    /**
     * Returns the request for the details of a anime. Override only if it's needed to change the
     * url, send different headers or request method like POST.
     *
     * @param anime the anime to be updated.
     */
    override fun animeDetailsRequest(anime: SAnime) = source().animeDetailsRequest(anime)

    /**
     * Returns an observable with the updated episode list for a anime. Normally it's not needed to
     * override this method.  If a anime is licensed an empty episode list observable is returned
     *
     * @param anime the anime to look for episodes.
     */
    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getEpisodeList(anime)"))
    override fun fetchEpisodeList(anime: SAnime) = source().fetchEpisodeList(anime)

    /**
     * [1.x API] Get all the available episodes for a anime.
     */
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = source().getEpisodeList(anime)

    /**
     * Returns an observable with the video list for a episode.
     *
     * @param episode the episode whose video list has to be fetched.
     */
    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getVideoList(episode)"))
    override fun fetchVideoList(episode: SEpisode) = source().fetchVideoList(episode)

    /**
     * [1.x API] Get the list of videos a episode has.
     */
    override suspend fun getVideoList(episode: SEpisode): List<Video> = source().getVideoList(episode)
    override fun videoListParse(response: Response, hoster: Hoster) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Returns an observable with the video containing the source url of the video. If there's any
     * error, it will return null instead of throwing an exception.
     *
     * @param video the video whose source video has to be fetched.
     */
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getVideoUrl(page)"))
    override fun fetchVideoUrl(video: Video) = source().fetchVideoUrl(video)

    override suspend fun getVideoUrl(video: Video) = source().getVideoUrl(video)

    /**
     * Returns the url of the provided anime
     *
     * @since extensions-lib 1.4
     * @param anime the anime
     * @return url of the anime
     */
    override fun getAnimeUrl(anime: SAnime) = source().getAnimeUrl(anime)

    /**
     * Returns the url of the provided episode
     *
     * @since extensions-lib 1.4
     * @param episode the episode
     * @return url of the episode
     */
    override fun getEpisodeUrl(episode: SEpisode) = source().getEpisodeUrl(episode)

    /**
     * Called before inserting a new episode into database. Use it if you need to override episode
     * fields, like the title or the episode number. Do not change anything to [anime].
     *
     * @param episode the episode to be added.
     * @param anime the anime of the episode.
     */
    override fun prepareNewEpisode(episode: SEpisode, anime: SAnime) =
        source().prepareNewEpisode(episode, anime)

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList() = source().getFilterList()

    fun source(): HttpSource {
        return if (Injekt.get<DelegateSourcePreferences>().delegateSources().get()) {
            enhancedSource
        } else {
            originalSource
        }
    }
}
