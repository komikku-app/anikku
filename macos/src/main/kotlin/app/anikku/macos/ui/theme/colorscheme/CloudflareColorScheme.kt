package app.anikku.macos.ui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Clouflare theme
 * Original color scheme by LuftVerbot
 */
object CloudflareColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFF38020),
        onPrimary = Color(0xFF1B1B22),
        primaryContainer = Color(0xFFF38020),
        onPrimaryContainer = Color(0xFF1B1B22),
        inversePrimary = Color(0xFFD6BAFF),
        secondary = Color(0xFFF38020),
        onSecondary = Color(0xFF1B1B22),
        secondaryContainer = Color(0xFFF38020),
        onSecondaryContainer = Color(0xFF1B1B22),
        tertiary = Color(0xFF1B1B22),
        onTertiary = Color(0xFFF38020),
        tertiaryContainer = Color(0xFF1B1B22),
        onTertiaryContainer = Color(0xFFF38020),
        background = Color(0xFF1B1B22),
        onBackground = Color(0xFFEFF2F5),
        surface = Color(0xFF1B1B22),
        onSurface = Color(0xFFEFF2F5),
        surfaceVariant = Color(0xFF3F3F46),
        onSurfaceVariant = Color(0xFFD8FFFFFF),
        surfaceTint = Color(0xFFF38020),
        inverseSurface = Color(0xFFF3EFF4),
        inverseOnSurface = Color(0xFF313033),
        outline = Color(0xFFF38020),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFF38020),
        onPrimary = Color(0xFFEFF2F5),
        primaryContainer = Color(0xFFF38020),
        onPrimaryContainer = Color(0xFFEFF2F5),
        inversePrimary = Color(0xFFD6BAFF),
        secondary = Color(0xFFF38020),
        onSecondary = Color(0xFFEFF2F5),
        secondaryContainer = Color(0xFFF38020),
        onSecondaryContainer = Color(0xFFEFF2F5),
        tertiary = Color(0xFFEFF2F5),
        onTertiary = Color(0xFFF38020),
        tertiaryContainer = Color(0xFFEFF2F5),
        onTertiaryContainer = Color(0xFFF38020),
        background = Color(0xFFEFF2F5),
        onBackground = Color(0xFF1B1B22),
        surface = Color(0xFFEFF2F5),
        onSurface = Color(0xFF1B1B22),
        surfaceVariant = Color(0xFFB9B0CC),
        onSurfaceVariant = Color(0xFFD849454E),
        surfaceTint = Color(0xFFF38020),
        inverseSurface = Color(0xFF313033),
        inverseOnSurface = Color(0xFFF3EFF4),
        outline = Color(0xFFF38020),
    )
}
