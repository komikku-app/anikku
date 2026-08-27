package app.anikku.macos.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikku.macos.player.PlaybackState

/**
 * A small chip/badge indicating the current playback state, styled consistently
 * with [OfflineBadge].
 *
 * Shows a colored [Surface] with an icon and label for non-default states
 * (everything except [PlaybackState.IDLE] and [PlaybackState.PLAYING]).
 *
 * When [isLive] is `true`, a special red "LIVE" badge is shown regardless
 * of [playbackState] (useful for live-stream content).
 *
 * Example layout (top bar or transport controls):
 * ```
 * ┌──────────┐    ┌───────────┐
 * │  Offline  │    │  Buffering│
 * └──────────┘    └───────────┘
 * ```
 */
@Composable
fun PlaybackStateBadge(
    playbackState: PlaybackState = PlaybackState.IDLE,
    isLive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val chip = when {
        isLive -> PlaybackChip(
            label = "LIVE",
            icon = Icons.Outlined.BluetoothConnected,
            color = Color(0xFFE53935), // Red
        )
        playbackState == PlaybackState.LOADING -> PlaybackChip(
            label = "Loading",
            icon = Icons.Outlined.HourglassEmpty,
            color = Color(0xFFFF9800), // Amber
        )
        playbackState == PlaybackState.BUFFERING -> PlaybackChip(
            label = "Buffering",
            icon = Icons.Outlined.Timer,
            color = Color(0xFFFFC107), // Yellow
        )
        playbackState == PlaybackState.ENDED -> PlaybackChip(
            label = "Ended",
            icon = Icons.Outlined.Stop,
            color = Color(0xFF78909C), // Blue-grey
        )
        playbackState == PlaybackState.ERROR -> PlaybackChip(
            label = "Error",
            icon = Icons.Outlined.ErrorOutline,
            color = Color(0xFFE53935), // Red
        )
        playbackState == PlaybackState.PAUSED -> PlaybackChip(
            label = "Paused",
            icon = Icons.Outlined.PauseCircle,
            color = Color(0xFF90CAF9), // Light blue
        )
        playbackState == PlaybackState.SEEKING -> PlaybackChip(
            label = "Seeking…",
            icon = Icons.Outlined.Timer,
            color = Color(0xFFCE93D8), // Purple
        )
        else -> null
    }

    if (chip != null) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(4.dp),
            color = chip.color,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = chip.icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = chip.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

private data class PlaybackChip(
    val label: String,
    val icon: ImageVector,
    val color: Color,
)
