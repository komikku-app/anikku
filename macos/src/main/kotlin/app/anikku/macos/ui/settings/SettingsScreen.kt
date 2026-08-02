package app.anikku.macos.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.auth.LocalTrackerManager
import app.anikku.macos.platform.backup.LocalBackupManager
import app.anikku.macos.platform.discord.LocalDiscordRPC
import app.anikku.macos.platform.web.BrowserLauncher
import app.anikku.macos.ui.components.CheckboxItem
import app.anikku.macos.ui.components.HeadingItem
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.SelectItem
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.crashlog.CrashLogViewerScreen
import app.anikku.macos.ui.screens.downloads.DownloadQueueScreen
import app.anikku.macos.ui.screens.stats.StatsScreen
import app.anikku.macos.ui.theme.AnikkuTheme
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

/**
 * Settings screen — Phase 5 expanded.
 *
 * Provides multiple preference categories:
 * - Appearance: theme selector (18+ color schemes), AMOLED black toggle
 * - Library: badges, tabs preferences
 * - Player: default player behavior
 * - Tracking: tracker login and synchronization controls
 * - About: app version, build info
 *
 * Preferences are read/written through [SettingsState] via [LocalSettingsState].
 */
@Composable
fun SettingsScreen() {
    val settings = LocalSettingsState.current
    val toastHost = LocalToastHost.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 80.dp),
    ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        )

        HorizontalDivider()

        // =====================================================================
        // Appearance
        // =====================================================================
        HeadingItem("Appearance")

        // Theme selector
        val themeNames = remember { AnikkuTheme.allThemes.map { it.displayName }.toTypedArray() }
        var themeIndex by remember(settings.theme) {
            mutableStateOf(AnikkuTheme.allThemes.indexOf(settings.theme).coerceAtLeast(0))
        }
        SelectItem(
            label = "Theme",
            options = themeNames,
            selectedIndex = themeIndex,
            onSelect = { index ->
                val theme = AnikkuTheme.allThemes[index]
                settings.theme = theme
                toastHost.show("Theme: ${theme.displayName}", ToastDuration.SHORT)
            },
        )

        // AMOLED black toggle
        var amoled by remember(settings.isAmoledOLED) { mutableStateOf(settings.isAmoledOLED) }
        CheckboxItem(
            label = "AMOLED black",
            checked = amoled,
            onClick = {
                amoled = !amoled
                settings.isAmoledOLED = amoled
                toastHost.show("AMOLED black: ${if (amoled) "on" else "off"}", ToastDuration.SHORT)
            },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Library
        // =====================================================================
        HeadingItem("Library")

        var showCategoryTabs by remember { mutableStateOf(true) }
        CheckboxItem(
            label = "Show category tabs",
            checked = showCategoryTabs,
            onClick = {
                showCategoryTabs = !showCategoryTabs
                toastHost.show("Category tabs: ${if (showCategoryTabs) "on" else "off"}", ToastDuration.SHORT)
            },
        )

        var showEpisodeCount by remember { mutableStateOf(false) }
        CheckboxItem(
            label = "Show number of items",
            checked = showEpisodeCount,
            onClick = {
                showEpisodeCount = !showEpisodeCount
                toastHost.show("Item count: ${if (showEpisodeCount) "on" else "off"}", ToastDuration.SHORT)
            },
        )

        var downloadBadge by remember { mutableStateOf(true) }
        CheckboxItem(
            label = "Show download badge",
            checked = downloadBadge,
            onClick = {
                downloadBadge = !downloadBadge
                toastHost.show("Download badge: ${if (downloadBadge) "on" else "off"}", ToastDuration.SHORT)
            },
        )

        var localBadge by remember { mutableStateOf(true) }
        CheckboxItem(
            label = "Show local badge",
            checked = localBadge,
            onClick = {
                localBadge = !localBadge
                toastHost.show("Local badge: ${if (localBadge) "on" else "off"}", ToastDuration.SHORT)
            },
        )

        var languageBadge by remember { mutableStateOf(true) }
        CheckboxItem(
            label = "Show language badge",
            checked = languageBadge,
            onClick = {
                languageBadge = !languageBadge
                toastHost.show("Language badge: ${if (languageBadge) "on" else "off"}", ToastDuration.SHORT)
            },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Player
        // =====================================================================
        HeadingItem("Player")

        var autoPlay by remember { mutableStateOf(settings.autoPlayNextEpisode) }
        CheckboxItem(
            label = "Auto-play next episode",
            checked = autoPlay,
            onClick = {
                autoPlay = !autoPlay
                settings.autoPlayNextEpisode = autoPlay
                toastHost.show("Auto-play: ${if (autoPlay) "on" else "off"}", ToastDuration.SHORT)
            },
        )

        var resumeFromLast by remember { mutableStateOf(settings.resumeFromLastPosition) }
        CheckboxItem(
            label = "Resume from last position",
            checked = resumeFromLast,
            onClick = {
                resumeFromLast = !resumeFromLast
                settings.resumeFromLastPosition = resumeFromLast
                toastHost.show("Resume: ${if (resumeFromLast) "on" else "off"}", ToastDuration.SHORT)
            },
        )

        var skipIntro by remember { mutableStateOf(settings.skipIntro) }
        CheckboxItem(
            label = "Skip intro (when available)",
            checked = skipIntro,
            onClick = {
                skipIntro = !skipIntro
                settings.skipIntro = skipIntro
                toastHost.show("Skip intro: ${if (skipIntro) "on" else "off"}", ToastDuration.SHORT)
            },
        )

        val playbackSpeedOptions = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        var speedIndex by remember { mutableStateOf(speedValues.indexOfFirst { it == settings.defaultPlaybackSpeed }.coerceAtLeast(0)) }
        SelectItem(
            label = "Default playback speed",
            options = playbackSpeedOptions,
            selectedIndex = speedIndex,
            onSelect = {
                speedIndex = it
                settings.defaultPlaybackSpeed = speedValues[it]
                toastHost.show("Speed: ${playbackSpeedOptions[it]}", ToastDuration.SHORT)
            },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Security
        // =====================================================================
        SecuritySettingsPanel()

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Downloads
        // =====================================================================
        HeadingItem("Downloads")

        var downloadOnWifiOnly by remember { mutableStateOf(settings.downloadOnWifiOnly) }
        CheckboxItem(
            label = "Download on Wi-Fi only",
            checked = downloadOnWifiOnly,
            onClick = {
                downloadOnWifiOnly = !downloadOnWifiOnly
                settings.downloadOnWifiOnly = downloadOnWifiOnly
                toastHost.show("Wi-Fi only: ${if (downloadOnWifiOnly) "on" else "off"}", ToastDuration.SHORT)
            },
        )

        var simultaneousDownloads by remember { mutableStateOf(settings.simultaneousDownloads) }
        SelectItem(
            label = "Simultaneous downloads",
            options = arrayOf("1", "2", "3", "4", "5"),
            selectedIndex = (simultaneousDownloads - 1).coerceIn(0, 4),
            onSelect = {
                simultaneousDownloads = it + 1
                settings.simultaneousDownloads = simultaneousDownloads
                toastHost.show("Downloads: ${simultaneousDownloads} simultaneous", ToastDuration.SHORT)
            },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // Navigate to download queue
        val downloadNav = LocalNavigator.currentOrThrow
        NavCard(
            icon = { Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(24.dp)) },
            title = "View Downloads",
            subtitle = "Manage ongoing and completed downloads",
            onClick = { downloadNav.push(DownloadQueueScreen()) },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Data & Storage
        // =====================================================================
        HeadingItem("Data & Storage")

        // Backup & Restore
        val backupManager = LocalBackupManager.current
        if (backupManager != null) {
            val backupNav = LocalNavigator.currentOrThrow
            NavCard(
                icon = {
                    Icon(
                        Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                title = "Backup & Restore",
                subtitle = "Create, restore, and manage data backups",
                onClick = {
                    backupNav.push(
                        BackupRestoreScreen(
                            backupManager = backupManager,
                            backupsDir = java.io.File(
                                System.getProperty("user.home"),
                                "Library/Application Support/Anikku/backups",
                            ),
                        ),
                    )
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Network
        // =====================================================================
        NetworkSettingsPanel()

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Tracking
        // =====================================================================
        val trackerManager = LocalTrackerManager.current
        if (trackerManager != null) {
            TrackerSettingsPanel(
                trackerManager = trackerManager,
                onTrackerChanged = { /* login status updated reactively via StateFlow */ },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Connections
        // =====================================================================
        HeadingItem("Connections")
        val discordRPC = LocalDiscordRPC.current
        CheckboxItem(
            label = "Discord Rich Presence",
            checked = settings.discordRichPresenceEnabled,
            onClick = {
                val enabled = !settings.discordRichPresenceEnabled
                settings.discordRichPresenceEnabled = enabled
                if (enabled) discordRPC?.start() else discordRPC?.stop()
                toastHost.show(
                    "Discord activity: ${if (enabled) "on" else "off"}",
                    ToastDuration.SHORT,
                )
            },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Statistics
        // =====================================================================
        val statsNav = LocalNavigator.currentOrThrow
        HeadingItem("Statistics")

        NavCard(
            icon = { Icon(Icons.Outlined.BarChart, contentDescription = null, modifier = Modifier.size(24.dp)) },
            title = "Watch Statistics",
            subtitle = "View anime watching stats, genres, and activity",
            onClick = { statsNav.push(StatsScreen()) },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Diagnostics
        // =====================================================================
        val diagNav = LocalNavigator.currentOrThrow
        HeadingItem("Diagnostics")

        NavCard(
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(24.dp)) },
            title = "Crash & Error Logs",
            subtitle = "View crash reports and error logs from this session",
            onClick = { diagNav.push(CrashLogViewerScreen()) },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // About
        // =====================================================================
        HeadingItem("About")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Anikku macOS",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "A native macOS anime watching application, " +
                    "ported from the Anikku Android app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "https://github.com/ErnestHysa/anikku",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    BrowserLauncher.openSafe("https://github.com/ErnestHysa/anikku")
                },
            )
        }
    }
}

/**
 * A clickable navigation card used to navigate to sub-screens.
 */
@Composable
private fun NavCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)                .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
