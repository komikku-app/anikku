package mihon.feature.airingschedule.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import mihon.feature.airingschedule.AiringScheduleEntry
import mihon.feature.airingschedule.SchedulePreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val timeFormatter24h = DateTimeFormatter.ofPattern("HH:mm")
private val timeFormatter12h = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun ScheduleAnimeCard(
    entry: AiringScheduleEntry,
    titleLanguage: SchedulePreferences.TitleLanguage,
    sourceDelays: Map<String, Long>,
    manualDelayMinutes: Long?,
    pinnedSourceIds: Set<String>,
    isInLibrary: Boolean,
    notifyState: BellNotifyState,
    onSearchClick: (String) -> Unit,
    onAddToLibraryClick: (String) -> Unit,
    onToggleNotifyOnce: () -> Unit,
    onToggleNotifySeries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = entry.displayTitle(titleLanguage)

    val matchedPinnedSource = remember(pinnedSourceIds, entry.titleUserPreferred) {
        pinnedSourceIds.firstOrNull { sourceId ->
            sourceId in sourceDelays.keys
        }
    }

    val delayMinutes = manualDelayMinutes ?: matchedPinnedSource?.let { sourceDelays[it] }
    val adjustedAirTime = if (delayMinutes != null) entry.airingAt + (delayMinutes * 60) else entry.airingAt
    val hasAired = adjustedAirTime <= System.currentTimeMillis() / 1000L

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onSearchClick(title) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .height(130.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScheduleAnimeCover(
                imageUrl = entry.coverImageUrl,
                title = title,
                hasAired = hasAired,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                ScheduleAnimeInfo(
                    title = title,
                    isInLibrary = isInLibrary,
                    airingAt = entry.airingAt,
                    delayMinutes = delayMinutes,
                    hasAired = hasAired,
                    episode = entry.episode,
                    totalEpisodes = entry.totalEpisodes,
                    format = entry.format,
                    score = entry.averageScore,
                )

                ScheduleAnimeActions(
                    isInLibrary = isInLibrary,
                    hasAired = hasAired,
                    notifyState = notifyState,
                    onSearchClick = { onSearchClick(title) },
                    onAddToLibraryClick = { onAddToLibraryClick(title) },
                    onToggleNotifyOnce = onToggleNotifyOnce,
                    onToggleNotifySeries = onToggleNotifySeries,
                )
            }
        }
    }
}

@Composable
private fun ScheduleAnimeCover(
    imageUrl: String?,
    title: String,
    hasAired: Boolean,
) {
    Box(
        modifier = Modifier
            .size(width = 90.dp, height = 130.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (hasAired) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ) {
                    Text(
                        text = "Aired",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleAnimeInfo(
    title: String,
    isInLibrary: Boolean,
    airingAt: Long,
    delayMinutes: Long?,
    hasAired: Boolean,
    episode: Int,
    totalEpisodes: Int?,
    format: String?,
    score: Int?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (isInLibrary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )

        AirTimeBadgeRow(
            hasAired = hasAired,
            airingAt = airingAt,
            delayMinutes = delayMinutes,
            episode = episode,
            totalEpisodes = totalEpisodes,
        )

        ScheduleAnimeMetaRow(
            format = format,
            score = score,
        )
    }
}

@Composable
private fun AirTimeBadgeRow(
    hasAired: Boolean,
    airingAt: Long,
    delayMinutes: Long?,
    episode: Int,
    totalEpisodes: Int?,
) {
    val zone = ZoneId.systemDefault()
    val airDateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(airingAt), zone)
    val use24h = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
    val timeStr = airDateTime.format(if (use24h) timeFormatter24h else timeFormatter12h)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Badge(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = "Ep $episode${totalEpisodes?.let { "/$it" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Text(
            text = timeStr,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!hasAired) {
            val adjustedAirTime = if (delayMinutes != null) airingAt + (delayMinutes * 60) else airingAt
            val countdown = formatCountdown(adjustedAirTime)
            if (countdown != null) {
                Text(
                    text = "• $countdown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ScheduleAnimeMetaRow(
    format: String?,
    score: Int?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        format?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        score?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color(0xFFFFB300),
                )
                Text(
                    text = "$it%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScheduleAnimeActions(
    isInLibrary: Boolean,
    hasAired: Boolean,
    notifyState: BellNotifyState,
    onSearchClick: () -> Unit,
    onAddToLibraryClick: () -> Unit,
    onToggleNotifyOnce: () -> Unit,
    onToggleNotifySeries: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isInLibrary) {
            IconButton(onClick = onAddToLibraryClick) {
                Icon(
                    imageVector = Icons.Outlined.BookmarkAdd,
                    contentDescription = stringResource(MR.strings.action_add),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(MR.strings.action_play),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        IconButton(onClick = onToggleNotifyOnce) {
            Icon(
                imageVector = when (notifyState) {
                    BellNotifyState.ONCE -> Icons.Outlined.NotificationsActive
                    else -> Icons.Outlined.NotificationsNone
                },
                contentDescription = null,
                tint = if (notifyState == BellNotifyState.ONCE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onToggleNotifySeries) {
            Icon(
                imageVector = when (notifyState) {
                    BellNotifyState.SERIES -> Icons.Outlined.NotificationsActive
                    else -> Icons.Outlined.Notifications
                },
                contentDescription = null,
                tint = if (notifyState == BellNotifyState.SERIES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCountdown(airingAt: Long): String? {
    val now = System.currentTimeMillis() / 1000L
    val diff = airingAt - now
    if (diff <= 0) return null

    val days = diff / 86400
    val hours = (diff % 86400) / 3600
    val minutes = (diff % 3600) / 60

    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
