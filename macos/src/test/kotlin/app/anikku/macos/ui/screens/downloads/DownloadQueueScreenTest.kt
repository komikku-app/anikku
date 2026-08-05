package app.anikku.macos.ui.screens.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.anikku.macos.fakes.FakeMacOSDownloadManager
import app.anikku.macos.platform.data.LocalDownloadManager
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastHost
import app.anikku.macos.ui.components.ToastHostState
import app.anikku.macos.ui.theme.AnikkuTheme
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [DownloadQueueScreen].
 *
 * Verifies rendering of the download queue header, download item cards,
 * and the toast-wired pause/resume/cancel buttons.
 *
 * Uses [FakeMacOSDownloadManager] to provide hardcoded download entries
 * so the full UI can be rendered without a real download pipeline.
 *
 * Note: Interactive click tests (performClick/performSemanticsAction) are
 * not available in Compose Multiplatform 1.11.1. Toast-wired button feedback
 * is tested indirectly by verifying [ToastHost] renders without error when
 * the [LocalToastHost] provider is wired alongside [DownloadQueueScreen].
 * Direct toast triggers are tested via [runOnIdle].
 */
class DownloadQueueScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Hardcoded test entries shared across tests. */
    private val testEntries = FakeMacOSDownloadManager.createTestEntries()

    /** Number of DOWNLOADING entries in test data. */
    private val activeCount = testEntries.count { it.isActive }

    /** Number of COMPLETED entries in test data. */
    private val completedCount = testEntries.count { it.status == app.anikku.macos.platform.data.DownloadRepository.DownloadStatus.COMPLETED }

    /**
     * Renders the download queue screen with a [FakeMacOSDownloadManager]
     * that provides hardcoded download entries.
     */
    @Composable
    private fun RenderWithFakeManager() {
        val fakeManager = rememberWithEntries()
        CompositionLocalProvider(
            LocalDownloadManager provides fakeManager,
        ) {
            AnikkuTheme {
                DownloadQueueScreen().Content()
            }
        }
    }

    /**
     * Wraps the download queue screen with the required providers for toast testing,
     * including LocalToastHost, FakeMacOSDownloadManager, AnikkuTheme, and a ToastHost overlay.
     */
    @Composable
    private fun FullDownloadContent(
        toastHostState: ToastHostState = ToastHostState(),
    ) {
        val fakeManager = rememberWithEntries()
        CompositionLocalProvider(
            LocalToastHost provides toastHostState,
            LocalDownloadManager provides fakeManager,
        ) {
            AnikkuTheme {
                Box(Modifier.fillMaxSize()) {
                    DownloadQueueScreen().Content()
                    ToastHost(state = toastHostState)
                }
            }
        }
    }

    /**
     * Creates a [FakeMacOSDownloadManager] with the shared test entries,
     * remembered across recompositions.
     */
    @Composable
    private fun rememberWithEntries(): FakeMacOSDownloadManager {
        return androidx.compose.runtime.remember {
            FakeMacOSDownloadManager(testEntries)
        }
    }

    // -----------------------------------------------------------------------
    // Header rendering
    // -----------------------------------------------------------------------

    @Test
    fun `renders Downloads header`() {
        composeTestRule.setContent {
            RenderWithFakeManager()
        }

        composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
    }

    @Test
    fun `renders download count summary`() {
        composeTestRule.setContent {
            RenderWithFakeManager()
        }

        composeTestRule.onNodeWithText("$activeCount active · $completedCount completed").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Download item rendering
    // -----------------------------------------------------------------------

    @Test
    fun `renders all download items`() {
        composeTestRule.setContent {
            RenderWithFakeManager()
        }

        composeTestRule.onNodeWithText("Attack on Titan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jujutsu Kaisen").assertIsDisplayed()
        composeTestRule.onNodeWithText("One Piece").assertIsDisplayed()
        composeTestRule.onNodeWithText("Demon Slayer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spy x Family").assertIsDisplayed()
    }

    @Test
    fun `renders episode names for download items`() {
        composeTestRule.setContent {
            RenderWithFakeManager()
        }

        composeTestRule.onNodeWithText("Episode 3 - A Dim Light Amid Despair").assertIsDisplayed()
        composeTestRule.onNodeWithText("Episode 1092 - A Night to Remember").assertIsDisplayed()
    }

    @Test
    fun `renders action buttons for active downloads`() {
        composeTestRule.setContent {
            RenderWithFakeManager()
        }

        // DOWNLOADING entries show Pause button
        composeTestRule.onAllNodesWithContentDescription("Pause").assertCountEquals(2)
        // PAUSED entries show Resume button
        composeTestRule.onNodeWithContentDescription("Resume").assertIsDisplayed()
        // All non-completed entries show Cancel button (3: 5 total - 2 completed = 3 active)
        composeTestRule.onAllNodesWithContentDescription("Cancel").assertCountEquals(3)
    }

    // -----------------------------------------------------------------------
    // ToastHost integration — full wired environment renders without error
    // -----------------------------------------------------------------------

    @Test
    fun `renders with ToastHost provider without crash`() {
        composeTestRule.setContent {
            FullDownloadContent()
        }

        composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
        composeTestRule.onNodeWithText("$activeCount active · $completedCount completed").assertIsDisplayed()
    }

    @Test
    fun `renders download items with ToastHost provider`() {
        composeTestRule.setContent {
            FullDownloadContent()
        }

        composeTestRule.onNodeWithText("Attack on Titan").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Pause").assertCountEquals(2)
        composeTestRule.onNodeWithContentDescription("Resume").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Cancel").assertCountEquals(3)
    }

    @Test
    fun `toast message appears when triggered alongside DownloadQueueScreen`() {
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            FullDownloadContent(toastHostState = toastHostState)
        }

        composeTestRule.runOnIdle {
            toastHostState.show("Paused: Attack on Titan")
        }

        composeTestRule.onNodeWithText("Paused: Attack on Titan").assertIsDisplayed()
    }

    @Test
    fun `cancel toast message appears with ToastHost provider`() {
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            FullDownloadContent(toastHostState = toastHostState)
        }

        composeTestRule.runOnIdle {
            toastHostState.show("Cancelled: Attack on Titan")
        }

        composeTestRule.onNodeWithText("Cancelled: Attack on Titan").assertIsDisplayed()
    }

    @Test
    fun `resume toast message appears with ToastHost provider`() {
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            FullDownloadContent(toastHostState = toastHostState)
        }

        composeTestRule.runOnIdle {
            toastHostState.show("Resumed: Spy x Family")
        }

        composeTestRule.onNodeWithText("Resumed: Spy x Family").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Search filtering
    // -----------------------------------------------------------------------

    @Test
    fun `search query filters the download list`() {
        composeTestRule.setContent {
            val fakeManager = rememberWithEntries()
            CompositionLocalProvider(
                LocalDownloadManager provides fakeManager,
            ) {
                AnikkuTheme {
                    DownloadQueueContent(
                        data = DownloadQueueData(testEntries, fakeManager),
                        searchQuery = "attack",
                        onSearchQueryChange = {},
                        onPauseResume = {},
                        onCancel = {},
                        onRetry = {},
                        onRemoveCompleted = {},
                        onClearAll = {},
                        onClearCompleted = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Attack on Titan").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Jujutsu Kaisen").assertCountEquals(0)
    }

    @Test
    fun `blank search query shows every download`() {
        composeTestRule.setContent {
            val fakeManager = rememberWithEntries()
            CompositionLocalProvider(
                LocalDownloadManager provides fakeManager,
            ) {
                AnikkuTheme {
                    DownloadQueueContent(
                        data = DownloadQueueData(testEntries, fakeManager),
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onPauseResume = {},
                        onCancel = {},
                        onRetry = {},
                        onRemoveCompleted = {},
                        onClearAll = {},
                        onClearCompleted = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Attack on Titan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jujutsu Kaisen").assertIsDisplayed()
        composeTestRule.onNodeWithText("One Piece").assertIsDisplayed()
    }
}
