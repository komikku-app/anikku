package keiyoushi.utils

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.serialization.json.Json
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import kotlin.getValue
import java.util.logging.Logger

/**
 * Base class for keiyoushi anime sources on JVM.
 *
 * Ported from the Android version: removes android.os.Handler/Looper,
 * android.widget.Toast, and android.app.Application references.
 *
 * Toast notifications are logged instead (macOS has no native toast API).
 * Main-thread dispatching uses coroutine Dispatchers.
 */
private val sourceLogger = Logger.getLogger("keiyoushi.utils.Source")

abstract class Source(
    override val lang: String,
    override val name: String,
    override val id: Long,
) : AnimeHttpSource(), ConfigurableAnimeSource {

    protected val json: Json by injectLazy()

    /**
     * Preferences storage for this source.
     * On macOS, backed by java.util.prefs.Preferences.
     * Implements the android.content.SharedPreferences interface for
     * compatibility with extension code that accesses preferences via
     * extension properties on SharedPreferences.
     */
    open val preferences: SharedPreferences by lazy {
        val sourceId = id
        getPreferences(sourceId)
    }

    // On JVM, setupPreferenceScreen requires a PreferenceScreen parameter.
    // No-op on macOS — preferences are configured via getFilterList().
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        /* no-op */
    }

    /**
     * Display a toast notification.
     *
     * On macOS, there is no native Toast API, so this logs the message.
     * Extensions should not rely on UI toast interactions on desktop.
     */
    protected fun displayToast(message: String) {
        sourceLogger.warning("displayToast (not supported on JVM): $message")
    }

    // ------------------------------------------------------------------
    // Overrides that throw UnsupportedOperationException — subclasses
    // must implement the suspend API instead.
    // TODO: Remove these with ext lib 16
    // ------------------------------------------------------------------

    override fun popularAnimeRequest(page: Int) =
        throw UnsupportedOperationException("Use getPopularAnime(page)")

    override fun popularAnimeParse(response: Response) =
        throw UnsupportedOperationException("Use getPopularAnime(page)")

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList) =
        throw UnsupportedOperationException("Use getSearchAnime(page, query, filters)")

    override fun searchAnimeParse(response: Response) =
        throw UnsupportedOperationException("Use getSearchAnime(page, query, filters)")

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("Use getLatestUpdates(page)")

    override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException("Use getLatestUpdates(page)")

    override fun animeDetailsParse(response: Response) =
        throw UnsupportedOperationException("Use getAnimeDetails(anime)")

    override fun episodeListParse(response: Response) =
        throw UnsupportedOperationException("Use getEpisodeList(anime)")

    override fun videoListParse(response: Response) =
        throw UnsupportedOperationException("Use getVideoList(episode)")

    override fun videoListRequest(episode: SEpisode) =
        throw UnsupportedOperationException("Use getVideoList(episode)")

    override fun videoListRequest(hoster: Hoster) =
        throw UnsupportedOperationException("Use getVideoList(episode)")

    // ------------------------------------------------------------------
    // Hoster-based API stubs (ext-lib 16+) — throw UnsupportedOperationException
    // ------------------------------------------------------------------

    override fun episodeVideoParse(response: Response): SEpisode =
        throw UnsupportedOperationException("Use getEpisodeList(anime)")

    override fun hosterListParse(response: Response): List<Hoster> =
        throw UnsupportedOperationException("Use getHosterList(episode)")

    override fun videoListParse(response: Response, hoster: Hoster): List<Video> =
        throw UnsupportedOperationException("Use getVideoList(episode)")

    override fun videoUrlParse(response: Response): String =
        throw UnsupportedOperationException("Use getVideoList(episode)")

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()
}
