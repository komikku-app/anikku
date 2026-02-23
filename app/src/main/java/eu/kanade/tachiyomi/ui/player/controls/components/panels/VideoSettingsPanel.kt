package eu.kanade.tachiyomi.ui.player.controls.components.panels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.player.DebandSettings
import eu.kanade.tachiyomi.ui.player.Debanding
import eu.kanade.tachiyomi.ui.player.VideoFilters
import eu.kanade.tachiyomi.ui.player.controls.components.panels.components.MultiCardPanel
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun VideoSettingsPanel(
    onDismissRequest: () -> Unit,
    onVideoFilterChange: (VideoFilters, Int) -> Unit,
    // Deband settings
    deband: Debanding,
    onDebandChange: (Debanding) -> Unit,
    debandSettings: (DebandSettings) -> Int,
    onDebandSettingsChange: (DebandSettings, Int) -> Unit,
    onDebandReset: () -> Unit,
    // Filter settings
    isGpuNextEnabled: Boolean,
    filterValue: (VideoFilters) -> Int,
    onFilterReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MultiCardPanel(
        onDismissRequest = onDismissRequest,
        title = stringResource(AMMR.strings.player_sheets_video_settings_title),
        cardCount = 2,
        modifier = modifier,
    ) { index, cardModifier ->
        when (index) {
            0 -> VideoSettingsDebandCard(
                deband = deband,
                onDebandingChange = onDebandChange,
                debandSettingsValue = debandSettings,
                onDebandingSettingsChange = onDebandSettingsChange,
                onReset = onDebandReset,
                modifier = cardModifier,
            )
            1 -> VideoSettingsFiltersCard(
                isGpuNextEnabled = isGpuNextEnabled,
                filterValue = filterValue,
                onFilterValueChange = onVideoFilterChange,
                onReset = onFilterReset,
                modifier = cardModifier,
            )
            else -> {}
        }
    }
}
