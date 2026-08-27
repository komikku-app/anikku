package app.anikku.macos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.MacOSShareUtil
import app.anikku.macos.platform.network.ChromeCDPClient
import app.anikku.macos.platform.network.ProxyType
import app.anikku.macos.ui.components.CheckboxItem
import app.anikku.macos.ui.components.HeadingItem
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.SelectItem
import app.anikku.macos.ui.components.TextItem
import app.anikku.macos.ui.components.ToastDuration
import java.io.File
import java.time.Instant

/**
 * Network settings panel — configures proxy, Chrome path, and SSL bypass.
 *
 * Features:
 * - Proxy type: None / HTTP / SOCKS4 / SOCKS5
 * - Proxy host, port, username, password
 * - Custom Chrome/Chromium path for Cloudflare bypass
 * - Verify Chrome installation button
 */
@Composable
fun NetworkSettingsPanel() {
    val settings = LocalSettingsState.current
    val toastHost = LocalToastHost.current

    var proxyHost by remember { mutableStateOf(settings.proxyHost) }
    var proxyPort by remember { mutableStateOf(settings.proxyPort.toString()) }
    var proxyUsername by remember { mutableStateOf(settings.proxyUsername) }
    var proxyPassword by remember { mutableStateOf(settings.proxyPassword) }
    var chromePath by remember { mutableStateOf(settings.chromePath) }
    var cdpDebugMode by remember { mutableStateOf(settings.cdpDebugMode) }

    HeadingItem("Network")

    // -----------------------------------------------------------------
    // Proxy configuration
    // -----------------------------------------------------------------
    Text(
        text = "Proxy",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )

    val proxyTypeOptions = arrayOf("Disabled", "HTTP", "SOCKS4", "SOCKS5")
    val proxyTypeValues = arrayOf(ProxyType.DISABLED, ProxyType.HTTP, ProxyType.SOCKS4, ProxyType.SOCKS5)
    val currentProxyIndex = proxyTypeValues.indexOf(settings.proxyType).coerceAtLeast(0)

    SelectItem(
        label = "Proxy Type",
        options = proxyTypeOptions,
        selectedIndex = currentProxyIndex,
        onSelect = { index ->
            settings.proxyType = proxyTypeValues[index]
            toastHost.show("Proxy: ${proxyTypeOptions[index]}", ToastDuration.SHORT)
        },
    )

    if (settings.proxyType != ProxyType.DISABLED) {
        TextItem(
            label = "Proxy Host",
            value = proxyHost,
            onChange = {
                proxyHost = it
                settings.proxyHost = it
            },
        )

        TextItem(
            label = "Proxy Port",
            value = proxyPort,
            onChange = {
                proxyPort = it
                settings.proxyPort = it.toIntOrNull() ?: 8080
            },
        )

        TextItem(
            label = "Username (optional)",
            value = proxyUsername,
            onChange = {
                proxyUsername = it
                settings.proxyUsername = it
            },
        )

        TextItem(
            label = "Password (optional)",
            value = proxyPassword,
            isPassword = true,
            onChange = {
                proxyPassword = it
                settings.proxyPassword = it
                settings.proxyPasswordStorageError?.let { error ->
                    toastHost.show(error, ToastDuration.LONG, isError = true)
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
            ),
        ) {
            Text(
                text = "Proxy settings apply to all extension HTTP requests. " +
                    "Use a local proxy like FlareSolverr (http://localhost:8191) " +
                    "to bypass Cloudflare protection, or route through a VPN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

    // -----------------------------------------------------------------
    // Chrome / Cloudflare bypass path
    // -----------------------------------------------------------------
    Text(
        text = "Cloudflare Bypass",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )

    TextItem(
        label = "Chrome/Chromium Path",
        value = chromePath,
        onChange = {
            chromePath = it
            settings.chromePath = it
        },
    )

    // Check if Chrome is accessible
    val resolvedPath = settings.resolveChromePath()
    val chromeExists = File(resolvedPath).isFile
    val chromeLabel = if (chromeExists) {
        "✅ Chrome found at: $resolvedPath"
    } else {
        "⚠ No Chrome/Chromium found. Cloudflare bypass requires a Chromium-based browser.\n" +
            "Install Chrome, Chromium, Brave, or Edge. You can specify the path above."
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (chromeExists)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
    ) {
        Text(
            text = chromeLabel,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }

    if (!chromeExists) {
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
            ),
        ) {
            Text(
                text = "Alternative: Use a proxy (like FlareSolverr) instead of a local browser. " +
                    "Set Proxy Type to HTTP and point it at your FlareSolverr instance " +
                    "(default: http://localhost:8191).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    // CDP Debug Mode toggle
    Spacer(Modifier.height(12.dp))
    CheckboxItem(
        label = "CDP Debug Mode",
        checked = cdpDebugMode,
        onClick = {
            cdpDebugMode = !cdpDebugMode
            settings.cdpDebugMode = cdpDebugMode
            app.anikku.macos.platform.network.ChromeCDPClient.debugMode = cdpDebugMode
        },
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        ),
    ) {
        Text(
            text = "Logs every Chrome DevTools WebSocket message during Cloudflare bypass " +
                "at INFO level. Use only for troubleshooting WAF issues — produces verbose output.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(12.dp),
        )
    }

    // Export CDP log button — only shown when debug mode is active
    if (cdpDebugMode) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    val logText = ChromeCDPClient.getDebugLog()
                    if (logText.isEmpty()) {
                        toastHost.show("No CDP debug messages captured yet", ToastDuration.SHORT)
                    } else {
                        val exportFile = File.createTempFile(
                            "anikku-cdp-debug-", ".log"
                        )
                        exportFile.deleteOnExit()
                        try {
                            val header = "Anikku CDP Debug Log\n" +
                                "Exported: ${Instant.now()}\n" +
                                "=" .repeat(60) + "\n\n"
                            exportFile.writeText(header + logText)
                            MacOSShareUtil.shareFile(exportFile)
                            toastHost.show("CDP debug log exported — choose where to save", ToastDuration.LONG)
                        } catch (e: Exception) {
                            toastHost.show(
                                "Failed to export: ${e.message?.take(60)}",
                                ToastDuration.LONG,
                            )
                        }
                    }
                },
            ) {
                Text("Export CDP Log", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = {
                    val count = ChromeCDPClient.getDebugLog().lines().size
                    ChromeCDPClient.clearDebugLog()
                    toastHost.show("Cleared $count CDP debug messages", ToastDuration.SHORT)
                },
            ) {
                Text("Clear", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

    // -----------------------------------------------------------------
    // SSL / DoH note
    // -----------------------------------------------------------------
    Text(
        text = "SSL Bypass & DNS",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Text(
            text = "The app automatically trusts all SSL certificates for extension " +
                "connections (insecure mode). This is necessary because many anime " +
                "streaming sites have broken SSL configurations.\n\n" +
                "DNS-over-HTTPS (DoH) is available via the source API if individual " +
                "extensions configure it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}
