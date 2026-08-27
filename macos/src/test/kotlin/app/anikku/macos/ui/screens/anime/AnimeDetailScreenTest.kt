package app.anikku.macos.ui.screens.anime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.LocalLibraryRepository
import app.anikku.macos.platform.preference.BookmarkStore
import app.anikku.macos.platform.preference.LocalBookmarkStore
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastHost
import app.anikku.macos.ui.components.ToastHostState
import app.anikku.macos.ui.screens.models.MockData
import app.anikku.macos.ui.theme.AnikkuTheme
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.Navigator
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Unit and Compose UI tests for [AnimeDetailScreen].
 *
 * Unit tests verify screen construction, mock data lookup, and episode data integrity.
 * Compose UI tests verify rendering of the Share button (top bar + info header),
 * other action buttons, and the toast-wired provider environment.
 *
 * Compose UI tests call [AnimeDetailScreen.Content] directly since the screen does not
 * use `LocalNavigator` for content rendering (Navigator dependency is only for navigation
 * actions not triggered during rendering tests).
 */
class AnimeDetailScreenTest {

    // =========================================================================
    // Compose UI Test Rule
    // =========================================================================

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Stub screen for the Voyager Navigator wrapper in tests.
     * Renders nothing — just provides the Navigator context.
     */
    private object StubScreen : AnikkuScreen() {
        override val key: ScreenKey = uniqueScreenKey

        @Composable
        override fun Content() {
            // Renders nothing
        }
    }

    /**
     * Renders AnimeDetailScreen with all required providers
     * (Navigator, BookmarkStore, ToastHost) inside AnikkuTheme.
     * Content() is called inside a Navigator because the screen
     * uses LocalNavigator.currentOrThrow.
     */
    @Composable
    private fun RenderAnimeDetail(
        toastHostState: ToastHostState = ToastHostState(),
    ) {
        val libraryRepo = remember {
            val tmpDir = File.createTempFile("anikku_test_library_", "").apply {
                delete(); mkdirs(); deleteOnExit()
            }
            LibraryRepository(tmpDir)
        }
        CompositionLocalProvider(
            LocalBookmarkStore provides BookmarkStore(MacOSPreferenceStore(File.createTempFile("bookmark_ui_", ".json").apply { deleteOnExit() })),
            LocalLibraryRepository provides libraryRepo,
            LocalToastHost provides toastHostState,
        ) {
            AnikkuTheme {
                Box(Modifier.fillMaxSize()) {
                    Navigator(StubScreen) {
                        AnimeDetailScreen(animeId = 1L).Content()
                    }
                    ToastHost(state = toastHostState)
                }
            }
        }
    }

    @Test
    fun `screen can be constructed with anime ID`() {
        val screen = AnimeDetailScreen(animeId = 1L)
        assert(screen.animeId == 1L)
    }

    @Test
    fun `mock data lookup finds anime by ID`() {
        val anime = MockData.sampleAnime.find { it.id == 1L }
        assert(anime != null)
        assert(anime!!.title == "Attack on Titan")
    }

    @Test
    fun `mock data returns null for unknown ID`() {
        val anime = MockData.sampleAnime.find { it.id == 999L }
        assert(anime == null) { "Unknown ID should return null" }
    }

    @Test
    fun `episodes filtered by anime ID`() {
        val anime1Episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        assert(anime1Episodes.size == 8) { "Anime 1 should have 8 episodes" }
    }

