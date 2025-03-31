package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.manga.DownloadAction
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DownloadDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadAmount = MR.plurals.download_amount_anime
    val downloadUnviewed = MR.strings.download_unseen
    val options = persistentListOf(
        DownloadAction.NEXT_1_EPISODE to pluralStringResource(downloadAmount, 1, 1),
        DownloadAction.NEXT_5_EPISODES to pluralStringResource(downloadAmount, 5, 5),
        DownloadAction.NEXT_10_EPISODES to pluralStringResource(downloadAmount, 10, 10),
        DownloadAction.NEXT_25_EPISODES to pluralStringResource(downloadAmount, 25, 25),
        DownloadAction.UNSEEN_EPISODES to stringResource(downloadUnviewed),
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
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
