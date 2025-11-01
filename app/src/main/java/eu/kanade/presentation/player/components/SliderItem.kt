/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.presentation.player.components

import androidx.annotation.IntRange
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R
import tachiyomi.presentation.core.components.BaseSliderItem
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.Slider
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.secondaryItemAlpha
import kotlin.math.max
import kotlin.math.min

@Composable
fun SliderItem(
    value: Int,
    valueRange: IntProgression,
    label: String,
    onChange: (Int) -> Unit,
    steps: Int = with(valueRange) { (last - first) - 1 },
    valueString: String = value.toString(),
    labelStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    pillColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tint: Color? = null,
    icon: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.large),
    ) {
        icon()
        BaseSliderItem(
            value = value,
            valueRange = valueRange,
            steps = steps,
            title = label,
            valueString = valueString,
            onChange = onChange,
            titleStyle = labelStyle,
            pillColor = pillColor,
            colors = tint?.let { generateSliderColors(it) } ?: SliderDefaults.colors(),
        )
    }
}

@Composable
fun SliderItem(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
    steps: Int = 0,
    valueString: String = value.toString(),
    labelStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    pillColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    icon: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.large),
    ) {
        icon()
        BaseSliderItem(
            value = value,
            valueRange = valueRange,
            steps = steps,
            title = label,
            valueString = valueString,
            onChange = onChange,
            titleStyle = labelStyle,
            pillColor = pillColor,
        )
    }
}

@Composable
fun BaseSliderItem(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    title: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    steps: Int = 0,
    valueString: String = value.toString(),
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall,
    pillColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    colors: SliderColors = SliderDefaults.colors(),
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = titleStyle,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = subtitleStyle,
                        modifier = Modifier.secondaryItemAlpha(),
                    )
                }
            }
            Pill(
                text = valueString,
                style = MaterialTheme.typography.bodyMedium,
                color = pillColor,
            )
        }
        Slider(
            value = value,
            onValueChange = f@{
                if (it == value) return@f
                onChange(it)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            valueRange = valueRange,
            steps = steps,
            colors = colors,
        )
    }
}

@Composable
fun VerticalSliderItem(
    value: Int,
    valueRange: IntProgression,
    title: String,
    onChange: (Int) -> Unit,
    subtitle: String? = null,
    steps: Int = with(valueRange) { (last - first) - 1 },
    valueString: String = value.toString(),
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall,
    pillColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    colors: SliderColors = SliderDefaults.colors(),
    icon: @Composable () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        BaseVerticalSliderItem(
            value = value,
            valueRange = valueRange,
            steps = steps,
            onValueChange = f@{
                if (it == value) return@f
                onChange(it)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            colors = colors,
            modifier = Modifier.weight(1f),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = titleStyle,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = subtitleStyle,
                    modifier = Modifier.secondaryItemAlpha(),
                )
            }
            Pill(
                text = valueString,
                style = MaterialTheme.typography.bodyMedium,
                color = pillColor,
            )
        }
    }
}

@Composable
fun BaseVerticalSliderItem(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: IntProgression = 0..1,
    @IntRange(from = 0) steps: Int = with(valueRange) { (last - first) - 1 },
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: @Composable (SliderState) -> Unit = {
        SliderDefaults.Thumb(
            interactionSource = interactionSource,
            colors = colors,
            enabled = enabled,
        )
    },
    track: @Composable (SliderState) -> Unit = { sliderState ->
        SliderDefaults.Track(colors = colors, enabled = enabled, sliderState = sliderState)
    },
) {
    Slider(
        modifier = modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    ),
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            }
            .width(180.dp)
            .height(50.dp),
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        thumb = thumb,
        track = track,
    )
}

@Composable
@PreviewLightDark
private fun PreviewVerticalSliderItem() {
    MaterialTheme(if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        var value by remember { mutableIntStateOf(2) }
        Surface {
            Row {
                VerticalSliderItem(
                    value = value,
                    valueRange = 1..5,
                    title = "Filter",
                    subtitle = "Subtitle",
                    onChange = { value = it },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_seek_triangle),
                            contentDescription = null,
                            modifier = Modifier
                                .size(width = 16.dp, height = 20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                )
                SliderItem(
                    value = value,
                    valueRange = 1..5,
                    label = "Filter",
                    onChange = { value = it },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_seek_triangle),
                            contentDescription = null,
                            modifier = Modifier
                                .size(width = 16.dp, height = 20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                )
            }
        }
    }
}

fun generateSliderColors(baseColor: Color): SliderColors {
    // Utility function to darken a color
    fun darken(color: Color, factor: Float): Color {
        val red = max((color.red * factor), 0f)
        val green = max((color.green * factor), 0f)
        val blue = max((color.blue * factor), 0f)
        return Color(red, green, blue, color.alpha)
    }

    // Utility function to lighten a color
    fun lighten(color: Color, factor: Float): Color {
        val red = min((color.red + (1 - color.red) * factor), 255f)
        val green = min((color.green + (1 - color.green) * factor), 255f)
        val blue = min((color.blue + (1 - color.blue) * factor), 255f)
        return Color(red, green, blue, color.alpha)
    }

    return SliderColors(
        thumbColor = baseColor,
        activeTrackColor = lighten(baseColor, 0.2f),
        activeTickColor = lighten(baseColor, 0.4f),
        inactiveTrackColor = darken(baseColor, 0.2f),
        inactiveTickColor = darken(baseColor, 0.4f),
        disabledThumbColor = baseColor.copy(alpha = 0.5f),
        disabledActiveTrackColor = lighten(baseColor, 0.2f).copy(alpha = 0.5f),
        disabledActiveTickColor = lighten(baseColor, 0.4f).copy(alpha = 0.5f),
        disabledInactiveTrackColor = darken(baseColor, 0.2f).copy(alpha = 0.5f),
        disabledInactiveTickColor = darken(baseColor, 0.4f).copy(alpha = 0.5f),
    )
}
