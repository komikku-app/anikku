package app.anikku.macos.ui.theme.colorscheme

import androidx.compose.material3.ColorScheme

/**
 * macOS Custom color scheme.
 *
 * Uses the deterministic desktop fallback palette. A user seed is accepted for
 * source compatibility but macOS does not currently expose a custom seed UI.
 */
class CustomColorScheme(
    seedColor: Int = 0xFF0058CA.toInt(),
) : BaseColorScheme() {

    private val fallback = TachiyomiColorScheme

    override val darkScheme: ColorScheme
        get() = fallback.darkScheme

    override val lightScheme: ColorScheme
        get() = fallback.lightScheme
}
