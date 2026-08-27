package app.anikku.macos.ui.security

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.anikku.macos.ui.theme.AnikkuTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppLockScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `wrong PIN stays locked and correct PIN unlocks`() {
        var unlocked = false
        composeRule.setContent {
            AnikkuTheme {
                AppLockScreen(
                    biometricAvailable = false,
                    useBiometrics = false,
                    onVerifyPin = { it == "2468" },
                    onBiometricUnlock = { false },
                    onUnlocked = { unlocked = true },
                )
            }
        }

        composeRule.onNodeWithText("Anikku is locked").assertIsDisplayed()
        composeRule.onNodeWithText("PIN").performTextInput("0000")
        composeRule.onNodeWithText("Unlock").performClick()
        composeRule.onNodeWithText("Incorrect PIN").assertIsDisplayed()
        assertFalse(unlocked)

        composeRule.onNodeWithText("PIN").performTextInput("2468")
        composeRule.onNodeWithText("Unlock").performClick()
        composeRule.runOnIdle { assertTrue(unlocked) }
    }

    @Test
    fun `Touch ID action unlocks after successful native result`() {
        var unlocked = false
        composeRule.setContent {
            AnikkuTheme {
                AppLockScreen(
                    biometricAvailable = true,
                    useBiometrics = true,
                    onVerifyPin = { false },
                    onBiometricUnlock = { true },
                    onUnlocked = { unlocked = true },
                )
            }
        }

        composeRule.onNodeWithText("Use Touch ID").performClick()
        composeRule.waitUntil(5_000) { unlocked }
        assertTrue(unlocked)
    }
}
