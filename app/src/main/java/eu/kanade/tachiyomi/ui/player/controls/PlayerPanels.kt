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

package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.DebandSettings
import eu.kanade.tachiyomi.ui.player.Debanding
import eu.kanade.tachiyomi.ui.player.Panels
import eu.kanade.tachiyomi.ui.player.VideoFilters
import eu.kanade.tachiyomi.ui.player.controls.components.panels.AudioDelayPanel
import eu.kanade.tachiyomi.ui.player.controls.components.panels.SubColorType
import eu.kanade.tachiyomi.ui.player.controls.components.panels.SubtitleDelayPanel
import eu.kanade.tachiyomi.ui.player.controls.components.panels.SubtitleSettingsPanel
import eu.kanade.tachiyomi.ui.player.controls.components.panels.SubtitlesBorderStyle
import eu.kanade.tachiyomi.ui.player.controls.components.panels.VideoSettingsPanel
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitleJustification
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun PlayerPanels(
    panelShown: Panels,
    onDismissRequest: () -> Unit,
    // Subtitle settings panel state
    isBold: Boolean,
    isItalic: Boolean,
    subJustify: SubtitleJustification,
    subFont: String,
    subFontSize: Int,
    subBorderStyle: SubtitlesBorderStyle,
    subBorderSize: Int,
    subShadowOffset: Int,
    subColor: SubColorType,
    currentSubtitleColor: Int,
    overrideAssSubs: Boolean,
    subScale: Float,
    subPos: Int,
    onSubBoldChange: (Boolean) -> Unit,
    onSubItalicChange: (Boolean) -> Unit,
    onSubJustifyChange: (SubtitleJustification) -> Unit,
    onSubFontChange: (String) -> Unit,
    onSubFontSizeChange: (Int) -> Unit,
    onSubBorderStyleChange: (SubtitlesBorderStyle) -> Unit,
    onSubBorderSizeChange: (Int) -> Unit,
    onSubShadowOffsetChange: (Int) -> Unit,
    onSubColorChange: (Int) -> Unit,
    onSubColorTypeChange: (SubColorType) -> Unit,
    onOverrideAssSubsChange: (Boolean) -> Unit,
    onSubScaleChange: (Float) -> Unit,
    onSubPosChange: (Int) -> Unit,
    onSubtitleSettingsReset: () -> Unit,
    onSubtitleMiscReset: () -> Unit,
    subDelayMsPrimary: Int,
    subDelayMsSecondary: Int,
    subSpeed: Double,
    onSubDelayPrimaryChange: (Int) -> Unit,
    onSubDelaySecondaryChange: (Int) -> Unit,
    onSubSpeedChange: (Double) -> Unit,
    onSubDelayApply: () -> Unit,
    onSubDelayReset: () -> Unit,
    onSubColorReset: (SubColorType) -> Unit,
    // Audio delay panel state
    audioDelayMs: Int,
    onAudioDelayChange: (Int) -> Unit,
    onAudioDelayApply: () -> Unit,
    onAudioDelayReset: () -> Unit,
    // Video settings panel state
    deband: Debanding,
    onDebandChange: (Debanding) -> Unit,
    onVideoFilterChange: (VideoFilters, Int) -> Unit,
    debandSettings: (DebandSettings) -> Int,
    onDebandSettingsChange: (DebandSettings, Int) -> Unit,
    onDebandReset: () -> Unit,
    isGpuNextEnabled: Boolean,
    filterValue: (VideoFilters) -> Int,
    onFilterReset: () -> Unit,
    modifier: Modifier,
) {
    AnimatedContent(
        targetState = panelShown,
        label = "panels",
        contentAlignment = Alignment.CenterEnd,
        contentKey = { it.name },
        transitionSpec = {
            fadeIn() + slideInHorizontally { it / 3 } togetherWith fadeOut() + slideOutHorizontally { it / 2 }
        },
        modifier = modifier,
    ) { currentPanel ->
        when (currentPanel) {
            Panels.None -> {
                Box(Modifier.fillMaxHeight())
            }
            Panels.SubtitleSettings -> {
                SubtitleSettingsPanel(
                    onDismissRequest = onDismissRequest,
                    isBold = isBold,
                    isItalic = isItalic,
                    justify = subJustify,
                    font = subFont,
                    fontSize = subFontSize,
                    borderStyle = subBorderStyle,
                    borderSize = subBorderSize,
                    shadowOffset = subShadowOffset,
                    onIsBoldChange = onSubBoldChange,
                    onIsItalicChange = onSubItalicChange,
                    onJustificationChange = onSubJustifyChange,
                    onFontChange = onSubFontChange,
                    onFontSizeChange = onSubFontSizeChange,
                    onBorderStyleChange = onSubBorderStyleChange,
                    onBorderSizeChange = onSubBorderSizeChange,
                    onShadowOffsetChange = onSubShadowOffsetChange,
                    onTypographyReset = onSubtitleSettingsReset,
                    currentColorType = subColor,
                    currentSubtitleColor = currentSubtitleColor,
                    onColorChange = onSubColorChange,
                    onColorReset = onSubColorReset,
                    onColorTypeChange = onSubColorTypeChange,
                    overrideAssSubs = overrideAssSubs,
                    subScale = subScale,
                    subPos = subPos,
                    onOverrideAssSubsChange = onOverrideAssSubsChange,
                    onSubScaleChange = onSubScaleChange,
                    onSubPosChange = onSubPosChange,
                    onMiscReset = onSubtitleMiscReset,
                    modifier = Modifier,
                )
            }
            Panels.SubtitleDelay -> {
                SubtitleDelayPanel(
                    delayMs = subDelayMsPrimary,
                    secondaryDelayMs = subDelayMsSecondary,
                    speed = subSpeed,
                    onSpeedChange = onSubSpeedChange,
                    onDelayChange = onSubDelayPrimaryChange,
                    onSecondaryDelayChange = onSubDelaySecondaryChange,
                    onApply = onSubDelayApply,
                    onReset = onSubDelayReset,
                    onDismissRequest = onDismissRequest,
                )
            }
            Panels.AudioDelay -> {
                AudioDelayPanel(
                    delayMs = audioDelayMs,
                    onDelayChange = onAudioDelayChange,
                    onApply = onAudioDelayApply,
                    onReset = onAudioDelayReset,
                    onDismissRequest = onDismissRequest,
                )
            }
            Panels.VideoFilters -> {
                VideoSettingsPanel(
                    onDismissRequest = onDismissRequest,
                    onVideoFilterChange = onVideoFilterChange,
                    deband = deband,
                    onDebandChange = onDebandChange,
                    debandSettings = debandSettings,
                    onDebandSettingsChange = onDebandSettingsChange,
                    onDebandReset = onDebandReset,
                    isGpuNextEnabled = isGpuNextEnabled,
                    filterValue = filterValue,
                    onFilterReset = onFilterReset,
                )
            }
        }
    }
}

val CARDS_MAX_WIDTH = 420.dp
val panelCardsColors: @Composable () -> CardColors = {
    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }

    val colors = CardDefaults.cardColors()
    colors.copy(
        containerColor = MaterialTheme.colorScheme.surface.copy(playerPreferences.panelOpacity().get() / 100f),
        disabledContainerColor = MaterialTheme.colorScheme.surfaceDim.copy(
            playerPreferences.panelOpacity().get() / 100f,
        ),
    )
}
