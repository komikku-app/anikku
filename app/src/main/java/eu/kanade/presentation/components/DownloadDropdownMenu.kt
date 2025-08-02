package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.DownloadAction
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DownloadDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
    // KMK -->
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    // KMK <--
) {
    val options = persistentListOf(
        DownloadAction.NEXT_1_CHAPTER to pluralStringResource(AYMR.plurals.download_amount_anime, 1, 1),
        DownloadAction.NEXT_5_CHAPTERS to pluralStringResource(AYMR.plurals.download_amount_anime, 5, 5),
        DownloadAction.NEXT_10_CHAPTERS to pluralStringResource(AYMR.plurals.download_amount_anime, 10, 10),
        DownloadAction.NEXT_25_CHAPTERS to pluralStringResource(AYMR.plurals.download_amount_anime, 25, 25),
        DownloadAction.UNSEEN_CHAPTERS to stringResource(AYMR.strings.download_unseen),
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        // KMK -->
        offset = offset,
        // KMK <--
    ) {
        options.map { (downloadAction, string) ->
            DropdownMenuItem(
                text = { Text(text = string) },
                onClick = {
                    onDownloadClicked(downloadAction)
                    onDismissRequest()
                },
            )
        }
    }
}
