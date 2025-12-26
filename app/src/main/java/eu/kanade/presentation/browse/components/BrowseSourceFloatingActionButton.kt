package eu.kanade.presentation.browse.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BrowseSourceFloatingActionButton(
    isVisible: Boolean,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textRes = if (isVisible) {
        MR.strings.action_filter
    } else {
        SYMR.strings.saved_searches
    }
    SmallExtendedFloatingActionButton(
        modifier = modifier,
        text = { Text(text = stringResource(textRes)) },
        icon = { Icon(Icons.Outlined.FilterList, contentDescription = null) },
        onClick = onFabClick,
    )
}
