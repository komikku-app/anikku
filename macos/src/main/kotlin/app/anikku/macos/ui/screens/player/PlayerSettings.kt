package app.anikku.macos.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.player.AudioDevice
import app.anikku.macos.platform.watch.WtMessage
import app.anikku.macos.player.TrackInfo

/**
 * Player settings panels (Phase 5.8).
 *
 * Provides composable bottom-sheet style panels for:
 * - Playback speed control (0.5x — 2.0x)
 * - Audio track selection
 * - Subtitle track selection
 * - Video equalizer (brightness, contrast, saturation)
 *
 * Each panel can be shown independently in a bottom sheet or
 * dialog from [PlayerScreen].
 *
 * Usage:
 * ```kotlin
 * var showSpeedPanel by remember { mutableStateOf(false) }
 * if (showSpeedPanel) {
 *     PlayerSpeedPanel(
 *         currentSpeed = 1.0f,
 *         onSpeedChange = { playerViewModel.setSpeed(it) },
 *         onDismiss = { showSpeedPanel = false },
 *     )
 * }
 * ```
 */

/**
 * Playback speed control panel.
 * Allows selecting from common speeds (0.5x — 2.0x) or using a slider.
 */
@Composable
fun PlayerSpeedPanel(
    currentSpeed: Float = 1.0f,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var sliderSpeed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Playback Speed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            // Preset speed buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            ) {
                speeds.forEach { speed ->
                    val isSelected = kotlin.math.abs(speed - currentSpeed) < 0.01f
                    Button(
                        onClick = {
                            onSpeedChange(speed)
                            sliderSpeed = speed
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                        modifier = Modifier.width(52.dp),
                    ) {
                        Text(
                            text = "${speed}x",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Fine-grained slider
            Slider(
                value = sliderSpeed,
                onValueChange = { sliderSpeed = it },
                onValueChangeFinished = { onSpeedChange(sliderSpeed) },
                valueRange = 0.25f..3.0f,
                steps = 10,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Text(
                text = "Custom: ${"%.2f".format(sliderSpeed)}x",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // Close button
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
        }
    }
}

/**
 * Video quality selector panel.
 * Lists every quality the source offered; picking one re-resolves the video
 * at that candidate and remembers the choice for future loads.
 */
@Composable
fun PlayerQualityPanel(
    candidates: List<PlayerScreen.VideoCandidate> = emptyList(),
    currentLabel: String? = null,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Quality",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            if (candidates.isEmpty()) {
                Text(
                    text = "No alternate qualities available for this episode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                candidates.forEachIndexed { index, candidate ->
                    val label = candidate.label?.takeIf { it.isNotBlank() }
                        ?: "Quality ${index + 1}"
                    val isSelected = candidate.label?.isNotBlank() == true &&
                        candidate.label == currentLabel
                    OutlinedButton(
                        onClick = {
                            onSelect(index)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        ),
                    ) {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
        }
    }
}

/**
 * Audio track selector panel.
 * Allows switching between available audio tracks.
 */
@Composable
fun PlayerChatPanel(
    messages: List<WtMessage.Chat> = emptyList(),
    yourName: String = "",
    memberCount: Int = 0,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Follow the conversation — scroll to the newest line.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (memberCount > 1) {
                    Text(
                        text = "$memberCount watching",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close chat",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No messages yet — say hi!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 80.dp, max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { "${it.ts}_${it.by}_${it.text.hashCode()}" }) { chat ->
                        val mine = chat.by == yourName && yourName.isNotBlank()
                        Column {
                            Text(
                                text = buildString {
                                    append(chat.by.ifBlank { "Guest" })
                                    if (mine) append("  (you)")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mine) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = chat.text,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message…") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val body = input.trim()
                        if (body.isNotEmpty()) {
                            onSend(body)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
fun PlayerAudioDevicePanel(
    devices: List<AudioDevice> = emptyList(),
    currentDevice: String? = null,
    onDeviceSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Audio Device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            if (devices.isEmpty()) {
                Text(
                    text = "No audio devices reported — using the system default",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // "auto" = system default, then every enumerated device.
                val options = buildList {
                    add(AudioDevice(name = "", description = "System default"))
                    addAll(devices)
                }
                options.forEach { device ->
                    val isSelected = (device.name == currentDevice) ||
                        (device.name.isEmpty() && currentDevice.isNullOrBlank())
                    OutlinedButton(
                        onClick = {
                            onDeviceSelected(device.name)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = if (isSelected) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = device.name.ifBlank { "System default" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (device.description.isNotBlank() && device.name != device.description) {
                                Text(
                                    text = device.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

@Composable
fun PlayerAudioTrackPanel(
    tracks: List<TrackInfo> = emptyList(),
    currentTrackIndex: Int = 0,
    audioDelay: Double = 0.0,
    onTrackSelected: (Int) -> Unit,
    onDelayChange: (Double) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var sliderDelay by remember(audioDelay) { mutableFloatStateOf(audioDelay.toFloat()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Audio Track",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            if (tracks.isEmpty()) {
                Text(
                    text = "No alternate audio tracks available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tracks.forEach { track ->
                    val isSelected = track.id == currentTrackIndex
                    OutlinedButton(
                        onClick = {
                            onTrackSelected(track.id)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        ),
                    ) {
                        Text(
                            text = track.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Audio delay slider
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Audio Delay (${"%.1f".format(sliderDelay)}s)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Slider(
                value = sliderDelay,
                onValueChange = { sliderDelay = it },
                onValueChangeFinished = { onDelayChange(sliderDelay.toDouble()) },
                valueRange = -10f..10f,
                steps = 40,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )

            // Quick nudge buttons for fine sync adjustments.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Nudge",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        sliderDelay = (sliderDelay - 0.5f).coerceIn(-10f, 10f)
                        onDelayChange(sliderDelay.toDouble())
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("-0.5s", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = {
                        sliderDelay = (sliderDelay + 0.5f).coerceIn(-10f, 10f)
                        onDelayChange(sliderDelay.toDouble())
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("+0.5s", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Close")
            }
        }
    }
}

/**
 * Subtitle track selector panel.
 * Allows switching between available subtitle tracks or disabling them.
 */
@Composable
fun PlayerSubtitleTrackPanel(
    tracks: List<app.anikku.macos.player.TrackInfo> = emptyList(),
    currentTrackIndex: Int = -1, // -1 = disabled; otherwise the mpv track ID
    subtitleDelay: Double = 0.0,
    subtitleFontSize: Float = 55f,
    subtitlePosition: Int = 100,
    onFontSizeChange: (Float) -> Unit = {},
    onPositionChange: (Int) -> Unit = {},
    onLoadLocalSubtitle: () -> Unit = {},
    onTrackSelected: (Int) -> Unit,
    onDelayChange: (Double) -> Unit,
    onDismiss: () -> Unit,
    // Online subtitle search (OpenSubtitles fallback).
    searchingOnline: Boolean = false,
    onlineCandidates: List<app.anikku.macos.platform.subtitle.SubtitleCandidate> = emptyList(),
    onlineError: String? = null,
    onSearchOnline: () -> Unit = {},
    onSelectOnline: (app.anikku.macos.platform.subtitle.SubtitleCandidate) -> Unit = {},
) {
    var sliderDelay by remember(subtitleDelay) { mutableFloatStateOf(subtitleDelay.toFloat()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Subtitles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            // Subtitle off toggle
            var subsEnabled by remember(currentTrackIndex) { mutableStateOf(currentTrackIndex >= 0) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Subtitles enabled",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = subsEnabled,
                    onCheckedChange = {
                        subsEnabled = it
                        onTrackSelected(if (it) tracks.firstOrNull()?.id ?: -1 else -1)
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Subtitle tracks
            if (subsEnabled && tracks.isNotEmpty()) {
                Text(
                    text = "Track",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                tracks.forEach { track ->
                    val isSelected = track.id == currentTrackIndex
                    OutlinedButton(
                        onClick = {
                            onTrackSelected(track.id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        ),
                    ) {
                        Text(
                            text = track.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Subtitle delay
                Text(
                    text = "Delay (${"%.1f".format(sliderDelay)}s)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Slider(
                    value = sliderDelay,
                    onValueChange = { sliderDelay = it },
                    onValueChangeFinished = { onDelayChange(sliderDelay.toDouble()) },
                    valueRange = -10f..10f,
                    steps = 40,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )

                // Quick nudge buttons for fine sync adjustments (also bound to
                // the , and . keys in the player).
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Nudge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            sliderDelay = (sliderDelay - 0.5f).coerceIn(-10f, 10f)
                            onDelayChange(sliderDelay.toDouble())
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("-0.5s", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = {
                            sliderDelay = (sliderDelay + 0.5f).coerceIn(-10f, 10f)
                            onDelayChange(sliderDelay.toDouble())
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("+0.5s", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Text(
                    text = "No subtitle tracks available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Subtitle appearance — applies to whatever track is selected
            Text(
                text = "Font size (${subtitleFontSize.toInt()})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = subtitleFontSize,
                onValueChange = onFontSizeChange,
                valueRange = 20f..160f,
                steps = 27,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Position (${subtitlePosition})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = subtitlePosition.toFloat(),
                onValueChange = { onPositionChange(it.toInt()) },
                valueRange = 0f..150f,
                steps = 29,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onLoadLocalSubtitle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Load subtitle file…")
            }

            Spacer(Modifier.height(12.dp))

            // Online subtitle search (OpenSubtitles fallback)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton(
                onClick = onSearchOnline,
                enabled = !searchingOnline,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (searchingOnline) "Searching OpenSubtitles…" else "Search OpenSubtitles…")
            }

            if (onlineError != null) {
                Text(
                    text = onlineError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (onlineCandidates.isNotEmpty()) {
                Text(
                    text = "Choose a subtitle (English first):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                onlineCandidates.forEach { candidate ->
                    OutlinedButton(
                        onClick = { onSelectOnline(candidate) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = candidate.title,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
        }
    }
}

/**
 * Video equalizer panel.
 * Adjusts brightness, contrast, saturation, and gamma.
 */
@Composable
fun PlayerEqualizerPanel(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f,
    gamma: Float = 1f,
    onBrightnessChange: (Float) -> Unit = {},
    onContrastChange: (Float) -> Unit = {},
    onSaturationChange: (Float) -> Unit = {},
    onGammaChange: (Float) -> Unit = {},
    onReset: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var localBrightness by remember(brightness) { mutableFloatStateOf(brightness) }
    var localContrast by remember(contrast) { mutableFloatStateOf(contrast) }
    var localSaturation by remember(saturation) { mutableFloatStateOf(saturation) }
    var localGamma by remember(gamma) { mutableFloatStateOf(gamma) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Video Equalizer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            // Brightness
            EqualizerSlider(
                label = "Brightness",
                value = localBrightness,
                onValueChange = { localBrightness = it; onBrightnessChange(it) },
                valueRange = -1f..1f,
                displayValue = "${"%.1f".format(localBrightness)}",
            )

            Spacer(Modifier.height(8.dp))

            // Contrast
            EqualizerSlider(
                label = "Contrast",
                value = localContrast,
                onValueChange = { localContrast = it; onContrastChange(it) },
                valueRange = 0f..2f,
                displayValue = "${"%.1f".format(localContrast)}",
            )

            Spacer(Modifier.height(8.dp))

            // Saturation
            EqualizerSlider(
                label = "Saturation",
                value = localSaturation,
                onValueChange = { localSaturation = it; onSaturationChange(it) },
                valueRange = 0f..2f,
                displayValue = "${"%.1f".format(localSaturation)}",
            )

            Spacer(Modifier.height(8.dp))

            // Gamma
            EqualizerSlider(
                label = "Gamma",
                value = localGamma,
                onValueChange = { localGamma = it; onGammaChange(it) },
                valueRange = 0.1f..2f,
                displayValue = "${"%.1f".format(localGamma)}",
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = {
                        localBrightness = 0f
                        localContrast = 1f
                        localSaturation = 1f
                        localGamma = 1f
                        onReset()
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Reset")
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Aspect Ratio Panel
// ---------------------------------------------------------------------------

/** Preset aspect ratio values with display labels. */
private val aspectRatioPresets = listOf(
    "-1" to "Original",
    "16:9" to "16:9",
    "4:3" to "4:3",
    "16:10" to "16:10",
    "21:9" to "21:9",
    "3:2" to "3:2",
    "5:4" to "5:4",
    "1:1" to "1:1",
)

/**
 * Aspect ratio selector panel.
 * Allows selecting from common display aspect ratio presets.
 */
@Composable
fun PlayerAspectRatioPanel(
    currentRatio: String = "-1",
    onRatioChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Aspect Ratio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            // Preset ratio buttons in a 4-column grid
            aspectRatioPresets.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { (value, label) ->
                        val isSelected = currentRatio == value
                        Button(
                            onClick = { onRatioChange(value) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    // Pad remaining slots in the last row
                    repeat(4 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Video Filter Panel (rotation + flip)
// ---------------------------------------------------------------------------

/**
 * Video filter panel.
 * Allows rotation (0/90/180/270) and horizontal/vertical flip.
 */
@Composable
fun PlayerVideoFilterPanel(
    currentRotation: Int = 0,
    isHflip: Boolean = false,
    isVflip: Boolean = false,
    onRotationChange: (Int) -> Unit,
    onToggleHflip: () -> Unit,
    onToggleVflip: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Video Filters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            // Rotation presets
            Text(
                text = "Rotation",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0 to "0°", 90 to "90°", 180 to "180°", 270 to "270°").forEach { (degrees, label) ->
                    val isSelected = currentRotation == degrees
                    Button(
                        onClick = { onRotationChange(degrees) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Flip toggles
            Text(
                text = "Flip",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterChip(
                    selected = isHflip,
                    onClick = onToggleHflip,
                    label = { Text("Horizontal Flip") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                FilterChip(
                    selected = isVflip,
                    onClick = onToggleVflip,
                    label = { Text("Vertical Flip") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun EqualizerSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
