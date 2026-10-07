// ANK -->
package eu.kanade.presentation.manga.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Horizontally scrollable row of season filter chips, shown above the episode list
 * when episode names contain two or more distinct seasons (see EpisodeSeasonParser).
 */
@Composable
fun EpisodeSeasonChips(
    seasons: List<Int>,
    selectedSeason: Int?,
    onSelectSeason: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = MaterialTheme.padding.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        FilterChip(
            selected = selectedSeason == null,
            onClick = { onSelectSeason(null) },
            label = { Text(text = stringResource(MR.strings.all)) },
        )
        seasons.forEach { season ->
            FilterChip(
                selected = selectedSeason == season,
                onClick = { onSelectSeason(season) },
                label = { Text(text = stringResource(AYMR.strings.display_mode_season, season.toString())) },
            )
        }
    }
}
// ANK <--
