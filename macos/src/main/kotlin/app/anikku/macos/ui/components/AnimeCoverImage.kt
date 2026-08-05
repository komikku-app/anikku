package app.anikku.macos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.SubcomposeAsyncImage

/**
 * Displays an anime cover image loaded from [thumbnailUrl] using Coil 3.
 *
 * Shows the title initials as a fallback when:
 * - [thumbnailUrl] is null or blank
 * - The image is still loading (placeholder)
 * - The image fails to load (error)
 *
 * Used in grid cards, list items, and the detail screen header.
 */
@Composable
fun AnimeCoverImage(
    thumbnailUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    title: String = "",
) {
    if (!thumbnailUrl.isNullOrBlank()) {
        SubcomposeAsyncImage(
            model = thumbnailUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            clipToBounds = true,
            loading = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            },
            error = {
                // Failed loads fall back to initials instead of a blank box.
                AnimeCoverFallback(title = title, modifier = Modifier.fillMaxSize())
            },
        )
    } else {
        AnimeCoverFallback(
            title = title,
            modifier = modifier,
        )
    }
}

/**
 * Shows initials as a placeholder when no image is available.
 * Replaces the previous inline placeholder logic from grid/list/detail components.
 */
@Composable
private fun AnimeCoverFallback(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.take(2).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}
