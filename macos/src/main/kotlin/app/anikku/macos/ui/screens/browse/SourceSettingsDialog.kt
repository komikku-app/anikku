package app.anikku.macos.ui.screens.browse

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import android.content.SharedPreferences
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.extension.model.Extension

/**
 * Per-source settings sheet. Extensions expose their preferences as a
 * SharedPreferences (new API) — this dialog renders whatever keys the source
 * stores and persists edits through the app's real preference store (see
 * [android.content.AndroidPrefsBridge]).
 *
 * Not every source stores preferences; those show an honest empty state.
 */
@Composable
fun SourceSettingsDialog(
    extension: Extension.Installed,
    onClose: () -> Unit,
) {
    // (source, configurable-interface) pairs — the interface has no name/id.
    val configurable = remember(extension) {
        extension.sources.mapNotNull { src -> (src as? ConfigurableAnimeSource)?.let { src to it } }
    }

    DialogWindow(
        onCloseRequest = onClose,
        title = "Settings — ${extension.name}",
        state = rememberDialogState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(480.dp, 520.dp),
        ),
        resizable = false,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                )
                Text(
                    text = "Changes are saved automatically and apply to this source on its next use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
                HorizontalDivider()

                if (configurable.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "This extension doesn't expose settings",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // LazyListScope is not a composable context — pre-compute
                    // each source's preferences before rendering.
                    val sourcesData = remember(configurable) {
                        configurable.map { (src, cfg) ->
                            SourcePrefsData(
                                id = src.id,
                                name = src.name,
                                prefs = runCatching { cfg.getSourcePreferences() }.getOrNull(),
                            )
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        sourcesData.forEach { data ->
                            item(key = "header_${data.id}") {
                                Text(
                                    text = data.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                                )
                            }
                            val prefs = data.prefs
                            if (prefs == null) {
                                item(key = "err_${data.id}") {
                                    Text(
                                        "Couldn't read this source's settings",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    )
                                }
                            } else {
                                val entries = prefs.all.toList().sortedBy { it.first }
                                if (entries.isEmpty()) {
                                    item(key = "empty_${data.id}") {
                                        Text(
                                            "No settings stored yet — values appear here once the source saves something.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                        )
                                    }
                                } else {
                                    entries.forEach { (key, value) ->
                                        item(key = "pref_${data.id}_$key") {
                                            PreferenceRow(
                                                key = key,
                                                value = value,
                                                onStringValue = { newValue ->
                                                    prefs.edit().putString(key, newValue).commit()
                                                },
                                                onBooleanValue = { newValue ->
                                                    prefs.edit().putBoolean(key, newValue).commit()
                                                },
                                                onLongValue = { newValue ->
                                                    prefs.edit().putLong(key, newValue).commit()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "footer") { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceRow(
    key: String,
    value: Any?,
    onStringValue: (String) -> Unit,
    onBooleanValue: (Boolean) -> Unit,
    onLongValue: (Long) -> Unit,
) {
    var text by remember(key) { mutableStateOf(value?.toString() ?: "") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = humanizeKey(key),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = key,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        when (value) {
            is Boolean -> {
                var checked by remember(key) { mutableStateOf(value) }
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        onBooleanValue(it)
                    },
                )
            }
            is Int, is Long -> {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() || it == '-' }
                        text.toLongOrNull()?.let(onLongValue)
                    },
                    modifier = Modifier.width(150.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                )
            }
            else -> {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input
                        onStringValue(input)
                    },
                    modifier = Modifier.width(190.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                )
            }
        }
    }
}

/** "preferred_quality" → "Preferred quality" (best-effort, no title API). */
private fun humanizeKey(key: String): String =
    key.replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }


/** Pre-computed preferences for one configurable source (LazyColumn-safe). */
private data class SourcePrefsData(
    val id: Long,
    val name: String,
    val prefs: SharedPreferences?,
)
