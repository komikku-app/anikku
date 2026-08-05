package app.anikku.macos.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A single action in an [OverflowMenu].
 */
data class OverflowItem(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
)

/**
 * A small "⋯" button that opens a dropdown of contextual actions.
 *
 * Used to remove anime/episodes from the library, continue-watching, or
 * history without a permanent visible button. DropdownMenu is safe here
 * (the root-level SelectionContainer that previously crashed popups was
 * removed).
 *
 * [tint] defaults to [MaterialTheme.colorScheme.onSurfaceVariant]; callers
 * that overlay cover images pass `Color.White` explicitly.
 */
@Composable
fun OverflowMenu(
    items: List<OverflowItem>,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val resolvedTint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "More options",
                tint = resolvedTint,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    leadingIcon = item.icon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}
