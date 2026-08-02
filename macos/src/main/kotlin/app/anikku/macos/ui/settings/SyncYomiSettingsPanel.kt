package app.anikku.macos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.sync.LocalSyncYomiService
import app.anikku.macos.platform.sync.SyncYomiOutcome
import app.anikku.macos.platform.sync.SyncYomiState
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Self-hosted SyncYomi connection and manual synchronization controls. */
@Composable
fun SyncYomiSettingsPanel() {
    val service = LocalSyncYomiService.current ?: return
    val toast = LocalToastHost.current
    val scope = rememberCoroutineScope()
    val state by service.state.collectAsState()
    var host by remember(service.host) { mutableStateOf(service.host) }
    var token by remember { mutableStateOf("") }
    val busy = state == SyncYomiState.SYNCING

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("SyncYomi", style = MaterialTheme.typography.titleSmall)
        Text(
            "Synchronize the portable macOS backup through your self-hosted SyncYomi server. " +
                "The API token is stored only in macOS Keychain.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Server URL") },
            placeholder = { Text("https://sync.example.com") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(if (service.isConfigured) "New API token (leave blank to keep current)" else "API token") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy && host.isNotBlank() && (token.isNotBlank() || service.isConfigured),
                onClick = {
                    scope.launch {
                        val result = if (token.isBlank() && service.isConfigured && host == service.host) {
                            service.sync()
                        } else {
                            withContext(Dispatchers.IO) { service.configure(host, token) }
                        }
                        if (result.success) {
                            token = ""
                            toast.show(
                                if (result.outcome == SyncYomiOutcome.NOT_MODIFIED) "SyncYomi saved" else "SyncYomi complete",
                                ToastDuration.SHORT,
                            )
                        } else {
                            toast.show(result.error ?: "SyncYomi failed", ToastDuration.LONG, true)
                        }
                    }
                },
            ) {
                Text(
                    if (service.isConfigured && token.isBlank() && host == service.host) "Sync Now" else "Save",
                )
            }

            if (service.isConfigured) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val removed = service.disconnect()
                            withContext(Dispatchers.Main) {
                                if (removed) {
                                    host = ""
                                    token = ""
                                    toast.show("SyncYomi disconnected", ToastDuration.SHORT)
                                } else {
                                    toast.show(service.lastError.value ?: "Disconnect failed", ToastDuration.LONG, true)
                                }
                            }
                        }
                    },
                ) { Text("Disconnect") }
            }
        }
        if (busy) Text("Synchronizing…", style = MaterialTheme.typography.bodySmall)
    }
}
