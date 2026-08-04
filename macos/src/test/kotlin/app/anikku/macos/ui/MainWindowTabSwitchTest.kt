package app.anikku.macos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import app.anikku.macos.ui.theme.AnikkuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [MainWindow] tab switching, [orderedTabs], and
 * [NavigationRailSidebar].
 *
 * Verifies that:
 * - The 9 primary tabs are correctly ordered by title
 * - NavigationRailSidebar renders all 9 tab labels
 * - NavigationRailSidebar renders without crashing at boundary index values
 */
class MainWindowTabSwitchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val expectedTabTitles = listOf(
        "Library", "Updates", "History", "Stats", "Browse", "Torrents", "Downloads", "Discover", "More",
    )

    // =========================================================================
    // orderedTabs — Structural tests (resolved via Compose for @Composable getter)
    // =========================================================================

    @Test
    fun `orderedTabs has 9 tabs`() {
        assertEquals(9, orderedTabs.size)
    }

    @Test
    fun `orderedTabs are in correct order`() {
        composeTestRule.setContent {
            expectedTabTitles.forEachIndexed { index, title ->
                assertEquals(title, orderedTabs[index].options.title)
            }
        }
    }

    @Test
    fun `all orderedTabs have non-empty titles`() {
        composeTestRule.setContent {
            orderedTabs.forEachIndexed { i, tab ->
                assert(tab.options.title.isNotEmpty()) { "Tab $i title should not be empty" }
            }
        }
    }

    // =========================================================================
    // NavigationRailSidebar — Compose UI rendering
    // =========================================================================

    @Test
    fun `renders all 9 tab labels with index 0`() {
        composeTestRule.setContent {
            AnikkuTheme {
                NavigationRailSidebar(currentTabIndex = 0, onSelectTab = {})
            }
        }

        // All 9 labels should be visible
        expectedTabTitles.forEach { title ->
            composeTestRule.onNodeWithText(title).assertIsDisplayed()
        }
    }

    @Test
    fun `renders all 9 tab labels with index 8`() {
        composeTestRule.setContent {
            AnikkuTheme {
                NavigationRailSidebar(currentTabIndex = 8, onSelectTab = {})
            }
        }

        expectedTabTitles.forEach { title ->
            composeTestRule.onNodeWithText(title).assertIsDisplayed()
        }
    }

    @Test
    fun `renders without AnikkuTheme without crash`() {
        // MaterialTheme has defaults even without AnikkuTheme
        composeTestRule.setContent {
            NavigationRailSidebar(currentTabIndex = 0, onSelectTab = {})
        }

        composeTestRule.onNodeWithText("Library").assertIsDisplayed()
    }

    // =========================================================================
    // Index-based selection consistency
    // =========================================================================

    @Test
    fun `renders with different currentTabIndex without crash`() {
        composeTestRule.setContent {
            AnikkuTheme {
                NavigationRailSidebar(currentTabIndex = 7, onSelectTab = {})
            }
        }

        composeTestRule.onNodeWithText("Discover").assertIsDisplayed()
        composeTestRule.onNodeWithText("More").assertIsDisplayed()
    }

    @Test
    fun `global search callback switches to Browse and opens focused search screen`() {
        composeTestRule.setContent {
            AnikkuTheme { MainWindow() }
        }

        composeTestRule.runOnIdle {
            requireNotNull(GlobalKeyboardShortcuts.onOpenSearch).invoke()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Global Search").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search all sources for anime...").assertIsDisplayed()
    }

    @Test
    fun `global sidebar callback hides navigation rail`() {
        composeTestRule.setContent {
            AnikkuTheme { MainWindow() }
        }
        composeTestRule.onNodeWithText("More").assertIsDisplayed()

        composeTestRule.runOnIdle {
            requireNotNull(GlobalKeyboardShortcuts.onToggleSidebar).invoke()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText("More").assertCountEquals(0)
    }
}
