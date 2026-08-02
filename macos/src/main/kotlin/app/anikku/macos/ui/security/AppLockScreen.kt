package app.anikku.macos.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Full-window privacy surface shown before any library or playback UI. */
@Composable
fun AppLockScreen(
    biometricAvailable: Boolean,
    useBiometrics: Boolean,
    onVerifyPin: (String) -> Boolean,
    onBiometricUnlock: suspend () -> Boolean,
    onUnlocked: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var authenticating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun verifyPin() {
        if (onVerifyPin(pin)) {
            pin = ""
            error = null
            onUnlocked()
        } else {
            error = "Incorrect PIN"
            pin = ""
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Anikku is locked", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Authenticate to return to your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    pin = value.take(64)
                    error = null
                },
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                label = { Text("PIN") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = ::verifyPin,
                enabled = pin.isNotEmpty() && !authenticating,
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            ) {
                Text("Unlock")
            }
            if (useBiometrics && biometricAvailable) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        authenticating = true
                        error = null
                        scope.launch {
                            if (onBiometricUnlock()) onUnlocked() else error = "Biometric authentication failed"
                            authenticating = false
                        }
                    },
                    enabled = !authenticating,
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                ) {
                    if (authenticating) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp))
                    } else {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                        Text("Use Touch ID", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
