package app.anikku.macos.ui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object MidnightDuskColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFF02475), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFBD1C5C), onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFFF02475), secondary = Color(0xFFF02475),
        onSecondary = Color(0xFF16151D), secondaryContainer = Color(0xFF66183C),
        onSecondaryContainer = Color(0xFFF02475), tertiary = Color(0xFF55971C),
        onTertiary = Color(0xFF16151D), tertiaryContainer = Color(0xFF386412),
        onTertiaryContainer = Color(0xFFE5E1E5), background = Color(0xFF16151D),
        onBackground = Color(0xFFE5E1E5), surface = Color(0xFF16151D),
        onSurface = Color(0xFFE5E1E5), surfaceVariant = Color(0xFF281624),
        onSurfaceVariant = Color(0xFFD6C1C4), surfaceTint = Color(0xFFF02475),
        inverseSurface = Color(0xFF333043), inverseOnSurface = Color(0xFFFFFFFF),
        outline = Color(0xFF9F8C8F), surfaceContainerLowest = Color(0xFF221320),
        surfaceContainerLow = Color(0xFF251522), surfaceContainer = Color(0xFF281624),
        surfaceContainerHigh = Color(0xFF2D1C2A), surfaceContainerHighest = Color(0xFF2F1F2C),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFBB0054), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9E1), onPrimaryContainer = Color(0xFF3F0017),
        inversePrimary = Color(0xFFFFB1C4), secondary = Color(0xFFBB0054),
        onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFEFBAD4),
        onSecondaryContainer = Color(0xFFD1377C), tertiary = Color(0xFF006638),
        onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFF00894b),
        onTertiaryContainer = Color(0xFF2D1600), background = Color(0xFFFFFBFF),
        onBackground = Color(0xFF1C1B1F), surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF1C1B1F), surfaceVariant = Color(0xFFF9E6F1),
        onSurfaceVariant = Color(0xFF524346), surfaceTint = Color(0xFFBB0054),
        inverseSurface = Color(0xFF313033), inverseOnSurface = Color(0xFFF4F0F4),
        outline = Color(0xFF847376), surfaceContainerLowest = Color(0xFFDAC0CD),
        surfaceContainerLow = Color(0xFFE8D1DD), surfaceContainer = Color(0xFFF9E6F1),
        surfaceContainerHigh = Color(0xFFFCF3F8), surfaceContainerHighest = Color(0xFFFEF9FC),
    )
}
