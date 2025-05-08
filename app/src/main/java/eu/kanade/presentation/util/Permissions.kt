package eu.kanade.presentation.util

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun rememberRequestPackageInstallsPermissionState(initialValue: Boolean = false): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var installGranted by remember { mutableStateOf(initialValue) }

    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                installGranted =
                    // KMK -->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // KMK <--
                        context.packageManager.canRequestPackageInstalls()
                        // KMK -->
                    } else {
                        // For API 25 and below, check if unknown sources is enabled
                        try {
                            @Suppress("DEPRECATION")
                            android.provider.Settings.Secure.getInt(
                                context.contentResolver,
                                android.provider.Settings.Secure.INSTALL_NON_MARKET_APPS,
                            ) == 1
                        } catch (e: Exception) {
                            true // Fall back to assuming permission granted
                        }
                    }
                // KMK <--
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return installGranted
}
