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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.player.components.ExpandableCard
import eu.kanade.presentation.player.components.SliderItem
import eu.kanade.tachiyomi.ui.player.controls.CARDS_MAX_WIDTH
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.toFixed
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import eu.kanade.tachiyomi.ui.player.settings.SubtitleAssOverride
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SubtitlesMiscellaneousCard(
    overrideAssSubs: SubtitleAssOverride,
    subScale: Float,
    subPos: Int,
    onOverrideAssSubsChange: (SubtitleAssOverride) -> Unit,
    onSubScaleChange: (Float) -> Unit,
    onSubPosChange: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }
    ExpandableCard(
        isExpanded,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                Icon(Icons.Default.Tune, null)
                Text(stringResource(AYMR.strings.player_sheets_sub_misc_title))
            }
        },
        onExpand = { isExpanded = !isExpanded },
        modifier.widthIn(max = CARDS_MAX_WIDTH),
        colors = panelCardsColors(),
    ) {
        Column {
            var selectingOverrideAss by remember { mutableStateOf(false) }
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = {
                                selectingOverrideAss = !selectingOverrideAss
                            },
                        )
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.large),
                ) {
                    Icon(Icons.Default.BorderStyle, null)
                    Column {
                        Text(
                            text = stringResource(AYMR.strings.player_sheets_sub_override_ass),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(overrideAssSubs.titleRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                DropdownMenu(expanded = selectingOverrideAss, onDismissRequest = { selectingOverrideAss = false }) {
                    SubtitleAssOverride.entries.forEach {
                        DropdownMenuItem(
                            text = { Text(stringResource(it.titleRes)) },
                            onClick = { onOverrideAssSubsChange(it) },
                            trailingIcon = {
                                if (overrideAssSubs == it) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    }
                }
            }
            SliderItem(
                label = stringResource(AYMR.strings.player_sheets_sub_scale),
                value = subScale,
                valueString = subScale.toFixed(2).toString(),
                onChange = onSubScaleChange,
                valueRange = 0f..5f,
                icon = {
                    Icon(
                        Icons.Default.FormatSize,
                        null,
                    )
                },
            )
            SliderItem(
                label = stringResource(AYMR.strings.player_sheets_sub_position),
                value = subPos,
                onChange = onSubPosChange,
                valueRange = 0..150,
                steps = 0,
                icon = {
                    Icon(
                        Icons.Default.AlignVerticalCenter,
                        null,
                    )
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = MaterialTheme.padding.medium, bottom = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onReset) {
                    Row {
                        Icon(Icons.Default.EditOff, null)
                        Text(stringResource(MR.strings.action_reset))
                    }
                }
            }
        }
    }
}
