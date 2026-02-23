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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.player.controls.components.panels.components.MultiCardPanel
import eu.kanade.tachiyomi.ui.player.settings.SubtitleJustification
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SubtitleSettingsPanel(
    onDismissRequest: () -> Unit,
    // Typography card state
    isBold: Boolean,
    isItalic: Boolean,
    justify: SubtitleJustification,
    font: String,
    fontSize: Int,
    borderStyle: SubtitlesBorderStyle,
    borderSize: Int,
    shadowOffset: Int,
    onIsBoldChange: (Boolean) -> Unit,
    onIsItalicChange: (Boolean) -> Unit,
    onJustificationChange: (SubtitleJustification) -> Unit,
    onFontChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onBorderStyleChange: (SubtitlesBorderStyle) -> Unit,
    onBorderSizeChange: (Int) -> Unit,
    onShadowOffsetChange: (Int) -> Unit,
    onTypographyReset: () -> Unit,
    // Colors card state
    currentSubtitleColor: Int,
    currentColorType: SubColorType,
    onColorChange: (Int) -> Unit,
    onColorReset: (SubColorType) -> Unit,
    onColorTypeChange: (SubColorType) -> Unit,
    // Misc card state
    overrideAssSubs: Boolean,
    subScale: Float,
    subPos: Int,
    onOverrideAssSubsChange: (Boolean) -> Unit,
    onSubScaleChange: (Float) -> Unit,
    onSubPosChange: (Int) -> Unit,
    onMiscReset: () -> Unit,
    modifier: Modifier,
) {
    MultiCardPanel(
        onDismissRequest = onDismissRequest,
        title = stringResource(AYMR.strings.player_sheets_subtitles_settings_title),
        cardCount = 3,
        modifier = modifier,
    ) { index, cardModifier ->
        when (index) {
            0 -> SubtitleSettingsTypographyCard(
                isBold = isBold,
                isItalic = isItalic,
                justify = justify,
                font = font,
                fontSize = fontSize,
                borderStyle = borderStyle,
                borderSize = borderSize,
                shadowOffset = shadowOffset,
                onIsBoldChange = onIsBoldChange,
                onIsItalicChange = onIsItalicChange,
                onJustificationChange = onJustificationChange,
                onFontChange = onFontChange,
                onFontSizeChange = onFontSizeChange,
                onBorderStyleChange = onBorderStyleChange,
                onBorderSizeChange = onBorderSizeChange,
                onShadowOffsetChange = onShadowOffsetChange,
                onReset = onTypographyReset,
                modifier = cardModifier,
            )
            1 -> {
                SubtitleSettingsColorsCard(
                    currentColor = currentSubtitleColor,
                    currentColorType = currentColorType,
                    onColorChange = onColorChange,
                    onColorReset = onColorReset,
                    onColorTypeChange = onColorTypeChange,
                    modifier = cardModifier,
                )
            }
            2 -> SubtitlesMiscellaneousCard(
                overrideAssSubs = overrideAssSubs,
                subScale = subScale,
                subPos = subPos,
                onOverrideAssSubsChange = onOverrideAssSubsChange,
                onSubScaleChange = onSubScaleChange,
                onSubPosChange = onSubPosChange,
                onReset = onMiscReset,
                modifier = cardModifier,
            )
            else -> {}
        }
    }
}
