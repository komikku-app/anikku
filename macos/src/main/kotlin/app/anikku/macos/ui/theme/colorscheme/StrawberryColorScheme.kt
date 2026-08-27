package app.anikku.macos.ui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object StrawberryColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFFB2B8), onPrimary = Color(0xFF67001D),
        primaryContainer = Color(0xFFD53855), onPrimaryContainer = Color(0xFFFFFFFF),
        secondary = Color(0xFFED4A65), onSecondary = Color(0xFF201A1A),
        secondaryContainer = Color(0xFF91002A), onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFFE8C08E), onTertiary = Color(0xFF201A1A),
        tertiaryContainer = Color(0xFF775930), onTertiaryContainer = Color(0xFFFFF7F1),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF201A1A), onBackground = Color(0xFFF7DCDD),
        surface = Color(0xFF201A1A), onSurface = Color(0xFFF7DCDD),
        surfaceVariant = Color(0xFF322727), onSurfaceVariant = Color(0xFFE1BEC0),
        outline = Color(0xFFA9898B), outlineVariant = Color(0xFF594042),
        scrim = Color(0xFF000000), inverseSurface = Color(0xFFF7DCDD),
        inverseOnSurface = Color(0xFF3D2C2D), inversePrimary = Color(0xFFB61F40),
        surfaceDim = Color(0xFF1D1011), surfaceBright = Color(0xFF463536),
        surfaceContainerLowest = Color(0xFF2C2222), surfaceContainerLow = Color(0xFF302525),
        surfaceContainer = Color(0xFF322727), surfaceContainerHigh = Color(0xFF3C2F2F),
        surfaceContainerHighest = Color(0xFF463737),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFA10833), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD53855), onPrimaryContainer = Color(0xFFFFFFFF),
        secondary = Color(0xFFA10833), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD53855), onSecondaryContainer = Color(0xFFF6EAED),
        tertiary = Color(0xFF5F441D), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF87683D), onTertiaryContainer = Color(0xFFFFFFFF),
        error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFAFAFA), onBackground = Color(0xFF261819),
        surface = Color(0xFFFAFAFA), onSurface = Color(0xFF261819),
        surfaceVariant = Color(0xFFF6EAED), onSurfaceVariant = Color(0xFF594042),
        outline = Color(0xFF8D7071), outlineVariant = Color(0xFFE1BEC0),
        scrim = Color(0xFF000000), inverseSurface = Color(0xFF3D2C2D),
        inverseOnSurface = Color(0xFFFFECED), inversePrimary = Color(0xFFFFB2B8),
        surfaceDim = Color(0xFFEED4D5), surfaceBright = Color(0xFFFFF8F7),
        surfaceContainerLowest = Color(0xFFF7DCDD), surfaceContainerLow = Color(0xFFFDE2E3),
        surfaceContainer = Color(0xFFF6EAED), surfaceContainerHigh = Color(0xFFFFF0F0),
        surfaceContainerHighest = Color(0xFFFFFFFF),
    )
}