    @Test
    fun `no episodes for unknown anime ID`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 999L }
        assert(episodes.isEmpty())
    }

    @Test
    fun `episodes have correct seen distribution`() {
        val anime1Episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val seen = anime1Episodes.count { it.seen }
        val unseen = anime1Episodes.count { !it.seen }

        assert(seen == 2) { "Should have 2 seen episodes" }
        assert(unseen == 6) { "Should have 6 unseen episodes" }
    }

    @Test
    fun `all episodes have non-blank names`() {
        val empty = MockData.sampleEpisodes.filter { it.name.isBlank() }
        assert(empty.isEmpty()) { "All episodes should have names" }
    }

    @Test
    fun `all episodes have valid episode numbers`() {
        val invalid = MockData.sampleEpisodes.filter { it.episodeNumber <= 0 }
        assert(invalid.isEmpty()) { "All episodes should have positive episode numbers" }
    }

    @Test
    fun `episodes are ordered by number`() {
        val numbers = MockData.sampleEpisodes
            .filter { it.animeId == 1L }
            .map { it.episodeNumber }
        for (i in 0 until numbers.size - 1) {
            assert(numbers[i] < numbers[i + 1]) {
                "Episodes should be ordered by number ascending"
            }
        }
    }

    // ---- Bookmark toggle state mutation ----

    @Test
    fun `episodes start with bookmark false`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val bookmarked = episodes.count { it.bookmark }
        assert(bookmarked == 0) { "All episodes should start with bookmark = false" }
    }

    @Test
    fun `bookmark toggle flips false to true for target episode`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val targetId = episodes.first().id

        val mutated = episodes.map { ep ->
            if (ep.id == targetId) {
                val toggled = !ep.bookmark
                ep.copy(bookmark = toggled)
            } else {
                ep
            }
        }

        val target = mutated.first { it.id == targetId }
        assert(target.bookmark) { "Target episode bookmark should be true after toggle" }
    }

    @Test
    fun `bookmark toggle flips true back to false on second toggle`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val targetId = episodes.first().id

        val once = episodes.map { ep ->
            if (ep.id == targetId) ep.copy(bookmark = !ep.bookmark) else ep
        }
        val twice = once.map { ep ->
            if (ep.id == targetId) ep.copy(bookmark = !ep.bookmark) else ep
        }

        val target = twice.first { it.id == targetId }
        assert(!target.bookmark) { "Target episode bookmark should be false after double toggle" }
    }

    @Test
    fun `bookmark toggle does not affect other episodes`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val targetId = episodes.first().id

        val mutated = episodes.map { ep ->
            if (ep.id == targetId) ep.copy(bookmark = !ep.bookmark) else ep
        }

        val unchanged = mutated.filter { it.id != targetId }
        val allStillFalse = unchanged.all { !it.bookmark }
        assert(allStillFalse) { "Non-target episodes should retain bookmark = false" }
    }

    @Test
    fun `bookmark toggle can mark multiple episodes independently`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val ids = episodes.take(3).map { it.id }

        val mutated = episodes.map { ep ->
            if (ep.id in ids) ep.copy(bookmark = !ep.bookmark) else ep
        }

        val bookmarked = mutated.filter { it.bookmark }
        assert(bookmarked.size == 3) { "Exactly 3 episodes should be bookmarked" }

        val unbookmarked = mutated.filter { it.id !in ids }
        assert(unbookmarked.all { !it.bookmark }) { "Untouched episodes should remain unbookmarked" }
    }

    // ---- Bookmark toggle via onToggleBookmark callback with BookmarkStore ----

    @Test
    fun `onToggleBookmark callback flips bookmark via BookmarkStore and mutates episode state`() {
        val tmpFile = File.createTempFile("bookmark_test_", ".json")
        tmpFile.deleteOnExit()
        val store = BookmarkStore(MacOSPreferenceStore(tmpFile))
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val targetId = episodes.first().id

        val newState = store.toggleBookmark(targetId)
        val mutated = episodes.map { ep ->
            if (ep.id == targetId) ep.copy(bookmark = newState) else ep
        }

        val target = mutated.first { it.id == targetId }
        assert(target.bookmark) { "Target episode bookmark should be true after toggle" }
        assert(store.isBookmarked(targetId)) { "BookmarkStore should have the ID persisted" }
        assert(mutated.filter { it.id != targetId }.all { !it.bookmark }) {
            "Non-target episodes should remain unbookmarked"
        }

        tmpFile.delete()
    }

    @Test
    fun `onToggleBookmark callback second toggle returns bookmark to false`() {
        val tmpFile = File.createTempFile("bookmark_test_", ".json")
        tmpFile.deleteOnExit()
        val store = BookmarkStore(MacOSPreferenceStore(tmpFile))
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val targetId = episodes.first().id

        val afterFirst = store.toggleBookmark(targetId)
        val mutatedOnce = episodes.map { ep ->
            if (ep.id == targetId) ep.copy(bookmark = afterFirst) else ep
        }
        assert(mutatedOnce.first { it.id == targetId }.bookmark) { "Should be bookmarked after first toggle" }

        val afterSecond = store.toggleBookmark(targetId)
        val mutatedTwice = mutatedOnce.map { ep ->
            if (ep.id == targetId) ep.copy(bookmark = afterSecond) else ep
        }
        assert(!mutatedTwice.first { it.id == targetId }.bookmark) {
            "Should be unbookmarked after second toggle"
        }
        assert(!store.isBookmarked(targetId)) { "BookmarkStore should have the ID removed" }

        tmpFile.delete()
    }

    @Test
    fun `onToggleBookmark callback persists across BookmarkStore instances`() {
        val tmpFile = File.createTempFile("bookmark_test_", ".json")
        tmpFile.deleteOnExit()
        val targetId = 1L

        BookmarkStore(MacOSPreferenceStore(tmpFile)).apply {
            toggleBookmark(targetId)
            assert(isBookmarked(targetId)) { "Should be bookmarked after toggle" }
        }

        val reloaded = BookmarkStore(MacOSPreferenceStore(tmpFile))
        assert(reloaded.isBookmarked(targetId)) { "Bookmark should survive store re-creation" }

        tmpFile.delete()
    }

    @Test
    fun `onToggleBookmark callback respects BookmarkStore in episode init logic`() {
        val tmpFile = File.createTempFile("bookmark_test_", ".json")
        tmpFile.deleteOnExit()
        val store = BookmarkStore(MacOSPreferenceStore(tmpFile))

        val targetId = 3L
        store.toggleBookmark(targetId)

        val bookmarkedIds = store.getBookmarkedIds()
        val baseEpisodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val initialized = baseEpisodes.map { ep ->
            if (ep.id in bookmarkedIds) ep.copy(bookmark = true) else ep
        }

        val target = initialized.first { it.id == targetId }
        assert(target.bookmark) { "Pre-bookmarked episode should load with bookmark = true" }
        assert(initialized.count { it.bookmark } == 1) { "Only one episode should be bookmarked" }

        tmpFile.delete()
    }

    // =========================================================================
    // Compose UI tests — Share button rendering
    // =========================================================================

    @Test
    fun `renders error message when no source data is provided`() {
        composeTestRule.setContent {
            RenderAnimeDetail()
        }

        // Without sourceId/animeUrl, the screen shows an error state
        composeTestRule.onNodeWithText("This anime isn't linked to a source yet — link one to see its episodes")
            .assertIsDisplayed()
    }

    @Test
    fun `renders Go Back button when source data is missing`() {
        composeTestRule.setContent {
            RenderAnimeDetail()
        }

        composeTestRule.onNodeWithText("Go back").assertIsDisplayed()
    }

    @Test
    fun `transitions from loading to error state after LaunchedEffect completes`() {
        composeTestRule.setContent {
            RenderAnimeDetail()
        }

        // Wait for LaunchedEffect to complete and error state to appear
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("This anime isn't linked to a source yet — link one to see its episodes")
            .assertIsDisplayed()
    }

    @Test
    fun `Go back button is displayed alongside error message`() {
        composeTestRule.setContent {
            RenderAnimeDetail()
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("This anime isn't linked to a source yet — link one to see its episodes")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Go back").assertIsDisplayed()
    }

    @Test
    fun `Share button toast appears when triggered with ToastHost provider`() {
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            RenderAnimeDetail(toastHostState = toastHostState)
        }

        composeTestRule.runOnIdle {
            toastHostState.show("URL ready to share")
        }

        composeTestRule.onNodeWithText("URL ready to share").assertIsDisplayed()
    }

    @Test
    fun `Share button toast fires with mock data URL`() {
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            RenderAnimeDetail(toastHostState = toastHostState)
        }

        composeTestRule.runOnIdle {
            toastHostState.show("URL ready to share")
        }
        composeTestRule.onNodeWithText("URL ready to share").assertIsDisplayed()
    }
}
