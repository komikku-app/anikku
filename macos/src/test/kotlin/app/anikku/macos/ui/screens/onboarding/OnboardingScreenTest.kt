package app.anikku.macos.ui.screens.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `first step shows welcome screen`() {
        composeRule.setContent {
            OnboardingScreen(onComplete = {}).Content()
        }

        composeRule.onNodeWithText("Welcome to Anikku").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun `continue button advances to second step`() {
        var completed = false
        composeRule.setContent {
            OnboardingScreen(onComplete = { completed = true }).Content()
        }

        // First step: click Continue twice to reach step 2 (Appearance)
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Choose Your Look").assertIsDisplayed()
    }

    @Test
    fun `progression through all steps`() {
        composeRule.setContent {
            OnboardingScreen(onComplete = {}).Content()
        }

        // Step 0: Welcome
        composeRule.onNodeWithText("Welcome to Anikku").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").performClick()

        // Step 1: Appearance
        composeRule.onNodeWithText("Choose Your Look").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").performClick()

        // Step 2: Storage
        composeRule.onNodeWithText("Download Location").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").performClick()

        // Step 3: Tips
        composeRule.onNodeWithText("Quick Tips").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").performClick()

        // Step 4: Ready
        composeRule.onNodeWithText("You're All Set!").assertIsDisplayed()
    }

    @Test
    fun `back button returns to previous step`() {
        composeRule.setContent {
            OnboardingScreen(onComplete = {}).Content()
        }

        // Advance to step 2
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Download Location").assertIsDisplayed()

        // Go back
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Choose Your Look").assertIsDisplayed()
    }

    @Test
    fun `get started button triggers onComplete`() {
        var completed = false
        composeRule.setContent {
            OnboardingScreen(onComplete = { completed = true }).Content()
        }

        // Navigate to final step
        repeat(4) { composeRule.onNodeWithText("Continue").performClick() }

        // Click "Get Started!"
        composeRule.onNodeWithText("Get Started!").performClick()
        assert(completed)
    }

    @Test
    fun `initial step resumes and step changes are reported`() {
        val changedSteps = mutableListOf<Int>()
        composeRule.setContent {
            OnboardingScreen(
                onComplete = {},
                initialStep = 3,
                onStepChanged = { changedSteps += it },
            ).Content()
        }

        composeRule.onNodeWithText("Quick Tips").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Download Location").assertIsDisplayed()
        assert(changedSteps == listOf(2))
    }

    @Test
    fun `completion reports reset step after resumed flow`() {
        var completed = false
        val changedSteps = mutableListOf<Int>()
        composeRule.setContent {
            OnboardingScreen(
                onComplete = { completed = true },
                initialStep = 4,
                onStepChanged = { changedSteps += it },
            ).Content()
        }

        composeRule.onNodeWithText("Get Started!").performClick()
        assert(completed)
        assert(changedSteps == listOf(0))
    }

    @Test
    fun `back button not shown on first step`() {
        composeRule.setContent {
            OnboardingScreen(onComplete = {}).Content()
        }

        composeRule.onNodeWithText("Back").assertDoesNotExist()
    }
}
