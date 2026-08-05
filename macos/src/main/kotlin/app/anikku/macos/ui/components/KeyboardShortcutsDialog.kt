package app.anikku.macos.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Keyboard-shortcuts reference dialog — reachable from Help > Keyboard
 * Shortcuts… and the player's `?` key.
 */
@Composable
fun KeyboardShortcutsDialog(onDismiss: () -> Unit) {
    val shortcuts = listOf(
        "Global" to listOf(
            "⌘1 – ⌘9" to "Switch tabs",
            "⌘," to "Settings",
            "⌘S" to "Toggle sidebar",
            "⌘F" to "Global search",
        ),
        "Player" to listOf(
            "Space / K" to "Play / pause",
            "← → / J L" to "Seek ±10s",
            "↑ ↓" to "Volume",
            "M" to "Mute",
            "[ ]" to "Playback speed",
            ", ." to "Subtitle delay",
            "F" to "Full screen",
            "S" to "Screenshot",
            "G" to "Save 5s GIF clip",
            "?" to "This help",
            "Esc" to "Exit full screen / back",
        ),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keyboard Shortcuts") },
        text = {
            Column {
                shortcuts.forEach { (section, rows) ->
                    Text(
                        section,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    rows.forEach { (key, action) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) {
                            Text(
                                key,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(110.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                action,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
