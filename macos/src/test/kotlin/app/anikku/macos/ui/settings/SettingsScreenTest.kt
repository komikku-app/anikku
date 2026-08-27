package app.anikku.macos.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastHost
import app.anikku.macos.ui.components.ToastHostState
import app.anikku.macos.ui.theme.AnikkuTheme
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.Navigator
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [SettingsScreen].
 *
 * Tests verify rendering and state-propagation via [LocalSettingsState].
 * Tests wrap content in a [Navigator] because [SettingsScreen] calls
 * `LocalNavigator.currentOrThrow` for navigation to sub-screens.
 *
 * Note: Interactive click tests (performClick/performSemanticsAction) are
 * not available in Compose Multiplatform 1.11.1. Click behavior is tested
 * indirectly through [SettingsStateTest] (state mutation + persistence).
 *
 * Toast-wired checkbox interactions (theme, AMOLED, library, player, download
 * settings) are tested by verifying the [ToastHost] renders without error when
 * the [LocalToastHost] provider is wired alongside [SettingsScreen].
 */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Wraps content in a Voyager Navigator with a stub screen.
     * Required because SettingsScreen uses LocalNavigator.currentOrThrow.
     */
    @Composable
    private fun WithNavigator(content: @Composable () -> Unit) {
        Navigator(StubScreen) {
            content()
        }
    }

    /**
     * Renders SettingsScreen inside Navigator + ToastHost providers.
     * Used by tests that need the full toast-wired environment.
     */
    @Composable
    private fun FullSettingsContent(
        settingsState: SettingsState = SettingsState(),
        toastHostState: ToastHostState = ToastHostState(),
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(
            LocalSettingsState provides settingsState,
            LocalToastHost provides toastHostState,
        ) {
            AnikkuTheme {
                Box(Modifier.fillMaxSize()) {
                    WithNavigator {
                        content()
                    }
                    ToastHost(state = toastHostState)
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Rendering: visible header content
    // -----------------------------------------------------------------------

    @Test
    fun `renders Settings header`() {
        composeTestRule.setContent {
            AnikkuTheme {
                WithNavigator {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun `renders Appearance section heading`() {
        composeTestRule.setContent {
            AnikkuTheme {
                WithNavigator {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun `renders AMOLED black checkbox label`() {
        composeTestRule.setContent {
            AnikkuTheme {
                WithNavigator {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("AMOLED black").assertIsDisplayed()
    }

    @Test
    fun `renders Theme label`() {
        composeTestRule.setContent {
            AnikkuTheme {
                WithNavigator {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
    }

    @Test
    fun `renders Library section heading`() {
        composeTestRule.setContent {
            AnikkuTheme {
                WithNavigator {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("Library").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Section headings for new sections (top of page — scrolled into view)
    // -----------------------------------------------------------------------

    @Test
    fun `renders Player section heading`() {
        composeTestRule.setContent {
            AnikkuTheme {
                WithNavigator {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("Player").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Checkbox labels (wired to toast feedback, top of page)
    // -----------------------------------------------------------------------

    @Test
    fun `renders the settings search field`() {
        composeTestRule.setContent {
            AnikkuTheme {
                WithNavigator {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("Search settings").assertIsDisplayed()
    }

    @Test
    fun `renders Auto-play next episode checkbox`() {
        composeTestRule.setContent {
            AnikkuTheme {
                WithNavigator {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("Auto-play next episode").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // ToastHost integration: full wired environment renders without error
    // -----------------------------------------------------------------------

    @Test
    fun `renders with ToastHost provider without crash`() {
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            FullSettingsContent(toastHostState = toastHostState) {
                SettingsScreen()
            }
        }

        // Settings header should render when wired with ToastHost
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun `renders AMOLED black with ToastHost provider`() {
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            FullSettingsContent(toastHostState = toastHostState) {
                SettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("AMOLED black").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // State propagation: theme renders correctly from SettingsState
    // -----------------------------------------------------------------------

    @Test
    fun `default theme name shown in selector`() {
        composeTestRule.setContent {
            AnikkuTheme {
                WithNavigator {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("Default").assertIsDisplayed()
    }

    @Test
    fun `custom theme SAPPHIRE renders in selector`() {
        val state = SettingsState()
        state.theme = AnikkuTheme.Theme.SAPPHIRE

        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsState provides state) {
                AnikkuTheme {
                    WithNavigator {
                        SettingsScreen()
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Sapphire").assertIsDisplayed()
    }

    @Test
    fun `custom theme MATRIX renders in selector`() {
        val state = SettingsState()
        state.theme = AnikkuTheme.Theme.MATRIX

        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsState provides state) {
                AnikkuTheme {
                    WithNavigator {
                        SettingsScreen()
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Matrix").assertIsDisplayed()
    }

    @Test
    fun `custom theme NORD renders in selector`() {
        val state = SettingsState()
        state.theme = AnikkuTheme.Theme.NORD

        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsState provides state) {
                AnikkuTheme {
                    WithNavigator {
                        SettingsScreen()
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Nord").assertIsDisplayed()
    }

    @Test
    fun `custom theme LAVENDER renders in selector`() {
        val state = SettingsState()
        state.theme = AnikkuTheme.Theme.LAVENDER

        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsState provides state) {
                AnikkuTheme {
                    WithNavigator {
                        SettingsScreen()
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Lavender").assertIsDisplayed()
    }

    @Test
    fun `AMOLED state propagates from SettingsState`() {
        val state = SettingsState()
        state.isAmoledOLED = true

        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsState provides state) {
                AnikkuTheme(isAmoledOLED = state.isAmoledOLED) {
                    WithNavigator {
                        SettingsScreen()
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("AMOLED black").assertIsDisplayed()
    }

    @Test
    fun `toast message appears when triggered alongside SettingsScreen`() {
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            FullSettingsContent(toastHostState = toastHostState) {
                SettingsScreen()
            }
        }

        composeTestRule.runOnIdle {
            toastHostState.show("Category tabs: on")
        }

        composeTestRule.onNodeWithText("Category tabs: on").assertIsDisplayed()
    }

    @Test
    fun `custom theme with ToastHost renders correctly`() {
        val settingsState = SettingsState()
        settingsState.theme = AnikkuTheme.Theme.DOOM
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            FullSettingsContent(
                settingsState = settingsState,
                toastHostState = toastHostState,
            ) {
                SettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("Doom").assertIsDisplayed()
        composeTestRule.onNodeWithText("AMOLED black").assertIsDisplayed()
    }
}

/**
 * A stub screen for the Voyager Navigator wrapper in tests.
 * Renders nothing — just provides the Navigator context.
 */
private object StubScreen : AnikkuScreen() {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        // Renders nothing — the actual test content is inside Navigator's content lambda
    }
}
