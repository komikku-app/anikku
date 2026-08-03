package app.anikku.macos.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.subtitle.SubtitleCredentialStore
import app.anikku.macos.platform.subtitle.SubtitleCredentials
import app.anikku.macos.ui.components.HeadingItem
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Subtitle settings panel — configures the Jimaku API token and the
 * OpenSubtitles.com API key + login used by the automatic English-subtitle
 * fetcher. All values are stored in the macOS keychain.
 *
 * The app ships with developer-provided baked-in defaults, so end users
 * normally never need to touch these fields; they exist for overrides.
 */
@Composable
fun SubtitleSettingsPanel() {
    val toastHost = LocalToastHost.current
    val scope = rememberCoroutineScope()

    val credentialStore = remember {
        runCatching {
            GlobalContext.get().get<SubtitleCredentialStore>()
        }.getOrNull()
    }

    // Load current values once; "Save" persists them to the keychain.
    val initial = remember(credentialStore) { credentialStore?.load() ?: SubtitleCredentials() }
    var jimakuToken by remember(initial) { mutableStateOf(initial.jimakuToken) }
    var osApiKey by remember(initial) { mutableStateOf(initial.openSubtitlesApiKey) }
    var osUsername by remember(initial) { mutableStateOf(initial.openSubtitlesUsername) }
    var osPassword by remember(initial) { mutableStateOf(initial.openSubtitlesPassword) }
    var saving by remember { mutableStateOf(false) }

    HeadingItem("Subtitles")

    Text(
        text = "When a source doesn't provide English subtitles, Anikku can fetch them " +
            "automatically from Jimaku (anime-native) with OpenSubtitles.com as the " +
            "fallback from the player's subtitle menu.",
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(8.dp))

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = "Jimaku",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
            value = jimakuToken,
            onValueChange = { jimakuToken = it.take(256) },
            label = { Text("Jimaku API token") },
            singleLine = true,
            placeholder = { Text("Get a free token at jimaku.cc/account") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "OpenSubtitles.com",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
            value = osApiKey,
            onValueChange = { osApiKey = it.take(256) },
            label = { Text("OpenSubtitles API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        OutlinedTextField(
            value = osUsername,
            onValueChange = { osUsername = it.take(256) },
            label = { Text("OpenSubtitles username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        OutlinedTextField(
            value = osPassword,
            onValueChange = { osPassword = it.take(256) },
            label = { Text("OpenSubtitles password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                if (credentialStore == null) {
                    toastHost.show(
                        text = "Subtitle settings unavailable",
                        duration = ToastDuration.SHORT,
                        isError = true,
                        location = "SubtitleSettingsPanel.save",
                    )
                    return@OutlinedButton
                }
                saving = true
                scope.launch {
                    credentialStore.save(
                        SubtitleCredentials(
                            jimakuToken = jimakuToken.trim(),
                            openSubtitlesApiKey = osApiKey.trim(),
                            openSubtitlesUsername = osUsername.trim(),
                            openSubtitlesPassword = osPassword.trim(),
                        ),
                    )
                    saving = false
                    toastHost.show("Subtitle credentials saved", ToastDuration.SHORT)
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) "Saving…" else "Save subtitle credentials")
        }

        Text(
            text = "Credentials are stored in your macOS Keychain. Saved values are used " +
                "instead of the app's built-in defaults.",
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }

    Spacer(Modifier.height(8.dp))

    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
}
