package app.anikku.macos.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.backup.ImportResult
import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.ui.components.HeadingItem
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class representing a scanned backup file on disk.
 */
private data class BackupEntry(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val libraryCount: Int = 0,
    val historyCount: Int = 0,
    val downloadsCount: Int = 0,
    val version: Int = 1,
)

/**
 * Backup & Restore settings panel.
 *
 * Features:
 * - **Create Backup**: Exports all app data (library, history, downloads, preferences)
 *   to a timestamped JSON file in the backups directory.
 * - **Backup Timeline**: Lists all existing backup files with size, date, and entry counts.
 * - **Restore**: Imports a selected backup file, restoring library, history, and preferences.
 * - **Delete**: Removes individual backup files.
 * - **Import from file**: Opens a file picker to restore from any location.
 *
 * This panel is accessible from the Settings screen via a NavCard.
 */
@Composable
fun BackupRestorePanel(
    backupManager: MacOSBackupManager,
    backupsDir: File,
) {
    val navigator = LocalNavigator.currentOrThrow
    val toastHost = LocalToastHost.current
    val scope = rememberCoroutineScope()

    // State
    val backupEntries = remember { mutableStateListOf<BackupEntry>() }
    var isCreating by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var confirmRestoreFile by remember { mutableStateOf<File?>(null) }
    var confirmDeleteFile by remember { mutableStateOf<File?>(null) }
    var restoringFileName by remember { mutableStateOf("") }

    // Scan backups directory for existing files
    fun scanBackups() {
        scope.launch {
            isScanning = true
            backupEntries.clear()
            withContext(Dispatchers.IO) {
                val files = backupsDir.listFiles()
                    ?.filter { it.extension == "json" && it.name.contains("anikku_backup") }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()

                for (file in files) {
                    val entry = try {
                        // Quick metadata scan - parse date from filename, get size
                        val sizeBytes = file.length()
                        val createdAt = file.lastModified()
                        val name = file.name.removeSuffix(MacOSBackupManager.BACKUP_EXTENSION)

                        // Try to extract entry counts from the backup for richer display
                        var libCount = 0
                        var histCount = 0
                        var dlCount = 0
                        var version = 1
                        try {
                            val text = file.readText().take(5000) // Read first 5KB for metadata
                            // Count library entries by counting "animeId" occurrences
                            libCount = text.split("\"animeId\"").size - 1
                            // Count history entries (last-level entries have episodeId)
                            histCount = text.split("\"episodeId\"").size - 1
                            // Count version
                            val versionMatch = Regex("\"version\"\\s*:\\s*(\\d+)").find(text)
                            version = versionMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                        } catch (_: Exception) {
                            // Metadata scan is best-effort
                        }

                        BackupEntry(
                            file = file,
                            name = name,
                            sizeBytes = sizeBytes,
                            createdAt = createdAt,
                            libraryCount = libCount,
                            historyCount = histCount,
                            downloadsCount = dlCount,
                            version = version,
                        )
                    } catch (_: Exception) {
                        null
                    }

                    if (entry != null) {
                        backupEntries.add(entry)
                    }
                }
            }
            isScanning = false
        }
    }

    // Scan on mount
    LaunchedEffect(Unit) {
        scanBackups()
    }

    // =========================================================================
    // Restore confirmation dialog
    // =========================================================================
    if (confirmRestoreFile != null) {
        AlertDialog(
            onDismissRequest = { confirmRestoreFile = null },
            title = { Text("Restore Backup") },
            text = {
                Column {
                    Text("This will replace your current library and history with the data from the backup.")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your current data will be lost. Make sure to create a backup first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = confirmRestoreFile
                        confirmRestoreFile = null
                        if (file != null) {
                            scope.launch {
                                isRestoring = true
                                restoringFileName = file.name
                                val result = withContext(Dispatchers.IO) {
                                    backupManager.importFrom(file)
                                }
                                isRestoring = false

                                if (result.success) {
                                    toastHost.show(
                                        "Restored: ${result.libraryCount} library, ${result.historyCount} history entries",
                                        ToastDuration.LONG,
                                    )
                                } else {
                                    toastHost.show(
                                        "Restore failed: ${result.error ?: "Unknown error"}",
                                        ToastDuration.LONG,
                                        isError = true,
                                    )
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreFile = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // =========================================================================
    // Delete confirmation dialog
    // =========================================================================
    if (confirmDeleteFile != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteFile = null },
            title = { Text("Delete Backup") },
            text = {
                Text("Are you sure you want to delete \"${confirmDeleteFile?.name}\"?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = confirmDeleteFile
                        confirmDeleteFile = null
                        if (file != null) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    file.delete()
                                }
                                scanBackups()
                                toastHost.show("Backup deleted", ToastDuration.SHORT)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFile = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // =========================================================================
    // Main UI
    // =========================================================================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp),
    ) {
        // ---- Header with back button ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navigator.pop() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Backup & Restore",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        HorizontalDivider()

        // ---- Create Backup button ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isCreating = true
                        val result = withContext(Dispatchers.IO) {
                            backupManager.exportToDir(backupsDir)
                        }
                        isCreating = false

                        if (result != null) {
                            toastHost.show(
                                "Backup created: ${result.name}",
                                ToastDuration.LONG,
                            )
                            scanBackups()
                        } else {
                            toastHost.show(
                                "Backup creation failed",
                                ToastDuration.LONG,
                                isError = true,
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                enabled = !isCreating,
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Creating backup...")
                } else {
                    Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Create Backup", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Exports library, history, downloads, and preferences to a backup file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        // ---- Import hint ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                "💡 Tip: Use File > Open Backup in the menu bar (⌘O) to restore a backup from any location on your computer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        // ---- Restoring indicator ----
        if (isRestoring) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Restoring...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            restoringFileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---- Backup timeline ----
        HeadingItem("Backup History")

        if (isScanning && backupEntries.isEmpty()) {
            // Loading state
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (backupEntries.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Backup,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No backups yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Create your first backup to protect your library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            backupEntries.forEach { entry ->
                BackupEntryCard(
                    entry = entry,
                    onRestore = { confirmRestoreFile = entry.file },
                    onDelete = { confirmDeleteFile = entry.file },
                    isRestoring = isRestoring,
                )
            }
        }

        // ---- Storage info ----
        Spacer(Modifier.height(16.dp))
        HeadingItem("Storage")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            val backupCount = backupEntries.size
            val totalSize = backupEntries.sumOf { it.sizeBytes }
            val totalSizeMB = "%.1f".format(totalSize / 1_048_576.0)

            Text(
                text = "$backupCount backup(s) using $totalSizeMB MB",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Directory: ${backupsDir.absolutePath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// =============================================================================
// Backup entry card
// =============================================================================

@Composable
private fun BackupEntryCard(
    entry: BackupEntry,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    isRestoring: Boolean,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.US) }
    val sizeStr = remember(entry.sizeBytes) {
        when {
            entry.sizeBytes < 1024 -> "${entry.sizeBytes} B"
            entry.sizeBytes < 1_048_576 -> "%.1f KB".format(entry.sizeBytes / 1024.0)
            else -> "%.1f MB".format(entry.sizeBytes / 1_048_576.0)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            // Top row: Name + date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(Date(entry.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = sizeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Entry counts row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (entry.libraryCount > 0) {
                    CountChip(
                        icon = { Icon(Icons.AutoMirrored.Outlined.LibraryBooks, null, Modifier.size(14.dp)) },
                        label = "${entry.libraryCount} anime",
                    )
                }
                if (entry.historyCount > 0) {
                    CountChip(
                        icon = { Icon(Icons.Outlined.Refresh, null, Modifier.size(14.dp)) },
                        label = "${entry.historyCount} episodes",
                    )
                }
                CountChip(
                    icon = { Text("v", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 2.dp)) },
                    label = "${entry.version}",
                )
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Delete button
                IconButton(
                    onClick = onDelete,
                    enabled = !isRestoring,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete backup",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Restore button
                Button(
                    onClick = onRestore,
                    enabled = !isRestoring,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Restore",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

// =============================================================================
// Count chip helper
// =============================================================================

@Composable
private fun CountChip(
    icon: @Composable () -> Unit,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
    }
}
