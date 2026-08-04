package app.anikku.macos.ui.screens.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import app.anikku.macos.ui.screens.models.AnimeModel
import app.anikku.macos.ui.theme.AnikkuTheme
import org.junit.Rule
import org.junit.Test

/** Regression coverage for the Phase 11 1,000-entry library requirement. */
class LibraryPerformanceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `one thousand entry library scrolls and transitions between lazy grid and list`() {
        val entries = (0 until 1_000).map { index ->
            AnimeModel(
                id = index.toLong(),
                title = "Anime ${index.toString().padStart(4, '0')}",
                status = if (index % 2 == 0) 1 else 2,
            )
        }

        composeTestRule.setContent {
            var mode by remember { mutableStateOf(LibraryTab.DisplayMode.Grid) }
            AnikkuTheme {
                LibraryContent(
                    libraryAnime = entries,
                    libraryCount = entries.size,
                    displayMode = mode,
                    searchQuery = "",
                    sortMode = LibrarySortMode.Title,
                    showSortMenu = false,
                    onSearchQueryChange = {},
                    onToggleDisplayMode = {
                        mode = if (mode == LibraryTab.DisplayMode.Grid) {
                            LibraryTab.DisplayMode.List
                        } else {
                            LibraryTab.DisplayMode.Grid
                        }
                    },
                    onSortModeChange = {},
                    onToggleSortMenu = {},
                    onDismissSortMenu = {},
                    onCategorySelect = {},
                    onAnimeClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("library_grid").performScrollToIndex(999)
        composeTestRule.onNodeWithText("Anime 0999").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Switch to list").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("library_list").fetchSemanticsNodes().size == 1
        }
        composeTestRule.onNodeWithTag("library_list").performScrollToIndex(999)
        composeTestRule.onNodeWithText("Anime 0999").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Switch to grid").assertIsDisplayed()
    }
}
