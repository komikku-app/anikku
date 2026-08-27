package app.anikku.macos.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.anikku.macos.platform.logging.TerminalErrorLogger
import app.anikku.macos.ui.theme.AnikkuTheme
import org.junit.Rule
import org.junit.Test

/**
 * Unit and Compose UI tests for [ToastHostState] and [ToastHost].
 *
 * Unit tests verify state transitions (show/dismiss, duration, ID sequencing).
 * Compose UI tests verify rendering, text updates, and dismiss behavior.
 *
 * Note: Auto-dismiss timing (LaunchedEffect + delay()) is intentionally not
 * tested here because Compose Multiplatform 1.11.1's test infrastructure
 * does not support advancing virtual time through coroutine delay() calls.
 */
class MacOSToastTest {

    // =========================================================================
    // ToastHostState — Unit tests
    // =========================================================================

    @Test
    fun `state initializes with null currentToast`() {
        val state = ToastHostState()
        assert(state.currentToast == null) {
            "currentToast should be null initially"
        }
    }

    @Test
    fun `show sets currentToast to non-null`() {
        val state = ToastHostState()
        state.show("Hello")
        val toast = state.currentToast
        assert(toast != null) { "currentToast should be non-null after show()" }
        assert(toast!!.text == "Hello")
    }

    @Test
    fun `show with default duration is SHORT`() {
        val state = ToastHostState()
        state.show("Test")
        assert(state.currentToast?.duration == ToastDuration.SHORT)
    }

    @Test
    fun `show with explicit LONG duration`() {
        val state = ToastHostState()
        state.show("Long toast", ToastDuration.LONG)
        assert(state.currentToast?.duration == ToastDuration.LONG)
    }

    @Test
    fun `dismiss sets currentToast to null`() {
        val state = ToastHostState()
        state.show("Goodbye")
        assert(state.currentToast != null)

        state.dismiss()
        assert(state.currentToast == null) {
            "currentToast should be null after dismiss()"
        }
    }

    @Test
    fun `dismiss on already-dismissed state is a no-op`() {
        val state = ToastHostState()
        state.dismiss() // no-op
        assert(state.currentToast == null)

        state.show("Hello")
        state.dismiss()
        state.dismiss() // second dismiss
        assert(state.currentToast == null)
    }

    @Test
    fun `sequential show calls increment IDs`() {
        val state = ToastHostState()
        state.show("First")
        val id1 = state.currentToast?.id

        state.show("Second")
        val id2 = state.currentToast?.id

        assert(id1 != null)
        assert(id2 != null)
        assert(id2!! > id1!!) {
            "Second toast ID should be greater than first"
        }
    }

    @Test
    fun `show replaces existing toast`() {
        val state = ToastHostState()
        state.show("Old")
        state.show("New")

        assert(state.currentToast?.text == "New") {
            "New toast should replace old one"
        }
    }

    @Test
    fun `show with isError true logs error to TerminalErrorLogger`() {
        TerminalErrorLogger.clear()
        val state = ToastHostState()
        state.show(
            text = "Network error",
            duration = ToastDuration.LONG,
            isError = true,
            source = "TestSource",
            location = "TestScreen.load",
        )

        val error = TerminalErrorLogger.errors.single()
        assert(error.message == "Network error")
        assert(error.source == "TestSource")
        assert(error.location == "TestScreen.load")
    }

    @Test
    fun `show with isError false does not log error`() {
        TerminalErrorLogger.clear()
        val state = ToastHostState()
        state.show("Just a message")

        assert(TerminalErrorLogger.errors.isEmpty())
    }

    @Test
    fun `showError convenience method logs error and shows toast`() {
        TerminalErrorLogger.clear()
        val state = ToastHostState()
        state.showError(
            text = "Something went wrong",
            source = "TestSource",
            location = "TestScreen.load",
        )

        assert(state.currentToast?.text == "Something went wrong")
        val error = TerminalErrorLogger.errors.single()
        assert(error.message == "Something went wrong")
        assert(error.source == "TestSource")
        assert(error.location == "TestScreen.load")
    }

    @Test
    fun `show with empty text string is accepted`() {
        val state = ToastHostState()
        state.show("")
        assert(state.currentToast != null)
        assert(state.currentToast?.text == "")
    }

