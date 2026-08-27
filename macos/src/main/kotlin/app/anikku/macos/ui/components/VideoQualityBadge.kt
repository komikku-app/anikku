package app.anikku.macos.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A small chip/badge indicating the video quality / resolution, styled
 * consistently with [OfflineBadge] and [PlaybackStateBadge].
 *
 * Infers the display label and color from the [resolution] (pixel height):
 * - 2160+  → "4K"    (gold/amber)
 * - 1440   → "1440p" (teal)
 * - 1080   → "1080p" (blue)
 * - 720    → "720p"  (indigo)
 * - 480    → "480p"  (grey)
 * - 360    → "360p"  (grey)
 * - other  → shown as-is (e.g. "HD", "FHD") with a neutral colour.
 *
 * If both [resolution] and [label] are `null`, nothing is rendered.
 *
 * @param resolution The video pixel height (e.g. 1080, 720). When non-null,
 *                   the label is auto-derived unless [label] is also provided.
 * @param label      Optional override for the display text (e.g. "HD", "FHD").
 *                   If null, the label is derived from [resolution].
 * @param modifier   Compose modifier.
 */
@Composable
fun VideoQualityBadge(
    resolution: Int? = null,
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    val displayLabel = label ?: resolution?.let { labelForResolution(it) } ?: return
    val backgroundColor = resolution?.let { colorForResolution(it) }
        ?: Color(0xFF607D8B) // Blue-grey fallback

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor,
    ) {
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Maps a resolution value (pixel height) to a human-readable label. */
private fun labelForResolution(resolution: Int): String = when {
    resolution >= 4320 -> "8K"
    resolution >= 2160 -> "4K"
    resolution >= 1440 -> "1440p"
    resolution >= 1080 -> "1080p"
    resolution >= 720 -> "720p"
    resolution >= 480 -> "480p"
    resolution >= 360 -> "360p"
    else -> "${resolution}p"
}

/** Maps a resolution value to a distinct chip colour. */
private fun colorForResolution(resolution: Int): Color = when {
    resolution >= 4320 -> Color(0xFFFFD54F) // Gold
    resolution >= 2160 -> Color(0xFFFFB300) // Amber
    resolution >= 1440 -> Color(0xFF26A69A) // Teal
    resolution >= 1080 -> Color(0xFF42A5F5) // Blue
    resolution >= 720 -> Color(0xFF5C6BC0)  // Indigo
    resolution >= 480 -> Color(0xFF78909C) // Blue-grey
    else -> Color(0xFF90A4AE)               // Light blue-grey
}
