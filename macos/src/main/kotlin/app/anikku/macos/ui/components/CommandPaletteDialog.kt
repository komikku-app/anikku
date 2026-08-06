package app.anikku.macos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState

/** A single command-palette entry: label + description + action. */
data class PaletteAction(
    val label: String,
    val description: String = "",
    val keywords: List<String> = emptyList(),
    val icon: ImageVector? = null,
    val run: () -> Unit,
)

/**
 * ⌘K command palette — jump to any tab, screen or setting by typing.
 *
 * macOS-style: type to filter, Enter runs the top match, click runs any row,
 * Esc closes. Mirrors the About/Shortcuts dialogs' window chrome.
 */
@Composable
fun CommandPaletteDialog(
    actions: List<PaletteAction>,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    val filtered = remember(actions, query) {
        val q = query.trim()
        if (q.isBlank()) actions
        else actions.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true) ||
                it.keywords.any { k -> k.contains(q, ignoreCase = true) }
        }
    }

    LaunchedEffect(filtered.size, query) { selectedIndex = 0 }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    DialogWindow(
        onCloseRequest = onClose,
        title = "Go to…",
        state = rememberDialogState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(540.dp, 440.dp),
        ),
        resizable = false,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.Escape -> { onClose(); true }
                            Key.DirectionUp -> {
                                if (filtered.isNotEmpty()) {
                                    selectedIndex = ((selectedIndex - 1) + filtered.size) % filtered.size
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                if (filtered.isNotEmpty()) {
                                    selectedIndex = (selectedIndex + 1) % filtered.size
                                }
                                true
                            }
                            Key.Enter -> {
                                filtered.getOrNull(selectedIndex)?.let { it.run(); onClose() }
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Type a tab, screen or setting…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                )

                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "No matches for \"$query\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(filtered) { index, action ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (index == selectedIndex) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .clickable {
                                        action.run()
                                        onClose()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (action.icon != null) {
                                    Icon(
                                        action.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Column {
                                    Text(
                                        action.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (action.description.isNotBlank()) {
                                        Text(
                                            action.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "↑↓ navigate · Enter open · Esc close",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
            }
        }
    }
}
