package app.anikku.macos.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.update.AppInfo

/**
 * "What's New" dialog shown once per version after an update — reads the
 * bundled `whatsnew.md` resource (release notes ship inside the app, so a
 * version bump never needs a code change to update the notes).
 */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val notes = remember { readBundledWhatsNew() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's New in Anikku ${AppInfo.VERSION}") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 420.dp),
            ) {
                if (notes.isBlank()) {
                    Text(
                        "A new version of Anikku is ready. Check the GitHub release page for what changed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

private fun readBundledWhatsNew(): String = runCatching {
    // Anchor on an object literal — the Compose compiler rejects `::class`
    // references to a @Composable function from a non-composable context.
    object {}.javaClass.classLoader
        ?.getResourceAsStream("whatsnew.md")
        ?.bufferedReader()
        ?.use { it.readText() }
        .orEmpty()
}.getOrDefault("")
