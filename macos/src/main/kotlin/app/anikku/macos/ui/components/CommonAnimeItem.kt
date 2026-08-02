package app.anikku.macos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.ui.screens.models.AnimeModel

/**
 * Shared anime grid card component for macOS.
 *
 * Shows a cover image placeholder with title, genre, and status indicators.
 * Used in Library, Browse, and other grid views.
 */
@Composable
fun AnimeGridCard(
    anime: AnimeModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: (@Composable RowScope.() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // Cover image loaded via Coil 3, with initials as fallback
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
            ) {
                AnimeCoverImage(
                    thumbnailUrl = anime.thumbnailUrl,
                    contentDescription = anime.title,
                    title = anime.title,
                    modifier = Modifier.fillMaxSize(),
                )

                // Bottom gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.4f)),
                )

                if (badge != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        content = badge,
                    )
                }
            }

            // Title + metadata
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Anime list item for list display mode.
 */
@Composable
fun AnimeListItem(
    anime: AnimeModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cover image loaded via Coil 3
        AnimeCoverImage(
            thumbnailUrl = anime.thumbnailUrl,
            contentDescription = anime.title,
            title = anime.title,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp)),
        )

        Spacer(Modifier.width(12.dp))

        // Title + subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Row(content = trailing)
        }
    }
}

/**
 * A grid of anime cards for library/browse screens.
 */
@Composable
fun AnimeGrid(
    items: List<AnimeModel>,
    onClick: (AnimeModel) -> Unit,
    modifier: Modifier = Modifier,
    minCardWidth: androidx.compose.ui.unit.Dp = 150.dp,
    getSubtitle: ((AnimeModel) -> String?)? = null,
    getBadge: (@Composable RowScope.(AnimeModel) -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCardWidth),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(
            items = items,
            key = { it.id },
            contentType = { "anime_grid" },
        ) { anime ->
            AnimeGridCard(
                anime = anime,
                onClick = { onClick(anime) },
                subtitle = getSubtitle?.invoke(anime),
                badge = getBadge?.let { { it(anime) } },
            )
        }
    }
}

/**
 * A list of anime items for list display mode.
 */
@Composable
fun AnimeList(
    items: List<AnimeModel>,
    onClick: (AnimeModel) -> Unit,
    modifier: Modifier = Modifier,
    getSubtitle: ((AnimeModel) -> String?)? = null,
    getTrailing: (@Composable RowScope.(AnimeModel) -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(
            items = items,
            key = { it.id },
            contentType = { "anime_list" },
        ) { anime ->
            AnimeListItem(
                anime = anime,
                onClick = { onClick(anime) },
                subtitle = getSubtitle?.invoke(anime),
                trailing = getTrailing?.let { { it(anime) } },
            )
        }
    }
}
