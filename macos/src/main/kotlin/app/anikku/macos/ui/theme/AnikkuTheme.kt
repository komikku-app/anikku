package app.anikku.macos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import app.anikku.macos.ui.settings.ThemeMode
import app.anikku.macos.ui.theme.colorscheme.BaseColorScheme
import app.anikku.macos.ui.theme.colorscheme.CloudflareColorScheme
import app.anikku.macos.ui.theme.colorscheme.CottoncandyColorScheme
import app.anikku.macos.ui.theme.colorscheme.CustomColorScheme
import app.anikku.macos.ui.theme.colorscheme.DoomColorScheme
import app.anikku.macos.ui.theme.colorscheme.GreenAppleColorScheme
import app.anikku.macos.ui.theme.colorscheme.LavenderColorScheme
import app.anikku.macos.ui.theme.colorscheme.MatrixColorScheme
import app.anikku.macos.ui.theme.colorscheme.MidnightDuskColorScheme
import app.anikku.macos.ui.theme.colorscheme.MochaColorScheme
import app.anikku.macos.ui.theme.colorscheme.MonetColorScheme
import app.anikku.macos.ui.theme.colorscheme.NordColorScheme
import app.anikku.macos.ui.theme.colorscheme.SapphireColorScheme
import app.anikku.macos.ui.theme.colorscheme.StrawberryColorScheme
import app.anikku.macos.ui.theme.colorscheme.TachiyomiColorScheme
import app.anikku.macos.ui.theme.colorscheme.TakoColorScheme
import app.anikku.macos.ui.theme.colorscheme.TealTurqoiseColorScheme
import app.anikku.macos.ui.theme.colorscheme.TidalWaveColorScheme
import app.anikku.macos.ui.theme.colorscheme.YinYangColorScheme
import app.anikku.macos.ui.theme.colorscheme.YotsubaColorScheme

/**
 * macOS Anikku Theme System.
 *
 * Ported from the Android TachiyomiTheme. Provides the same 18+ color schemes.
 * Theme selection and appearance mode are persisted by [SettingsState]. Monet
 * and Custom use deterministic desktop palettes because wallpaper colors are
 * not exposed through the macOS desktop target.
 */
object AnikkuTheme {

    /**
     * Available color schemes matching the Android AppTheme enum.
     */
    enum class Theme(
        val displayName: String,
        val scheme: BaseColorScheme,
    ) {
        DEFAULT("Default", TachiyomiColorScheme),
        MONET("Monet", MonetColorScheme()),
        CUSTOM("Custom", CustomColorScheme()),
        CLOUDFLARE("Cloudflare", CloudflareColorScheme),
        COTTONCANDY("Cotton Candy", CottoncandyColorScheme),
        DOOM("Doom", DoomColorScheme),
        GREEN_APPLE("Green Apple", GreenAppleColorScheme),
        LAVENDER("Lavender", LavenderColorScheme),
        MATRIX("Matrix", MatrixColorScheme),
        MIDNIGHT_DUSK("Midnight Dusk", MidnightDuskColorScheme),
        MOCHA("Mocha", MochaColorScheme),
        SAPPHIRE("Sapphire", SapphireColorScheme),
        NORD("Nord", NordColorScheme),
        STRAWBERRY_DAIQUIRI("Strawberry Daiquiri", StrawberryColorScheme),
        TAKO("Tako", TakoColorScheme),
        TEALTURQUOISE("Teal & Turquoise", TealTurqoiseColorScheme),
        TIDAL_WAVE("Tidal Wave", TidalWaveColorScheme),
        YINYANG("Yin Yang", YinYangColorScheme),
        YOTSUBA("Yotsuba", YotsubaColorScheme),
    }

    /**
     * Convenient list of all themes for settings UI.
     */
    val allThemes: List<Theme> = Theme.entries.toList()
}

/**
 * Applies the Anikku theme with the specified color scheme, dark mode, and AMOLED black option.
 *
 * Phase 9.7: Dark Mode Detection — Supports [ThemeMode] for SYSTEM / LIGHT / DARK.
 *
 * @param theme The color scheme theme to use (default: DEFAULT / TachiyomiColorScheme)
 * @param isAmoledOLED Whether to use AMOLED pure black backgrounds in dark mode
 * @param isDarkOverride Dark mode override: SYSTEM=sync with macOS, LIGHT=force light, DARK=force dark
 * @param content The content to render within the theme
 */
@Composable
fun AnikkuTheme(
    theme: AnikkuTheme.Theme = AnikkuTheme.Theme.DEFAULT,
    isAmoledOLED: Boolean = false,
    isDarkOverride: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (isDarkOverride) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = theme.scheme.getColorScheme(isDark, isAmoledOLED)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/**
 * Get the current color scheme without wrapping in MaterialTheme.
 * Useful for obtaining colors outside of a @Composable context.
 */
@Composable
@ReadOnlyComposable
fun getThemeColorScheme(
    theme: AnikkuTheme.Theme = AnikkuTheme.Theme.DEFAULT,
    isAmoledOLED: Boolean = false,
): ColorScheme {
    val isDark = isSystemInDarkTheme()
    return theme.scheme.getColorScheme(isDark, isAmoledOLED)
}
