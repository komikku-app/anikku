package app.anikku.macos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Shared status colors (success/warning) that adapt to the active theme.
 *
 * Previously each screen hardcoded the same hues (e.g. `0xFF4CAF50`), which
 * clashed on light/dark schemes. These getters pick a readable variant for
 * the current theme's background luminance. Tracker brand colors stay fixed —
 * they're logos, not status colors.
 */
object AnikkuStatusColors {

    val SuccessGreen = Color(0xFF4CAF50)
    val SuccessGreenDark = Color(0xFF81C784)

    val WarningAmber = Color(0xFFFFB300)
    val WarningAmberDark = Color(0xFFFFD54F)

    /** Success green — lighter on dark themes for contrast. */
    @Composable
    fun success(): Color =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) SuccessGreenDark else SuccessGreen

    /** Warning amber — lighter on dark themes for contrast. */
    @Composable
    fun warning(): Color =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) WarningAmberDark else WarningAmber
}
