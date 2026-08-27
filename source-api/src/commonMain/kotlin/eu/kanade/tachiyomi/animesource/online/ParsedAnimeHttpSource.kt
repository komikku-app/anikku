package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * A simple implementation for sources from a website using Jsoup, an HTML parser.
 */
@Suppress("unused")
abstract class ParsedAnimeHttpSource : AnimeHttpSource() {

    // ------------------------------------------------------------------
    // Related anime
    // ------------------------------------------------------------------

    /**
     * Returns the Jsoup selector for the related anime list.
     */
    protected open fun relatedAnimeListSelector(): String = ""

    /**
     * Returns an [SAnime] from the given element in the related anime list.
     */
    protected open fun relatedAnimeFromElement(element: Element): SAnime = SAnime.create()

    /**
     * Parses the response from the site and returns the list of related anime.
     */
    override open fun relatedAnimeListParse(response: Response): List<SAnime> = emptyList()

    // ------------------------------------------------------------------
    // Popular anime
    // ------------------------------------------------------------------

    /**
     * Parses the response from the site and returns a [AnimesPage] object.
     */
    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animes = document.select(popularAnimeSelector()).map { element ->
            popularAnimeFromElement(element)
        }
        val hasNextPage = popularAnimeNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null
        return AnimesPage(animes, hasNextPage)
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each anime.
     */
    protected abstract fun popularAnimeSelector(): String

    /**
     * Returns an anime from the given [element].
     */
    protected abstract fun popularAnimeFromElement(element: Element): SAnime

    /**
     * Returns the Jsoup selector that returns the <a> tag linking to the next page, or null if
     * there's no next page.
     */
    protected abstract fun popularAnimeNextPageSelector(): String?

    // ------------------------------------------------------------------
    // Search anime
    // ------------------------------------------------------------------

    /**
     * Parses the response from the site and returns a [AnimesPage] object.
     */
    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animes = document.select(searchAnimeSelector()).map { element ->
            searchAnimeFromElement(element)
        }
        val hasNextPage = searchAnimeNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null
        return AnimesPage(animes, hasNextPage)
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each anime.
     */
    protected abstract fun searchAnimeSelector(): String

    /**
     * Returns an anime from the given [element].
     */
    protected abstract fun searchAnimeFromElement(element: Element): SAnime

    /**
     * Returns the Jsoup selector that returns the <a> tag linking to the next page, or null if
     * there's no next page.
     */
    protected abstract fun searchAnimeNextPageSelector(): String?

    // ------------------------------------------------------------------
    // Latest updates
    // ------------------------------------------------------------------

    /**
     * Parses the response from the site and returns a [AnimesPage] object.
     */
    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animes = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null
        return AnimesPage(animes, hasNextPage)
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each anime.
     */
    protected abstract fun latestUpdatesSelector(): String

    /**
     * Returns an anime from the given [element].
     */
    protected abstract fun latestUpdatesFromElement(element: Element): SAnime

    /**
     * Returns the Jsoup selector that returns the <a> tag linking to the next page, or null if
     * there's no next page.
     */
    protected abstract fun latestUpdatesNextPageSelector(): String?

    // ------------------------------------------------------------------
    // Anime details
    // ------------------------------------------------------------------

    /**
     * Parses the response from the site and returns the details of an anime.
     */
    override fun animeDetailsParse(response: Response): SAnime {
        return animeDetailsParse(response.asJsoup())
    }

    /**
     * Returns the details of the anime from the given [document].
     */
    protected abstract fun animeDetailsParse(document: Document): SAnime

    // ------------------------------------------------------------------
    // Episode list
    // ------------------------------------------------------------------

    /**
     * Parses the response from the site and returns a list of episodes.
     */
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        return document.select(episodeListSelector()).map { episodeFromElement(it) }
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each episode.
     */
    protected abstract fun episodeListSelector(): String

    /**
     * Returns an episode from the given element.
     */
    protected abstract fun episodeFromElement(element: Element): SEpisode

    // ------------------------------------------------------------------
    // Hoster list
    // ------------------------------------------------------------------

    /**
     * Parses the response from the site and returns the hoster list.
     */
    override fun hosterListParse(response: Response): List<Hoster> {
        val document = response.asJsoup()
        return document.select(hosterListSelector()).map(::hosterFromElement)
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each hoster.
     */
    protected open fun hosterListSelector(): String =
        throw UnsupportedOperationException("hosterListSelector not implemented — extension uses custom video extraction")

    /**
     * Returns a hoster from the given element.
     */
    protected open fun hosterFromElement(element: Element): Hoster =
        throw UnsupportedOperationException("hosterFromElement not implemented — extension uses custom video extraction")

    // ------------------------------------------------------------------
    // Video list
    // ------------------------------------------------------------------

    /**
     * Parses the response from the site and returns the page list.
     */
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        return document.select(videoListSelector()).map { videoFromElement(it) }
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each video.
     */
    protected abstract fun videoListSelector(): String

    /**
     * Returns a video from the given element.
     */
    protected abstract fun videoFromElement(element: Element): Video

    /**
     * Parse the response from the site and returns the absolute url to the source video.
     */
    override fun videoUrlParse(response: Response): String {
        return videoUrlParse(response.asJsoup())
    }

    /**
     * Returns the absolute url to the source image from the document.
     */
    protected abstract fun videoUrlParse(document: Document): String
}
