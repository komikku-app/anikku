package app.anikku.macos.ui.screens.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.logging.UIActionLogger
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.settings.LocalSettingsState
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.launch

/**
 * Extension management screen (Phase 5.6).
 *
 * Allows users to:
 * - View and add extension repositories
 * - Browse available extensions from repos
 * - Install, update, and remove extensions
 * - View installed extensions with their sources
 */
data class ExtensionsScreen(
    private val extensionManager: MacOSExtensionManager? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        val installedExtensions by (extensionManager?.installedExtensionsFlow?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
        val availableExtensions by (extensionManager?.availableExtensionsFlow?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
        val untrustedExtensions by (extensionManager?.untrustedExtensionsFlow?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
        val fetchError by (extensionManager?.availableFetchError?.collectAsState() ?: remember { mutableStateOf<String?>(null) })

        var selectedTab by remember { mutableStateOf(0) } // 0=Installed, 1=Available, 2=Repos, 3=Untrusted
        // Default repo — pre-converted JAR extensions for macOS.
        // Legacy APK repos are no longer auto-selected because they require
        // jadx/DexClassLoader conversion which is slow and unreliable.
        val defaultRepoUrl = "https://raw.githubusercontent.com/ErnestHysa/anikku-extensions-jar/main/"

        var repoUrl by remember { mutableStateOf(defaultRepoUrl) }
        var isFetching by remember { mutableStateOf(false) }
        var hasAutoFetched by remember { mutableStateOf(false) }
        var installingPkg by remember { mutableStateOf<String?>(null) }
        var installProgress by remember { mutableStateOf(0f) }
        val toastHost = LocalToastHost.current

        // Auto-fetch available extensions from default repo on first load
        LaunchedEffect(extensionManager) {
            if (!hasAutoFetched && extensionManager != null) {
                isFetching = true
                try {
                    // Use force=false so the 1-day rate limit in MacOSExtensionManager is respected
                    extensionManager.findAvailableExtensions(defaultRepoUrl, force = false)
                } catch (e: Exception) {
                    toastHost.show(
                        text = "Failed to fetch extensions: ${e.message?.take(60)}",
                        duration = ToastDuration.LONG,
                        isError = true,
                        source = defaultRepoUrl,
                        throwable = e,
                        location = "ExtensionsScreen.autoFetchAvailableExtensions",
                    )
                }
                hasAutoFetched = true
                isFetching = false
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "Extensions",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Install source extensions to browse and watch anime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }

            // Tab switcher
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Installed", "Available", "Repos", "Untrusted").forEachIndexed { i, label ->
                        Button(
                            onClick = { selectedTab = i },
                            shape = RoundedCornerShape(8.dp),
                            colors = if (selectedTab == i)
                                androidx.compose.material3.ButtonDefaults.buttonColors()
                            else
                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        ) {
                            Text(label)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when (selectedTab) {
                0 -> {
                    // Installed extensions
                    if (installedExtensions.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No extensions installed", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(installedExtensions, key = { it.pkgName }) { ext ->
                            InstalledExtensionCard(
                                extension = ext,
                                onRemove = {
                                                    UIActionLogger.logExtension(ext.name, "remove", ext.pkgName)
                                extensionManager?.removeExtension(ext)
                                    toastHost.show("Removed ${ext.name}", ToastDuration.SHORT)
                                },
                            )
                        }
                    }
                }

                1 -> {
                    // Available extensions from repos
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = repoUrl,
                                onValueChange = { repoUrl = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("Extension repo URL") },
                                shape = RoundedCornerShape(8.dp),
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        isFetching = true
                                        extensionManager?.findAvailableExtensions(repoUrl, force = true)
                                        isFetching = false
                                    }
                                },
                                enabled = !isFetching,
                            ) {
                                Icon(
                                    if (isFetching) Icons.Outlined.Refresh else Icons.Outlined.Download,
                                    contentDescription = "Fetch",
                                )
                            }
                        }
                    }

                    if (isFetching) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    if (availableExtensions.isEmpty() && !isFetching && fetchError != null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.CloudOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Couldn't load extensions",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        fetchError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                isFetching = true
                                                extensionManager?.findAvailableExtensions(repoUrl, force = false)
                                                isFetching = false
                                            }
                                        },
                                        enabled = !isFetching,
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    } else if (availableExtensions.isEmpty() && !isFetching) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "No extensions available",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Make sure the repo URL points to an index.min.json file. " +
                                            "For the best experience, use a pre-converted JAR repo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    } else {
                        items(availableExtensions, key = { it.pkgName }) { ext ->
                            AvailableExtensionCard(
                                extension = ext,
                                isInstalled = installedExtensions.any { it.pkgName == ext.pkgName },
                                isInstalling = installingPkg == ext.pkgName,
                                installProgress = installProgress,
                                onInstall = {
                                    scope.launch {
                                        installingPkg = ext.pkgName
                                        try {
                                            UIActionLogger.logExtension(ext.name, "install", ext.pkgName)
                                        extensionManager?.installExtension(ext) { step ->
                                                when (step) {
                                                    is InstallStep.Downloading -> {
                                                        installProgress = step.progress
                                                    }
                                                    is InstallStep.Complete -> {
                                                        UIActionLogger.logExtension(ext.name, "installed", ext.pkgName)
                                                        toastHost.show("Installed ${ext.name}", ToastDuration.SHORT)
                                                    }
                                                    is InstallStep.Error -> {
                                                        toastHost.show(
                                                            text = step.message,
                                                            duration = ToastDuration.LONG,
                                                            isError = true,
                                                            source = ext.pkgName,
                                                            location = "ExtensionsScreen.installExtension",
                                                        )
                                                    }
                                                    else -> {} // Installing handled internally
                                                }
                                            }
                                        } finally {
                                            installingPkg = null
                                            installProgress = 0f
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                3 -> {
                    // Untrusted extensions
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Untrusted Extensions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (untrustedExtensions.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        UIActionLogger.logClick("Extensions", "trust_all", "untrusted", "count=${untrustedExtensions.size}")
                                        untrustedExtensions.forEach { ext ->
                                            extensionManager?.trustExtension(ext)
                                        }
                                        toastHost.show("Trusted ${untrustedExtensions.size} extension(s)", ToastDuration.SHORT)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text("Trust All", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "These extensions are not trusted by the app. Review them and click Trust to enable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (untrustedExtensions.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "All extensions are trusted",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Untrusted extensions will appear here after you add extension JARs to the extensions directory",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    } else {
                        items(untrustedExtensions, key = { it.pkgName }) { ext ->
                            UntrustedExtensionCard(
                                extension = ext,
                                onTrust = {
                                    extensionManager?.trustExtension(ext)
                                    toastHost.show("Trusted ${ext.name}", ToastDuration.SHORT)
                                },
                            )
                        }
                    }
                }

                2 -> {
                    // Repos management
                    item {
                        Text(
                            "Extension Repositories",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Pre-configured anime extension repos. The first one with available extensions is used automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // Saved repos — persisted across launches (seeded with the
                    // defaults on first run). Selecting one fetches it.
                    item {
                        val settings = LocalSettingsState.current
                        val savedRepos = remember(settings.extensionRepos) {
                            settings.extensionRepos.sortedBy { it != defaultRepoUrl.trimEnd('/') }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            savedRepos.forEach { url ->
                                val isActive = url == repoUrl.trimEnd('/')
                                val name = if (url == defaultRepoUrl.trimEnd('/')) {
                                    "Anikku macOS Extensions"
                                } else {
                                    url.removePrefix("https://").removePrefix("http://")
                                        .substringBefore('/').substringBefore(':')
                                }
                                val desc = if (url == defaultRepoUrl.trimEnd('/')) {
                                    "Pre-converted JVM JARs for macOS — recommended, no conversion needed"
                                } else {
                                    "Custom extension repository"
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isActive)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    onClick = {
                                        repoUrl = url
                                        scope.launch {
                                            isFetching = true
                                            extensionManager?.findAvailableExtensions(url, force = true)
                                            isFetching = false
                                        }
                                    },
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(name, fontWeight = FontWeight.Medium)
                                            Text(
                                                desc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (isActive) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Active",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        if (url != defaultRepoUrl.trimEnd('/')) {
                                            Spacer(Modifier.width(8.dp))
                                            IconButton(onClick = { settings.removeExtensionRepo(url) }) {
                                                Icon(
                                                    Icons.Outlined.Delete,
                                                    contentDescription = "Remove repo",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = repoUrl,
                                    onValueChange = { repoUrl = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("Custom repo URL") },
                                    placeholder = { Text("Paste a repo index URL...") },
                                    shape = RoundedCornerShape(8.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        settings.addExtensionRepo(repoUrl)
                                        scope.launch {
                                            isFetching = true
                                            extensionManager?.findAvailableExtensions(repoUrl, force = true)
                                            isFetching = false
                                        }
                                    },
                                    enabled = repoUrl.isNotBlank() && !isFetching,
                                ) {
                                    Text("Add")
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Saved repos persist — add a URL pointing to an index.min.json file",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledExtensionCard(
    extension: Extension.Installed,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(extension.name, fontWeight = FontWeight.Medium)
                Text(
                    "v${extension.versionName} · ${extension.sources.size} sources",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (extension.hasUpdate) {
                    Text(
                        "Update available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                // Show health indicator badges for up to 8 sources, then "+N"
                if (extension.sources.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    val displaySources = extension.sources.take(8)
                    val remaining = extension.sources.size - displaySources.size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        displaySources.forEach { source ->
                            SourceHealthBadge(source = source)
                        }
                        if (remaining > 0) {
                            Text(
                                "+$remaining",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun UntrustedExtensionCard(
    extension: Extension.Untrusted,
    onTrust: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(extension.name, fontWeight = FontWeight.Medium)
                    Text(
                        "v${extension.versionName} · ${extension.pkgName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Signed: ${extension.signatureHash.take(16)}…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onTrust,
                    shape = RoundedCornerShape(6.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    contentPadding = PaddingValues(12.dp, 6.dp),
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Trust", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun AvailableExtensionCard(
    extension: Extension.Available,
    isInstalled: Boolean,
    isInstalling: Boolean,
    installProgress: Float,
    onInstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(extension.name, fontWeight = FontWeight.Medium)
                    if (extension.isNsfw) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "NSFW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    "v${extension.versionName} · ${extension.lang} · ${extension.sources.size} sources",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Show cached health badges (gray if never installed before)
                if (extension.sources.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    val displaySources = extension.sources.take(8)
                    val remaining = extension.sources.size - displaySources.size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        displaySources.forEach { animeSource ->
                            SourceHealthBadge(
                                sourceId = animeSource.id,
                            )
                        }
                        if (remaining > 0) {
                            Text(
                                "+$remaining",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                }
            }
            if (isInstalled) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Installed",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else if (isInstalling) {
                CircularProgressIndicator(
                    progress = { installProgress },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Button(
                    onClick = onInstall,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp, 4.dp),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Install", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
