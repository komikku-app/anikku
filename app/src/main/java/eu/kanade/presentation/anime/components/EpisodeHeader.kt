package eu.kanade.presentation.anime.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.model.FetchType
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ItemHeader(
    enabled: Boolean,
    episodeCount: Int?,
    missingEpisodeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fetchType: FetchType = FetchType.Episodes,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(
            text = if (episodeCount == null) {
                when (fetchType) {
                    FetchType.Seasons -> stringResource(MR.strings.pref_library_season)
                    FetchType.Episodes -> stringResource(MR.strings.episodes)
                }
            } else {
                val pluralCount =
                    when (fetchType) {
                        FetchType.Seasons -> MR.plurals.anime_num_seasons
                        FetchType.Episodes -> MR.plurals.anime_num_episodes
                    }
                pluralStringResource(pluralCount, count = episodeCount, episodeCount)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        MissingEpisodesWarning(missingEpisodeCount)
    }
}

@Composable
private fun MissingEpisodesWarning(count: Int) {
    if (count == 0) {
        return
    }

    Text(
        text = pluralStringResource(MR.plurals.missing_items, count = count, count),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error.copy(alpha = SECONDARY_ALPHA),
    )
}
