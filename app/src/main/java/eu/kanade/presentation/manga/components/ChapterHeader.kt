package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.animesource.model.FetchType
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ChapterHeader(
    enabled: Boolean,
    chapterCount: Int?,
    missingChapterCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // AY -->
    fetchType: FetchType = FetchType.Episodes,
    // <-- AY
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        // KMK -->
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // KMK <--
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Text(
                text = if (chapterCount == null) {
                    stringResource(AYMR.strings.episodes)
                } else {
                    // AY -->
                    val pluralCount = when (fetchType) {
                        FetchType.Seasons -> AYMR.plurals.anime_num_seasons
                        FetchType.Episodes -> AYMR.plurals.anime_num_episodes
                    }
                    pluralStringResource(pluralCount, count = chapterCount, chapterCount)
                    // <-- AY
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            MissingChaptersWarning(fetchType, missingChapterCount)
        }
    }
}

@Composable
private fun MissingChaptersWarning(fetchType: FetchType, count: Int) {
    if (count == 0) {
        return
    }

    // AY -->
    val pluralRes = when (fetchType) {
        FetchType.Seasons -> AMMR.plurals.missing_seasons
        FetchType.Episodes -> AMMR.plurals.missing_episodes
    }
    // <-- AY
    Text(
        text = pluralStringResource(pluralRes, count = count, count),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error.copy(alpha = SECONDARY_ALPHA),
    )
}
