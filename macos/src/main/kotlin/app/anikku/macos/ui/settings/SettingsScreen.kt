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
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.anikku.macos.ui.components.IconItem
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.SelectItem
import app.anikku.macos.ui.components.SliderItem
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.platform.data.LocalDownloadManager
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

        // Search — filters sections and auto-expands matches.
        var searchQuery by remember { mutableStateOf("") }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search settings") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

        // =====================================================================
        // Appearance
        // =====================================================================
        SettingsSection(
            title = "Appearance",
            searchQuery = searchQuery,
            searchLabels = listOf("Theme", "Theme mode", "Light", "Dark", "System", "AMOLED black"),
        ) {

        // Theme mode (System / Light / Dark) — was previously only reachable
        // during onboarding; lives here so it can be changed any time.
        val themeModeLabels = arrayOf("Follow system", "Light", "Dark")
        SelectItem(
            label = "Theme mode",
            options = themeModeLabels,
            selectedIndex = ThemeMode.entries.indexOf(settings.themeMode).coerceAtLeast(0),
            onSelect = { index ->
                settings.themeMode = ThemeMode.entries[index]
                toastHost.show("Theme mode: ${themeModeLabels[index]}", ToastDuration.SHORT)
            },
        )

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

        }
        // =====================================================================
        // Library
        // =====================================================================
        SettingsSection(
            title = "Library",
            searchQuery = searchQuery,
            searchLabels = listOf("Library update schedule", "Auto-download new episodes"),
        ) {

        val updateIntervals = intArrayOf(0, 1, 2, 4, 6, 12, 24)
        val updateIntervalLabels = arrayOf("Off", "Hourly", "Every 2 hours", "Every 4 hours", "Every 6 hours", "Every 12 hours", "Daily")
        SelectItem(
            label = "Library update schedule",
            options = updateIntervalLabels,
            selectedIndex = updateIntervals.indexOf(settings.libraryUpdateIntervalHours).coerceAtLeast(0),
            onSelect = { settings.libraryUpdateIntervalHours = updateIntervals[it] },
        )

        // Auto-download newly discovered episodes for opted-in anime. The
        // per-anime switch lives on each anime's detail page; without the
        // master switch here nothing downloads.
        var autoDownloadNew by remember { mutableStateOf(settings.autoDownloadNewEpisodes) }
        CheckboxItem(
            label = "Auto-download new episodes",
            checked = autoDownloadNew,
            onClick = {
                autoDownloadNew = !autoDownloadNew
                settings.autoDownloadNewEpisodes = autoDownloadNew
                toastHost.show(
                    "Auto-download: ${if (autoDownloadNew) "on — pick anime on their detail pages" else "off"}",
                    ToastDuration.SHORT,
                )
            },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        }
        // =====================================================================
        // Player
        // =====================================================================
        SettingsSection(
            title = "Player",
            searchQuery = searchQuery,
            searchLabels = listOf(
                "Auto-play next episode", "Resume from last position", "Skip intro (when available)",
                "Default playback speed", "Subtitle font size", "Subtitle position", "App lock", "PIN", "Touch ID",
            ),
        ) {

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

        var subtitleFontSize by remember { mutableStateOf(settings.subtitleFontSize.toInt()) }
        SliderItem(
            label = "Subtitle font size",
            value = subtitleFontSize,
            valueText = "$subtitleFontSize",
            onChange = {
                subtitleFontSize = it
                settings.subtitleFontSize = it.toFloat()
            },
            max = 160,
            min = 20,
        )

        var subtitlePosition by remember { mutableStateOf(settings.subtitlePosition) }
        SliderItem(
            label = "Subtitle position",
            value = subtitlePosition,
            valueText = "$subtitlePosition",
            onChange = {
                subtitlePosition = it
                settings.subtitlePosition = it
            },
            max = 150,
            min = 0,
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Security
        // =====================================================================
        SecuritySettingsPanel()

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        }
        // =====================================================================
        // Playback
        // =====================================================================
        SettingsSection(
            title = "Playback",
            searchQuery = searchQuery,
            searchLabels = listOf("Seek increment", "GIF clip length", "Screenshot format", "Screenshot folder"),
        ) {

        var seekIncrement by remember { mutableStateOf(settings.seekIncrementSeconds) }
        val seekOptions = intArrayOf(5, 10, 15, 30, 60)
        SelectItem(
            label = "Seek increment",
            options = arrayOf("5 seconds", "10 seconds", "15 seconds", "30 seconds", "60 seconds"),
            selectedIndex = seekOptions.indexOf(seekIncrement).coerceAtLeast(0),
            onSelect = {
                seekIncrement = seekOptions[it]
                settings.seekIncrementSeconds = seekIncrement
                toastHost.show("Seek increment: ${seekIncrement}s", ToastDuration.SHORT)
            },
        )

        var clipSeconds by remember { mutableStateOf(settings.clipCaptureSeconds) }
        SliderItem(
            label = "GIF clip length",
            value = clipSeconds,
            valueText = "${clipSeconds}s",
            onChange = {
                clipSeconds = it
                settings.clipCaptureSeconds = it
            },
            max = 10,
            min = 3,
        )

        SelectItem(
            label = "Screenshot format",
            options = arrayOf("PNG", "JPG"),
            selectedIndex = if (settings.screenshotFormat == "jpg") 1 else 0,
            onSelect = {
                settings.screenshotFormat = if (it == 0) "png" else "jpg"
                toastHost.show("Screenshot format: ${settings.screenshotFormat.uppercase()}", ToastDuration.SHORT)
            },
        )

        // Screenshot / GIF-clip folder. Empty = default ~/Pictures/Anikku.
        IconItem(
            label = if (settings.screenshotDirectory.isBlank()) {
                "Screenshot folder: Pictures/Anikku (default)"
            } else {
                "Screenshot folder: ${settings.screenshotDirectory}"
            },
            icon = Icons.Outlined.Folder,
            onClick = {
                val picker = runCatching {
                    org.koin.core.context.GlobalContext.get()
                        .get<app.anikku.macos.platform.storage.MacOSFilePicker>()
                }.getOrNull()
                if (picker == null) {
                    toastHost.show("Folder picker unavailable", ToastDuration.SHORT, isError = true)
                } else {
                    val folder = picker.openDirectory(title = "Choose screenshot folder")
                    if (folder != null) {
                        settings.screenshotDirectory = folder.absolutePath
                        toastHost.show("Screenshots will be saved to ${folder.absolutePath}", ToastDuration.SHORT)
                    }
                }
            },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        }
        // =====================================================================
        // Downloads
        // =====================================================================
        SettingsSection(
            title = "Downloads",
            searchQuery = searchQuery,
            searchLabels = listOf("Simultaneous downloads", "View Downloads"),
        ) {

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

        // Download location — only new downloads use it; existing files stay.
        val downloadManagerForFolder = LocalDownloadManager.current
        IconItem(
            label = if (settings.downloadDirectory.isBlank()) {
                "Download location: Anikku data folder (default)"
            } else {
                "Download location: ${settings.downloadDirectory}"
            },
            icon = Icons.Outlined.Folder,
            onClick = {
                val picker = runCatching {
                    org.koin.core.context.GlobalContext.get()
                        .get<app.anikku.macos.platform.storage.MacOSFilePicker>()
                }.getOrNull()
                if (picker == null) {
                    toastHost.show("Folder picker unavailable", ToastDuration.SHORT, isError = true)
                } else {
                    val folder = picker.openDirectory(title = "Choose download folder")
                    if (folder != null) {
                        settings.downloadDirectory = folder.absolutePath
                        downloadManagerForFolder?.setDownloadsDirectory(folder.absolutePath)
                        toastHost.show("New downloads will be saved to ${folder.absolutePath}", ToastDuration.SHORT)
                    }
                }
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

        }
        // =====================================================================
        // Data & Storage
        // =====================================================================
        SettingsSection(
            title = "Data & Storage",
            searchQuery = searchQuery,
            searchLabels = listOf(
                "Automatic local backups", "New episode notifications", "Google Drive backup schedule",
                "Backup & Restore", "Proxy", "Chrome", "Subtitles", "Jimaku", "OpenSubtitles",
            ),
        ) {

        val backupIntervals = intArrayOf(0, 6, 12, 24, 48, 168)
        val backupIntervalLabels = arrayOf("Off", "Every 6 hours", "Every 12 hours", "Daily", "Every 2 days", "Weekly")
        SelectItem(
            label = "Automatic local backups",
            options = backupIntervalLabels,
            selectedIndex = backupIntervals.indexOf(settings.autoBackupIntervalHours).coerceAtLeast(0),
            onSelect = { settings.autoBackupIntervalHours = backupIntervals[it] },
        )

        val updateIntervals = intArrayOf(0, 1, 2, 4, 6, 12, 24)
        val updateIntervalLabels = arrayOf("Off", "Hourly", "Every 2 hours", "Every 4 hours", "Every 6 hours", "Every 12 hours", "Daily")
        var newEpisodeNotifications by remember { mutableStateOf(settings.newEpisodeNotificationsEnabled) }
        CheckboxItem(
            label = "New episode notifications",
            checked = newEpisodeNotifications,
            onClick = {
                newEpisodeNotifications = !newEpisodeNotifications
                settings.newEpisodeNotificationsEnabled = newEpisodeNotifications
                toastHost.show(
                    "New episode notifications: ${if (newEpisodeNotifications) "on" else "off"}",
                    ToastDuration.SHORT,
                )
            },
        )
        SelectItem(
            label = "Google Drive backup schedule",
            options = updateIntervalLabels,
            selectedIndex = updateIntervals.indexOf(settings.googleDriveSyncIntervalHours).coerceAtLeast(0),
            onSelect = { settings.googleDriveSyncIntervalHours = updateIntervals[it] },
        )
        SelectItem(
            label = "SyncYomi schedule",
            options = updateIntervalLabels,
            selectedIndex = updateIntervals.indexOf(settings.syncYomiIntervalHours).coerceAtLeast(0),
            onSelect = { settings.syncYomiIntervalHours = updateIntervals[it] },
        )

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
                                app.anikku.macos.platform.storage.MacOSStorageProvider.baseDirectory,
                                "backups",
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
        // Subtitles (Jimaku + OpenSubtitles credentials)
        // =====================================================================
        SubtitleSettingsPanel()

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

        }
        // =====================================================================
        // Connections
        // =====================================================================
        SettingsSection(
            title = "Connections",
            searchQuery = searchQuery,
            searchLabels = listOf("SyncYomi", "Discord Rich Presence"),
        ) {
        SyncYomiSettingsPanel()
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

        }
        // =====================================================================
        // Statistics
        // =====================================================================
        val statsNav = LocalNavigator.currentOrThrow
        SettingsSection(title = "Statistics", searchQuery = searchQuery) {

        NavCard(
            icon = { Icon(Icons.Outlined.BarChart, contentDescription = null, modifier = Modifier.size(24.dp)) },
            title = "Watch Statistics",
            subtitle = "View anime watching stats, genres, and activity",
            onClick = { statsNav.push(StatsScreen()) },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        }
        // =====================================================================
        // Diagnostics
        // =====================================================================
        val diagNav = LocalNavigator.currentOrThrow
        SettingsSection(title = "Diagnostics", searchQuery = searchQuery) {

        NavCard(
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(24.dp)) },
            title = "Crash & Error Logs",
            subtitle = "View crash reports and error logs from this session",
            onClick = { diagNav.push(CrashLogViewerScreen()) },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        }
        // =====================================================================
        // About
        // =====================================================================
        SettingsSection(title = "About", searchQuery = searchQuery) {

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
                text = "Version ${app.anikku.macos.platform.update.AppInfo.VERSION}",
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


/**
 * Collapsible settings section. Searching auto-expands matching sections and
 * hides the rest.
 */
@Composable
private fun SettingsSection(
    title: String,
    searchQuery: String,
    searchLabels: List<String> = emptyList(),
    content: @Composable () -> Unit,
) {
    val matches = searchQuery.isBlank() ||
        title.contains(searchQuery, ignoreCase = true) ||
        searchLabels.any { it.contains(searchQuery, ignoreCase = true) }
    var expanded by remember { mutableStateOf(true) }
    if (!matches && searchQuery.isNotBlank()) return
    HeadingItem(title, onClick = { expanded = !expanded })
    // NOTE: AnimatedVisibility must NOT be used here. Inside the scrollable
    // Column its SubcomposeLayout places every child at the same offset, so a
    // section's rows stack on top of each other (text over text — the "squashed
    // settings" bug). animateContentSize keeps the collapse animation while
    // laying children out normally.
    Column(
        modifier = Modifier.animateContentSize(),
    ) {
        if (expanded || searchQuery.isNotBlank()) {
            content()
        }
    }
}
