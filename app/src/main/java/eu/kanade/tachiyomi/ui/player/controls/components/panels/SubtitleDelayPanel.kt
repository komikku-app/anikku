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

package eu.kanade.tachiyomi.ui.player.controls.components.panels

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.player.components.OutlinedNumericChooser
import eu.kanade.tachiyomi.ui.player.controls.CARDS_MAX_WIDTH
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import kotlinx.coroutines.delay
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.round

@Composable
fun SubtitleDelayPanel(
    delayMs: Int,
    secondaryDelayMs: Int,
    speed: Double,
    onSpeedChange: (Double) -> Unit,
    onDelayChange: (Int) -> Unit,
    onSecondaryDelayChange: (Int) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .padding(MaterialTheme.padding.medium),
    ) {
        val delayControlCard = createRef()

        var affectedSubtitle by remember { mutableStateOf(SubtitleDelayType.Primary) }
        SubtitleDelayCard(
            delayMs = if (affectedSubtitle == SubtitleDelayType.Secondary) secondaryDelayMs else delayMs,
            onDelayChange = {
                when (affectedSubtitle) {
                    SubtitleDelayType.Both -> {
                        onDelayChange(it)
                        onSecondaryDelayChange(it)
                    }

                    SubtitleDelayType.Primary -> onDelayChange(it)
                    else -> onSecondaryDelayChange(it)
                }
            },
            speed = speed.toFloat(),
            onSpeedChange = { onSpeedChange(round(it * 1000) / 1000.0) },
            affectedSubtitle = affectedSubtitle,
            onTypeChange = { affectedSubtitle = it },
            onApply = onApply,
            onReset = onReset,
            onClose = onDismissRequest,
            modifier = Modifier.constrainAs(delayControlCard) {
                linkTo(parent.top, parent.bottom, bias = 0.8f)
                end.linkTo(parent.end)
            },
        )
    }
}

@Composable
fun SubtitleDelayCard(
    delayMs: Int,
    onDelayChange: (Int) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    affectedSubtitle: SubtitleDelayType,
    onTypeChange: (SubtitleDelayType) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DelayCard(
        delayMs = delayMs,
        onDelayChange = onDelayChange,
        onApply = onApply,
        onReset = onReset,
        title = {
            SubtitleDelayTitle(
                affectedSubtitle = affectedSubtitle,
                onClose = onClose,
                onTypeChange = onTypeChange,
            )
        },
        extraSettings = {
            when (affectedSubtitle) {
                SubtitleDelayType.Primary -> {
                    OutlinedNumericChooser(
                        label = { Text(stringResource(AYMR.strings.player_sheets_sub_delay_speed)) },
                        value = speed,
                        onChange = onSpeedChange,
                        max = 10f,
                        step = .01f,
                        min = .1f,
                    )
                }

                else -> {}
            }
        },
        delayType = DelayType.Subtitle,
        modifier = modifier,
    )
}

enum class SubtitleDelayType(
    val title: StringResource,
) {
    Primary(AYMR.strings.player_sheets_sub_delay_subtitle_type_primary),
    Secondary(AYMR.strings.player_sheets_sub_delay_subtitle_type_secondary),
    Both(AYMR.strings.player_sheets_sub_delay_subtitle_type_primary_and_secondary),
}

@Suppress("LambdaParameterInRestartableEffect") // Intentional
@Composable
fun DelayCard(
    delayMs: Int,
    onDelayChange: (Int) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    title: @Composable () -> Unit,
    delayType: DelayType,
    modifier: Modifier = Modifier,
    extraSettings: @Composable ColumnScope.() -> Unit = {},
) {
    Card(
        modifier = modifier
            .widthIn(max = CARDS_MAX_WIDTH)
            .animateContentSize(),
        colors = panelCardsColors(),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            title()
            OutlinedNumericChooser(
                label = { Text(stringResource(AYMR.strings.player_sheets_sub_delay_delay)) },
                value = delayMs,
                onChange = onDelayChange,
                step = 50,
                min = Int.MIN_VALUE,
                max = Int.MAX_VALUE,
                suffix = { Text(stringResource(AYMR.strings.player_generic_unit_ms)) },
            )
            Column(
                modifier = Modifier.animateContentSize(),
            ) { extraSettings() }
            // true (heard -> spotted), false (spotted -> heard)
            var isDirectionPositive by remember { mutableStateOf<Boolean?>(null) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                var timerStart by remember { mutableStateOf<Long?>(null) }
                var finalDelay by remember { mutableIntStateOf(delayMs) }
                LaunchedEffect(isDirectionPositive) {
                    if (isDirectionPositive == null) {
                        onDelayChange(finalDelay)
                        return@LaunchedEffect
                    }
                    finalDelay = delayMs
                    timerStart = System.currentTimeMillis()
                    val startingDelay: Int = finalDelay
                    while (isDirectionPositive != null && timerStart != null) {
                        val elapsed = System.currentTimeMillis() - timerStart!!
                        finalDelay = startingDelay + (if (isDirectionPositive!!) elapsed else -elapsed).toInt()
                        // Arbitrary delay of 20ms
                        delay(20)
                    }
                }
                Button(
                    onClick = {
                        isDirectionPositive = if (isDirectionPositive == null) delayType == DelayType.Audio else null
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isDirectionPositive != (delayType == DelayType.Audio),
                ) {
                    Text(
                        stringResource(
                            if (delayType == DelayType.Audio) {
                                AYMR.strings.player_sheets_sub_delay_audio_sound_heard
                            } else {
                                AYMR.strings.player_sheets_sub_delay_subtitle_voice_heard
                            },
                        ),
                    )
                }
                Button(
                    onClick = {
                        isDirectionPositive = if (isDirectionPositive == null) delayType != DelayType.Audio else null
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isDirectionPositive != (delayType == DelayType.Subtitle),
                ) {
                    Text(
                        stringResource(
                            if (delayType == DelayType.Audio) {
                                AYMR.strings.player_sheets_sub_delay_sound_sound_spotted
                            } else {
                                AYMR.strings.player_sheets_sub_delay_subtitle_text_seen
                            },
                        ),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                    enabled = isDirectionPositive == null,
                ) {
                    Text(stringResource(AYMR.strings.player_sheets_delay_set_as_default))
                }
                FilledIconButton(
                    onClick = onReset,
                    enabled = isDirectionPositive == null,
                ) {
                    Icon(Icons.Default.Refresh, null)
                }
            }
        }
    }
}

@Composable
fun SubtitleDelayTitle(
    affectedSubtitle: SubtitleDelayType,
    onClose: () -> Unit,
    onTypeChange: (SubtitleDelayType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(AYMR.strings.player_sheets_sub_delay_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        var showDropDownMenu by remember { mutableStateOf(false) }
        Row(modifier = Modifier.clickable { showDropDownMenu = true }) {
            Text(
                stringResource(affectedSubtitle.title),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(Icons.Default.ArrowDropDown, null)
            DropdownMenu(
                expanded = showDropDownMenu,
                onDismissRequest = { showDropDownMenu = false },
            ) {
                SubtitleDelayType.entries.forEach {
                    DropdownMenuItem(
                        text = { Text(stringResource(it.title)) },
                        onClick = {
                            onTypeChange(it)
                            showDropDownMenu = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClose) {
            Icon(
                Icons.Default.Close,
                null,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

enum class DelayType {
    Audio,
    Subtitle,
}
