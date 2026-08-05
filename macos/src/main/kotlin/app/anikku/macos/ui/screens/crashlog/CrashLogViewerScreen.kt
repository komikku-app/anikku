package app.anikku.macos.ui.screens.crashlog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.MacOSShareUtil
import app.anikku.macos.platform.logging.CrashReporter
import app.anikku.macos.platform.logging.TerminalErrorLogger
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tabs available in the crash log viewer's list view.
 */
private enum class LogViewerTab(val label: String) {
    CRASH_LOGS("Crash Logs"),
    UI_ERRORS("UI Errors"),
}

/**
 * Crash & error log viewer screen.
 *
 * Displays two tabs:
 * - **Crash Logs** — persistent crash/error log files from [CrashReporter]
 * - **UI Errors** — in-memory UI errors from [TerminalErrorLogger] (current session only)
 *
 * Tapping a crash log opens a detail view with the full file contents and
 * Share/Copy actions.
 *
 * This screen is reachable from **Settings → Diagnostics → Crash & Error Logs**.
 */
class CrashLogViewerScreen : AnikkuScreen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current

        var logFiles by remember { mutableStateOf(CrashReporter.getRecentCrashLogs(maxAgeDays = 30)) }
        var selectedLog by remember { mutableStateOf<File?>(null) }
        var selectedLogContent by remember { mutableStateOf<String?>(null) }
        var showClearAllDialog by remember { mutableStateOf(false) }
        var showClearUiErrorsDialog by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableStateOf(LogViewerTab.CRASH_LOGS) }

        // Reload helper
        fun refresh() {
            logFiles = CrashReporter.getRecentCrashLogs(maxAgeDays = 30)
        }

        // Clear all crash logs confirmation dialog
        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                icon = {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                title = { Text("Clear All Crash Logs?") },
                text = {
                    Text(
                        "This will permanently delete all ${logFiles.size} crash/error log " +
                            "file(s). This action cannot be undone.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            logFiles.forEach { it.delete() }
                            logFiles = emptyList()
                            selectedLog = null
                            showClearAllDialog = false
                            toastHost.show("All crash logs deleted", ToastDuration.SHORT)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Delete All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // Clear UI errors confirmation dialog
        if (showClearUiErrorsDialog) {
            val errorCount = TerminalErrorLogger.errorCount
            AlertDialog(
                onDismissRequest = { showClearUiErrorsDialog = false },
                icon = {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                title = { Text("Clear UI Errors?") },
                text = {
                    Text(
                        "This will clear all $errorCount captured UI error(s) " +
                            "from the current session. This action cannot be undone.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            TerminalErrorLogger.clear()
                            showClearUiErrorsDialog = false
                            toastHost.show("UI errors cleared", ToastDuration.SHORT)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Clear All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearUiErrorsDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        val titleText = when {
            selectedLog != null -> selectedLog!!.name
            selectedTab == LogViewerTab.UI_ERRORS -> "UI Errors"
            else -> "Crash & Error Logs"
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(titleText) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (selectedLog != null) {
                                    selectedLog = null
                                } else {
                                    navigator.pop()
                                }
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    actions = {
                        if (selectedLog == null) {
                            if (selectedTab == LogViewerTab.CRASH_LOGS) {
                                // Refresh
                                IconButton(onClick = { refresh() }) {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        contentDescription = "Refresh",
                                    )
                                }
                                // Clear all crash logs
                                if (logFiles.isNotEmpty()) {
                                    IconButton(onClick = { showClearAllDialog = true }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Clear all",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            } else {
                                // Clear all UI errors
                                if (TerminalErrorLogger.errorCount > 0) {
                                    IconButton(onClick = { showClearUiErrorsDialog = true }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Clear UI errors",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        } else {
                            // Reveal log file in Finder
                            TextButton(
                                onClick = {
                                    try {
                                        ProcessBuilder("open", "-R", selectedLog!!.absolutePath).start()
                                        toastHost.show("Revealed in Finder", ToastDuration.SHORT)
                                    } catch (_: Exception) {
                                        toastHost.show("Failed to reveal in Finder", ToastDuration.LONG)
                                    }
                                },
                            ) {
                                Text("Reveal", style = MaterialTheme.typography.labelMedium)
                            }
                            // Share log file via macOS native Share Menu
                            TextButton(
                                onClick = {
                                    val content = selectedLogContent.orEmpty()
                                    val logName = selectedLog!!.nameWithoutExtension
                                    val shareFile = java.io.File.createTempFile("$logName-", ".log")
                                    shareFile.deleteOnExit()
                                    try {
                                        shareFile.writeText(content)
                                        val shared = MacOSShareUtil.shareFile(shareFile)
                                        if (shared) {
                                            toastHost.show("Share menu opened", ToastDuration.SHORT)
                                        } else {
                                            toastHost.show("Share failed — unsupported on this system", ToastDuration.LONG)
                                        }
                                    } catch (_: Exception) {
                                        toastHost.show("Failed to export log file", ToastDuration.LONG)
                                    }
                                },
                            ) {
                                Text("Share", style = MaterialTheme.typography.labelMedium)
                            }
                            // Copy full log to clipboard
                            TextButton(
                                onClick = {
                                    val content = selectedLogContent.orEmpty()
                                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                    clipboard.setContents(java.awt.datatransfer.StringSelection(content), null)
                                    toastHost.show("Log copied to clipboard", ToastDuration.SHORT)
                                },
                            ) {
                                Text("Copy", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            if (selectedLog != null) {
                // ── Detail view ────────────────────────────────────────────
                LogDetailView(
                    file = selectedLog!!,
                    modifier = Modifier.padding(padding),
                    onContentLoaded = { selectedLogContent = it },
                )
            } else {
                // ── Tab bar ────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    LogViewerTabBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        crashLogCount = logFiles.size,
                        uiErrorCount = TerminalErrorLogger.errorCount,
                    )

                    // ── Tab content ────────────────────────────────────────
                    when (selectedTab) {
                        LogViewerTab.CRASH_LOGS -> CrashLogsTab(
                            logFiles = logFiles,
                            onLogClick = { selectedLog = it },
                        )
                        LogViewerTab.UI_ERRORS -> UiErrorsTab()
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab bar
// -----------------------------------------------------------------------------

@Composable
private fun LogViewerTabBar(
    selectedTab: LogViewerTab,
    onTabSelected: (LogViewerTab) -> Unit,
    crashLogCount: Int,
    uiErrorCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LogViewerTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            val count = when (tab) {
                LogViewerTab.CRASH_LOGS -> crashLogCount
                LogViewerTab.UI_ERRORS -> uiErrorCount
            }
            TextButton(
                onClick = { onTabSelected(tab) },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else Color.Transparent,
                    ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "${tab.label} ($count)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider()
}

// -----------------------------------------------------------------------------
// Crash logs tab
// -----------------------------------------------------------------------------

@Composable
private fun CrashLogsTab(
    logFiles: List<File>,
    onLogClick: (File) -> Unit,
) {
    if (logFiles.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF4CAF50).copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No crash logs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No crash or error logs have been recorded. " +
                        "Crash logs are created when the app encounters " +
                        "unexpected errors or crashes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Log location: ~/Library/Logs/Anikku/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "header") {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Text(
                        text = "${logFiles.size} log file(s) found",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Click a log to view its contents",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            items(
                items = logFiles,
                key = { it.absolutePath },
            ) { file ->
                LogFileCard(
                    file = file,
                    onClick = { onLogClick(file) },
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// UI errors tab
// -----------------------------------------------------------------------------

@Composable
private fun UiErrorsTab() {
    // Re-read errors each time this composable enters composition
    // so the list updates when the user switches to this tab.
    var errors by remember { mutableStateOf(TerminalErrorLogger.errors) }

    // Refresh errors when the tab becomes visible.
    LaunchedEffect(Unit) {
        errors = TerminalErrorLogger.errors
    }

    if (errors.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF4CAF50).copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No UI errors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No UI errors have been captured this session. " +
                        "UI errors are recorded when pop-in error toasts, " +
                        "banners, or alert dialogs are shown to the user.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Errors are shown here until the app is closed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    } else {
        val groups = remember(errors) {
            errors
                .groupBy { Pair(it.message, it.source) }
                .values
                .map { group ->
                    UiErrorGroup(
                        message = group.first().message,
                        source = group.first().source,
                        location = group.first().location,
                        throwable = group.first().throwable,
                        stackTrace = group.first().stackTrace,
                        count = group.size,
                        firstSeen = group.minOf { it.timestamp },
                        lastSeen = group.maxOf { it.timestamp },
                    )
                }
                .sortedBy { it.firstSeen }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "header") {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Text(
                        text = "${errors.size} error(s) in ${groups.size} group(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Errors are grouped by message + source. In-memory only — cleared on app exit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            items(
                items = groups,
                key = { "${it.message}-${it.source}-${it.firstSeen}" },
            ) { group ->
                UiErrorCard(group = group)
            }
        }
    }
}

/**
 * Simple data class for a grouped set of identical UI errors.
 */
private data class UiErrorGroup(
    val message: String,
    val source: String?,
    val location: String?,
    val throwable: Throwable?,
    val stackTrace: String?,
    val count: Int,
    val firstSeen: java.time.Instant,
    val lastSeen: java.time.Instant,
)

@Composable
private fun UiErrorCard(group: UiErrorGroup) {
    val dateFormat = remember {
        java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
    }

    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (group.count > 1) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = if (group.count > 1) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row {
                        group.source?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        group.location?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        if (group.count > 1) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    text = "${group.count}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = dateFormat.format(group.firstSeen),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        if (group.count > 1) {
                            Text(
                                text = " – ${dateFormat.format(group.lastSeen)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            // Expandable stack trace
            if (expanded && group.stackTrace != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                group.throwable?.let { throwable ->
                    Text(
                        text = "${throwable.javaClass.simpleName}: ${throwable.message}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E1E1E))
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    Text(
                        text = group.stackTrace,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFD4D4D4),
                        ),
                    )
                }
            } else if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No stack trace available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Log detail view
// -----------------------------------------------------------------------------

@Composable
private fun LogDetailView(
    file: File,
    modifier: Modifier = Modifier,
    onContentLoaded: (String) -> Unit = {},
) {
    var content by remember(file) {
        mutableStateOf(
            try {
                file.readText()
            } catch (e: Exception) {
                "Failed to read log file: ${e.message}"
            },
        )
    }

    // Notify the parent once content is loaded so Copy can reuse it.
    LaunchedEffect(content) {
        onContentLoaded(content)
    }

    // File metadata header
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val fileSizeFormatted = remember(file) {
        val bytes = file.length()
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
        }
    }
    val lastModified = remember(file) { dateFormat.format(Date(file.lastModified())) }

    Column(modifier = modifier.fillMaxSize()) {
        // Metadata card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        text = "Modified: $lastModified",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Size: $fileSizeFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = file.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Log content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E1E1E)),
        ) {
            val scrollState = rememberScrollState()

            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFD4D4D4),
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Log file card (list item)
// -----------------------------------------------------------------------------

@Composable
private fun LogFileCard(
    file: File,
    onClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val lastModified = remember(file) { dateFormat.format(Date(file.lastModified())) }
    val fileSizeFormatted = remember(file) {
        val bytes = file.length()
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
        }
    }

    // Determine if this is a crash or an event-only log
    val isCrashLog = remember(file) {
        try {
            file.readText().contains("CRASH — Uncaught Exception")
        } catch (_: Exception) {
            false
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCrashLog) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isCrashLog) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isCrashLog) Icons.Outlined.Warning
                    else Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = if (isCrashLog) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            // File info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isCrashLog) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "CRASH",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        text = lastModified,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = fileSizeFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
