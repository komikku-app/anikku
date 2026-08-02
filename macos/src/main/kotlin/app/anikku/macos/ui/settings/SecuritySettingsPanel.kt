package app.anikku.macos.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.security.MacOSBiometricAuth
import app.anikku.macos.ui.components.CheckboxItem
import app.anikku.macos.ui.components.HeadingItem
import app.anikku.macos.ui.components.IconItem
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration

data class AppLockController(
    val authentication: MacOSBiometricAuth,
    val lockNow: () -> Unit,
)

val LocalAppLockController = compositionLocalOf<AppLockController?> { null }

@Composable
fun SecuritySettingsPanel() {
    val settings = LocalSettingsState.current
    val controller = LocalAppLockController.current
    val toast = LocalToastHost.current
    var showPinDialog by remember { mutableStateOf(false) }

    HeadingItem("Security")
    CheckboxItem(
        label = "Enable app lock",
        checked = settings.appLockEnabled,
        onClick = {
            if (settings.appLockEnabled) {
                if (controller?.authentication?.clearPin() == true) {
                    settings.appLockEnabled = false
                    toast.show("App lock disabled", ToastDuration.SHORT)
                } else {
                    toast.show("Could not remove the secure PIN", ToastDuration.LONG, true)
                }
            } else if (controller != null) {
                showPinDialog = true
            }
        },
    )

    if (settings.appLockEnabled) {
        CheckboxItem(
            label = "Lock when Anikku loses focus",
            checked = settings.lockOnBlur,
            onClick = { settings.lockOnBlur = !settings.lockOnBlur },
        )
        val biometricAvailable = controller?.authentication?.isBiometricAvailable == true
        CheckboxItem(
            label = if (biometricAvailable) "Use Touch ID" else "Use Touch ID (unavailable)",
            checked = biometricAvailable && settings.useBiometrics,
            onClick = {
                if (biometricAvailable) settings.useBiometrics = !settings.useBiometrics
            },
        )
        IconItem("Lock now", Icons.Outlined.Lock) { controller?.lockNow?.invoke() }
    }

    if (showPinDialog && controller != null) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onSave = { pin ->
                if (controller.authentication.setPin(pin)) {
                    settings.appLockEnabled = true
                    settings.useBiometrics = controller.authentication.isBiometricAvailable
                    showPinDialog = false
                    toast.show("App lock enabled", ToastDuration.SHORT)
                } else {
                    toast.show("Keychain could not save the app-lock PIN", ToastDuration.LONG, true)
                }
            },
        )
    }
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = pin.length in 4..64 && pin == confirmation

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set app-lock PIN") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("The PIN is hashed and stored in your macOS Keychain.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.take(64) },
                    label = { Text("New PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.take(64) },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    isError = confirmation.isNotEmpty() && confirmation != pin,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(pin) }, enabled = valid) { Text("Enable Lock") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
