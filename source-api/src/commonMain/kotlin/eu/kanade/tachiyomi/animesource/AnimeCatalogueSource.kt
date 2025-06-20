package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import rx.Observable
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.awaitSingle
import tachiyomi.core.common.util.system.logcat

interface AnimeCatalogueSource : AnimeSource {

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang: String

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean

    /**
     * Get a page with a list of anime.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     */
    @Suppress("DEPRECATION")
    suspend fun getPopularAnime(page: Int): AnimesPage {
        return fetchPopularAnime(page).awaitSingle()
    }

    /**
     * Get a page with a list of anime.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    @Suppress("DEPRECATION")
    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        return fetchSearchAnime(page, query, filters).awaitSingle()
    }

    /**
     * Get a page with a list of latest anime updates.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     */
    @Suppress("DEPRECATION")
    suspend fun getLatestUpdates(page: Int): AnimesPage {
        return fetchLatestUpdates(page).awaitSingle()
    }

    /**
     * Returns the list of filters for the source.
     */
    fun getFilterList(): AnimeFilterList

    // Should be replaced as soon as Anime Extension reach 1.5
    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPopularAnime"),
    )
    fun fetchPopularAnime(page: Int): Observable<AnimesPage>

    // Should be replaced as soon as Anime Extension reach 1.5
    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getSearchAnime"),
    )
    fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage>

    // Should be replaced as soon as Anime Extension reach 1.5
    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getLatestUpdates"),
    )
    fun fetchLatestUpdates(page: Int): Observable<AnimesPage>

    // KMK -->
    /**
     * Whether parsing related animes in anime page or extension provide custom related animes request.
     * @default false
     * @since komikku/extensions-lib 1.6
     */
    val supportsRelatedAnimes: Boolean get() = false

    /**
     * Extensions doesn't want to use App's [getRelatedAnimeListBySearch].
     * @default false
     * @since komikku/extensions-lib 1.6
     */
    val disableRelatedAnimesBySearch: Boolean get() = false

    /**
     * Disable showing any related animes.
     * @default false
     * @since komikku/extensions-lib 1.6
     */
    val disableRelatedAnimes: Boolean get() = false

    /**
     * Get all the available related animes for a anime.
     * Normally it's not needed to override this method.
     *
     * @since komikku/extensions-lib 1.6
     * @param anime the current anime to get related animes.
     * @return a list of <keyword, related animes>
     * @throws UnsupportedOperationException if a source doesn't support related animes.
     */
    override suspend fun getRelatedAnimeList(
        anime: SAnime,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ) {
        val handler = CoroutineExceptionHandler { _, e -> exceptionHandler(e) }
        if (!disableRelatedAnimes) {
            supervisorScope {
                if (supportsRelatedAnimes) launch(handler) { getRelatedAnimeListByExtension(anime, pushResults) }
                if (!disableRelatedAnimesBySearch) launch(handler) { getRelatedAnimeListBySearch(anime, pushResults) }
            }
        }
    }

    /**
     * Get related animes provided by extension
     *
     * @return a list of <keyword, related animes>
     * @since komikku/extensions-lib 1.6
     */
    suspend fun getRelatedAnimeListByExtension(
        anime: SAnime,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ) {
        runCatching { fetchRelatedAnimeList(anime) }
            .onSuccess { if (it.isNotEmpty()) pushResults(Pair("", it), false) }
            .onFailure { e ->
                logcat(LogPriority.ERROR, e) { "## getRelatedAnimeListByExtension: $e" }
            }
    }

    /**
     * Fetch related animes for a anime from source/site.
     *
     * @since komikku/extensions-lib 1.6
     * @param anime the current anime to get related animes.
     * @return the related animes for the current anime.
     * @throws UnsupportedOperationException if a source doesn't support related animes.
     */
    suspend fun fetchRelatedAnimeList(anime: SAnime): List<SAnime> = throw UnsupportedOperationException("Unsupported!")

    /**
     * Slit & strip anime's title into separate searchable keywords.
     * Used for searching related animes.
     *
     * @since komikku/extensions-lib 1.6
     * @return List of keywords.
     */
    fun String.stripKeywordForRelatedAnimes(): List<String> {
        val regexWhitespace = Regex("\\s+")
        val regexSpecialCharacters =
            Regex("([!~#$%^&*+_|/\\\\,?:;'“”‘’\"<>(){}\\[\\]。・～：—！？、―«»《》〘〙【】「」｜]|\\s-|-\\s|\\s\\.|\\.\\s)")
        val regexNumberOnly = Regex("^\\d+$")

        return replace(regexSpecialCharacters, " ")
            .split(regexWhitespace)
            .map {
                // remove number only
                it.replace(regexNumberOnly, "")
                    .lowercase()
            }
            // exclude single character
            .filter { it.length > 1 }
    }
    fun String.stripKeywordForRelatedMangas() = stripKeywordForRelatedAnimes()

    /**
     * Get related animes by searching for each keywords from anime's title.
     *
     * @return a list of <keyword, related animes>
     * @since komikku/extensions-lib 1.6
     */
    suspend fun getRelatedAnimeListBySearch(
        anime: SAnime,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ) {
        val words = HashSet<String>()
        words.add(anime.title)
        if (anime.title.lowercase() != anime.originalTitle.lowercase()) words.add(anime.originalTitle)
        anime.title.stripKeywordForRelatedAnimes()
            .filterNot { word -> words.any { it.lowercase() == word } }
            .onEach { words.add(it) }
        anime.originalTitle.stripKeywordForRelatedAnimes()
            .filterNot { word -> words.any { it.lowercase() == word } }
            .onEach { words.add(it) }
        if (words.isEmpty()) return

        coroutineScope {
            val filterList = getFilterList()
            words.map { keyword ->
                launch {
                    runCatching {
                        getRelatedAnimePage(1, keyword.sanitize(), filterList).animes
                    }
                        .onSuccess { if (it.isNotEmpty()) pushResults(Pair(keyword, it), false) }
                        .onFailure { e ->
                            logcat(LogPriority.ERROR, e) { "## getRelatedAnimeListBySearch: $e" }
                        }
                }
            }
        }
    }

    /**
     * TODO: This should become a similar catalog as getPopular, which support pages and group into different category.
     *  Clicking each category should allow open more page.
     */
    suspend fun getRelatedAnimePage(page: Int, query: String, filterList: AnimeFilterList): AnimesPage {
        return getSearchAnime(page, query, filterList)
    }
    // KMK <--
}
