package app.anikku.macos.ui.settings

import androidx.compose.runtime.Composable
import app.anikku.macos.platform.backup.MacOSBackupManager
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import java.io.File

/**
 * Voyager Screen wrapper for [BackupRestorePanel].
 *
 * Provides proper Voyager navigation support with a unique screen key
 * so the navigator can differentiate between instances.
 */
data class BackupRestoreScreen(
    val backupManager: MacOSBackupManager,
    val backupsDir: File,
) : Screen {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        BackupRestorePanel(
            backupManager = backupManager,
            backupsDir = backupsDir,
        )
    }
}
