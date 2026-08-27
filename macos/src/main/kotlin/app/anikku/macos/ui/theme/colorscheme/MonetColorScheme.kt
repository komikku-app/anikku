package app.anikku.macos.ui.theme.colorscheme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import app.anikku.macos.ui.theme.colorscheme.BaseColorScheme
import app.anikku.macos.ui.theme.colorscheme.TachiyomiColorScheme

/**
 * macOS Monet color scheme.
 *
 * On Android, Monet uses WallpaperManager to extract dynamic colors from the system wallpaper.
 * On macOS, we fall back to the default TachiyomiColorScheme since we don't have access to
 * macOS wallpaper colors via Compose Desktop.
 *
 * Future enhancement: extract seed color from a user-selected image.
 */
class MonetColorScheme : BaseColorScheme() {

    private val fallback = TachiyomiColorScheme

    override val darkScheme: ColorScheme
        get() = fallback.darkScheme

    override val lightScheme: ColorScheme
        get() = fallback.lightScheme

    companion object {
        /**
         * Extract a seed color from an image.
         * Placeholder for future: use material-kolor to extract dominant colors.
         */
        fun extractSeedColorFromImage(pixels: IntArray, width: Int, height: Int): Int? {
            // Can be implemented using material-kolor's color extraction utilities
            return null
        }
    }
}