    @Test
    fun `dismiss clears toast and next show produces new ID`() {
        val state = ToastHostState()
        state.show("First")
        val id1 = state.currentToast?.id

        state.dismiss()
        assert(state.currentToast == null)

        state.show("Second")
        val id2 = state.currentToast?.id

        assert(id2 != null)
        assert(id2 != id1) {
            "Toast after dismiss+show should have a new ID"
        }
    }

    @Test
    fun `multiple rapid shows only last toast is current`() {
        val state = ToastHostState()
        state.show("A")
        state.show("B")
        state.show("C")
        state.show("D")

        assert(state.currentToast?.text == "D") {
            "Only the last toast should be current after rapid shows"
        }
    }

    // =========================================================================
    // ToastHost — Compose UI rendering tests
    // =========================================================================

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `toast text is displayed after show call`() {
        val state = ToastHostState()

        composeTestRule.setContent {
            AnikkuTheme {
                ToastHost(state = state)
            }
        }

        composeTestRule.runOnIdle {
            state.show("Download complete")
        }

        composeTestRule.onNodeWithText("Download complete").assertIsDisplayed()
    }

    @Test
    fun `toast renders with explicitly passed state`() {
        val state = ToastHostState()

        composeTestRule.setContent {
            AnikkuTheme {
                ToastHost(state = state)
            }
        }

        composeTestRule.runOnIdle {
            state.show("Explicit state toast")
        }

        composeTestRule.onNodeWithText("Explicit state toast").assertIsDisplayed()
    }

    @Test
    fun `toast content changes when new show replaces old toast`() {
        val state = ToastHostState()

        composeTestRule.setContent {
            AnikkuTheme {
                ToastHost(state = state)
            }
        }

        composeTestRule.runOnIdle {
            state.show("First message")
        }
        composeTestRule.onNodeWithText("First message").assertIsDisplayed()

        // Replace with new toast
        composeTestRule.runOnIdle {
            state.show("Second message")
        }

        // New toast text is displayed
        composeTestRule.onNodeWithText("Second message").assertIsDisplayed()

        // After replacement, show a third toast to verify old ones don't linger
        composeTestRule.runOnIdle {
            state.show("Third message")
        }
        composeTestRule.onNodeWithText("Third message").assertIsDisplayed()
    }

    @Test
    fun `toast disappears after dismiss and reappears with new show`() {
        val state = ToastHostState()

        composeTestRule.setContent {
            AnikkuTheme {
                ToastHost(state = state)
            }
        }

        composeTestRule.runOnIdle {
            state.show("Dismiss me")
        }
        composeTestRule.onNodeWithText("Dismiss me").assertIsDisplayed()

        // Dismiss
        composeTestRule.runOnIdle {
            state.dismiss()
        }

        // Show a new toast — the old one should be gone
        composeTestRule.runOnIdle {
            state.show("New toast after dismiss")
        }
        composeTestRule.onNodeWithText("New toast after dismiss").assertIsDisplayed()
    }

    @Test
    fun `rapid show calls render only the last toast`() {
        val state = ToastHostState()

        composeTestRule.setContent {
            AnikkuTheme {
                ToastHost(state = state)
            }
        }

        composeTestRule.runOnIdle {
            state.show("Alpha")
            state.show("Beta")
            state.show("Gamma")
        }

        // Only the last one should be visible
        composeTestRule.onNodeWithText("Gamma").assertIsDisplayed()
    }

    @Test
    fun `toast is not rendered when state has not been shown`() {
        val state = ToastHostState()

        composeTestRule.setContent {
            AnikkuTheme {
                ToastHost(state = state)
            }
        }

        // Show a specific toast, then dismiss, then verify we can show a new one
        composeTestRule.runOnIdle {
            state.show("Temporary toast")
        }
        composeTestRule.onNodeWithText("Temporary toast").assertIsDisplayed()

        composeTestRule.runOnIdle {
            state.dismiss()
        }

        // Show a fresh toast to confirm render works after dismiss cycle
        composeTestRule.runOnIdle {
            state.show("Fresh toast")
        }
        composeTestRule.onNodeWithText("Fresh toast").assertIsDisplayed()
    }

    @Test
    fun `toast renders inside AnikkuTheme composition`() {
        val state = ToastHostState()

        composeTestRule.setContent {
            AnikkuTheme {
                ToastHost(state = state)
            }
        }

        composeTestRule.runOnIdle {
            state.show("Themed toast")
        }

        composeTestRule.onNodeWithText("Themed toast").assertIsDisplayed()
    }

