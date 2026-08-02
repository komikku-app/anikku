package app.anikku.macos.ui.screens.player

import app.anikku.macos.player.formatDuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anikku.macos.player.PlaybackState
import app.anikku.macos.ui.components.PlaybackStateBadge

/**
 * Transport controls for the mpv player (Phase 5.8).
 *
 * Extracted from [PlayerScreen] for modularity. Provides:
 * - Seek bar with elapsed / total time
 * - Previous / Next episode buttons
 * - Rewind / Forward 10s buttons
 * - Play / Pause toggle (large center icon)
 * - Episode pill selector
 * - Playback status text
 *
 * @param currentPositionSeconds Current playback position in seconds.
 * @param totalDurationSeconds Total duration in seconds.
 * @param isPlaying Whether media is currently playing.
 * @param playbackState Current mpv playback state.
 * @param currentEpisodeIndex Index of the current episode in the list.
 * @param episodeCount Total number of episodes available.
 * @param volume Current volume level.
 * @param showVolume Whether to show the volume indicator.
 * @param onTogglePlay Called when play/pause is toggled.
 * @param onSeek Called when the user seeks to a specific second.
 * @param onSeekRelative Called when the user seeks by an offset (seconds, positive or negative).
 * @param onNavigateEpisode Called to navigate to a specific episode index.
 */
@Composable
fun PlayerTransportControls(
    currentPositionSeconds: Long,
    totalDurationSeconds: Long,
    isPlaying: Boolean,
    isLive: Boolean = false,
    playbackState: PlaybackState = PlaybackState.IDLE,
    currentEpisodeIndex: Int,
    episodeCount: Int,
    volume: Int = 100,
    showVolume: Boolean = false,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit,
    onSeekRelative: (Double) -> Unit,
    onNavigateEpisode: (Int) -> Unit,
) {
    val hasSeekableDuration = !isLive && totalDurationSeconds > 0
    val seekFraction = if (hasSeekableDuration) {
        (currentPositionSeconds.toFloat() / totalDurationSeconds).coerceIn(0f, 1f)
    } else 0f
    // Keep the thumb under the mouse while dragging. The position flow updates
    // several times per second, so binding Slider.value directly to it makes
    // the thumb jump back underneath the pointer and makes seeking unreliable.
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(seekFraction) }
    val displayedFraction = if (isDragging) dragFraction else seekFraction

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Seek bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(currentPositionSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.width(48.dp),
            )

            Slider(
                value = displayedFraction,
                onValueChange = {
                    isDragging = true
                    dragFraction = it.coerceIn(0f, 1f)
                    onSeek(dragFraction)
                },
                onValueChangeFinished = {
                    onSeekEnd(dragFraction)
                    isDragging = false
                },
                enabled = hasSeekableDuration,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .semantics {
                        contentDescription = "Seek position"
                    },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )

            Text(
                text = when {
                    isLive -> "LIVE"
                    totalDurationSeconds > 0 -> formatDuration(totalDurationSeconds)
                    else -> "—"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End,
            )
        }

        // Volume indicator
        if (showVolume) {
            Text(
                text = "Volume: $volume",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Transport controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Previous episode
            TransportIconButton(
                icon = Icons.Filled.SkipPrevious,
                description = "Previous episode",
                enabled = currentEpisodeIndex > 0,
                onClick = { onNavigateEpisode(currentEpisodeIndex - 1) },
            )

            Spacer(Modifier.width(16.dp))

            // Rewind 10s
            TransportIconButton(
                icon = Icons.Outlined.FastRewind,
                description = "Rewind 10 seconds",
                enabled = hasSeekableDuration,
                onClick = { onSeekRelative(-10.0) },
            )

            Spacer(Modifier.width(24.dp))

            // Play / Pause
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(Modifier.width(24.dp))

            // Forward 10s
            TransportIconButton(
                icon = Icons.Outlined.FastForward,
                description = "Forward 10 seconds",
                enabled = hasSeekableDuration,
                onClick = { onSeekRelative(10.0) },
            )

            Spacer(Modifier.width(16.dp))

            // Next episode
            TransportIconButton(
                icon = Icons.Filled.SkipNext,
                description = "Next episode",
                enabled = currentEpisodeIndex < episodeCount - 1,
                onClick = { onNavigateEpisode(currentEpisodeIndex + 1) },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Episode pill selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            for (index in 0 until episodeCount) {
                val isSelected = index == currentEpisodeIndex
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(
                            width = if (isSelected) 24.dp else 8.dp,
                            height = 4.dp,
                        )
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.3f),
                        )
                        .clickable { onNavigateEpisode(index) },
                )
            }
        }

        // Playback state badge
        if (playbackState != PlaybackState.IDLE && playbackState != PlaybackState.PLAYING) {
            Spacer(Modifier.height(8.dp))
            PlaybackStateBadge(
                playbackState = playbackState,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * Small circular icon button for transport controls.
 */
@Composable
fun TransportIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(24.dp),
        )
    }
}