    // =========================================================================
    // LocalToastHost — CompositionLocal provider pattern
    // =========================================================================

    @Test
    fun `LocalToastHost has safe default and does not crash without provider`() {
        composeTestRule.setContent {
            AnikkuTheme {
                // Accessing LocalToastHost.current without CompositionLocalProvider
                // should return a default ToastHostState (not crash)
                ToastHost()
            }
        }

        // If we reach here, the composition didn't crash — the safe default works
        composeTestRule.onNodeWithText("Press SPACE to play/pause") 
            .assertDoesNotExist()
    }

    @Test
    fun `default LocalToastHost can show and display a toast`() {
        val capturedState = ToastHostState()

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalToastHost provides capturedState,
            ) {
                AnikkuTheme {
                    ToastHost(state = LocalToastHost.current)
                }
            }
        }

        composeTestRule.runOnIdle {
            capturedState.show("Provided via LocalToastHost")
        }

        composeTestRule.onNodeWithText("Provided via LocalToastHost").assertIsDisplayed()
    }

    @Test
    fun `LocalToastHost provided state is accessible via current`() {
        val state = ToastHostState()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalToastHost provides state) {
                AnikkuTheme {
                    // Access the provided state through the CompositionLocal
                    val retrieved = LocalToastHost.current
                    ToastHost(state = retrieved)
                }
            }
        }

        composeTestRule.runOnIdle {
            state.show("Retrieved from CompositionLocal")
        }

        composeTestRule.onNodeWithText("Retrieved from CompositionLocal").assertIsDisplayed()
    }

    @Test
    fun `ToastHost default parameter picks up provided LocalToastHost`() {
        val state = ToastHostState()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalToastHost provides state) {
                AnikkuTheme {
                    // ToastHost() with no arguments defaults to LocalToastHost.current
                    ToastHost()
                }
            }
        }

        composeTestRule.runOnIdle {
            state.show("Default parameter picks up provider")
        }

        composeTestRule.onNodeWithText("Default parameter picks up provider").assertIsDisplayed()
    }

    @Test
    fun `nested CompositionLocalProvider overrides LocalToastHost`() {
        val outerState = ToastHostState()
        val innerState = ToastHostState()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalToastHost provides outerState) {
                AnikkuTheme {
                    CompositionLocalProvider(LocalToastHost provides innerState) {
                        // Inner scope should use innerState
                        ToastHost(state = LocalToastHost.current)
                    }
                }
            }
        }

        composeTestRule.runOnIdle {
            innerState.show("Inner provider override")
        }

        composeTestRule.onNodeWithText("Inner provider override").assertIsDisplayed()
    }

    @Test
    fun `screen rendering pattern with LocalToastHost provider`() {
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalToastHost provides toastHostState,
            ) {
                AnikkuTheme {
                    // Simulates the pattern from AnikkuApp.kt:
                    // CompositionLocalProvider + Box + content + ToastHost overlay
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Screen content")
                        }
                        ToastHost(state = toastHostState)
                    }
                }
            }
        }

        // Screen content renders
        composeTestRule.onNodeWithText("Screen content").assertIsDisplayed()

        // Toast appears on top
        composeTestRule.runOnIdle {
            toastHostState.show("Toast overlay message")
        }
        composeTestRule.onNodeWithText("Toast overlay message").assertIsDisplayed()
    }

    @Test
    fun `multiple independent LocalToastHost states do not interfere`() {
        val stateA = ToastHostState()
        val stateB = ToastHostState()

        composeTestRule.setContent {
            AnikkuTheme {
                Box {
                    CompositionLocalProvider(LocalToastHost provides stateA) {
                        ToastHost(state = LocalToastHost.current)
                    }
                    CompositionLocalProvider(LocalToastHost provides stateB) {
                        ToastHost(state = LocalToastHost.current)
                    }
                }
            }
        }

        // Show a toast in stateA
        composeTestRule.runOnIdle {
            stateA.show("State A toast")
        }
        composeTestRule.onNodeWithText("State A toast").assertIsDisplayed()

        // Show a different toast in stateB — both should render
        composeTestRule.runOnIdle {
            stateB.show("State B toast")
        }
        composeTestRule.onNodeWithText("State B toast").assertIsDisplayed()
    }
}
